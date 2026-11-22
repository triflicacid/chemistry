package com.ruben.chemistry.forcefield;

import static com.ruben.chemistry.forcefield.Validation.finite;

/**
 * One normalised spherical Gaussian density component.
 *
 * @param atomIndex the index of the atom the Gaussian is centred on
 * @param x the x coordinate of the centre
 * @param y the y coordinate of the centre
 * @param z the z coordinate of the centre
 * @param population the number of electrons the Gaussian holds
 * @param exponent the Gaussian exponent, which must be positive
 */
public record Gaussian(int atomIndex, double x, double y, double z, double population, double exponent)
{
    public Gaussian
    {
        finite(x, "gaussian x");
        finite(y, "gaussian y");
        finite(z, "gaussian z");
        finite(population, "gaussian population");
        if (atomIndex < 0)
        {
            throw new IllegalArgumentException("negative atom index");
        }
        if (!(exponent > 0.0) || !Double.isFinite(exponent))
        {
            throw new IllegalArgumentException("gaussian exponent must be positive");
        }
    }
}
