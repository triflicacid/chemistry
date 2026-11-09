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
     * Counts a molecule's elements, implicit hydrogens included.
     * <p>
     * The formulae can be rendered via {@link #toString} or {@link #render}, which allows for configuration.
     *
     * @param molecule the molecule to count
     * @return the molecule's formula
     */
    public static MolecularFormula of(Molecule molecule)
    {
        Map<String, Integer> totals = new HashMap<>();
        int charge = 0;
        for (Atom atom : molecule.atoms())
        {
            totals.merge(Nuclide.symbolOf(atom.element(), atom.isotope().orElse(0)), 1, Integer::sum);
            molecule.hydrogenIsotopeCounts(atom).forEach((massNumber, count) ->
                totals.merge(Nuclide.symbolOf("H", massNumber), count, Integer::sum));
            charge += atom.charge();
        }
        return new MolecularFormula(inWrittenOrder(totals, ElementOrder.CONVENTIONAL), charge);
    }

    /**
     * @return each nuclide's count, iterating in {@link ElementOrder#CONVENTIONAL} order, unmodifiable
     */
    public Map<String, Integer> counts()
    {
        return counts;
    }

    /**
     * Counts one nuclide exactly. A labelled nuclide is counted apart from its element, so
     * {@code countOf("H")} excludes any deuterium; use {@link #countOfElement} to include it.
     *
     * @param nuclide a nuclide symbol, e.g. {@code "C"} or {@code "2H"}
     * @return the number of {@code nuclide} atoms counted, or {@code 0} if the molecule has none
     */
    public int countOf(String nuclide)
    {
        return counts.getOrDefault(nuclide, 0);
    }

    /**
     * Counts an element across every one of its nuclides, labelled or not.
     *
     * @param element an element symbol, e.g. {@code "H"}
     * @return the number of {@code element} atoms counted, whatever their mass number
     */
    public int countOfElement(String element)
    {
        return counts.entrySet().stream()
            .filter(entry -> Nuclide.elementOf(entry.getKey()).equals(element))
            .mapToInt(Map.Entry::getValue)
            .sum();
    }

    /**
     * @return the number of distinct nuclides present
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
     * Combines this formula with another:
     * {@code other} becomes a disconnected fragment inside this molecule.
     *
     * @param other the formula to add to this one
     * @return the combined formula
     */
    public MolecularFormula plus(MolecularFormula other)
    {
        Map<String, Integer> totals = new HashMap<>(counts);
        other.counts.forEach((nuclide, count) -> totals.merge(nuclide, count, Integer::sum));
        return new MolecularFormula(inWrittenOrder(totals, ElementOrder.CONVENTIONAL), charge + other.charge);
    }

    /**
     * Multiplies every count, and the net charge, by a factor.
     * That is, adds on {@code factor} copies of this molecule as disconnected fragments.
     *
     * @param factor how many times over to count this formula, zero or above
     * @return the multiplied formula, or an empty formula if {@code factor} is zero
     * @throws IllegalArgumentException if {@code factor} is negative
     */
    public MolecularFormula times(int factor)
    {
        if (factor < 0)
        {
            throw new IllegalArgumentException("a formula cannot be counted a negative number of times: " + factor);
        }
        if (factor == 1)
        {
            return this;
        }
        if (factor == 0)
        {
            return new MolecularFormula(Map.of(), 0);
        }

        // multiplying every count leaves the elements in the order they already sort in
        Map<String, Integer> multiplied = new LinkedHashMap<>();
        counts.forEach((nuclide, count) -> multiplied.put(nuclide, count * factor));
        return new MolecularFormula(Collections.unmodifiableMap(multiplied), charge * factor);
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
     * Sorts nuclide counts into the order they are written in.
     *
     * @param counts each nuclide's count
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
     * @param counts each nuclide's count
     * @param order the order to write the elements in
     * @return the comparator to sort the nuclide symbols with
     */
    private static Comparator<String> comparatorFor(Map<String, Integer> counts, ElementOrder order)
    {
        if (counts.keySet().stream().anyMatch(nuclide -> Nuclide.elementOf(nuclide).equals("C")))
        {
            return hillOrder();
        }
        return order == ElementOrder.HILL ? alphabeticalOrder() : sequenceOrder();
    }

    /**
     * @return a comparator placing one nuclide symbol against another, in Hill order
     */
    private static Comparator<String> hillOrder()
    {
        return Comparator.comparingInt(MolecularFormula::hillBand)
            .thenComparing(Nuclide::elementOf)
            .thenComparingInt(Nuclide::massNumberOf);
    }

    /**
     * @param nuclide the nuclide symbol to place
     * @return the nuclide's band under Hill ordering, a lower band being written first
     */
    private static int hillBand(String nuclide)
    {
        return switch (Nuclide.elementOf(nuclide))
        {
            case "C" -> 0;
            case "H" -> 1;
            case WILDCARD -> 3;
            default -> 2;
        };
    }

    /**
     * @return a comparator placing one nuclide symbol against another alphabetically
     */
    private static Comparator<String> alphabeticalOrder()
    {
        // '*' sorts below 'A', so the wildcard needs lifting out of the alphabet to stay last
        return Comparator.comparingInt((String nuclide) -> Nuclide.isWildcard(nuclide) ? 1 : 0)
            .thenComparing(Nuclide::elementOf)
            .thenComparingInt(Nuclide::massNumberOf);
    }

    /**
     * @return a comparator placing one nuclide symbol against another, in IUPAC sequence order
     */
    private static Comparator<String> sequenceOrder()
    {
        // a wildcard stands for no element, so it has no place in the sequence and goes last
        return Comparator
            .comparingInt((String nuclide) ->
                Nuclide.isWildcard(nuclide) ? Integer.MAX_VALUE : Elements.iupacOrderOf(Nuclide.elementOf(nuclide)))
            .thenComparingInt(Nuclide::massNumberOf);
    }

    /**
     * @return the formula, with any net charge appended, e.g., {@code C2H6}, {@code NH4+}, or {@code C6H5[2H]}
     */
    @Override
    public String toString()
    {
        return render(FormulaStyle.PLAIN);
    }

    /**
     * @return the formula in Unicode, with any net charge appended, e.g., {@code C₂H₆} or {@code NH₄⁺}
     */
    public String toUnicode()
    {
        return render(FormulaStyle.UNICODE);
    }

    /**
     * Writes the formula, with any net charge appended.
     *
     * @param style how to write it
     * @return the formula as text
     */
    public String render(FormulaStyle style)
    {
        boolean unicode = style.unicode();
        StringBuilder text = new StringBuilder();
        inWrittenOrder(counts, style.elementOrder()).forEach((nuclide, count) ->
        {
            text.append(Nuclide.render(nuclide, unicode, style.isotopeStyle()));
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
}
