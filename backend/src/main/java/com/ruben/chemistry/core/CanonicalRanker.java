package com.ruben.chemistry.core;

import com.ruben.chemistry.util.CachedFunction;
import com.ruben.chemistry.util.CachedValue;
import com.ruben.chemistry.util.DisjointSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link Ranker} that ranks each atom by its structural role within the molecule, so that
 * the same molecule built in a different order always yields the same ranks. This includes stereochemistry.
 * <p>
 * Ranks are derived by Morgan-style iterative refinement, with deterministic tie-breaking for
 * atoms that refinement cannot tell apart (such as the two ends of a symmetric molecule).
 * <p>
 * <strong>Note</strong>: where a molecule has a non-trivial symmetry, its interchangeable atoms cannot
 * be told apart by any ranking, so ranks are canonical only up to that symmetry.
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
        .thenComparing(AtomKey::inRing)
        .thenComparingInt(AtomKey::stereoRole);

    private boolean ignoreStereochemistry;
    private Function<Molecule, Ranking> rankings = rankingCache(false);

    @Override
    public Ranking rank(Molecule molecule)
    {
        return rankings.apply(molecule);
    }

    /**
     * @return {@code true} if declared stereochemistry is left out of the ranking
     */
    public boolean isIgnoreStereochemistry()
    {
        return ignoreStereochemistry;
    }

    /**
     * Chooses whether declared stereochemistry takes part in the ranking.
     *
     * @param ignoreStereochemistry whether to leave declared stereochemistry out of the ranking
     * @return this ranker, for chaining
     */
    public CanonicalRanker setIgnoreStereochemistry(boolean ignoreStereochemistry)
    {
        this.ignoreStereochemistry = ignoreStereochemistry;
        this.rankings = rankingCache(ignoreStereochemistry);
        return this;
    }

    /**
     * @param ignoreStereochemistry whether the cached rankings are to leave stereochemistry out
     * @return a cache of the rankings produced under that setting, empty of the rankings produced under any other
     */
    private static Function<Molecule, Ranking> rankingCache(boolean ignoreStereochemistry)
    {
        return CachedFunction.byIdentity(molecule -> new Canonicalisation(molecule, ignoreStereochemistry).rank());
    }

    /**
     * Ranks one molecule.
     */
    private static final class Canonicalisation
    {
        private final List<Atom> atoms;
        private final Neighbourhood neighbours;

        /** Everything the molecule declares about its stereochemistry, in atom-index space. */
        private final Stereochemistry stereo;

        /** Each atom's class before any neighbour is looked at, which also orders the canonical representation. */
        private final Partition initial;

        /** Only tie-breaking needs the molecule as a graph, and most molecules have no tie to break, hold off for now. */
        private final CachedValue<AtomGraph> graph;

        /**
         * @param molecule the molecule to rank
         * @param ignoreStereochemistry whether to leave the molecule's declared stereochemistry out of the ranking
         */
        private Canonicalisation(Molecule molecule, boolean ignoreStereochemistry)
        {
            this.atoms = molecule.atoms();

            Map<Atom, Integer> indexOf = new IdentityHashMap<>();
            for (int atom = 0; atom < atoms.size(); atom++)
            {
                indexOf.put(atoms.get(atom), atom);
            }

            this.stereo = new Stereochemistry(molecule, atoms, indexOf, ignoreStereochemistry);
            this.neighbours = new Neighbourhood(molecule, atoms, indexOf, stereo);
            this.initial = initialClasses(molecule, atoms, stereo);
            this.graph = new CachedValue<>(() -> new AtomGraph(molecule, atoms, indexOf, stereo));
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
            int[] parities = stereo.parityCodes(classOf);

            // pairing the old class with the new keys is what keeps classes from merging back together
            return classify(partition.size(), (first, second) ->
            {
                int byClass = Integer.compare(classOf[first], classOf[second]);
                if (byClass != 0)
                {
                    return byClass;
                }
                int byParity = Integer.compare(parities[first], parities[second]);
                return byParity != 0 ? byParity : neighbours.compareKeys(keys, first, second);
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

            int[] parities = stereo.parityCodes(classOf);
            int[] invariantByClass = new int[size];
            int[] parityByClass = new int[size];
            int[] keyOffset = new int[size + 1];
            for (int position = 0; position < size; position++)
            {
                invariantByClass[position] = initial.classOf()[atomByClass[position]];
                parityByClass[position] = parities[atomByClass[position]];
                keyOffset[position + 1] = keyOffset[position] + neighbours.degree(atomByClass[position]);
            }

            long[] keys = new long[keyOffset[size]];
            for (int position = 0; position < size; position++)
            {
                neighbours.writeKeys(atomByClass[position], classOf, keys, keyOffset[position]);
                Arrays.sort(keys, keyOffset[position], keyOffset[position + 1]);
            }
            return new Representation(invariantByClass, parityByClass, keyOffset, keys);
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
     * @param stereo the molecule's declared stereochemistry
     * @return each atom's class under its own structural properties, its neighbours ignored
     */
    private static Partition initialClasses(Molecule molecule, List<Atom> atoms, Stereochemistry stereo)
    {
        Set<Bond> ringBonds = molecule.ringBonds();
        AtomKey[] keys = new AtomKey[atoms.size()];
        for (int atom = 0; atom < keys.length; atom++)
        {
            keys[atom] = initialInvariant(molecule, ringBonds, atoms.get(atom), stereo.roleOf(atom));
        }
        return classify(keys.length, (first, second) -> ATOM_KEY_ORDER.compare(keys[first], keys[second]));
    }

    /**
     * @param molecule the molecule being ranked
     * @param ringBonds the molecule's ring bonds
     * @param atom the atom to describe
     * @param stereoRole what {@code atom} plays in the molecule's declared stereochemistry, {@link Stereochemistry#roleOf}
     * @return {@code atom}'s initial structural properties without considering the structural properties of neighbouring atoms
     */
    private static AtomKey initialInvariant(Molecule molecule, Set<Bond> ringBonds, Atom atom, int stereoRole)
    {
        List<Bond> bonds = molecule.bondsOf(atom);
        boolean inRing = bonds.stream().anyMatch(ringBonds::contains);
        return new AtomKey(atom.element(), atom.aromatic(), atom.charge(), atom.isotope().orElse(-1), molecule.totalHydrogenCount(atom), bonds.size(), inRing, stereoRole);
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
        /** How many bits of a key one bond's declared configuration occupies. */
        private static final int CONFIGURATION_BITS = 2;

        /** Cumulative bond offsets for each atom, with one extra entry marking the end. */
        private final int[] offset;

        private final int[] neighbour;
        private final int[] bondOrder;

        /** Which declared configuration each bond carries, as {@link Stereochemistry#configurationOf} numbers them. */
        private final int[] configuration;

        private final Stereochemistry stereo;

        /**
         * @param molecule the molecule being ranked
         * @param atoms the molecule's atoms, in the order giving each its index
         * @param indexOf each atom's index, by atom reference
         * @param stereo the molecule's declared stereochemistry
         */
        private Neighbourhood(Molecule molecule, List<Atom> atoms, Map<Atom, Integer> indexOf, Stereochemistry stereo)
        {
            int size = atoms.size();
            this.stereo = stereo;
            this.offset = new int[size + 1];
            for (int atom = 0; atom < size; atom++)
            {
                offset[atom + 1] = offset[atom] + molecule.bondsOf(atoms.get(atom)).size();
            }

            this.neighbour = new int[offset[size]];
            this.bondOrder = new int[offset[size]];
            this.configuration = new int[offset[size]];
            for (int atom = 0; atom < size; atom++)
            {
                int at = offset[atom];
                for (Bond bond : molecule.bondsOf(atoms.get(atom)))
                {
                    neighbour[at] = indexOf.get(bond.getOther(atoms.get(atom)));
                    bondOrder[at] = bond.order().ordinal();
                    configuration[at] = stereo.configurationOf(atom, neighbour[at]);
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
                // Compare high to low bits, so order matters
                keys[at++] = ((long) bondOrder[bond] << (Integer.SIZE + CONFIGURATION_BITS))
                    | ((long) stereo.configurationCode(configuration[bond], classOf) << Integer.SIZE)
                    | classOf[neighbour[bond]];
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
        private final Stereochemistry stereo;

        /**
         * @param molecule the molecule being ranked
         * @param atoms the molecule's atoms, in the order giving each its index
         * @param indexOf each atom's index, by atom reference
         * @param stereo the molecule's declared stereochemistry
         */
        private AtomGraph(Molecule molecule, List<Atom> atoms, Map<Atom, Integer> indexOf, Stereochemistry stereo)
        {
            this.stereo = stereo;
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

        @Override
        public List<GraphAutomorphismDetector.StereoElement> stereochemistry()
        {
            return stereo.elements();
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
     * A molecule's declared stereochemistry, held in atom-index space.
     * This means nothing until it is expressed against atom classes as it must be relative to an ordering.
     */
    private static final class Stereochemistry
    {
        /** How many ligands a tetrahedral parity describes the arrangement of. */
        private static final int LIGANDS = 4;

        /** Role bit marking a stereocentre. */
        private static final int STEREOCENTRE = 1;

        /** Role bit marking an endpoint of a double bond carrying a declared configuration. */
        private static final int STEREO_BOND_ENDPOINT = 2;

        /** Code for an atom or bond carrying no declared configuration. */
        private static final int NONE = 0;

        /** Code for a declared configuration the atom classes in force cannot read. */
        private static final int UNREADABLE = 1;

        /** The code of the first readable configuration, which the configuration's own ordinal is added to. */
        private static final int READABLE = 2;

        /** Stands in for an implicit hydrogen, which has no atom index of its own. */
        private static final int IMPLICIT_HYDROGEN = GraphAutomorphismDetector.Graph.IMPLICIT_HYDROGEN;

        /** Marks a bond the molecule declares no configuration for. */
        private static final int NO_CONFIGURATION = -1;

        /** Marks an end of a double bond the classes in force leave with no reference substituent. */
        private static final int NO_REFERENCE = Integer.MIN_VALUE;

        /** Each stereocentre's ligands in the order its parity is declared against, or {@code null} where an atom declares none. */
        private final int[][] ligandsByCentre;

        /** Each stereocentre's parity as declared against {@link #ligandsByCentre}, or {@code null} where an atom declares none. */
        private final Chirality[] parityByCentre;

        private final int[] roleByAtom;

        /** Every declared double bond configuration, in the order {@link #configurationOf} numbers them. */
        private final List<Configuration> configurations;

        /** Which declared configuration the bond between a pair of atoms carries, by {@link #pairKey}. */
        private final Map<Long, Integer> configurationByPair;

        private final List<GraphAutomorphismDetector.StereoElement> elements;

        /** Reused where nothing is declared, so that such a molecule allocates nothing per refinement pass. */
        private final int[] noParities;

        /**
         * @param molecule the molecule being ranked
         * @param atoms the molecule's atoms, in the order giving each its index
         * @param indexOf each atom's index, by atom reference
         * @param ignore whether to hold nothing, leaving the molecule's stereochemistry out of the ranking
         */
        private Stereochemistry(Molecule molecule, List<Atom> atoms, Map<Atom, Integer> indexOf, boolean ignore)
        {
            int size = atoms.size();
            this.ligandsByCentre = new int[size][];
            this.parityByCentre = new Chirality[size];
            this.roleByAtom = new int[size];
            this.configurations = new ArrayList<>();
            this.configurationByPair = new HashMap<>();
            this.elements = new ArrayList<>();
            this.noParities = new int[size];

            if (ignore)
            {
                return;
            }

            for (int centre = 0; centre < size; centre++)
            {
                registerCentre(molecule, atoms, indexOf, centre);
            }
            for (Bond bond : molecule.bonds())
            {
                if (bond.order() == BondOrder.DOUBLE)
                {
                    registerBond(molecule, indexOf, bond);
                }
            }
        }

        /**
         * @param atom an atom's index
         * @return a mask of the parts {@code atom} plays in the declared stereochemistry: {@link #STEREOCENTRE}, {@link #STEREO_BOND_ENDPOINT}, or zero for neither
         */
        private int roleOf(int atom)
        {
            return roleByAtom[atom];
        }

        /**
         * @return every declared configuration, in the form the automorphism search takes them in
         */
        private List<GraphAutomorphismDetector.StereoElement> elements()
        {
            return elements;
        }

        /**
         * @param classOf each atom's current class, by atom index
         * @return each atom's parity re-expressed against those classes, as a code: {@link #NONE}, {@link #UNREADABLE}, or {@link #READABLE} plus the parity's ordinal
         */
        private int[] parityCodes(int[] classOf)
        {
            if (elements.isEmpty())
            {
                return noParities;
            }

            int[] codes = new int[classOf.length];
            for (int centre = 0; centre < codes.length; centre++)
            {
                int[] ligands = ligandsByCentre[centre];
                if (ligands == null)
                {
                    continue;
                }

                Chirality parity = parityAgainstClasses(ligands, parityByCentre[centre], classOf);
                codes[centre] = parity == null ? UNREADABLE : READABLE + parity.ordinal();
            }
            return codes;
        }

        /**
         * @param atom one bonded atom's index
         * @param neighbour the other bonded atom's index
         * @return which declared configuration the bond between them carries, or {@link #NO_CONFIGURATION} if it carries none
         */
        private int configurationOf(int atom, int neighbour)
        {
            return configurationByPair.getOrDefault(pairKey(atom, neighbour), NO_CONFIGURATION);
        }

        /**
         * @param configuration which declared configuration a bond carries, as {@link #configurationOf} numbers them
         * @param classOf each atom's current class, by atom index
         * @return that configuration re-expressed against those classes, as a code: {@link #NONE}, {@link #UNREADABLE}, or {@link #READABLE} plus the configuration's ordinal
         */
        private int configurationCode(int configuration, int[] classOf)
        {
            if (configuration == NO_CONFIGURATION)
            {
                return NONE;
            }

            Configuration declared = configurations.get(configuration);
            int firstReference = referenceAgainstClasses(declared.first(), classOf);
            int secondReference = referenceAgainstClasses(declared.second(), classOf);
            if (firstReference == NO_REFERENCE || secondReference == NO_REFERENCE)
            {
                return UNREADABLE;
            }

            // measuring from the other substituent at one end swaps the sides, and doing so at both swaps them back
            boolean flipped = (firstReference != declared.first().declaredReference())
                    ^ (secondReference != declared.second().declaredReference());
            DoubleBondStereo resolved = flipped ? declared.configuration().flip() : declared.configuration();
            return READABLE + resolved.ordinal();
        }

        /**
         * Records one atom's declared parity, if it has one the classes could ever read.
         *
         * @param molecule the molecule being ranked
         * @param atoms the molecule's atoms, in the order giving each its index
         * @param indexOf each atom's index, by atom reference
         * @param centre the index of the atom to record
         */
        private void registerCentre(Molecule molecule, List<Atom> atoms, Map<Atom, Integer> indexOf, int centre)
        {
            Atom atom = atoms.get(centre);
            Optional<Chirality> declared = molecule.chiralityOf(atom);
            if (declared.isEmpty())
            {
                return;
            }

            int[] ligands = declaredLigands(molecule, indexOf, atom);
            // a parity is the arrangement of four ligands, and describes nothing without all four
            if (ligands.length != LIGANDS)
            {
                return;
            }

            ligandsByCentre[centre] = ligands;
            parityByCentre[centre] = declared.get();
            roleByAtom[centre] |= STEREOCENTRE;
            elements.add(new GraphAutomorphismDetector.Tetrahedral(centre, declared.get(), ligands));
        }

        /**
         * Records one double bond's declared configuration, if it has one.
         *
         * @param molecule the molecule being ranked
         * @param indexOf each atom's index, by atom reference
         * @param bond the double bond to record
         */
        private void registerBond(Molecule molecule, Map<Atom, Integer> indexOf, Bond bond)
        {
            Optional<DoubleBondStereo> declared = molecule.doubleBondStereoOf(bond);
            if (declared.isEmpty())
            {
                return;
            }

            Endpoint first = endpointOf(molecule, indexOf, bond, bond.first());
            Endpoint second = endpointOf(molecule, indexOf, bond, bond.second());

            configurationByPair.put(pairKey(first.atom(), second.atom()), configurations.size());
            configurations.add(new Configuration(first, second, declared.get()));
            roleByAtom[first.atom()] |= STEREO_BOND_ENDPOINT;
            roleByAtom[second.atom()] |= STEREO_BOND_ENDPOINT;
            elements.add(new GraphAutomorphismDetector.DoubleBond(
                first.atom(),
                second.atom(),
                declared.get(),
                first.declaredReference(),
                second.declaredReference()));
        }

        /**
         * @param molecule the molecule being ranked
         * @param indexOf each atom's index, by atom reference
         * @param atom the stereocentre to read
         * @return {@code atom}'s neighbours in the order {@link Molecule#chiralityOf(Atom)} is relative to,
         *     any implicit hydrogen leading as {@link #IMPLICIT_HYDROGEN}
         */
        private static int[] declaredLigands(Molecule molecule, Map<Atom, Integer> indexOf, Atom atom)
        {
            List<Bond> bonds = molecule.bondsOf(atom);
            boolean hasImplicitHydrogen = hasImplicitHydrogen(atom);

            int[] ligands = new int[bonds.size() + (hasImplicitHydrogen ? 1 : 0)];
            int at = 0;
            if (hasImplicitHydrogen)
            {
                ligands[at++] = IMPLICIT_HYDROGEN;
            }
            for (Bond bond : bonds)
            {
                ligands[at++] = indexOf.get(bond.getOther(atom));
            }
            return ligands;
        }

        /**
         * @param molecule the molecule being ranked
         * @param indexOf each atom's index, by atom reference
         * @param doubleBond the bond carrying the configuration
         * @param atom the end of {@code doubleBond} to describe
         * @return what reading {@code atom}'s end of the configuration takes
         */
        private static Endpoint endpointOf(Molecule molecule, Map<Atom, Integer> indexOf, Bond doubleBond, Atom atom)
        {
            List<Bond> bonds = molecule.bondsOf(atom);
            int[] substituents = new int[bonds.size() - 1];
            int at = 0;

            // with no substituent of its own an end has nothing to be measured against, as an implicit hydrogen then is
            int declaredReference = IMPLICIT_HYDROGEN;
            for (Bond bond : bonds)
            {
                if (bond == doubleBond)
                {
                    continue;
                }

                int neighbour = indexOf.get(bond.getOther(atom));
                // the molecule declares against the first substituent it was given, which is the first bond bar this one
                if (at == 0)
                {
                    declaredReference = neighbour;
                }
                substituents[at++] = neighbour;
            }
            return new Endpoint(indexOf.get(atom), declaredReference, hasImplicitHydrogen(atom), substituents);
        }

        /**
         * Picks the substituent one end of a double bond is measured against, taking the end's
         * neighbours in the order the classes in force put them in.
         *
         * @param endpoint the end to measure
         * @param classOf each atom's current class, by atom index
         * @return the chosen substituent's index, or {@link #NO_REFERENCE} if those classes leave the choice open
         */
        private static int referenceAgainstClasses(Endpoint endpoint, int[] classOf)
        {
            // an implicit hydrogen leads its atom's neighbours, so it is the reference wherever an atom has one
            if (endpoint.hasImplicitHydrogen())
            {
                return IMPLICIT_HYDROGEN;
            }

            int reference = NO_REFERENCE;
            boolean tied = false;
            for (int substituent : endpoint.substituents())
            {
                if (reference == NO_REFERENCE || classOf[substituent] < classOf[reference])
                {
                    reference = substituent;
                    tied = false;
                }
                else if (classOf[substituent] == classOf[reference])
                {
                    tied = true;
                }
            }
            return tied ? NO_REFERENCE : reference;
        }

        /**
         * Re-expresses a declared parity against the order its centre's ligands fall into by class.
         *
         * @param ligands the centre's ligands, in the order the parity is declared against
         * @param declared the parity as declared
         * @param classOf each atom's current class, by atom index
         * @return the parity relative to those ligands in ascending class order, or {@code null} if two of them share a class
         */
        private static Chirality parityAgainstClasses(int[] ligands, Chirality declared, int[] classOf)
        {
            int inversions = 0;
            for (int first = 0; first < ligands.length; first++)
            {
                for (int second = first + 1; second < ligands.length; second++)
                {
                    int byClass = Integer.compare(keyOf(ligands[first], classOf), keyOf(ligands[second], classOf));
                    // two ligands of one class leave their order, and with it the parity, undecided
                    if (byClass == 0)
                    {
                        return null;
                    }
                    if (byClass > 0)
                    {
                        inversions++;
                    }
                }
            }
            // an even number of swaps reaches the ascending order without disturbing the parity
            return inversions % 2 == 0 ? declared : declared.flip();
        }

        /**
         * @param ligand one of a stereocentre's ligands, or {@link #IMPLICIT_HYDROGEN}
         * @param classOf each atom's current class, by atom index
         * @return where the ligand falls in its centre's ordering
         */
        private static int keyOf(int ligand, int[] classOf)
        {
            return ligand == IMPLICIT_HYDROGEN ? IMPLICIT_HYDROGEN : classOf[ligand];
        }

        /**
         * @param atom the atom to measure
         * @return whether {@code atom} has an implicit hydrogen taking a neighbour slot of its own
         */
        private static boolean hasImplicitHydrogen(Atom atom)
        {
            return atom.hydrogenCount().orElse(0) > 0;
        }

        /**
         * @param atom one atom's index
         * @param neighbour the other atom's index
         * @return a key identifying the pair, whichever way round the two are given
         */
        private static long pairKey(int atom, int neighbour)
        {
            return ((long) Math.min(atom, neighbour) << Integer.SIZE) | Math.max(atom, neighbour);
        }

        /**
         * One declared double bond configuration.
         *
         * @param first one end of the bond
         * @param second the other end of the bond
         * @param configuration the configuration as declared, against each end's {@link Endpoint#declaredReference}
         */
        private record Configuration(Endpoint first, Endpoint second, DoubleBondStereo configuration)
        {
        }

        /**
         * One end of a double bond carrying a declared configuration.
         *
         * @param atom the end's own index
         * @param declaredReference the substituent the molecule declares the configuration against
         * @param hasImplicitHydrogen whether the end has an implicit hydrogen, which leads its substituents
         * @param substituents the end's neighbours other than the opposite end
         */
        private record Endpoint(int atom, int declaredReference, boolean hasImplicitHydrogen, int[] substituents)
        {
        }
    }

    /**
     * An atom's own structural properties, used to split atoms into their initial classes.
     */
    private record AtomKey(String element, boolean aromatic, int charge, int isotope, int hydrogenCount, int degree, boolean inRing, int stereoRole)
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
     * @param parityByClass the parity of the atom holding each class, in class order, as {@link Stereochemistry#parityCodes} numbers them
     * @param keyOffset where each atom's keys begin, one entry longer than the molecule
     * @param keys every atom's neighbour keys in class order, {@link Neighbourhood#writeKeys}
     */
    private record Representation(int[] invariantByClass, int[] parityByClass, int[] keyOffset, long[] keys)
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

                int byParity = Integer.compare(parityByClass[position], other.parityByClass[position]);
                if (byParity != 0)
                {
                    return byParity;
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
