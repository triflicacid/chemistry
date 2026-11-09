package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * One atom of a {@link Depiction}, together with where it sits.
 *
 * @param index this placement's own index within its depiction, which a {@link BondPlacement} refers to
 * @param element the canonical element symbol, e.g. {@code "C"}, {@code "Cl"}, or {@code "*"} for a wildcard atom
 * @param isotope the mass number to label this atom with, or empty to leave it unlabelled
 * @param charge the net formal charge, {@code 0} if uncharged
 * @param aromatic whether this atom is aromatic
 * @param sourceAtom the index of the {@link Molecule#atoms() molecule atom} this came from, or empty if it is a hydrogen the depiction made up
 * @param position where this atom sits, in depiction space
 */
public record AtomPlacement(
    int index,
    String element,
    OptionalInt isotope,
    int charge,
    boolean aromatic,
    OptionalInt sourceAtom,
    Point position)
{
    /**
     * @throws NullPointerException if any reference component is {@code null}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public AtomPlacement
    {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(isotope, "isotope");
        Objects.requireNonNull(sourceAtom, "sourceAtom");
        Objects.requireNonNull(position, "position");
        if (index < 0)
        {
            throw new IllegalArgumentException("Atom placement index cannot be negative, but was " + index);
        }
    }

    /**
     * @return whether this atom stands for a hydrogen the molecule left implicit, so has no atom of its own
     */
    public boolean isGeneratedHydrogen()
    {
        return sourceAtom.isEmpty();
    }

    /**
     * @param position where the atom should sit instead
     * @return this placement, moved
     */
    public AtomPlacement at(Point position)
    {
        return new AtomPlacement(index, element, isotope, charge, aromatic, sourceAtom, position);
    }
}
