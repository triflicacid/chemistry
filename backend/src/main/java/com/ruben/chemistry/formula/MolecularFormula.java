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
 * <p>
 * A molecule containing carbon is written in Hill order: carbon first, then hydrogen, then every
 * other element alphabetically. One containing no carbon follows {@link Elements#iupacOrderOf} instead.
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
            totals.merge(atom.element(), 1, Integer::sum);
            int hydrogens = molecule.totalHydrogenCount(atom);
            if (hydrogens > 0)
            {
                totals.merge("H", hydrogens, Integer::sum);
            }
            charge += atom.charge();
        }
        return new MolecularFormula(inWrittenOrder(totals), charge);
    }

    /**
     * @param molecule the molecule to write
     * @return the molecule's formula
     */
    public static String write(Molecule molecule)
    {
        return of(molecule).toString();
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
     * Sorts element counts into the order they are written in.
     *
     * @param counts each element's count
     * @return the same counts, iterating in written order
     */
    private static Map<String, Integer> inWrittenOrder(Map<String, Integer> counts)
    {
        Comparator<String> order = counts.containsKey("C") ? hillOrder() : sequenceOrder();

        Map<String, Integer> ordered = new LinkedHashMap<>();
        counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(order))
            .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(ordered);
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
        StringBuilder text = new StringBuilder();
        counts.forEach((element, count) ->
        {
            text.append(element);
            if (count > 1)
            {
                text.append(count);
            }
        });

        if (charge != 0)
        {
            // written magnitude-first, i.e., 2- rather than -2
            if (Math.abs(charge) > 1)
            {
                text.append(Math.abs(charge));
            }
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
