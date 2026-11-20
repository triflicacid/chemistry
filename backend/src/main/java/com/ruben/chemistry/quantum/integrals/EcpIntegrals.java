package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.basis.EcpChannel;
import com.ruben.chemistry.quantum.basis.EcpTerm;
import com.ruben.chemistry.quantum.basis.EffectiveCorePotential;
import com.ruben.chemistry.quantum.grid.RadialGrid;
import com.ruben.chemistry.quantum.grid.RealSphericalHarmonics;
import com.ruben.chemistry.quantum.grid.SphericalGrid;
import com.ruben.chemistry.quantum.linalg.Matrix;

import java.util.List;

/**
 * Matrix elements of semilocal effective core potentials between basis functions, for any angular momentum.
 * <p>
 * The potential on a centre is {@code U_L(r) + sum over l < L, m of |lm> (U_l(r) - U_L(r)) <lm|}, where {@code |lm>}
 * are spherical harmonics about the centre.
 */
public final class EcpIntegrals
{
    private EcpIntegrals()
    {
    }

    /**
     * Uses {@link EcpQuadrature#defaults()}.
     *
     * @param basis the basis functions, in order
     * @param centres the core potentials to add up
     * @return the symmetric matrix of core-potential integrals between every pair of functions, in hartree
     */
    public static Matrix matrix(List<ContractedGaussian> basis, List<EcpCentre> centres)
    {
        return matrix(basis, centres, EcpQuadrature.defaults());
    }

    /**
     * @param basis the basis functions, in order
     * @param centres the core potentials to add up
     * @param quadrature the grid around each centre
     * @return the symmetric matrix of core-potential integrals between every pair of functions, in hartree
     */
    public static Matrix matrix(List<ContractedGaussian> basis, List<EcpCentre> centres, EcpQuadrature quadrature)
    {
        int n = basis.size();
        RadialGrid radial = RadialGrid.mapped(quadrature.radialPoints(), quadrature.radialScale());
        SphericalGrid angular = SphericalGrid.product(quadrature.polarPoints());

        double[][] sum = new double[n][n];
        for (EcpCentre centre : centres)
        {
            addCentre(sum, basis, centre, radial, angular);
        }

        Matrix result = new Matrix(n, n);
        for (int row = 0; row < n; row++)
        {
            for (int column = 0; column <= row; column++)
            {
                // only the lower triangle was accumulated, as the operator is symmetric
                result.set(row, column, sum[row][column]);
                result.set(column, row, sum[row][column]);
            }
        }
        return result;
    }

    private static void addCentre(double[][] sum, List<ContractedGaussian> basis, EcpCentre centre, RadialGrid radial, SphericalGrid angular)
    {
        EffectiveCorePotential potential = centre.potential();
        int n = basis.size();
        int directions = angular.size();

        // the harmonics of each projected angular momentum at every direction do not depend on the radius
        double[][][] harmonics = new double[potential.maxAngularMomentum()][directions][];
        for (int l = 0; l < potential.maxAngularMomentum(); l++)
        {
            for (int j = 0; j < directions; j++)
            {
                harmonics[l][j] = RealSphericalHarmonics.values(l, angular.x()[j], angular.y()[j], angular.z()[j]);
            }
        }

        double[][] values = new double[n][directions];
        for (int shell = 0; shell < radial.size(); shell++)
        {
            double r = radial.radii()[shell];
            double weight = radial.weights()[shell];
            for (int b = 0; b < n; b++)
            {
                for (int j = 0; j < directions; j++)
                {
                    values[b][j] = basis.get(b).valueAt(
                            centre.position().x() + r * angular.x()[j],
                            centre.position().y() + r * angular.y()[j],
                            centre.position().z() + r * angular.z()[j]);
                }
            }

            // the local part acts on every electron, so it is just a weighted overlap on this shell
            double local = weight * radialValue(potential.local(), r);
            for (int a = 0; a < n; a++)
            {
                for (int b = 0; b <= a; b++)
                {
                    double overlap = 0.0;
                    for (int j = 0; j < directions; j++)
                    {
                        overlap += angular.weights()[j] * values[a][j] * values[b][j];
                    }
                    sum[a][b] += local * overlap;
                }
            }

            // each projector keeps only the l-th angular component of a function on this shell
            for (int l = 0; l < potential.maxAngularMomentum(); l++)
            {
                double radialFactor = weight * radialValue(potential.projectors().get(l), r);
                int components = 2 * l + 1;
                double[][] projected = new double[n][components];
                for (int b = 0; b < n; b++)
                {
                    for (int j = 0; j < directions; j++)
                    {
                        double weighted = angular.weights()[j] * values[b][j];
                        for (int m = 0; m < components; m++)
                        {
                            projected[b][m] += weighted * harmonics[l][j][m];
                        }
                    }
                }
                for (int a = 0; a < n; a++)
                {
                    for (int b = 0; b <= a; b++)
                    {
                        double overlap = 0.0;
                        for (int m = 0; m < components; m++)
                        {
                            overlap += projected[a][m] * projected[b][m];
                        }
                        sum[a][b] += radialFactor * overlap;
                    }
                }
            }
        }
    }

    /**
     * @return the channel's radial potential {@code sum of c r^(n-2) exp(-zeta r^2)} at distance {@code r}
     */
    private static double radialValue(EcpChannel channel, double r)
    {
        double value = 0.0;
        for (EcpTerm term : channel.terms())
        {
            value += term.coefficient() * Math.pow(r, term.power() - 2) * Math.exp(-term.exponent() * r * r);
        }
        return value;
    }
}
