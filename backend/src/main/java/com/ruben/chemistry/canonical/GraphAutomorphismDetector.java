package com.ruben.chemistry.canonical;

import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.StereoDeclaration;
import com.ruben.chemistry.core.StereoDeclaration.DoubleBond;
import com.ruben.chemistry.core.StereoDeclaration.Tetrahedral;
import com.ruben.chemistry.core.Stereochemistry;
import com.ruben.chemistry.util.PermutationSearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds automorphisms of a molecular graph that preserve atom classes.
 * <p>
 * An automorphism is a permutation of the atoms that preserves atom
 * classes, bonds and declared stereochemistry.
 */
final class GraphAutomorphismDetector
{
    /** Caps the search, as exhausting all permutations can take a while. */
    private static final int MAX_SEARCH_NODES = 100_000;

    private GraphAutomorphismDetector()
    {
    }

    /**
     * Prepares everything about an automorphism search that depends on the graph and its atom
     * classes but not on which pair of atoms is being tested.
     *
     * @param graph the molecular graph
     * @param atomClasses the current atom class for each atom
     * @return the prepared search, usable for any pair of atoms
     * @throws IllegalArgumentException if there is not one atom class for every atom
     */
    static AtomEquivalence prepare(Graph graph, int[] atomClasses)
    {
        int size = graph.atomCount();

        if (atomClasses.length != size)
        {
            throw new IllegalArgumentException("There must be one atom class for every atom");
        }

        // Index stereochemistry before refining atom classes.
        StereoIndex stereo = new StereoIndex(graph, size);

        // Refine classes until no further structural distinctions emerge.
        int[] refinedClasses = refineAtomClasses(
                graph,
                atomClasses,
                stereo.roleByAtom,
                stereo.tetrahedralByCentre);

        return new AtomEquivalence(graph, stereo, refinedClasses);
    }

    /**
     * Finds an automorphism mapping one atom to another.
     *
     * @param graph the molecular graph
     * @param atomClasses the current atom class for each atom
     * @param fromAtom the atom to map
     * @param toAtom the required destination atom
     * @return an automorphism mapping {@code fromAtom} to {@code toAtom}, or empty if none was found
     */
    static Optional<int[]> findAutomorphism(
            Graph graph,
            int[] atomClasses,
            int fromAtom,
            int toAtom)
    {
        return prepare(graph, atomClasses).findAutomorphism(fromAtom, toAtom);
    }

    /**
     * An automorphism search over one graph and one set of atom classes.
     */
    static final class AtomEquivalence
    {
        private final Graph graph;
        private final StereoIndex stereo;
        private final int[] refinedClasses;

        private AtomEquivalence(Graph graph, StereoIndex stereo, int[] refinedClasses)
        {
            this.graph = graph;
            this.stereo = stereo;
            this.refinedClasses = refinedClasses;
        }

        /**
         * @param fromAtom the atom to map
         * @param toAtom the required destination atom
         * @return an automorphism mapping {@code fromAtom} to {@code toAtom}, or empty if none was found
         */
        Optional<int[]> findAutomorphism(int fromAtom, int toAtom)
        {
            int size = graph.atomCount();

            checkAtomIndex(fromAtom, size);
            checkAtomIndex(toAtom, size);

            // No mapping can undo a refinement, so differing refined classes rule the pair out.
            if (refinedClasses[fromAtom] != refinedClasses[toAtom])
            {
                return Optional.empty();
            }

            int[] order = createSearchOrder(graph, size, fromAtom);
            int[] targetSlotByAtom = createAtomToSearchSlotLookup(order);

            var constraints = stereo.constrain(order, targetSlotByAtom);

            int requiredTargetSlot = targetSlotByAtom[toAtom];
            AtomicInteger remainingNodes = new AtomicInteger(MAX_SEARCH_NODES);

            var permutations = PermutationSearch.find(
                    size,
                    1,
                    (permutation, position, target) ->
                    {
                        if (remainingNodes.decrementAndGet() < 0)
                        {
                            return false;
                        }

                        int sourceAtom = order[position];
                        int targetAtom = order[target];

                        // Ensure the first atom in the search order maps to the requested target atom.
                        if (position == 0 && target != requiredTargetSlot)
                        {
                            return false;
                        }

                        // Refined classes must be preserved.
                        if (refinedClasses[sourceAtom] != refinedClasses[targetAtom])
                        {
                            return false;
                        }

                        // Preserve every relationship with previously mapped atoms.
                        for (int previous = 0; previous < position; previous++)
                        {
                            int previousSourceAtom = order[previous];
                            int previousTargetAtom = order[permutation[previous]];

                            if (graph.bondClass(sourceAtom, previousSourceAtom) != graph.bondClass(targetAtom, previousTargetAtom))
                            {
                                return false;
                            }
                        }

                        // Disallow self-loops (not checked above.)
                        if (graph.bondClass(sourceAtom, sourceAtom) != graph.bondClass(targetAtom, targetAtom))
                        {
                            return false;
                        }

                        // Check stereochemistry as soon as its participants are mapped.
                        return constraints.isPreserved(permutation, position, target);
                    });

            if (permutations.isEmpty())
            {
                return Optional.empty();
            }

            // Convert the search-order permutation into an atom-index permutation.
            int[] permutation = permutations.getFirst();
            int[] mapping = new int[size];

            for (int sourceSlot = 0; sourceSlot < size; sourceSlot++)
            {
                int sourceAtom = order[sourceSlot];
                int targetAtom = order[permutation[sourceSlot]];

                mapping[sourceAtom] = targetAtom;
            }

            return Optional.of(mapping);
        }
    }

    /**
     * @param graph the molecular graph
     * @param atomClasses the current atom class for each atom
     * @param firstAtom the first atom
     * @param secondAtom the second atom
     * @return whether an automorphism mapping the first atom to the second was found
     */
    static boolean areEquivalent(
            Graph graph,
            int[] atomClasses,
            int firstAtom,
            int secondAtom)
    {
        return findAutomorphism(
                graph,
                atomClasses,
                firstAtom,
                secondAtom).isPresent();
    }

    /**
     * Refines atom classes using neighbourhood and stereochemical signatures.
     *
     * @param graph the molecular graph
     * @param atomClasses the initial atom classes
     * @param roleByAtom the stereochemical roles of each atom
     * @param tetrahedralByCentre the tetrahedral configurations indexed by centre
     * @return refined atom classes
     */
    private static int[] refineAtomClasses(
            Graph graph,
            int[] atomClasses,
            int[] roleByAtom,
            Tetrahedral[] tetrahedralByCentre)
    {
        int size = atomClasses.length;

        // Initially distinguish atoms by their supplied classes and stereo roles.
        List<BaseSignature> initial = new ArrayList<>(size);
        for (int atom = 0; atom < size; atom++)
        {
            initial.add(new BaseSignature(atomClasses[atom], roleByAtom[atom]));
        }

        int[] colours = assignColours(initial);

        while (true)
        {
            List<AtomSignature> signatures = new ArrayList<>(size);

            for (int atom = 0; atom < size; atom++)
            {
                List<NeighbourSignature> neighbours = new ArrayList<>();

                // Collect the coloured neighbourhood of this atom.
                for (int neighbour = 0; neighbour < size; neighbour++)
                {
                    if (atom == neighbour)
                    {
                        continue;
                    }

                    int bondClass = graph.bondClass(atom, neighbour);
                    if (bondClass != Graph.NO_BOND)
                    {
                        neighbours.add(new NeighbourSignature(bondClass, colours[neighbour]));
                    }
                }

                // Sorted so independent of atom ordering.
                neighbours.sort(NeighbourSignature.COMPARATOR);

                // Refine tetrahedral parity when ligand classes are distinguishable.
                Tetrahedral tetrahedral = tetrahedralByCentre[atom];

                TetrahedralSignature tetrahedralSignature = tetrahedral == null
                        ? null
                        : createTetrahedralSignature(tetrahedral, colours);

                signatures.add(new AtomSignature(
                        colours[atom],
                        roleByAtom[atom],
                        graph.bondClass(atom, atom),
                        List.copyOf(neighbours),
                        tetrahedralSignature));
            }

            int[] refined = assignColours(signatures);

            // An unchanged partition is stable.
            if (Arrays.equals(colours, refined))
            {
                return refined;
            }

            colours = refined;
        }
    }

    /**
     * Creates an order-independent signature for a tetrahedral configuration.
     *
     * @param stereo the tetrahedral configuration
     * @param colours the current atom colours
     * @return the canonical ligand signature and parity, if distinguishable
     */
    private static TetrahedralSignature createTetrahedralSignature(Tetrahedral stereo, int[] colours)
    {
        List<Integer> ligandColours = new ArrayList<>(stereo.ligands().length);

        // Implicit hydrogen has a reserved colour distinct from real atoms.
        for (int ligand : stereo.ligands())
        {
            ligandColours.add(ligand == Graph.IMPLICIT_HYDROGEN
                    ? Graph.IMPLICIT_HYDROGEN
                    : colours[ligand]);
        }
        ligandColours.sort(Integer::compareTo);

        // Empty where repeated colours leave the parity ambiguous at this refinement level.
        return new TetrahedralSignature(
                List.copyOf(ligandColours),
                stereo.parityAgainst(colours).orElse(null));
    }

    /**
     * @param signatures the signatures to classify
     * @return the assigned colour of each signature
     */
    private static int[] assignColours(List<? extends Signature> signatures)
    {
        Map<Signature, Integer> colourBySignature = new HashMap<>();
        int[] colours = new int[signatures.size()];

        for (int index = 0; index < signatures.size(); index++)
        {
            Signature signature = signatures.get(index);
            colours[index] = colourBySignature.computeIfAbsent(signature, ignored -> colourBySignature.size());
        }

        return colours;
    }

    /**
     * @param graph the molecular graph
     * @param size the number of atoms
     * @param firstAtom the atom to place first in the search order
     * @return an array of atom indices in search order
     */
    private static int[] createSearchOrder(Graph graph, int size, int firstAtom)
    {
        int[] order = new int[size];
        boolean[] placed = new boolean[size];
        boolean[] bondedToPlaced = new boolean[size];

        for (int slot = 0; slot < size; slot++)
        {
            int next = slot == 0 ? firstAtom : nextToPlace(placed, bondedToPlaced);

            order[slot] = next;
            placed[next] = true;

            // Whatever is still unplaced beside it has just joined the frontier.
            for (int atom = 0; atom < size; atom++)
            {
                if (!placed[atom] && graph.bondClass(next, atom) != Graph.NO_BOND)
                {
                    bondedToPlaced[atom] = true;
                }
            }
        }

        return order;
    }

    /**
     * @param placed whether each atom has been given a search position
     * @param bondedToPlaced whether each atom is bonded to one that has been given a search position
     * @return the lowest-indexed unplaced atom on the frontier, or the lowest-indexed unplaced atom
     */
    private static int nextToPlace(boolean[] placed, boolean[] bondedToPlaced)
    {
        int firstUnplaced = -1;

        for (int atom = 0; atom < placed.length; atom++)
        {
            if (placed[atom])
            {
                continue;
            }

            if (bondedToPlaced[atom])
            {
                return atom;
            }

            if (firstUnplaced < 0)
            {
                firstUnplaced = atom;
            }
        }

        // Nothing unplaced is bonded to what is placed, so a disconnected component starts here.
        return firstUnplaced;
    }

    /**
     * @param order the atom indices in search order
     * @return an array mapping each atom index to its search position
     */
    private static int[] createAtomToSearchSlotLookup(int[] order)
    {
        int[] lookup = new int[order.length];

        // Translate atom indices into search positions.
        for (int slot = 0; slot < order.length; slot++)
        {
            lookup[order[slot]] = slot;
        }

        return lookup;
    }

    private static void checkAtomIndex(int atom, int size)
    {
        if (atom < 0 || atom >= size)
        {
            throw new IndexOutOfBoundsException("Atom index out of range: " + atom);
        }
    }

    interface Graph
    {
        /** Represents an implicit hydrogen instead of a graph atom. */
        int IMPLICIT_HYDROGEN = StereoDeclaration.IMPLICIT_HYDROGEN;

        /** The bond class of two atoms with no bond between them. */
        int NO_BOND = 0;

        int atomCount();

        /**
         * @return the class of the bond between two atoms, or {@link #NO_BOND}
         */
        int bondClass(int firstAtom, int secondAtom);

        /**
         * @return every declared configuration
         */
        default List<StereoDeclaration> stereochemistry()
        {
            return List.of();
        }
    }

    private sealed interface Signature permits BaseSignature, NeighbourSignature, TetrahedralSignature, AtomSignature
    {

    }

    private record BaseSignature(int atomClass, int stereoRole) implements Signature
    {
    }

    private record NeighbourSignature(int bondClass, int atomColour) implements Signature
    {
        static Comparator<NeighbourSignature> COMPARATOR = Comparator.comparingInt(NeighbourSignature::bondClass)
                .thenComparingInt(NeighbourSignature::atomColour);
    }

    private record TetrahedralSignature(List<Integer> ligandColours, Chirality canonicalParity) implements Signature
    {
    }

    private record AtomSignature(
            int previousColour,
            int stereoRole,
            int selfLoopClass,
            List<NeighbourSignature> neighbours,
            TetrahedralSignature tetrahedral) implements Signature
    {
    }

    /**
     * A graph's declared stereochemistry.
     */
    private static final class StereoIndex
    {
        private final int[] roleByAtom;

        private final Tetrahedral[] tetrahedralByCentre;
        private final Map<Long, DoubleBond> doubleBondByPair;

        private final StereoDeclaration[] elements;

        /**
         * @param graph the molecular graph
         * @param size the number of atoms
         */
        StereoIndex(Graph graph, int size)
        {
            this.roleByAtom = new int[size];

            this.tetrahedralByCentre = new Tetrahedral[size];
            this.doubleBondByPair = new HashMap<>();

            this.elements = graph.stereochemistry().toArray(StereoDeclaration[]::new);

            for (StereoDeclaration element : elements)
            {
                register(element, graph, size);
            }
        }

        /**
         * Binds this index to one search order, which decides when each declaration can be checked.
         *
         * @param order the atom indices in search order
         * @param slotByAtom lookup from atom indices to search positions
         * @return the constraints this index places on a search in that order
         */
        StereoConstraints constrain(int[] order, int[] slotByAtom)
        {
            return new StereoConstraints(order, slotByAtom);
        }

        /**
         * Registers and validates a stereochemical declaration.
         */
        private void register(StereoDeclaration element, Graph graph, int size)
        {
            switch (element)
            {
                case Tetrahedral tetrahedral ->
                {
                    int centre = checkParticipant(tetrahedral.centre(), size, false);
                    if (tetrahedralByCentre[centre] != null)
                    {
                        throw new IllegalArgumentException("Atom " + centre + " declares more than one parity");
                    }

                    int[] neighbours = tetrahedral.ligands();

                    // A tetrahedral centre must have exactly four distinct ligands.
                    if (neighbours.length != 4)
                    {
                        throw new IllegalArgumentException("Tetrahedral centre " + centre + " must have exactly four neighbours");
                    }

                    Set<Integer> uniqueNeighbours = new HashSet<>();
                    for (int neighbour : neighbours)
                    {
                        checkParticipant(neighbour, size, true);

                        // A centre cannot be its own ligand.
                        if (neighbour == centre)
                        {
                            throw new IllegalArgumentException("Tetrahedral centre cannot be its own neighbour: " + centre);
                        }

                        // Duplicate ligands make the declared parity invalid.
                        if (!uniqueNeighbours.add(neighbour))
                        {
                            throw new IllegalArgumentException("Tetrahedral centre " + centre + " has duplicate neighbours");
                        }

                        // Every explicit ligand must be bonded to the centre.
                        if (neighbour != Graph.IMPLICIT_HYDROGEN && graph.bondClass(centre, neighbour) == Graph.NO_BOND)
                        {
                            throw new IllegalArgumentException("Tetrahedral neighbour " + neighbour + " is not bonded to centre " + centre);
                        }
                    }

                    tetrahedralByCentre[centre] = tetrahedral;
                    roleByAtom[centre] |= Stereochemistry.STEREOCENTRE;
                }
                case DoubleBond doubleBond ->
                {
                    int first = checkParticipant(doubleBond.firstAtom(), size, false);
                    int second = checkParticipant(doubleBond.secondAtom(), size, false);
                    if (first == second)
                    {
                        throw new IllegalArgumentException("Double-bond endpoints must be distinct");
                    }

                    // Both endpoints must be connected,
                    if (graph.bondClass(first, second) == Graph.NO_BOND)
                    {
                        throw new IllegalArgumentException("Double-bond endpoints are not bonded: " + first + "-" + second);
                    }

                    int firstReference = checkParticipant(doubleBond.firstReference(), size, true);
                    int secondReference = checkParticipant(doubleBond.secondReference(), size, true);

                    // A reference must identify a substituent, not the opposite endpoint.
                    if (firstReference == second || secondReference == first)
                    {
                        throw new IllegalArgumentException("Double-bond references cannot be opposite endpoints");
                    }

                    checkReferenceBond(graph, first, firstReference);
                    checkReferenceBond(graph, second, secondReference);

                    if (doubleBondByPair.putIfAbsent(DoubleBond.pairKey(first, second), doubleBond) != null)
                    {
                        throw new IllegalArgumentException("Bond " + first + "-" + second + " declares more than one configuration");
                    }

                    roleByAtom[first] |= Stereochemistry.STEREO_BOND_ENDPOINT;
                    roleByAtom[second] |= Stereochemistry.STEREO_BOND_ENDPOINT;
                }
            }
        }

        /**
         * Validates that a reference substituent belongs to its endpoint.
         */
        private static void checkReferenceBond(Graph graph, int endpoint, int reference)
        {
            if (reference != Graph.IMPLICIT_HYDROGEN && graph.bondClass(endpoint, reference) == Graph.NO_BOND)
            {
                throw new IllegalArgumentException("Double-bond reference " + reference + " is not bonded to endpoint " + endpoint);
            }
        }

        /**
         * Determines whether two neighbour orders differ by an odd permutation.
         */
        private static Boolean isOddPermutation(int[] from, int[] to)
        {
            if (from.length != to.length)
            {
                return null;
            }

            int[] positions = new int[from.length];
            boolean[] matched = new boolean[to.length];

            // Express each source neighbour as its position in the destination order.
            for (int index = 0; index < from.length; index++)
            {
                int position = indexOfUnmatched(to, matched, from[index]);

                if (position < 0)
                {
                    return null;
                }

                matched[position] = true;
                positions[index] = position;
            }

            // Count inversions to determine permutation parity.
            int inversions = 0;

            for (int first = 0; first < positions.length; first++)
            {
                for (int second = first + 1; second < positions.length; second++)
                {
                    if (positions[first] > positions[second])
                    {
                        inversions++;
                    }
                }
            }

            return inversions % 2 != 0;
        }

        /**
         * Finds the first unmatched occurrence of an atom.
         */
        private static int indexOfUnmatched(int[] atoms, boolean[] matched, int atom)
        {
            for (int index = 0; index < atoms.length; index++)
            {
                if (!matched[index] && atoms[index] == atom)
                {
                    return index;
                }
            }

            return -1;
        }

        /**
         * @return {@code atom} if is a valid stereochemical participant, throws otherwise
         */
        private static int checkParticipant(int atom, int size, boolean allowImplicitHydrogen)
        {
            if (allowImplicitHydrogen && atom == Graph.IMPLICIT_HYDROGEN)
            {
                return atom;
            }

            if (atom < 0 || atom >= size)
            {
                throw new IllegalArgumentException("Stereochemistry refers to an atom outside the graph: " + atom);
            }

            return atom;
        }

        /**
         * The stereochemical constraints a {@link StereoIndex} places on a search in one particular order.
         */
        final class StereoConstraints
        {
            private final int[] order;
            private final int[] slotByAtom;
            private final int[] triggerPositions;

            /**
             * @param order the atom indices in search order
             * @param slotByAtom lookup from atom indices to search positions
             */
            private StereoConstraints(int[] order, int[] slotByAtom)
            {
                this.order = order;
                this.slotByAtom = slotByAtom;
                this.triggerPositions = new int[elements.length];

                for (int index = 0; index < elements.length; index++)
                {
                    // Defer each check until its last participant is mapped (when it will be 'triggered'.)
                    triggerPositions[index] = triggerPosition(elements[index]);
                }
            }

            /**
             * Checks whether the current partial mapping preserves all stereochemical
             * constraints whose participating atoms have been mapped.
             *
             * @param permutation the current partial mapping from search slots to target slots
             * @param position the search position currently being mapped
             * @param target the target search slot assigned to the current position
             * @return whether all applicable stereochemical constraints are preserved
             */
            boolean isPreserved(int[] permutation, int position, int target)
            {
                if (elements.length == 0)
                {
                    return true;
                }

                // Stereochemical participation must be preserved.
                if (roleByAtom[order[position]] != roleByAtom[order[target]])
                {
                    return false;
                }

                // Include the candidate mapping when resolving atom images.
                Images images = new Images(
                        order,
                        slotByAtom,
                        permutation,
                        position,
                        target);

                for (int index = 0; index < elements.length; index++)
                {
                    if (triggerPositions[index] != position)
                    {
                        continue;
                    }

                    boolean preserved = switch (elements[index])
                    {
                        case Tetrahedral tetrahedral -> preserves(tetrahedral, images);
                        case DoubleBond doubleBond -> preserves(doubleBond, images);
                    };

                    if (!preserved)
                    {
                        return false;
                    }
                }

                return true;
            }

            /**
             * @return whether the tetrahedral configuration is preserved under the supplied image mapping.
             */
            private boolean preserves(Tetrahedral stereo, Images images)
            {
                Tetrahedral image = tetrahedralByCentre[images.of(stereo.centre())];
                if (image == null)
                {
                    return false;
                }

                int[] mapped = images.mapAll(stereo.ligands());

                // Determine whether the mapping changes neighbour ordering parity.
                Boolean odd = isOddPermutation(image.ligands(), mapped);
                if (odd == null)
                {
                    return false;
                }

                Chirality expected = odd ? image.parity().flip() : image.parity();
                return expected == stereo.parity();
            }

            /**
             * @return whether the double-bond configuration is preserved under the supplied image mapping.
             */
            private boolean preserves(DoubleBond stereo, Images images)
            {
                int imageFirst = images.of(stereo.firstAtom());
                int imageSecond = images.of(stereo.secondAtom());

                // Bond lookup is independent of endpoint order.
                DoubleBond image = doubleBondByPair.get(DoubleBond.pairKey(imageFirst, imageSecond));
                if (image == null)
                {
                    return false;
                }

                // Align the destination references with the source endpoints.
                boolean aligned = image.firstAtom() == imageFirst;
                int imageFirstReference = aligned ? image.firstReference() : image.secondReference();
                int imageSecondReference = aligned ? image.secondReference() : image.firstReference();

                // An odd number of reference changes reverses the configuration.
                boolean flipped = (images.of(stereo.firstReference()) != imageFirstReference)
                                ^ (images.of(stereo.secondReference()) != imageSecondReference);

                DoubleBondStereo expected = flipped ? image.configuration().flip() : image.configuration();
                return expected == stereo.configuration();
            }

            /**
             * Determines the search position at which all atoms participating in the stereochemical element have been mapped.
             */
            private int triggerPosition(StereoDeclaration element)
            {
                return switch (element)
                {
                    case Tetrahedral tetrahedral -> Math.max(
                            slotByAtom[tetrahedral.centre()],
                            lastSlotOf(tetrahedral.ligands()));

                    case DoubleBond doubleBond -> lastSlotOf(
                            doubleBond.firstAtom(),
                            doubleBond.secondAtom(),
                            doubleBond.firstReference(),
                            doubleBond.secondReference());
                };
            }

            /**
             * Finds the last search position occupied by real atoms.
             */
            private int lastSlotOf(int... atoms)
            {
                int last = 0;

                for (int atom : atoms)
                {
                    if (atom != Graph.IMPLICIT_HYDROGEN)
                    {
                        last = Math.max(last, slotByAtom[atom]);
                    }
                }

                return last;
            }
        }

        /**
         * Resolves source atoms to their destination atoms under a partial permutation.
         *
         * @param order atom indices in search order
         * @param slotByAtom lookup from atom indices to search positions
         * @param permutation current partial permutation
         * @param position current search position
         * @param target candidate destination slot for the current position
         */
        private record Images(
                int[] order,
                int[] slotByAtom,
                int[] permutation,
                int position,
                int target)
        {
            /**
             * @param atom the source atom index
             * @return the destination atom index, or the implicit hydrogen sentinel
             */
            int of(int atom)
            {
                // Implicit hydrogen maps to itself.
                if (atom == Graph.IMPLICIT_HYDROGEN)
                {
                    return Graph.IMPLICIT_HYDROGEN;
                }

                int slot = slotByAtom[atom];

                // The current atom has not yet been added to the permutation, so use its candidate target.
                return order[slot == position ? target : permutation[slot]];
            }

            /**
             * Maps an array of source atom indices to their corresponding destination atoms
             *
             * @param atoms the source atom indices to map
             * @return the mapped destination atom indices
             */
            int[] mapAll(int[] atoms)
            {
                int[] mapped = new int[atoms.length];

                for (int index = 0; index < atoms.length; index++)
                {
                    mapped[index] = of(atoms[index]);
                }

                return mapped;
            }
        }
    }
}
