package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link Molecule}'s molecular formula, e.g., {@code C2H6} for ethane.
 */
public final class MolecularFormula
{
    private static final String WILDCARD = "*";

    private final Map<String, Integer> counts;
    private final int charge;

    /**
     * @param counts each element's count, iterating in the order they are to be written
     * @param charge the net formal charge
     */
    private MolecularFormula(Map<String, Integer> counts, int charge)
    {
        this.counts = counts;
        this.charge = charge;
    }

    /**
     * Counts a molecule's elements, implicit hydrogens included, writing them in
     * {@link ElementOrder#CONVENTIONAL} order.
     *
     * @param molecule the molecule to count
     * @return the molecule's formula
     */
    public static MolecularFormula of(Molecule molecule)
    {
        return of(molecule, ElementOrder.CONVENTIONAL);
    }

    /**
     * Counts a molecule's elements, implicit hydrogens included.
     *
     * @param molecule the molecule to count
     * @param order the order to write the elements in
     * @return the molecule's formula
     */
    public static MolecularFormula of(Molecule molecule, ElementOrder order)
    {
        Map<String, Integer> totals = new HashMap<>();
        int charge = 0;
        for (Atom atom : molecule.atoms())
        {
            totals.merge(atom.element(), 1, Integer::sum);
            int hydrogens = molecule.totalHydrogenCount(atom);
            if (hydrogens > 0)
            {
                totals.merge("H", hydrogens, Integer::sum);
            }
            charge += atom.charge();
        }
        return new MolecularFormula(inWrittenOrder(totals, order), charge);
    }

    /**
     * @return each element's count, iterating in the order they are written, unmodifiable
     */
    public Map<String, Integer> counts()
    {
        return counts;
    }

    /**
     * @param element an element symbol, e.g. {@code "C"}
     * @return the number of {@code element} atoms counted, or {@code 0} if the molecule has none
     */
    public int countOf(String element)
    {
        return counts.getOrDefault(element, 0);
    }

    /**
     * @return the number of distinct elements present
     */
    public int elementCount()
    {
        return counts.size();
    }

    /**
     * @return the total number of atoms counted, implicit hydrogens included
     */
    public int atomCount()
    {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * @return the sum of the molecule's atoms' formal charges
     */
    public int charge()
    {
        return charge;
    }

    /**
     * Reduces this formula to the simplest whole-number ratio of its elements, e.g., {@code CH2O}
     * from {@code C6H12O6}, or {@code CH} from benzene's {@code C6H6}.
     *
     * @return the reduced formula, or this formula if it is charged, empty, or already reduced
     */
    public MolecularFormula empirical()
    {
        int divisor = counts.values().stream().mapToInt(Integer::intValue).reduce(0, MolecularFormula::greatestCommonDivisor);
        if (charge != 0 || divisor <= 1)
        {
            return this;
        }

        // dividing every count leaves the elements in the order they already sort in
        Map<String, Integer> reduced = new LinkedHashMap<>();
        counts.forEach((element, count) -> reduced.put(element, count / divisor));
        return new MolecularFormula(Collections.unmodifiableMap(reduced), charge);
    }

    /**
     * @param first one count
     * @param second the other count
     * @return the largest number dividing both {@code first} and {@code second}
     */
    private static int greatestCommonDivisor(int first, int second)
    {
        while (second != 0)
        {
            int remainder = first % second;
            first = second;
            second = remainder;
        }
        return first;
    }

    /**
     * Sorts element counts into the order they are written in.
     *
     * @param counts each element's count
     * @param order the order to write the elements in
     * @return the same counts, iterating in written order
     */
    private static Map<String, Integer> inWrittenOrder(Map<String, Integer> counts, ElementOrder order)
    {
        Comparator<String> comparator = comparatorFor(counts, order);

        Map<String, Integer> ordered = new LinkedHashMap<>();
        counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(comparator))
            .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(ordered);
    }

    /**
     * @param counts each element's count
     * @param order the order to write the elements in
     * @return the comparator to sort the element symbols with
     */
    private static Comparator<String> comparatorFor(Map<String, Integer> counts, ElementOrder order)
    {
        if (counts.containsKey("C"))
        {
            return hillOrder();
        }
        return order == ElementOrder.HILL ? alphabeticalOrder() : sequenceOrder();
    }

    /**
     * @return a comparator placing one element symbol against another, in Hill order
     */
    private static Comparator<String> hillOrder()
    {
        return Comparator.comparingInt(MolecularFormula::hillBand).thenComparing(Comparator.naturalOrder());
    }

    /**
     * @param element the element symbol to place
     * @return the element's band under Hill ordering, a lower band being written first
     */
    private static int hillBand(String element)
    {
        return switch (element)
        {
            case "C" -> 0;
            case "H" -> 1;
            case WILDCARD -> 3;
            default -> 2;
        };
    }

    /**
     * @return a comparator placing one element symbol against another alphabetically
     */
    private static Comparator<String> alphabeticalOrder()
    {
        // '*' sorts below 'A', so the wildcard needs lifting out of the alphabet to stay last
        return Comparator.comparingInt((String element) -> element.equals(WILDCARD) ? 1 : 0)
            .thenComparing(Comparator.naturalOrder());
    }

    /**
     * @return a comparator placing one element symbol against another, in IUPAC sequence order
     */
    private static Comparator<String> sequenceOrder()
    {
        // a wildcard stands for no element, so it has no place in the sequence and goes last
        return Comparator.comparingInt(element -> element.equals(WILDCARD) ? Integer.MAX_VALUE : Elements.iupacOrderOf(element));
    }

    /**
     * @return the formula, with any net charge appended, e.g., {@code C2H6} or {@code NH4+}
     */
    @Override
    public String toString()
    {
        return render(false);
    }

    /**
     * @return the formula in Unicode, with any net charge appended, e.g., {@code C₂H₆} or {@code NH₄⁺}
     */
    public String toUnicode()
    {
        return render(true);
    }

    /**
     * @param unicode whether to render the numbers as subscripts and superscripts rather than as plain digits
     * @return the formula, with any net charge appended
     */
    private String render(boolean unicode)
    {
        StringBuilder text = new StringBuilder();
        counts.forEach((element, count) ->
        {
            text.append(element);
            if (count > 1)
            {
                text.append(unicode ? Unicode.subscript(count) : String.valueOf(count));
            }
        });

        if (charge == 0)
        {
            return text.toString();
        }

        // written magnitude-first, i.e., 2- rather than -2
        int magnitude = Math.abs(charge);
        if (magnitude > 1)
        {
            text.append(unicode ? Unicode.superscript(magnitude) : String.valueOf(magnitude));
        }
        if (unicode)
        {
            text.append(charge > 0 ? Unicode.PLUS : Unicode.MINUS);
        }
        else
        {
            text.append(charge > 0 ? '+' : '-');
        }
        return text.toString();
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof MolecularFormula formula && charge == formula.charge && counts.equals(formula.counts);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(counts, charge);
    }

    /**
     * The order a {@link MolecularFormula}'s elements are written in.
     */
    public enum ElementOrder
    {
        /**
         * Conventional ordering
         * <ul>
         *     <li>Organic: carbon, then hydrogen, then every other element alphabetically</li>
         *     <li>Inorganic: follows {@link Elements#iupacOrderOf}</li>
         * </ul>
         */
        CONVENTIONAL,

        /**
         * Hill-system ordering
         * <ul>
         *     <li>Organic: carbon, then hydrogen, then every other element alphabetically</li>
         *     <li>Inorganic: alphabetical throughout (including hydrogen)</li>
         * </ul>
         */
        HILL
    }
}
