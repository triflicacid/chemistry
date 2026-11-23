package com.ruben.chemistry.quantum.integrals;

/**
 * Calculates the McMurchie-Davidson Hermite expansion coefficients for products of one-dimensional Cartesian Gaussian functions.
 * <p>
 * The coefficients depend on the Gaussian exponents, angular momentum powers and distance between the Gaussian centres.
 */
public final class HermiteExpansion
{
    private HermiteExpansion()
    {
    }

    /**
     * Calculates the Hermite expansion coefficient E_t^{ij} using the McMurchie-Davidson recurrence relations.
     *
     * @param i power of the first Gaussian
     * @param j power of the second Gaussian
     * @param t Hermite expansion order
     * @param separation distance between the Gaussian centres (bohr)
     * @param a exponent of the first Gaussian
     * @param b exponent of the second Gaussian
     * @return the expansion coefficient E_t^{ij}
     */
    public static double coefficient(int i, int j, int t, double separation, double a, double b)
    {
        if (t < 0 || t > i + j)
        {
            return 0.0;
        }
        double p = a + b;
        double q = a * b / p;
        if (i == 0 && j == 0)
        {
            return Math.exp(-q * separation * separation);
        }
        if (j == 0)
        {
            return coefficient(i - 1, j, t - 1, separation, a, b) / (2.0 * p)
                    - q * separation / a * coefficient(i - 1, j, t, separation, a, b)
                    + (t + 1) * coefficient(i - 1, j, t + 1, separation, a, b);
        }
        return coefficient(i, j - 1, t - 1, separation, a, b) / (2.0 * p)
                + q * separation / b * coefficient(i, j - 1, t, separation, a, b)
                + (t + 1) * coefficient(i, j - 1, t + 1, separation, a, b);
    }
}
