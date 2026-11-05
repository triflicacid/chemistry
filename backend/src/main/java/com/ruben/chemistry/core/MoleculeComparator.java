package com.ruben.chemistry.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compares molecules by structure: two molecules compare equal exactly when they are
 * isomorphic, including matching stereochemistry, regardless of the order their atoms
 * and bonds were added.
 * <p>
 * This comparator defines a total ordering, compared in tiers, each only consulted
 * if every earlier tier was equal:
 * <ol>
 *     <li>atom count</li>
 *     <li>bond count</li>
 *     <li>each atom's own properties (element, charge, isotope, aromaticity, hydrogen
 *     count, chirality), atom-for-atom in ascending canonical rank order</li>
 *     <li>each bond, as its endpoints' canonical ranks and its order, sorted and
 *     compared entry-by-entry</li>
 * </ol>
 *  This ordering has no chemical meaning: "ranks higher" just means "differs first, at
 *  an earlier tier or earlier rank, with a larger value there".
 */
public final class MoleculeComparator implements Comparator<Molecule>
{
    private final Ranker ranker;

    public MoleculeComparator()
    {
        this(new CanonicalRanker());
    }

    public MoleculeComparator(Ranker ranker)
    {
        this.ranker = ranker;
    }

    @Override
    public int compare(Molecule first, Molecule second)
    {
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

        Ranking firstRanking = ranker.rank(first);
        Ranking secondRanking = ranker.rank(second);

        int atomsDiff = compareLists(atomSignatures(first, firstRanking), atomSignatures(second, secondRanking));
        if (atomsDiff != 0)
        {
            return atomsDiff;
        }
        return compareLists(bondSignatures(first, firstRanking), bondSignatures(second, secondRanking));
    }

    // every atom's own properties, in ascending-rank order; independent of atom identity or insertion order
    private List<AtomSignature> atomSignatures(Molecule molecule, Ranking ranking)
    {
        List<AtomSignature> signatures = new ArrayList<>();
        for (Atom atom : ranking.atomsByAscendingRank())
        {
            signatures.add(new AtomSignature(atom.element(), atom.aromatic(), atom.charge(), atom.isotope().orElse(-1), atom.hydrogenCount().orElse(-1), chiralityOf(molecule, ranking, atom)));
        }
        return signatures;
    }

    // the atom's declared chirality, if any, reprojected onto its rank-ordered neighbours
    private Chirality chiralityOf(Molecule molecule, Ranking ranking, Atom atom)
    {
        List<NeighbourReference> order = new ArrayList<>();
        if (atom.hydrogenCount().orElse(0) > 0)
        {
            order.add(Molecule.IMPLICIT_HYDROGEN);
        }
        List<Bond> bonds = new ArrayList<>();
        for (Bond bond : molecule.bondsOf(atom))
        {
            bonds.add(bond);
        }
        bonds.sort(Comparator.<Bond>comparingInt(bond -> ranking.rankOf(bond.getOther(atom))).thenComparingInt(bond -> bond.order().ordinal()));
        order.addAll(bonds);

        return molecule.chiralityOf(atom, order).orElse(null);
    }

    // every bond, described by its endpoints' ranks rather than atom identity, sorted for a deterministic order
    private List<BondSignature> bondSignatures(Molecule molecule, Ranking ranking)
    {
        List<BondSignature> signatures = new ArrayList<>();
        for (Bond bond : molecule.bonds())
        {
            int rankA = ranking.rankOf(bond.first());
            int rankB = ranking.rankOf(bond.second());
            signatures.add(new BondSignature(Math.min(rankA, rankB), Math.max(rankA, rankB), bond.order()));
        }
        signatures.sort(Comparator.naturalOrder());
        return signatures;
    }

    private static <T extends Comparable<T>> int compareLists(List<T> first, List<T> second)
    {
        for (int i = 0; i < first.size(); i++)
        {
            int diff = first.get(i).compareTo(second.get(i));
            if (diff != 0)
            {
                return diff;
            }
        }
        return 0;
    }

    private record AtomSignature(String element, boolean aromatic, int charge, int isotope, int hydrogenCount, Chirality chirality) implements Comparable<AtomSignature>
    {
        @Override
        public int compareTo(AtomSignature other)
        {
            int diff = element.compareTo(other.element);
            if (diff != 0)
            {
                return diff;
            }
            diff = Boolean.compare(aromatic, other.aromatic);
            if (diff != 0)
            {
                return diff;
            }
            diff = Integer.compare(charge, other.charge);
            if (diff != 0)
            {
                return diff;
            }
            diff = Integer.compare(isotope, other.isotope);
            if (diff != 0)
            {
                return diff;
            }
            diff = Integer.compare(hydrogenCount, other.hydrogenCount);
            if (diff != 0)
            {
                return diff;
            }
            return Comparator.nullsFirst(Comparator.<Chirality>naturalOrder()).compare(chirality, other.chirality);
        }
    }

    private record BondSignature(int lowerRank, int higherRank, BondOrder order) implements Comparable<BondSignature>
    {
        @Override
        public int compareTo(BondSignature other)
        {
            int diff = Integer.compare(lowerRank, other.lowerRank);
            if (diff != 0)
            {
                return diff;
            }
            diff = Integer.compare(higherRank, other.higherRank);
            if (diff != 0)
            {
                return diff;
            }
            return order.compareTo(other.order);
        }
    }
}
