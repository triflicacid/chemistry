package com.ruben.chemistry.quantum.grid;

/**
 * Points on the unit sphere with weights that sum to {@code 4 pi}.
 *
 * @param x the x components of the unit vectors
 * @param y the y components of the unit vectors
 * @param z the z components of the unit vectors
 * @param weights the weight of each direction
 */
public record SphericalGrid(double[] x, double[] y, double[] z, double[] weights)
{
    /**
     * A product grid: Gauss-Legendre points in the cosine of the polar angle, and equally spaced azimuths.
     *
     * @param polarPoints the number of Gauss-Legendre points in the polar angle; there are twice as many azimuths
     * @return the grid, with {@code 2 * polarPoints^2} directions
     * @throws IllegalArgumentException if {@code polarPoints} is less than 1
     */
    public static SphericalGrid product(int polarPoints)
    {
        GaussLegendre polar = GaussLegendre.of(polarPoints);
        int azimuths = 2 * polarPoints;
        int count = polarPoints * azimuths;
        double[] x = new double[count];
        double[] y = new double[count];
        double[] z = new double[count];
        double[] weights = new double[count];
        int index = 0;
        for (int i = 0; i < polarPoints; i++)
        {
            double cosine = polar.nodes()[i];
            double sine = Math.sqrt(1.0 - cosine * cosine);
            for (int j = 0; j < azimuths; j++)
            {
                double phi = 2.0 * Math.PI * j / azimuths;
                x[index] = sine * Math.cos(phi);
                y[index] = sine * Math.sin(phi);
                z[index] = cosine;
                weights[index] = polar.weights()[i] * 2.0 * Math.PI / azimuths;
                index++;
            }
        }
        return new SphericalGrid(x, y, z, weights);
    }

    /**
     * @return the number of directions
     */
    public int size()
    {
        return weights.length;
    }
}
