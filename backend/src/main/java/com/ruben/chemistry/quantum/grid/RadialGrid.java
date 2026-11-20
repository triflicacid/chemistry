package com.ruben.chemistry.quantum.grid;

/**
 * Points along a ray from the origin, with weights for integrating {@code f(r) r^2 dr} from 0 to infinity.
 *
 * @param radii the distances from the origin, in bohr, which are all positive
 * @param weights the weight of each point, which already includes the factor {@code r^2}
 */
public record RadialGrid(double[] radii, double[] weights)
{
    /**
     * Gauss-Legendre quadrature on {@code x} from -1 to 1, mapped to {@code r = scale (1 + x) / (1 - x)} so the points
     * crowd towards the origin and thin out slowly at large distance.
     *
     * @param count the number of points, at least 1
     * @param scale the distance the mapping sends {@code x = 0} to, in bohr, which must be positive
     * @return the grid
     * @throws IllegalArgumentException if {@code count} is less than 1 or {@code scale} is not positive
     */
    public static RadialGrid mapped(int count, double scale)
    {
        if (!(scale > 0.0))
        {
            throw new IllegalArgumentException("scale must be positive, was " + scale);
        }
        GaussLegendre rule = GaussLegendre.of(count);
        double[] radii = new double[count];
        double[] weights = new double[count];
        for (int i = 0; i < count; i++)
        {
            double x = rule.nodes()[i];
            double r = scale * (1.0 + x) / (1.0 - x);
            // dr/dx converts the integral over r into one over x
            double jacobian = 2.0 * scale / ((1.0 - x) * (1.0 - x));
            radii[i] = r;
            weights[i] = rule.weights()[i] * jacobian * r * r;
        }
        return new RadialGrid(radii, weights);
    }

    /**
     * @return the number of points
     */
    public int size()
    {
        return radii.length;
    }
}
