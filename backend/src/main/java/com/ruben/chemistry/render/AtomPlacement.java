package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Molecule;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * One atom of a {@link Depiction}.
 *
 * @param placementIndex this placement's own index within its depiction's atoms, which a {@link BondPlacement} refers to
 * @param element the canonical element symbol, e.g. {@code "C"}, {@code "Cl"}, or {@code "*"} for a wildcard atom
 * @param isotope the mass number to label this atom with, or empty to leave it unlabelled
 * @param charge the net formal charge, {@code 0} if uncharged
 * @param hydrogens how many hydrogens are gathered into this atom's own label
 * @param aromatic whether this atom is aromatic
 * @param vertex whether this atom is left as a bare corner of the drawing rather than written out (skeletal carbon)
 * @param sourceAtom the index of the {@link Molecule#atoms() molecule atom} this came from, or empty if it is a hydrogen the depiction made up
 * @param fragment the index of the disconnected part of the molecule this belongs to, counting from {@code 0} in order of
 *     each part's first atom in {@link Molecule#atoms()}
 * @param position where this atom sits, in depiction space
 */
public record AtomPlacement(
    int placementIndex,
    String element,
    OptionalInt isotope,
    int charge,
    int hydrogens,
    boolean aromatic,
    boolean vertex,
    OptionalInt sourceAtom,
    int fragment,
    Point position)
{
    public AtomPlacement
    {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(isotope, "isotope");
        Objects.requireNonNull(sourceAtom, "sourceAtom");
        Objects.requireNonNull(position, "position");
        if (placementIndex < 0)
        {
            throw new IllegalArgumentException("Atom placement index cannot be negative, but was " + placementIndex);
        }
        if (hydrogens < 0)
        {
            throw new IllegalArgumentException("An atom cannot carry " + hydrogens + " hydrogens");
        }
    }

    /**
     * @param placementIndex this placement's own index within its depiction
     * @param element the canonical element symbol
     * @param isotope the mass number to label this atom with, or empty to leave it unlabelled
     * @param charge the net formal charge
     * @param aromatic whether this atom is aromatic
     * @param sourceAtom the molecule atom this came from, or empty for a made-up hydrogen
     * @param position where this atom sits
     * @return that atom, carrying no hydrogens in its own label
     */
    public static AtomPlacement of(
        int placementIndex,
        String element,
        OptionalInt isotope,
        int charge,
        boolean aromatic,
        OptionalInt sourceAtom,
        Point position)
    {
        return new AtomPlacement(placementIndex, element, isotope, charge, 0, aromatic, false, sourceAtom, 0, position);
    }

    /**
     * @return whether this atom stands for a hydrogen the molecule left implicit, so has no atom of its own
     */
    public boolean isGeneratedHydrogen()
    {
        return sourceAtom.isEmpty();
    }

    /**
     * @param vertex whether the atom is left as a bare corner of the drawing rather than written out
     * @return this placement, drawn that way
     */
    public AtomPlacement asVertex(boolean vertex)
    {
        return new AtomPlacement(placementIndex, element, isotope, charge, hydrogens, aromatic, vertex, sourceAtom, fragment, position);
    }

    /**
     * @param fragment the index of the disconnected part of the molecule the atom belongs to
     * @return this placement, in that fragment
     */
    public AtomPlacement inFragment(int fragment)
    {
        return new AtomPlacement(placementIndex, element, isotope, charge, hydrogens, aromatic, vertex, sourceAtom, fragment, position);
    }

    /**
     * @param placementIndex the index the atom should have instead
     * @return this placement, renumbered
     */
    public AtomPlacement withIndex(int placementIndex)
    {
        return new AtomPlacement(placementIndex, element, isotope, charge, hydrogens, aromatic, vertex, sourceAtom, fragment, position);
    }

    /**
     * @param position where the atom should sit instead
     * @return this placement, moved
     */
    public AtomPlacement at(Point position)
    {
        return new AtomPlacement(placementIndex, element, isotope, charge, hydrogens, aromatic, vertex, sourceAtom, fragment, position);
    }
}
