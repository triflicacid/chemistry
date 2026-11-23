package com.ruben.chemistry.quantum.integrals;

/**
 * The numerical grid around each core-potential centre that its integrals are evaluated on.
 *
 * @param radialPoints the number of points along each ray from the centre
 * @param polarPoints the number of Gauss-Legendre points in the polar angle; the number of directions is {@code 2 * polarPoints^2}
 * @param radialScale the distance in bohr where the radial points are densest, as in {@code RadialGrid.mapped}
 */
public record EcpQuadrature(int radialPoints, int polarPoints, double radialScale)
{
    public static EcpQuadrature defaults()
    {
        return new EcpQuadrature(150, 30, 1.0);
    }
}
