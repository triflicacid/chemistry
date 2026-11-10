package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Molecule;

import java.util.List;
import java.util.Objects;

/**
 * A laid-out {@link Molecule}: every atom to draw, where it sits, and every bond between them.
 *
 * @param atoms the atoms to draw, each holding its own {@link AtomPlacement#index()}, in index order
 * @param bonds the bonds to draw, referring to atoms by index
 * @param rings the rings the molecule has, referring to atoms by index
 */
public record Depiction(List<AtomPlacement> atoms, List<BondPlacement> bonds, List<RingPlacement> rings)
{
    /** The distance between two bonded atoms, which is the unit depiction space is measured in. */
    public static final double BOND_LENGTH = 1.0;

    /**
     * The distance between an atom and a hydrogen hanging off it, drawn shorter than
     * {@link #BOND_LENGTH} so that a crowded atom's hydrogens stay clear of its other bonds.
     */
    public static final double HYDROGEN_BOND_LENGTH = 0.7;

    /**
     * @throws NullPointerException if either list is {@code null}
     * @throws IllegalArgumentException if an atom is out of index order, or a bond refers to an atom that is not there
     */
    public Depiction
    {
        Objects.requireNonNull(atoms, "atoms");
        Objects.requireNonNull(bonds, "bonds");
        Objects.requireNonNull(rings, "rings");
        atoms = List.copyOf(atoms);
        bonds = List.copyOf(bonds);
        rings = List.copyOf(rings);

        for (int index = 0; index < atoms.size(); index++)
        {
            if (atoms.get(index).index() != index)
            {
                throw new IllegalArgumentException("Atom at position " + index + " reports index " + atoms.get(index).index());
            }
        }
        for (BondPlacement bond : bonds)
        {
            requireAtom(atoms, bond.first());
            requireAtom(atoms, bond.second());
        }
        for (RingPlacement ring : rings)
        {
            for (int member : ring.atoms())
            {
                requireAtom(atoms, member);
            }
        }
    }

    /**
     * @param atoms the atoms to draw
     * @param bonds the bonds to draw
     * @see #Depiction(List, List, List)
     */
    public Depiction(List<AtomPlacement> atoms, List<BondPlacement> bonds)
    {
        this(atoms, bonds, List.of());
    }

    /**
     * @return a depiction of nothing at all
     */
    public static Depiction empty()
    {
        return new Depiction(List.of(), List.of(), List.of());
    }

    /**
     * @param index an atom's index
     * @return that atom
     * @throws IndexOutOfBoundsException if no atom has that index
     */
    public AtomPlacement atom(int index)
    {
        return atoms.get(index);
    }

    /**
     * @return the smallest rectangle containing every atom, which is {@link Bounds#EMPTY} if there are none.
     */
    public Bounds bounds()
    {
        return Bounds.around(atoms.stream().map(AtomPlacement::position).toList());
    }

    /**
     * @return whether there is nothing to draw
     */
    public boolean isEmpty()
    {
        return atoms.isEmpty();
    }

    /**
     * @param atoms the atoms a bond may refer to
     * @param index the index the bond refers to
     * @throws IllegalArgumentException if no atom has that index
     */
    private static void requireAtom(List<AtomPlacement> atoms, int index)
    {
        if (index >= atoms.size())
        {
            throw new IllegalArgumentException("Bond refers to atom " + index + ", but there are only " + atoms.size() + " atoms");
        }
    }
}
