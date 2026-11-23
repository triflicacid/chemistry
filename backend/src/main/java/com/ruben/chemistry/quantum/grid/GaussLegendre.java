package com.ruben.chemistry.quantum.grid;

/**
 * Gauss-Legendre quadrature on the interval from -1 to 1, which integrates polynomials up to degree {@code 2n - 1}
 * exactly with {@code n} points.
 *
 * @param nodes the points, in ascending order
 * @param weights the weight of each point
 */
public record GaussLegendre(double[] nodes, double[] weights)
{
    /**
     * @param count the number of points, at least 1
     * @return the rule with that many points
     * @throws IllegalArgumentException if {@code count} is less than 1
     */
    public static GaussLegendre of(int count)
    {
        if (count < 1)
        {
            throw new IllegalArgumentException("need at least one point, was " + count);
        }
        double[] nodes = new double[count];
        double[] weights = new double[count];
        for (int i = 0; i < count; i++)
        {
            // each root of the Legendre polynomial is refined by Newton's method from a cosine estimate
            double x = Math.cos(Math.PI * (i + 0.75) / (count + 0.5));
            for (int iteration = 0; iteration < 100; iteration++)
            {
                double step = legendre(count, x)[0] / derivative(count, x);
                x -= step;
                if (Math.abs(step) < 1.0e-15)
                {
                    break;
                }
            }
            double slope = derivative(count, x);
            // the estimates run from the largest root down, so they are stored from the far end to give ascending order
            nodes[count - 1 - i] = x;
            weights[count - 1 - i] = 2.0 / ((1.0 - x * x) * slope * slope);
        }
        return new GaussLegendre(nodes, weights);
    }

    /**
     * @return {@code P_n(x)} followed by {@code P_(n-1)(x)}
     */
    private static double[] legendre(int n, double x)
    {
        double previous = 1.0;
        double current = x;
        for (int k = 2; k <= n; k++)
        {
            double next = ((2 * k - 1) * x * current - (k - 1) * previous) / k;
            previous = current;
            current = next;
        }
        return new double[] {current, previous};
    }

    /**
     * @return {@code P_n'(x)}, from the identity {@code (x^2 - 1) P_n' = n (x P_n - P_(n-1))}
     */
    private static double derivative(int n, double x)
    {
        double[] values = legendre(n, x);
        return n * (x * values[0] - values[1]) / (x * x - 1.0);
    }
}
