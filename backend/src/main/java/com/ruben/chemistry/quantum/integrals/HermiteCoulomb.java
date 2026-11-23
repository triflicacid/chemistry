package com.ruben.chemistry.quantum.integrals;

/**
 * The Hermite Coulomb integrals {@code R_tuv}.
 * <p>
 * For a Gaussian pair with total exponent {@code p} and a separation vector {@code (X, Y, Z)} between the
 * pair's centre and the other point, {@code R_tuv} is the result of differentiating
 * {@code F_0(p (X^2 + Y^2 + Z^2))} {@code t} times with respect to {@code X}, {@code u} times with respect to
 * {@code Y} and {@code v} times with respect to {@code Z}, where {@code F_0} is the Boys function.
 */
public final class HermiteCoulomb
{
    private HermiteCoulomb()
    {
    }

    /**
     * @param maxOrder the largest total order {@code t + u + v} wanted, at least 0
     * @param exponent the total exponent {@code p} of the Gaussian pair, which must be positive
     * @param x the x component of the separation, in bohr
     * @param y the y component of the separation, in bohr
     * @param z the z component of the separation, in bohr
     * @return the array {@code r[t][u][v]} holding {@code R_tuv} for every {@code t + u + v <= maxOrder}, where each
     *     dimension has length {@code maxOrder + 1}
     * @throws IllegalArgumentException if {@code maxOrder} is negative or {@code exponent} is not positive
     */
    public static double[][][] integrals(int maxOrder, double exponent, double x, double y, double z)
    {
        if (maxOrder < 0)
        {
            throw new IllegalArgumentException("order must not be negative, was " + maxOrder);
        }
        if (!(exponent > 0.0))
        {
            throw new IllegalArgumentException("exponent must be positive, was " + exponent);
        }
        int size = maxOrder + 1;
        double[] boys = BoysFunction.values(maxOrder, exponent * (x * x + y * y + z * z));

        // r[n] is the auxiliary integral R^n_tuv, which the recurrence lowers to n = 0 one order at a time
        // it starts from (-2p)^n F_n at order zero
        double[][][][] r = new double[size][size][size][size];
        double factor = 1.0;
        for (int n = 0; n < size; n++)
        {
            r[n][0][0][0] = factor * boys[n];
            factor *= -2.0 * exponent;
        }

        for (int order = 1; order <= maxOrder; order++)
        {
            for (int n = 0; n <= maxOrder - order; n++)
            {
                for (int t = 0; t <= order; t++)
                {
                    for (int u = 0; u <= order - t; u++)
                    {
                        int v = order - t - u;
                        r[n][t][u][v] = raise(r[n + 1], t, u, v, x, y, z);
                    }
                }
            }
        }
        return r[0];
    }

    /**
     * @return {@code R^n_tuv} from the entries one order lower at {@code n + 1}, which are in {@code lower}
     */
    private static double raise(double[][][] lower, int t, int u, int v, double x, double y, double z)
    {
        // each step lowers one index by one and, for indices of 2 or more, also by two
        if (t > 0)
        {
            return (t > 1 ? (t - 1) * lower[t - 2][u][v] : 0.0) + x * lower[t - 1][u][v];
        }
        if (u > 0)
        {
            return (u > 1 ? (u - 1) * lower[t][u - 2][v] : 0.0) + y * lower[t][u - 1][v];
        }
        return (v > 1 ? (v - 1) * lower[t][u][v - 2] : 0.0) + z * lower[t][u][v - 1];
    }
}
