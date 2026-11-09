package com.ruben.chemistry.canonical;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.util.CachedValue;
import com.ruben.chemistry.util.IdentityMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A read-only view of each atom's rank, as computed by a {@link Ranker}.
 */
public final class Ranking
{
    private final Map<Atom, Integer> rankByAtom;
    private final Supplier<List<Atom>> atomsByAscendingRank = new CachedValue<>(this::sortByAscendingRank);

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
     * @return the ranked atoms, sorted by ascending rank, as an unmodifiable list
     */
    public List<Atom> atomsByAscendingRank()
    {
        return atomsByAscendingRank.get();
    }

    /**
     * @return the ranked atoms, sorted by ascending rank
     */
    private List<Atom> sortByAscendingRank()
    {
        List<Atom> atoms = new ArrayList<>(rankByAtom.keySet());
        atoms.sort(Comparator.comparingInt(this::rankOf));
        return List.copyOf(atoms);
    }

    /**
     * Orders an atom's bonds by the rank of the atom each one leads to.
     *
     * @param ranking the ranking to order by
     * @param atom the atom the bonds are incident to, by reference identity
     * @param bonds {@code atom}'s incident bonds
     * @return {@code bonds} by ascending rank of the atom across each bond, ties left in the order given
     * @throws IllegalArgumentException if an atom across one of {@code bonds} was not part of {@code ranking}
     */
    public static List<Bond> bondsByAscendingRank(Ranking ranking, Atom atom, List<Bond> bonds)
    {
        if (bonds.size() < 2)
        {
            return bonds;
        }

        Bond[] ordered = bonds.toArray(new Bond[0]);
        int[] ranks = new int[ordered.length];
        for (int index = 0; index < ordered.length; index++)
        {
            ranks[index] = ranking.rankOf(ordered[index].getOther(atom));
        }

        // insertion sort
        for (int index = 1; index < ordered.length; index++)
        {
            Bond bond = ordered[index];
            int rank = ranks[index];

            int place = index;
            while (place > 0 && ranks[place - 1] > rank)
            {
                ordered[place] = ordered[place - 1];
                ranks[place] = ranks[place - 1];
                place--;
            }
            ordered[place] = bond;
            ranks[place] = rank;
        }
        return Arrays.asList(ordered);
    }
}
