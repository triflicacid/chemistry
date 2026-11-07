package com.ruben.chemistry.core;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A {@link Ranker} that ranks each atom by its structural role within the molecule, so that
 * the same molecule built in a different order always yields the same ranks.
 * <p>
 * Ranks are derived by Morgan-style iterative refinement, with deterministic tie-breaking for
 * atoms that refinement cannot tell apart (such as the two ends of a symmetric molecule).
 */
public final class CanonicalRanker implements Ranker
{
    /** Total order over initial invariants, so classes are numbered by signature rather than atom order. */
    private static final Comparator<AtomKey> ATOM_KEY_ORDER = Comparator
        .comparing(AtomKey::element)
        .thenComparing(AtomKey::aromatic)
        .thenComparingInt(AtomKey::charge)
        .thenComparingInt(AtomKey::isotope)
        .thenComparingInt(AtomKey::hydrogenCount)
        .thenComparingInt(AtomKey::degree)
        .thenComparing(AtomKey::inRing);

    /** Total order over one neighbour signature: by bond order, then by the neighbour's class. */
    private static final Comparator<NeighbourKey> NEIGHBOUR_KEY_ORDER = Comparator
        .comparingInt(NeighbourKey::bondOrder)
        .thenComparingInt(NeighbourKey::neighbourClass);

    /** Total order over a whole neighbour invariant, comparing the (already sorted) neighbour lists lexicographically. */
    private static final Comparator<List<NeighbourKey>> NEIGHBOUR_LIST_ORDER = CanonicalRanker::compareNeighbourLists;

    @Override
    public Ranking rank(Molecule molecule)
    {
        List<Atom> atoms = molecule.atoms();

        Set<Bond> ringBonds = molecule.ringBonds();

        // everyone starts in one class, then gets split by invariant, then refined by neighbours
        Map<Atom, Integer> classOf = new IdentityHashMap<>();
        for (Atom atom : atoms)
        {
            classOf.put(atom, 0);
        }
        Refinement initial = reclassify(atoms, classOf, atom -> initialInvariant(molecule, ringBonds, atom), ATOM_KEY_ORDER);
        Refinement refined = refineToStability(molecule, atoms, initial);
        return new Ranking(breakTies(molecule, atoms, refined));
    }

    /**
     * @param molecule the molecule being ranked
     * @param atoms the molecule's atoms
     * @param classification each atom's current class, and how many classes are in use
     * @return a classification that keeps every distinction {@code classification} already makes, and
     *     in which two atoms share a class iff they are bonded to the same classes in the same way.
     *     (May still contain ties.)
     */
    private Refinement refineToStability(Molecule molecule, List<Atom> atoms, Refinement classification)
    {
        while (true)
        {
            // re-split by neighbour classes
            Map<Atom, Integer> current = classification.classOf();
            Refinement refined = reclassify(atoms, current, atom -> neighbourInvariant(molecule, atom, current), NEIGHBOUR_LIST_ORDER);
            // a pass that splits nothing means there is nothing left to split
            if (refined.distinctClasses() == classification.distinctClasses())
            {
                return refined;
            }
            classification = refined;
        }
    }

    /**
     * @param molecule the molecule being ranked
     * @param atoms the molecule's atoms
     * @param classification each atom's current class, as settled by {@link #refineToStability}, and how many classes are in use
     * @return a classification in which every atom has a class of its own.
     *     Two atoms that {@code classification} holds apart are never put back together, though every class number is handed out afresh.
     */
    private Map<Atom, Integer> breakTies(Molecule molecule, List<Atom> atoms, Refinement classification)
    {
        while (classification.distinctClasses() < atoms.size())
        {
            // arbitrary but deterministic: the first still-tied atom in insertion order
            Map<Atom, Integer> classOf = classification.classOf();
            Atom toSplit = firstTiedAtom(atoms, classOf);
            int freshClass = Collections.max(classOf.values()) + 1;
            Map<Atom, Integer> split = new IdentityHashMap<>(classOf);
            split.put(toSplit, freshClass);
            // grant toSplit a new class and refine further; do this exhaustively until each atom has its own class
            classification = refineToStability(molecule, atoms, new Refinement(split, classification.distinctClasses() + 1));
        }
        return classification.classOf();
    }

    /**
     * @param atoms the molecule's atoms
     * @param classOf each atom's current class
     * @return the first atom, in {@code atoms} order, that still shares its class with another atom
     * @throws IllegalStateException if every atom already has a class of its own
     */
    private Atom firstTiedAtom(List<Atom> atoms, Map<Atom, Integer> classOf)
    {
        // a class holding more than one atom is a tie, so count the members of each
        Map<Integer, Integer> countByClass = new HashMap<>();
        for (Atom atom : atoms)
        {
            countByClass.merge(classOf.get(atom), 1, Integer::sum);
        }

        for (Atom atom : atoms)
        {
            if (countByClass.get(classOf.get(atom)) > 1)
            {
                return atom;
            }
        }

        throw new IllegalStateException("No tied class remains to split");
    }

    /**
     * @param atoms the molecule's atoms
     * @param previous each atom's current class
     * @param invariantOf the further property to split each class by
     * @param invariantOrder a total order over that property, used to number the resulting classes deterministically
     * @return a classification in which two atoms share a class only if they shared one in {@code previous} and agree on {@code invariantOf}
     */
    private <K> Refinement reclassify(
        List<Atom> atoms,
        Map<Atom, Integer> previous,
        Function<Atom, K> invariantOf,
        Comparator<K> invariantOrder)
    {
        // pairing the old class with the new invariant is what keeps classes from merging back together
        Comparator<Classification<K>> order = Comparator.<Classification<K>>comparingInt(Classification::previousClass)
            .thenComparing(Classification::invariant, invariantOrder);

        // sorted, so the class numbers handed out depend on the signatures and not on atom order
        List<Classification<K>> classifications = atoms.stream()
            .map(atom -> new Classification<>(atom, previous.get(atom), invariantOf.apply(atom)))
            .sorted(order)
            .toList();

        Map<Atom, Integer> result = new IdentityHashMap<>();
        int currentClass = -1;
        Classification<K> last = null;
        for (Classification<K> classification : classifications)
        {
            if (last == null || order.compare(classification, last) != 0)
            {
                currentClass++;
            }
            result.put(classification.atom(), currentClass);
            last = classification;
        }
        // classes are handed out as 0, 1, 2, ... in sorted order, so the highest one handed out is the count
        return new Refinement(result, currentClass + 1);
    }

    /**
     * @param molecule the molecule being ranked
     * @param ringBonds the molecule's ring bonds
     * @param atom the atom to describe
     * @return {@code atom}'s own structural properties, ignoring its neighbours
     */
    private AtomKey initialInvariant(Molecule molecule, Set<Bond> ringBonds, Atom atom)
    {
        List<Bond> bonds = molecule.bondsOf(atom);
        boolean inRing = bonds.stream().anyMatch(ringBonds::contains);
        return new AtomKey(atom.element(), atom.aromatic(), atom.charge(), atom.isotope().orElse(-1), atom.hydrogenCount().orElse(-1), bonds.size(), inRing);
    }

    /**
     * @param molecule the molecule being ranked
     * @param atom the atom to describe
     * @param classOf each atom's current class
     * @return how {@code atom} is bonded to each of its neighbours' classes, in an order independent of how its bonds were added
     */
    private List<NeighbourKey> neighbourInvariant(Molecule molecule, Atom atom, Map<Atom, Integer> classOf)
    {
        return molecule.bondsOf(atom).stream()
            .map(bond -> new NeighbourKey(bond.order().ordinal(), classOf.get(bond.getOther(atom))))
            .sorted(NEIGHBOUR_KEY_ORDER)
            .toList();
    }

    /**
     * @param a a neighbour invariant, already sorted by {@link #NEIGHBOUR_KEY_ORDER}
     * @param b a neighbour invariant, already sorted by {@link #NEIGHBOUR_KEY_ORDER}
     * @return their lexicographic comparison under {@link #NEIGHBOUR_KEY_ORDER}
     */
    private static int compareNeighbourLists(List<NeighbourKey> a, List<NeighbourKey> b)
    {
        int limit = Math.min(a.size(), b.size());
        for (int i = 0; i < limit; i++)
        {
            int comparison = NEIGHBOUR_KEY_ORDER.compare(a.get(i), b.get(i));
            if (comparison != 0)
            {
                return comparison;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    /**
     * An atom's own structural properties, used to split atoms into their initial classes.
     */
    private record AtomKey(String element, boolean aromatic, int charge, int isotope, int hydrogenCount, int degree, boolean inRing)
    {
    }

    /**
     * How an atom is bonded to one neighbour: the bond order and the neighbour's current class.
     */
    private record NeighbourKey(int bondOrder, int neighbourClass)
    {
    }

    /**
     * An atom paired with the class it previously held and the invariant used to split it further.
     */
    private record Classification<K>(Atom atom, int previousClass, K invariant)
    {
    }

    /**
     * A classification together with the number of distinct classes it uses.
     */
    private record Refinement(Map<Atom, Integer> classOf, int distinctClasses)
    {
    }
}
