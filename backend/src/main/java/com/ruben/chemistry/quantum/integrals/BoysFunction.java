package com.ruben.chemistry.quantum.integrals;

/**
 * The Boys function {@code F_n(x) = \int_0^1 t^(2n) exp(-x t^2) dt}, which turns the Coulomb
 * integrals over Gaussians into closed forms.
 */
public final class BoysFunction
{
    /**
     * Above this {@code x} the error function in {@code F_0(x) = sqrt(pi / x) erf(sqrt(x)) / 2} is one to machine precision.
     */
    private static final double LARGE = 35.0;

    private static final double CONVERGENCE = 1.0e-17;
    private static final int MAX_TERMS = 1000;

    private BoysFunction()
    {
    }

    /**
     * @param maxOrder the highest order wanted, at least 0
     * @param x the argument, at least 0
     * @return {@code F_0(x)} to {@code F_maxOrder(x)}, with {@code F_n(x)} at index {@code n}
     * @throws IllegalArgumentException if {@code maxOrder} is negative or {@code x} is negative or not a number
     */
    public static double[] values(int maxOrder, double x)
    {
        if (maxOrder < 0)
        {
            throw new IllegalArgumentException("order must not be negative, was " + maxOrder);
        }
        if (!(x >= 0.0))
        {
            throw new IllegalArgumentException("argument must not be negative, was " + x);
        }
        double[] values = new double[maxOrder + 1];
        double decay = Math.exp(-x);
        if (x < LARGE)
        {
            // the series for the highest order has only positive terms, so it loses no precision, and the lower
            // orders follow by recursing downwards, which does not amplify errors
            values[maxOrder] = series(maxOrder, x) * decay;
            for (int n = maxOrder; n > 0; n--)
            {
                values[n - 1] = (2.0 * x * values[n] + decay) / (2.0 * n - 1.0);
            }
        }
        else
        {
            // the highest orders are tiny here, and recursing upwards from the exact F_0 is stable for large x
            values[0] = 0.5 * Math.sqrt(Math.PI / x);
            for (int n = 0; n < maxOrder; n++)
            {
                values[n + 1] = ((2.0 * n + 1.0) * values[n] - decay) / (2.0 * x);
            }
        }
        return values;
    }

    /**
     * @return the sum over k of {@code (2x)^k / ((2n + 1)(2n + 3)...(2n + 2k + 1))}, which is {@code F_n(x) / exp(-x)}
     */
    private static double series(int n, double x)
    {
        double term = 1.0 / (2.0 * n + 1.0);
        double sum = term;
        for (int k = 1; k < MAX_TERMS; k++)
        {
            term *= 2.0 * x / (2.0 * n + 2.0 * k + 1.0);
            sum += term;
            if (term < sum * CONVERGENCE)
            {
                break;
            }
        }
        return sum;
    }
}
