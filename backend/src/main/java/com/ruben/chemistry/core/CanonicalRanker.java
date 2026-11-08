package com.ruben.chemistry.core;

import com.ruben.chemistry.util.CachedFunction;
import com.ruben.chemistry.util.CachedValue;
import com.ruben.chemistry.util.DisjointSet;

import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link Ranker} that ranks each atom by its structural role within the molecule, so that
 * the same molecule built in a different order always yields the same ranks.
 * <p>
 * Ranks are derived by Morgan-style iterative refinement, with deterministic tie-breaking for
 * atoms that refinement cannot tell apart (such as the two ends of a symmetric molecule).
 * <p>
 * Where a molecule has a non-trivial symmetry its interchangeable atoms cannot be told apart by any
 * ranking, so ranks are canonical only up to that symmetry: which of two interchangeable atoms ends
 * up with the lower rank is arbitrary, but the molecule's overall representation is not.
 * <p>
 * TODO: nothing here looks at stereochemistry.
 */
public final class CanonicalRanker implements Ranker
{
    /** Total ordering for {@link AtomKey}. */
    private static final Comparator<AtomKey> ATOM_KEY_ORDER = Comparator
        .comparing(AtomKey::element)
        .thenComparing(AtomKey::aromatic)
        .thenComparingInt(AtomKey::charge)
        .thenComparingInt(AtomKey::isotope)
        .thenComparingInt(AtomKey::hydrogenCount)
        .thenComparingInt(AtomKey::degree)
        .thenComparing(AtomKey::inRing);

    private final Function<Molecule, Ranking> rankings = CachedFunction.byIdentity(molecule -> new Canonicalisation(molecule).rank());

    @Override
    public Ranking rank(Molecule molecule)
    {
        return rankings.apply(molecule);
    }

    /**
     * Ranks one molecule.
     */
    private static final class Canonicalisation
    {
        private final List<Atom> atoms;
        private final Neighbourhood neighbours;

        /** Each atom's class before any neighbour is looked at, which also orders the canonical representation. */
        private final Partition initial;

        /** Only tie-breaking needs the molecule as a graph, and most molecules have no tie to break, hold off for now. */
        private final CachedValue<AtomGraph> graph;

        /**
         * @param molecule the molecule to rank
         */
        private Canonicalisation(Molecule molecule)
        {
            this.atoms = molecule.atoms();

            Map<Atom, Integer> indexOf = new IdentityHashMap<>();
            for (int atom = 0; atom < atoms.size(); atom++)
            {
                indexOf.put(atoms.get(atom), atom);
            }

            this.neighbours = new Neighbourhood(molecule, atoms, indexOf);
            this.initial = initialClasses(molecule, atoms);
            this.graph = new CachedValue<>(() -> new AtomGraph(molecule, atoms, indexOf));
        }

        /**
         * @return each atom's rank, every atom in a class of its own
         */
        private Ranking rank()
        {
            Partition refined = refineToStability(initial);
            int[] classOf = refined.classes() == refined.size()
                ? refined.classOf()
                : breakTies(refined).classOf();
            return new Ranking(ranksByAtom(classOf));
        }

        /**
         * @param classOf each atom's class, with every atom in a class of its own
         * @return each atom's rank, keyed by atom reference
         */
        private Map<Atom, Integer> ranksByAtom(int[] classOf)
        {
            Map<Atom, Integer> rankByAtom = new IdentityHashMap<>();
            for (int atom = 0; atom < classOf.length; atom++)
            {
                rankByAtom.put(atoms.get(atom), classOf[atom]);
            }
            return rankByAtom;
        }

        /**
         * @param partition each atom's current class
         * @return a classification that keeps every distinction {@code partition} already makes, and
         *     in which two atoms share a class iff they are bonded to the same classes in the same way.
         *     (May still contain ties.)
         */
        private Partition refineToStability(Partition partition)
        {
            while (true)
            {
                // re-split by neighbour classes
                Partition refined = refine(partition);
                // a pass that splits nothing means there is nothing left to split
                if (refined.classes() == partition.classes())
                {
                    return refined;
                }
                partition = refined;
            }
        }

        /**
         * @param partition each atom's current class
         * @return a classification in which two atoms share a class only if they shared one in
         *     {@code partition} and are bonded to the same classes in the same way
         */
        private Partition refine(Partition partition)
        {
            int[] classOf = partition.classOf();
            long[] keys = neighbours.keysByAtom(classOf);

            // pairing the old class with the new keys is what keeps classes from merging back together
            return classify(partition.size(), (first, second) ->
            {
                int byClass = Integer.compare(classOf[first], classOf[second]);
                return byClass != 0 ? byClass : neighbours.compareKeys(keys, first, second);
            });
        }

        /**
         * @param partition each atom's current class, as settled by {@link #refineToStability}
         * @return the discrete classification, with every atom in a class of its own
         */
        private Candidate breakTies(Partition partition)
        {
            if (partition.classes() == partition.size())
            {
                int[] classOf = partition.classOf();
                return new Candidate(classOf, new CachedValue<>(() -> representationOf(classOf)));
            }

            int classToSplit = smallestTiedClass(partition);

            // every symmetry found below preserves these classes, so all of them hold for this step alone
            GraphAutomorphismDetector.AtomEquivalence equivalence = graph.get().equivalence(partition.classOf());
            DisjointSet orbits = new DisjointSet(partition.size());

            int[] tried = new int[partition.size()];
            int triedCount = 0;
            Candidate best = null;

            // try every atom still tied in that class so the choice can't depend on atom insertion order
            for (int candidate = 0; candidate < partition.size(); candidate++)
            {
                if (partition.classOf()[candidate] != classToSplit)
                {
                    continue;
                }

                // an atom a symmetry maps onto one already tried can only repeat that atom's whole subtree
                if (isInterchangeableWithAny(equivalence, orbits, tried, triedCount, candidate))
                {
                    continue;
                }
                tried[triedCount++] = candidate;

                int[] split = partition.classOf().clone();
                split[candidate] = partition.classes();
                Candidate result = breakTies(refineToStability(new Partition(split, partition.classes() + 1)));

                if (best == null || result.representation().get().compareTo(best.representation().get()) < 0)
                {
                    best = result;
                }
            }
            return best;
        }

        /**
         * Decides whether an atom is interchangeable with one already split on.
         *
         * @param equivalence the symmetry search for the classes in force at this tie-breaking step
         * @param orbits the atoms a symmetry found at this step has already shown to be interchangeable
         * @param tried the atoms already split on at this tie-breaking step
         * @param triedCount how many of {@code tried} hold an atom
         * @param candidate the atom about to be split on
         * @return {@code true} if a class-preserving symmetry maps one of {@code tried} onto {@code candidate}
         */
        private boolean isInterchangeableWithAny(
            GraphAutomorphismDetector.AtomEquivalence equivalence,
            DisjointSet orbits,
            int[] tried,
            int triedCount,
            int candidate)
        {
            for (int index = 0; index < triedCount; index++)
            {
                int atom = tried[index];

                // a symmetry found earlier may already join the two, which settles it without searching
                if (orbits.areJoined(atom, candidate))
                {
                    return true;
                }

                Optional<int[]> symmetry = equivalence.findAutomorphism(atom, candidate);
                if (symmetry.isPresent())
                {
                    // the symmetry swaps far more atoms than this pair, and every such pair is interchangeable
                    record(orbits, symmetry.get());
                    return true;
                }
            }
            return false;
        }

        /**
         * @param classOf each atom's class, with every atom in a class of its own
         * @return the molecule described atom-by-atom in class order
         */
        private Representation representationOf(int[] classOf)
        {
            int size = classOf.length;
            int[] atomByClass = new int[size];
            for (int atom = 0; atom < size; atom++)
            {
                atomByClass[classOf[atom]] = atom;
            }

            int[] invariantByClass = new int[size];
            int[] keyOffset = new int[size + 1];
            for (int position = 0; position < size; position++)
            {
                invariantByClass[position] = initial.classOf()[atomByClass[position]];
                keyOffset[position + 1] = keyOffset[position] + neighbours.degree(atomByClass[position]);
            }

            long[] keys = new long[keyOffset[size]];
            for (int position = 0; position < size; position++)
            {
                neighbours.writeKeys(atomByClass[position], classOf, keys, keyOffset[position]);
                Arrays.sort(keys, keyOffset[position], keyOffset[position + 1]);
            }
            return new Representation(invariantByClass, keyOffset, keys);
        }
    }

    /**
     * @param orbits the atoms shown to be interchangeable so far, extended in place
     * @param symmetry an atom permutation preserving everything this ranking distinguishes
     */
    private static void record(DisjointSet orbits, int[] symmetry)
    {
        for (int atom = 0; atom < symmetry.length; atom++)
        {
            orbits.union(atom, symmetry[atom]);
        }
    }

    /**
     * @param partition each atom's current class
     * @return the smallest class shared by more than one atom, the lowest such class number winning a tie
     * @throws IllegalStateException if every atom already has a class of its own
     */
    private static int smallestTiedClass(Partition partition)
    {
        int[] members = new int[partition.classes()];
        for (int atom = 0; atom < partition.size(); atom++)
        {
            members[partition.classOf()[atom]]++;
        }

        // splitting the smallest tie branches the fewest ways; the class number only settles a draw
        int smallest = -1;
        for (int klass = 0; klass < members.length; klass++)
        {
            if (members[klass] > 1 && (smallest < 0 || members[klass] < members[smallest]))
            {
                smallest = klass;
            }
        }

        if (smallest < 0)
        {
            throw new IllegalStateException("No tied class remains to split");
        }
        return smallest;
    }

    /**
     * @param molecule the molecule being ranked
     * @param atoms the molecule's atoms
     * @return each atom's class under its own structural properties, its neighbours ignored
     */
    private static Partition initialClasses(Molecule molecule, List<Atom> atoms)
    {
        Set<Bond> ringBonds = molecule.ringBonds();
        AtomKey[] keys = new AtomKey[atoms.size()];
        for (int atom = 0; atom < keys.length; atom++)
        {
            keys[atom] = initialInvariant(molecule, ringBonds, atoms.get(atom));
        }
        return classify(keys.length, (first, second) -> ATOM_KEY_ORDER.compare(keys[first], keys[second]));
    }

    /**
     * @param molecule the molecule being ranked
     * @param ringBonds the molecule's ring bonds
     * @param atom the atom to describe
     * @return {@code atom}'s initial structural properties without considering the structural properties of neighbouring atoms
     */
    private static AtomKey initialInvariant(Molecule molecule, Set<Bond> ringBonds, Atom atom)
    {
        List<Bond> bonds = molecule.bondsOf(atom);
        boolean inRing = bonds.stream().anyMatch(ringBonds::contains);
        return new AtomKey(atom.element(), atom.aromatic(), atom.charge(), atom.isotope().orElse(-1), molecule.totalHydrogenCount(atom), bonds.size(), inRing);
    }

    /**
     * Numbers atoms by where they fall in a total order, with atoms the order cannot separate sharing a number.
     *
     * @param size how many atoms are being classified
     * @param order a total order over the atoms, by index
     * @return each atom's class, numbered from zero in {@code order}
     */
    private static Partition classify(int size, Comparison order)
    {
        Integer[] sorted = new Integer[size];
        for (int atom = 0; atom < size; atom++)
        {
            sorted[atom] = atom;
        }
        // sorted, so the class numbers handed out depend on the atoms' own properties and not any insdertion order
        Arrays.sort(sorted, order::compare);

        int[] classOf = new int[size];
        int classes = 0;
        for (int position = 0; position < size; position++)
        {
            if (position > 0 && order.compare(sorted[position - 1], sorted[position]) != 0)
            {
                classes++;
            }
            classOf[sorted[position]] = classes;
        }
        // classes are dense and monotonically increasing
        return new Partition(classOf, size == 0 ? 0 : classes + 1);
    }

    /**
     * A total order over atoms.
     */
    @FunctionalInterface
    private interface Comparison
    {
        /**
         * @param first one atom's index
         * @param second the other atom's index
         * @return how the two atoms compare
         */
        int compare(int first, int second);
    }

    /**
     * Every atom's bonds, held as indices so that refinement never looks an atom up by reference.
     */
    private static final class Neighbourhood
    {
        /** Cumulative bond offsets for each atom, with one extra entry marking the end. */
        private final int[] offset;

        private final int[] neighbour;
        private final int[] bondOrder;

        /**
         * @param molecule the molecule being ranked
         * @param atoms the molecule's atoms, in the order giving each its index
         * @param indexOf each atom's index, by atom reference
         */
        private Neighbourhood(Molecule molecule, List<Atom> atoms, Map<Atom, Integer> indexOf)
        {
            int size = atoms.size();
            this.offset = new int[size + 1];
            for (int atom = 0; atom < size; atom++)
            {
                offset[atom + 1] = offset[atom] + molecule.bondsOf(atoms.get(atom)).size();
            }

            this.neighbour = new int[offset[size]];
            this.bondOrder = new int[offset[size]];
            for (int atom = 0; atom < size; atom++)
            {
                int at = offset[atom];
                for (Bond bond : molecule.bondsOf(atoms.get(atom)))
                {
                    neighbour[at] = indexOf.get(bond.getOther(atoms.get(atom)));
                    bondOrder[at] = bond.order().ordinal();
                    at++;
                }
            }
        }

        /**
         * @param atom the atom to measure
         * @return how many bonds {@code atom} has
         */
        private int degree(int atom)
        {
            return offset[atom + 1] - offset[atom];
        }

        /**
         * Builds a sorted description of each atom's neighbourhood.
         * Each bond contributes a key combining its order and the current class of its neighbouring atom.
         * Each atom's keys occupy its bond-offset range and are sorted independently.
         *
         * @param classOf the current class of each atom
         * @return the sorted bond keys, grouped by atom in bond-offset order
         */
        private long[] keysByAtom(int[] classOf)
        {
            long[] keys = new long[neighbour.length];
            for (int atom = 0; atom < classOf.length; atom++)
            {
                writeKeys(atom, classOf, keys, offset[atom]);
                Arrays.sort(keys, offset[atom], offset[atom + 1]);
            }
            return keys;
        }

        /**
         * Encodes each bond of an atom as a key combining the bond order and its neighbour's current class.
         * Keys are written consecutively into the supplied array, without sorting.
         *
         * @param atom the atom whose bonds are being encoded
         * @param classOf the current class of each atom
         * @param keys the array into which the keys are written
         * @param at the starting index at which to write the keys
         */
        private void writeKeys(int atom, int[] classOf, long[] keys, int at)
        {
            for (int bond = offset[atom]; bond < offset[atom + 1]; bond++)
            {
                // Bond order occupies the high half so that it takes precedence when keys are compared.
                keys[at++] = ((long) bondOrder[bond] << Integer.SIZE) | classOf[neighbour[bond]];
            }
        }

        /**
         * @param keys every atom's keys, as {@link #keysByAtom} laid them out
         * @param first one atom's index
         * @param second the other atom's index
         * @return how the two atoms' keys compare, lexicographically, the atom with fewer bonds first
         */
        private int compareKeys(long[] keys, int first, int second)
        {
            int limit = Math.min(degree(first), degree(second));
            for (int index = 0; index < limit; index++)
            {
                int comparison = Long.compare(keys[offset[first] + index], keys[offset[second] + index]);
                if (comparison != 0)
                {
                    return comparison;
                }
            }
            return Integer.compare(degree(first), degree(second));
        }
    }

    /**
     * Adapts a molecule to the graph abstraction automorphism detection works in.
     */
    private static final class AtomGraph implements GraphAutomorphismDetector.Graph
    {
        private final int[][] bondClasses;

        /**
         * @param molecule the molecule being ranked
         * @param atoms the molecule's atoms, in the order giving each its index
         * @param indexOf each atom's index, by atom reference
         */
        private AtomGraph(Molecule molecule, List<Atom> atoms, Map<Atom, Integer> indexOf)
        {
            this.bondClasses = new int[atoms.size()][atoms.size()];
            for (Bond bond : molecule.bonds())
            {
                // an absent bond is class zero, so a real one has to be shifted clear of it
                int bondClass = bond.order().ordinal() + 1;
                int first = indexOf.get(bond.first());
                int second = indexOf.get(bond.second());

                bondClasses[first][second] = bondClass;
                bondClasses[second][first] = bondClass;
            }
        }

        @Override
        public int atomCount()
        {
            return bondClasses.length;
        }

        @Override
        public int bondClass(int firstAtom, int secondAtom)
        {
            return bondClasses[firstAtom][secondAtom];
        }

        /**
         * Prepares a symmetry search against one set of atom classes.
         *
         * @param classOf each atom's current class, by atom index
         * @return a symmetry search holding everything those classes imply
         */
        private GraphAutomorphismDetector.AtomEquivalence equivalence(int[] classOf)
        {
            return GraphAutomorphismDetector.prepare(this, classOf);
        }
    }

    /**
     * An atom's own structural properties, used to split atoms into their initial classes.
     */
    private record AtomKey(String element, boolean aromatic, int charge, int isotope, int hydrogenCount, int degree, boolean inRing)
    {
    }

    /**
     * A classification of a molecule's atoms.
     *
     * @param classOf each atom's class, by atom index
     * @param classes the number of distinct classes it uses
     */
    private record Partition(int[] classOf, int classes)
    {
        /**
         * @return how many atoms are classified
         */
        private int size()
        {
            return classOf.length;
        }
    }

    /**
     * A fully resolved classification together with the canonical representation it produces.
     *
     * @param classOf each atom's class, every atom in a class of its own
     * @param representation the representation that classification gives the molecule
     */
    private record Candidate(int[] classOf, Supplier<Representation> representation)
    {
    }

    /**
     * A molecule described atom by atom in class order.
     *
     * @param invariantByClass the initial class of the atom holding each class, in class order
     * @param keyOffset where each atom's keys begin, one entry longer than the molecule
     * @param keys every atom's neighbour keys in class order, {@link Neighbourhood#writeKeys}
     */
    private record Representation(int[] invariantByClass, int[] keyOffset, long[] keys)
    {
        /**
         * @param other the representation to compare with
         * @return how the two representations compare, atom by atom in class order
         */
        private int compareTo(Representation other)
        {
            for (int position = 0; position < invariantByClass.length; position++)
            {
                int byInvariant = Integer.compare(invariantByClass[position], other.invariantByClass[position]);
                if (byInvariant != 0)
                {
                    return byInvariant;
                }

                int ours = keyOffset[position + 1] - keyOffset[position];
                int theirs = other.keyOffset[position + 1] - other.keyOffset[position];
                int limit = Math.min(ours, theirs);
                for (int index = 0; index < limit; index++)
                {
                    int byKey = Long.compare(keys[keyOffset[position] + index], other.keys[other.keyOffset[position] + index]);
                    if (byKey != 0)
                    {
                        return byKey;
                    }
                }

                // an atom with fewer bonds comes first, as the shorter of two otherwise equal runs
                if (ours != theirs)
                {
                    return Integer.compare(ours, theirs);
                }
            }
            return 0;
        }
    }
}
