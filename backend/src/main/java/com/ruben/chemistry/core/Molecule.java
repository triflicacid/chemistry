package com.ruben.chemistry.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Molecule
{
    /** Sentinel standing in for an atom's implicit hydrogen within a neighbour-order list passed to {@link #chiralityOf(Atom, List)}. */
    public static final Object IMPLICIT_HYDROGEN = new Object();

    private final List<Atom> atoms;
    private final List<Bond> bonds;
    private final Map<Atom, List<Bond>> incidentBonds;
    private final Map<Atom, Chirality> chiralityByAtom;

    /**
     * Creates a molecule from an already-built atom and bond list.
     *
     * @param atoms the atoms of the molecule, in index order
     * @param bonds the bonds of the molecule
     * @param chirality each stereocentre's declared chirality, keyed by atom identity
     */
    Molecule(List<Atom> atoms, List<Bond> bonds, Map<Atom, Chirality> chirality)
    {
        this.atoms = List.copyOf(atoms);
        this.bonds = List.copyOf(bonds);
        this.incidentBonds = buildIncidentBonds(this.atoms, this.bonds);
        this.chiralityByAtom = new IdentityHashMap<>(chirality);
    }

    /**
     * @return the atoms of this molecule
     */
    public Iterable<Atom> atoms()
    {
        return atoms;
    }

    /**
     * @return the bonds of this molecule
     */
    public Iterable<Bond> bonds()
    {
        return bonds;
    }

    /**
     * @return an arbitrary atom of this molecule, suitable as a traversal starting point
     */
    public Optional<Atom> anyAtom()
    {
        return atoms.isEmpty() ? Optional.empty() : Optional.of(atoms.getFirst());
    }

    /**
     * @return the number of atoms in this molecule
     */
    public int atomCount()
    {
        return atoms.size();
    }

    /**
     * @return the number of bonds in this molecule
     */
    public int bondCount()
    {
        return bonds.size();
    }

    /**
     * @param atom an atom of this molecule, by reference identity
     * @return the bonds incident to {@code atom}, in no particular order
     * @throws IllegalArgumentException if {@code atom} is not part of this molecule
     */
    public Iterable<Bond> bondsOf(Atom atom)
    {
        List<Bond> result = incidentBonds.get(atom);
        if (result == null)
        {
            throw new IllegalArgumentException("Atom is not part of this molecule: " + atom);
        }
        return result;
    }

    /**
     * Returns the declared chirality of a stereocentre, if any.
     * The value is relative to bond-addition order, so depends on how this
     * molecule was built, and could vary based on which atom was added first,
     * or which bond was added first to a given atom.
     *
     * @param atom an atom of this molecule, by reference identity
     * @return the declared chirality, or empty if {@code atom} is not a stereocentre
     * @throws IllegalArgumentException if {@code atom} is not part of this molecule
     */
    public Optional<Chirality> chiralityOf(Atom atom)
    {
        return chiralityOf(atom, declaredOrder(atom));
    }

    /**
     * Returns the declared chirality of a stereocentre, reprojected onto a caller-supplied
     * neighbour order.
     *
     * @param atom an atom of this molecule, by reference identity
     * @param order {@code atom}'s neighbours in the order chirality should be resolved against,
     *     each either a {@link Bond} incident to {@code atom} or {@link #IMPLICIT_HYDROGEN}.
     * @return the declared chirality relative to {@code order}, or empty if {@code atom} is not a stereocentre
     * @throws IllegalArgumentException if {@code atom} is not part of this molecule
     * @throws IllegalStateException if {@code order} does not match the stored order in size
     */
    public Optional<Chirality> chiralityOf(Atom atom, List<Object> order)
    {
        if (!incidentBonds.containsKey(atom))
        {
            throw new IllegalArgumentException("Atom is not part of this molecule: " + atom);
        }
        Chirality declared = chiralityByAtom.get(atom);
        if (declared == null)
        {
            return Optional.empty();
        }
        return Optional.of(isOddPermutation(declaredOrder(atom), order) ? declared.flip() : declared);
    }

    /**
     * Builds the fixed neighbour order chirality is stored relative to: {@code atom}'s
     * implicit hydrogen, followed by {@link #bondsOf(Atom)} in insertion order.
     *
     * @param atom an atom of this molecule
     * @return the stored neighbour order for {@code atom}
     */
    private List<Object> declaredOrder(Atom atom)
    {
        List<Object> order = new ArrayList<>();
        if (atom.hydrogenCount().orElse(0) > 0)
        {
            order.add(IMPLICIT_HYDROGEN);
        }
        for (Bond bond : bondsOf(atom))
        {
            order.add(bond);
        }
        return order;
    }

    /**
     * Determines whether rearranging {@code from} into the order of {@code to} is an odd
     * permutation (an odd number of pairwise swaps), by counting inversions.
     *
     * @param from a list of neighbour-slot identities
     * @param to the same identities, in a possibly different order
     * @return {@code true} if the permutation from {@code from} to {@code to} is odd
     * @throws IllegalStateException if {@code from} and {@code to} are not the same size
     */
    private static boolean isOddPermutation(List<Object> from, List<Object> to)
    {
        if (from.size() != to.size())
        {
            throw new IllegalStateException("Stereocentre's declared neighbour count (" + from.size() + ") does not match its given neighbour count (" + to.size() + ")");
        }

        Map<Object, Integer> positionInTo = new IdentityHashMap<>();
        for (int i = 0; i < to.size(); i++)
        {
            positionInTo.put(to.get(i), i);
        }

        int[] permutation = new int[from.size()];
        for (int i = 0; i < from.size(); i++)
        {
            permutation[i] = positionInTo.get(from.get(i));
        }

        int inversions = 0;
        for (int i = 0; i < permutation.length; i++)
        {
            for (int j = i + 1; j < permutation.length; j++)
            {
                if (permutation[i] > permutation[j])
                {
                    inversions++;
                }
            }
        }
        return inversions % 2 != 0;
    }

    /**
     * @param atom an atom of this molecule, by reference identity
     * @return the atoms directly bonded to {@code atom}
     * @throws IllegalArgumentException if {@code atom} is not part of this molecule
     */
    public Iterable<Atom> neighboursOf(Atom atom)
    {
        List<Atom> neighbours = new ArrayList<>();
        for (Bond bond : bondsOf(atom))
        {
            neighbours.add(bond.getOther(atom));
        }
        return List.copyOf(neighbours);
    }

    /**
     * Returns a builder pre-populated with this molecule's atoms and bonds, for building a
     * modified copy. The original molecule is unaffected by subsequent changes to the builder.
     *
     * @return a new builder containing this molecule's atoms and bonds
     */
    public Builder toBuilder()
    {
        Builder builder = new Builder();
        for (Atom atom : atoms)
        {
            builder.addAtom(atom);
        }
        for (Bond bond : bonds)
        {
            builder.addBond(bond.first(), bond.second(), bond.order());
        }
        for (Map.Entry<Atom, Chirality> entry : chiralityByAtom.entrySet())
        {
            builder.setChirality(entry.getKey(), entry.getValue());
        }
        return builder;
    }

    /**
     * Builds, for each atom, the list of bonds incident to it.
     *
     * @param atoms the atoms of the molecule
     * @param bonds the bonds of the molecule
     * @return an identity-keyed, immutable map from each atom to its immutable incident-bond list
     */
    private static Map<Atom, List<Bond>> buildIncidentBonds(List<Atom> atoms, List<Bond> bonds)
    {
        Map<Atom, List<Bond>> adjacency = new IdentityHashMap<>();
        for (Atom atom : atoms)
        {
            adjacency.put(atom, new ArrayList<>());
        }

        for (Bond bond : bonds)
        {
            adjacency.get(bond.first()).add(bond);
            adjacency.get(bond.second()).add(bond);
        }

        Map<Atom, List<Bond>> immutable = new IdentityHashMap<>();
        for (Map.Entry<Atom, List<Bond>> entry : adjacency.entrySet())
        {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    /**
     * Incrementally builds a {@link Molecule} by adding atoms and bonding them together.
     *
     * <p>Atoms are tracked by reference identity, not {@link Atom#equals}, so adding two
     * separately constructed but field-equal atoms creates two distinct atoms.
     */
    public static final class Builder
    {
        private final List<Atom> atoms = new ArrayList<>();
        private final List<Bond> bonds = new ArrayList<>();
        private final Set<Atom> added = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Atom, Chirality> chirality = new IdentityHashMap<>();

        /**
         * Adds an atom to the molecule being built.
         *
         * @param atom the atom to add
         * @return this builder, for chaining
         */
        public Builder addAtom(Atom atom)
        {
            added.add(atom);
            atoms.add(atom);
            return this;
        }

        /**
         * Add multiple atoms to the molecule being built.
         *
         * @param atoms the atoms to add
         * @return this builder, for chaining
         */
        public Builder addAtoms(Atom... atoms)
        {
            for (Atom atom : atoms)
            {
                addAtom(atom);
            }
            return this;
        }

        /**
         * Adds a bond between two previously added atoms.
         *
         * @param first one endpoint atom
         * @param second the other endpoint atom
         * @param order the bond order
         * @return this builder, for chaining
         * @throws IllegalArgumentException if either atom was not added via {@link #addAtom},
         *     or a bond already exists between {@code first} and {@code second}
         */
        public Builder addBond(Atom first, Atom second, BondOrder order)
        {
            requireAdded(first);
            requireAdded(second);
            if (indexOfBond(first, second) != -1)
            {
                throw new IllegalArgumentException("A bond already exists between the given atoms");
            }
            bonds.add(new Bond(first, second, order));
            return this;
        }

        /**
         * Declares a previously added atom as a stereocentre with the given chirality,
         * relative to the fixed neighbour order documented on {@link Molecule#chiralityOf(Atom)}.
         *
         * @param atom a previously added atom, by reference identity
         * @param chirality the declared chirality, or {@code null} to remove any previously declared chirality
         * @return this builder, for chaining
         * @throws IllegalArgumentException if {@code atom} was not added via {@link #addAtom}
         */
        public Builder setChirality(Atom atom, Chirality chirality)
        {
            requireAdded(atom);
            if (chirality == null)
            {
                this.chirality.remove(atom);
            }
            else
            {
                this.chirality.put(atom, chirality);
            }
            return this;
        }

        /**
         * Removes a previously added atom, along with any bonds incident to it and any
         * chirality declared for it.
         *
         * @param atom the atom to remove, by reference identity
         * @return this builder, for chaining
         * @throws IllegalArgumentException if {@code atom} was not added via {@link #addAtom}
         */
        public Builder removeAtom(Atom atom)
        {
            requireAdded(atom);
            atoms.remove(indexOfAtom(atom));
            added.remove(atom);
            bonds.removeIf(bond -> bond.first() == atom || bond.second() == atom);
            chirality.remove(atom);
            return this;
        }

        /**
         * Removes the bond between two atoms. Since a molecule is a simple graph, at most
         * one bond can exist between any pair of atoms, so no bond order needs to be given.
         *
         * @param first one endpoint atom
         * @param second the other endpoint atom
         * @return this builder, for chaining
         * @throws IllegalArgumentException if no bond exists between {@code first} and {@code second}
         */
        public Builder removeBond(Atom first, Atom second)
        {
            int index = indexOfBond(first, second);
            if (index == -1)
            {
                throw new IllegalArgumentException("No bond exists between the given atoms");
            }
            bonds.remove(index);
            return this;
        }

        /**
         * Builds the molecule from the atoms and bonds added so far.
         *
         * @return the built molecule
         */
        public Molecule build()
        {
            return new Molecule(atoms, bonds, chirality);
        }

        /**
         * Checks that an atom was previously added to this builder.
         *
         * @param atom the atom to check, by reference identity
         * @throws IllegalArgumentException if {@code atom} was not added via {@link #addAtom}
         */
        private void requireAdded(Atom atom)
        {
            if (!added.contains(atom))
            {
                throw new IllegalArgumentException("Atom was not added to this builder: " + atom);
            }
        }

        /**
         * Finds the position of an atom within {@link #atoms}, by reference identity.
         *
         * @param atom the atom to find
         * @return the atom's index, or {@code -1} if not found
         */
        private int indexOfAtom(Atom atom)
        {
            for (int i = 0; i < atoms.size(); i++)
            {
                if (atoms.get(i) == atom)
                {
                    return i;
                }
            }
            return -1;
        }

        /**
         * Finds the position of the bond, if any, between two atoms, in either direction,
         * by reference identity.
         *
         * @param first one endpoint atom
         * @param second the other endpoint atom
         * @return the bond's index, or {@code -1} if no such bond exists
         */
        private int indexOfBond(Atom first, Atom second)
        {
            for (int i = 0; i < bonds.size(); i++)
            {
                Bond bond = bonds.get(i);
                if (bond.matches(first, second))
                {
                    return i;
                }
            }
            return -1;
        }
    }
}
