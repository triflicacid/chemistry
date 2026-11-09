package com.ruben.chemistry.canonical;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.util.CachedFunction;
import com.ruben.chemistry.util.Comparators;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Compares molecules by structure: two molecules compare equal exactly when they are isomorphic,
 * stereochemistry included.
 * <p>
 * This comparator defines a total ordering, compared in tiers:
 * <ol>
 *     <li>atom count</li>
 *     <li>bond count</li>
 *     <li>how many atoms of each element, by element</li>
 *     <li>{@link CanonicalForm}</li>
 * </ol>
 * The first three tiers settle most pairs without canonicalising either molecule, which is what
 * keeps sorting a list of molecules cheap. Whether two molecules are the same is decided by the last
 * tier alone: a molecule is described once, by the canonicalisation, and never a second time here.
 */
public final class MoleculeComparator implements Comparator<Molecule>
{
    /** Total ordering for {@link ElementCount}. */
    private static final Comparator<ElementCount> ELEMENT_COUNT_ORDER = Comparator
        .comparing(ElementCount::element)
        .thenComparingInt(ElementCount::count);

    /** Total ordering for a molecule's element counts: lexicographic over {@link #ELEMENT_COUNT_ORDER}. */
    private static final Comparator<List<ElementCount>> ELEMENT_COUNTS_ORDER = Comparators.lexicographically(ELEMENT_COUNT_ORDER);

    /** Avoid re-canonicalising a molecule, as its form will never change (immutable.) */
    private final Function<Molecule, CanonicalForm> forms;

    /**
     * Creates a comparator using the default {@link CanonicalRanker}.
     */
    public MoleculeComparator()
    {
        this(new CanonicalRanker());
    }

    /**
     * @param canonicaliser the canonicalisation deciding what two molecules have to agree on to be equal
     */
    public MoleculeComparator(Canonicaliser canonicaliser)
    {
        this.forms = CachedFunction.byIdentity(canonicaliser::canonicalise);
    }

    @Override
    public int compare(Molecule first, Molecule second)
    {
        // atom and bond counts are cheap, so do them first
        int atomCountDiff = Integer.compare(first.atomCount(), second.atomCount());
        if (atomCountDiff != 0)
        {
            return atomCountDiff;
        }
        int bondCountDiff = Integer.compare(first.bondCount(), second.bondCount());
        if (bondCountDiff != 0)
        {
            return bondCountDiff;
        }

        // rule out trivial difference of different elements
        int elementsDiff = ELEMENT_COUNTS_ORDER.compare(elementCounts(first), elementCounts(second));
        if (elementsDiff != 0)
        {
            return elementsDiff;
        }

        return forms.apply(first).compareTo(forms.apply(second));
    }

    /**
     * @param molecule the molecule to describe
     * @return how many atoms of each element {@code molecule} has, sorted by element
     */
    private static List<ElementCount> elementCounts(Molecule molecule)
    {
        Map<String, Integer> countByElement = new HashMap<>();
        for (Atom atom : molecule.atoms())
        {
            countByElement.merge(atom.element(), 1, Integer::sum);
        }

        return countByElement.entrySet().stream()
            .map(entry -> new ElementCount(entry.getKey(), entry.getValue()))
            .sorted(ELEMENT_COUNT_ORDER)
            .toList();
    }

    /**
     * How many atoms of one element a molecule has.
     */
    private record ElementCount(String element, int count)
    {
    }
}
