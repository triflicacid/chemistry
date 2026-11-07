package com.ruben.chemistry.core;

import com.ruben.chemistry.util.IdentityMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A read-only view of each atom's canonical rank, as computed by {@link CanonicalRanker}.
 */
public final class Ranking
{
    private final Map<Atom, Integer> rankByAtom;

    /**
     * @param rankByAtom each ranked atom's rank, copied in by reference identity
     */
    Ranking(Map<Atom, Integer> rankByAtom)
    {
        this.rankByAtom = IdentityMap.copyOf(rankByAtom);
    }

    /**
     * @param atom an atom of the ranked molecule, by reference identity
     * @return the atom's canonical rank
     * @throws IllegalArgumentException if {@code atom} was not part of the ranked molecule
     */
    public int rankOf(Atom atom)
    {
        Integer rank = rankByAtom.get(atom);
        if (rank == null)
        {
            throw new IllegalArgumentException("Atom was not part of the ranked molecule: " + atom);
        }
        return rank;
    }

    /**
     * @return the number of ranked atoms
     */
    public int size()
    {
        return rankByAtom.size();
    }

    /**
     * @return the ranked atoms, sorted by ascending rank
     */
    public List<Atom> atomsByAscendingRank()
    {
        List<Atom> atoms = new ArrayList<>(rankByAtom.keySet());
        atoms.sort(Comparator.comparingInt(this::rankOf));
        return atoms;
    }
}
