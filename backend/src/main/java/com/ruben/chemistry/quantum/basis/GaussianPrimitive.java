package com.ruben.chemistry.quantum.basis;

/**
 * One term of a contracted Gaussian: an exponent and the coefficient it is weighted by.
 *
 * @param exponent the exponent of the Gaussian, in inverse square bohr
 * @param coefficient the contraction coefficient, as tabulated for a normalised primitive
 */
public record GaussianPrimitive(double exponent, double coefficient)
{
    public GaussianPrimitive
    {
        if (!(exponent > 0.0) || !Double.isFinite(exponent))
        {
            throw new IllegalArgumentException("exponent must be positive and finite, was " + exponent);
        }
        if (!Double.isFinite(coefficient))
        {
            throw new IllegalArgumentException("coefficient must be finite, was " + coefficient);
        }
    }
}
