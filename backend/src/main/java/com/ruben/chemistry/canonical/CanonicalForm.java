package com.ruben.chemistry.canonical;

import com.ruben.chemistry.core.Stereochemistry;

import java.util.Arrays;
import java.util.List;

/**
 * What a molecule looks like once its atoms are put in canonical order.
 * Two forms compare equal when their molecules are isomorphic, stereochemistry included.
 */
public final class CanonicalForm implements Comparable<CanonicalForm>
{
    /** The distinct invariants of one molecule, in ascending order. */
    private final List<AtomInvariant> invariants;

    /** Which of {@link #invariants} the atom at each rank carries. */
    private final int[] invariantByRank;

    /** The parity the atom at each rank declares, as {@link Stereochemistry#parityCodeOf} encodes it. */
    private final int[] parityByRank;

    /** Where each rank's bond keys begin in {@link #keys}, one entry longer than the molecule. */
    private final int[] keyOffset;

    /** Every atom's bond keys, each atom's own run sorted, the runs in rank order. */
    private final long[] keys;

    /**
     * @param invariants the distinct invariants of the molecule, in ascending order
     * @param invariantByRank which of {@code invariants} the atom at each rank carries
     * @param parityByRank the parity the atom at each rank declares, encoded
     * @param keyOffset where each rank's bond keys begin, one entry longer than the molecule
     * @param keys every atom's bond keys, each atom's own run sorted, the runs in rank order
     */
    CanonicalForm(List<AtomInvariant> invariants, int[] invariantByRank, int[] parityByRank, int[] keyOffset, long[] keys)
    {
        this.invariants = invariants;
        this.invariantByRank = invariantByRank;
        this.parityByRank = parityByRank;
        this.keyOffset = keyOffset;
        this.keys = keys;
    }

    /**
     * @return how many atoms the molecule has
     */
    public int atomCount()
    {
        return invariantByRank.length;
    }

    /**
     * @param rank a rank of the molecule, from zero
     * @return the own properties of the atom holding that rank
     * @throws IndexOutOfBoundsException if {@code rank} is not a rank of this molecule
     */
    public AtomInvariant invariantAt(int rank)
    {
        return invariants.get(invariantByRank[rank]);
    }

    @Override
    public int compareTo(CanonicalForm other)
    {
        int byAtomCount = Integer.compare(atomCount(), other.atomCount());
        if (byAtomCount != 0)
        {
            return byAtomCount;
        }

        // two forms of one molecule share its invariants, so for them a table position says as much as the invariant does
        boolean sharedInvariants = invariants == other.invariants;

        for (int rank = 0; rank < invariantByRank.length; rank++)
        {
            int byInvariant = sharedInvariants
                ? Integer.compare(invariantByRank[rank], other.invariantByRank[rank])
                : invariantAt(rank).compareTo(other.invariantAt(rank));
            if (byInvariant != 0)
            {
                return byInvariant;
            }

            int byParity = Integer.compare(parityByRank[rank], other.parityByRank[rank]);
            if (byParity != 0)
            {
                return byParity;
            }

            int byBonds = compareKeys(other, rank);
            if (byBonds != 0)
            {
                return byBonds;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object other)
    {
        return this == other || other instanceof CanonicalForm form && compareTo(form) == 0;
    }

    @Override
    public int hashCode()
    {
        // the invariants themselves, not their table positions
        int hash = Arrays.hashCode(parityByRank);
        for (int rank = 0; rank < invariantByRank.length; rank++)
        {
            hash = 31 * hash + invariantAt(rank).hashCode();
        }
        return 31 * (31 * hash + Arrays.hashCode(keyOffset)) + Arrays.hashCode(keys);
    }

    /**
     * @param other the form to compare with
     * @param rank the rank whose bonds to compare
     * @return how the two atoms at that rank compare by their bonds, the atom with fewer coming first
     */
    private int compareKeys(CanonicalForm other, int rank)
    {
        int ours = keyOffset[rank + 1] - keyOffset[rank];
        int theirs = other.keyOffset[rank + 1] - other.keyOffset[rank];

        int limit = Math.min(ours, theirs);
        for (int index = 0; index < limit; index++)
        {
            int byKey = Long.compare(keys[keyOffset[rank] + index], other.keys[other.keyOffset[rank] + index]);
            if (byKey != 0)
            {
                return byKey;
            }
        }

        // an atom with fewer bonds comes first
        return Integer.compare(ours, theirs);
    }
}
