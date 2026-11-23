package com.ruben.chemistry.forcefield;

import java.util.Objects;

import static com.ruben.chemistry.forcefield.Validation.finite;

/**
 * An atom with force-field metadata.
 *
 * @param element the element symbol
 * @param x the x coordinate, in bohr
 * @param y the y coordinate, in bohr
 * @param z the z coordinate, in bohr
 * @param partialCharge the atom's partial charge, in the convention of the model it is used with
 */
public record ForceFieldAtom(String element, double x, double y, double z, double partialCharge)
{
    public ForceFieldAtom
    {
        Objects.requireNonNull(element, "element");
        element = element.trim();
        if (element.isEmpty())
        {
            throw new IllegalArgumentException("empty element");
        }
        finite(x, "x");
        finite(y, "y");
        finite(z, "z");
        finite(partialCharge, "partial charge");
    }
}
