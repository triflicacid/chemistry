package com.ruben.chemistry.core;

import com.ruben.chemistry.util.CachedFunction;
import com.ruben.chemistry.util.Comparators;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

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
 *     <li>each bond, as its endpoints' canonical ranks, its order, and its declared double
 *     bond stereo configuration, sorted and compared entry-by-entry</li>
 * </ol>
 */
public final class MoleculeComparator implements Comparator<Molecule>
{
    /** Total ordering for {@link AtomSignature}. */
    private static final Comparator<AtomSignature> ATOM_SIGNATURE_ORDER = Comparator
        .comparing(AtomSignature::element)
        .thenComparing(AtomSignature::aromatic)
        .thenComparingInt(AtomSignature::charge)
        .thenComparingInt(AtomSignature::isotope)
        .thenComparingInt(AtomSignature::hydrogenCount)
        .thenComparing(AtomSignature::chirality, Comparator.nullsFirst(Comparator.naturalOrder()));

    /** Total ordering for {@link BondSignature}. */
    private static final Comparator<BondSignature> BOND_SIGNATURE_ORDER = Comparator
        .comparingInt(BondSignature::lowerRank)
        .thenComparingInt(BondSignature::higherRank)
        .thenComparing(BondSignature::order)
        .thenComparing(BondSignature::doubleBondStereo, Comparator.nullsFirst(Comparator.naturalOrder()));

    /** Total ordering for a molecule's atoms: lexicographic over {@link #ATOM_SIGNATURE_ORDER}. */
    private static final Comparator<List<AtomSignature>> ATOM_SIGNATURES_ORDER = Comparators.lexicographically(ATOM_SIGNATURE_ORDER);

    /** Total ordering for a molecule's bonds: lexicographic over {@link #BOND_SIGNATURE_ORDER}. */
    private static final Comparator<List<BondSignature>> BOND_SIGNATURES_ORDER = Comparators.lexicographically(BOND_SIGNATURE_ORDER);

    /** Avoid re-computing a molecule's ranking, as it will never change (immutable.) */
    private final Function<Molecule, Ranking> rankings;

    /**
     * Creates a comparator using the default {@link CanonicalRanker}.
     */
    public MoleculeComparator()
    {
        this(new CanonicalRanker());
    }

    /**
     * @param ranker the ranker used to put each molecule's atoms into a comparable order
     */
    public MoleculeComparator(Ranker ranker)
    {
        this.rankings = CachedFunction.byIdentity(ranker::rank);
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

        // same size, so the atoms have to be lined up by structural role before comparing them
        Ranking firstRanking = rankings.apply(first);
        Ranking secondRanking = rankings.apply(second);

        Function<Atom, List<NeighbourReference>> firstOrder = CachedFunction.byIdentity(atom -> rankedNeighbourOrder(first, firstRanking, atom));
        Function<Atom, List<NeighbourReference>> secondOrder = CachedFunction.byIdentity(atom -> rankedNeighbourOrder(second, secondRanking, atom));

        int atomsDiff = ATOM_SIGNATURES_ORDER.compare(atomSignatures(first, firstRanking, firstOrder), atomSignatures(second, secondRanking, secondOrder));
        if (atomsDiff != 0)
        {
            return atomsDiff;
        }
        return BOND_SIGNATURES_ORDER.compare(bondSignatures(first, firstRanking, firstOrder), bondSignatures(second, secondRanking, secondOrder));
    }

    /**
     * @param molecule the molecule to describe
     * @param ranking {@code molecule}'s atom ranking
     * @param neighbourOrder each atom's rank-sorted neighbour order
     * @return every atom's own properties, in ascending-rank order, independent of atom identity or insertion order
     */
    private List<AtomSignature> atomSignatures(Molecule molecule, Ranking ranking, Function<Atom, List<NeighbourReference>> neighbourOrder)
    {
        return ranking.atomsByAscendingRank().stream()
            .map(atom -> new AtomSignature(atom.element(), atom.aromatic(), atom.charge(), atom.isotope().orElse(-1), atom.hydrogenCount().orElse(-1), chiralityOf(molecule, atom, neighbourOrder)))
            .toList();
    }

    /**
     * Puts an atom's neighbours into an order derived from the ranking alone.
     *
     * @param molecule the molecule {@code atom} belongs to
     * @param ranking {@code molecule}'s atom ranking
     * @param atom the atom whose neighbours to order
     * @return {@code atom}'s implicit hydrogen, followed by its bonds, in an order that depends only on {@code ranking}
     */
    private List<NeighbourReference> rankedNeighbourOrder(Molecule molecule, Ranking ranking, Atom atom)
    {
        List<NeighbourReference> order = new ArrayList<>();
        // the implicit hydrogen has no rank to sort by, so it takes the first slot
        if (atom.hydrogenCount().orElse(0) > 0)
        {
            order.add(Molecule.IMPLICIT_HYDROGEN);
        }
        List<Bond> bonds = new ArrayList<>(molecule.bondsOf(atom));
        bonds.sort(Comparator.<Bond>comparingInt(bond -> ranking.rankOf(bond.getOther(atom))).thenComparingInt(bond -> bond.order().ordinal()));
        order.addAll(bonds);
        return order;
    }

    /**
     * @param molecule the molecule {@code atom} belongs to
     * @param atom the atom to read chirality from
     * @param neighbourOrder {@code atom}'s rank-sorted neighbour order
     * @return {@code atom}'s declared chirality, if any, reprojected onto its rank-ordered neighbours;
     *     {@code null} if {@code atom} is not a stereocentre
     */
    private Chirality chiralityOf(Molecule molecule, Atom atom, Function<Atom, List<NeighbourReference>> neighbourOrder)
    {
        // if empty now, will also be empty after laborious neighbour sorting, so this is an optimisation
        if (molecule.chiralityOf(atom).isEmpty())
        {
            return null;
        }
        return molecule.chiralityOf(atom, neighbourOrder.apply(atom)).orElse(null);
    }

    /**
     * @param molecule the molecule {@code bond} belongs to
     * @param bond the bond to read stereo from
     * @param neighbourOrder each endpoint's rank-sorted neighbour order, computed at most once per atom
     * @return {@code bond}'s declared stereo configuration, if any, reprojected onto its
     *     endpoints' rank-ordered reference substituents (the first ranked neighbour, other
     *     than the bond itself, on each side); {@code null} if either endpoint has no other
     *     substituent to use as one
     */
    private DoubleBondStereo doubleBondStereoOf(Molecule molecule, Bond bond, Function<Atom, List<NeighbourReference>> neighbourOrder)
    {
        // if empty now, will also be empty after applying computed references, so this is an optimisation
        if (molecule.doubleBondStereoOf(bond).isEmpty())
        {
            return null;
        }
        NeighbourReference firstReference = firstOtherThan(neighbourOrder.apply(bond.first()), bond);
        NeighbourReference secondReference = firstOtherThan(neighbourOrder.apply(bond.second()), bond);
        if (firstReference == null || secondReference == null)
        {
            return null;
        }
        return molecule.doubleBondStereoOf(bond, firstReference, secondReference).orElse(null);
    }

    /**
     * @param order an atom's ordered neighbours
     * @param excluded the bond to skip over
     * @return the first entry in {@code order} that isn't {@code excluded}, or {@code null} if there is none
     */
    private static NeighbourReference firstOtherThan(List<NeighbourReference> order, Bond excluded)
    {
        return order.stream().filter(candidate -> candidate != excluded).findFirst().orElse(null);
    }

    /**
     * @param molecule the molecule to describe
     * @param ranking {@code molecule}'s atom ranking
     * @param neighbourOrder each atom's rank-sorted neighbour order
     * @return every bond, described by its endpoints' ranks rather than atom identity, sorted deterministically
     */
    private List<BondSignature> bondSignatures(Molecule molecule, Ranking ranking, Function<Atom, List<NeighbourReference>> neighbourOrder)
    {
        return molecule.bonds().stream()
            .map(bond ->
            {
                // lower rank first, so a bond reads the same whichever endpoint it was stored from
                int rankA = ranking.rankOf(bond.first());
                int rankB = ranking.rankOf(bond.second());
                return new BondSignature(Math.min(rankA, rankB), Math.max(rankA, rankB), bond.order(), doubleBondStereoOf(molecule, bond, neighbourOrder));
            })
            .sorted(BOND_SIGNATURE_ORDER)
            .toList();
    }

    /**
     * One atom's own comparable properties.
     */
    private record AtomSignature(String element, boolean aromatic, int charge, int isotope, int hydrogenCount, Chirality chirality)
    {
    }

    /**
     * A bond, described by its endpoints' ranks rather than by the atoms themselves, so that bonds of different molecules can be compared.
     */
    private record BondSignature(int lowerRank, int higherRank, BondOrder order, DoubleBondStereo doubleBondStereo)
    {
    }
}
