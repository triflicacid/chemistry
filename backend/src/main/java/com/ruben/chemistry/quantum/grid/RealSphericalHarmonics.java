package com.ruben.chemistry.quantum.grid;

/**
 * Real spherical harmonics {@code Y_lm}, orthonormal over the unit sphere, for any angular momentum {@code l}.
 */
public final class RealSphericalHarmonics
{
    private RealSphericalHarmonics()
    {
    }

    /**
     * @param l the angular momentum, at least 0
     * @param x the x component of a unit vector
     * @param y the y component of the unit vector
     * @param z the z component of the unit vector
     * @return the {@code 2l + 1} harmonics at that direction, ordered {@code m = 0}, then {@code cos} and {@code sin} for
     *     {@code m = 1} up to {@code l}, so the entry for {@code cos(m phi)} is at {@code 2m - 1} and for {@code sin(m phi)} at {@code 2m}
     * @throws IllegalArgumentException if {@code l} is negative
     */
    public static double[] values(int l, double x, double y, double z)
    {
        if (l < 0)
        {
            throw new IllegalArgumentException("angular momentum must not be negative, was " + l);
        }
        double[] result = new double[2 * l + 1];
        // (x + iy)^m = sin^m(theta) exp(i m phi), which supplies the azimuthal factor and the sin^m(theta) of the
        // associated Legendre function without dividing by sin(theta), so the poles are harmless
        double[] real = new double[l + 1];
        double[] imaginary = new double[l + 1];
        real[0] = 1.0;
        for (int m = 1; m <= l; m++)
        {
            real[m] = real[m - 1] * x - imaginary[m - 1] * y;
            imaginary[m] = real[m - 1] * y + imaginary[m - 1] * x;
        }
        for (int m = 0; m <= l; m++)
        {
            double legendre = legendreOverSine(l, m, z) * normalisation(l, m);
            if (m == 0)
            {
                result[0] = legendre;
            }
            else
            {
                result[2 * m - 1] = Math.sqrt(2.0) * legendre * real[m];
                result[2 * m] = Math.sqrt(2.0) * legendre * imaginary[m];
            }
        }
        return result;
    }

    /**
     * @return {@code P_l^m(z) / (1 - z^2)^(m/2)}, a polynomial in {@code z}, with the Condon-Shortley sign
     */
    private static double legendreOverSine(int l, int m, double z)
    {
        double diagonal = 1.0;
        for (int k = 1; k <= m; k++)
        {
            diagonal *= -(2 * k - 1);
        }
        if (l == m)
        {
            return diagonal;
        }
        double previous = diagonal;
        double current = z * (2 * m + 1) * diagonal;
        for (int k = m + 2; k <= l; k++)
        {
            double next = ((2 * k - 1) * z * current - (k + m - 1) * previous) / (k - m);
            previous = current;
            current = next;
        }
        return current;
    }

    /**
     * @return {@code sqrt((2l + 1) / (4 pi) * (l - m)! / (l + m)!)}
     */
    private static double normalisation(int l, int m)
    {
        double ratio = 1.0;
        for (int k = l - m + 1; k <= l + m; k++)
        {
            ratio /= k;
        }
        return Math.sqrt((2 * l + 1) / (4.0 * Math.PI) * ratio);
    }
}
