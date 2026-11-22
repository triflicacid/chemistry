package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.quantum.Units;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.integrals.ElectrostaticPotential;
import com.ruben.chemistry.quantum.linalg.Matrix;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The electron density of a molecule and its electrostatic potential, evaluated on a grid.
 */
public final class ElectronDensity
{
    private ElectronDensity()
    {
    }

    /**
     * @param orbitals the calculation, whose density matrix holds all the electrons
     * @param grid the points to evaluate it at, in ångström
     * @param check called once for each slice of the grid, and able to stop the evaluation by throwing
     * @return the density at each point, in electrons per cubic bohr, running along x fastest, then y, then z
     */
    public static float[] evaluate(MolecularOrbitals orbitals, FieldGrid grid, Runnable check)
    {
        List<ContractedGaussian> functions = orbitals.basis().functions();
        Matrix density = orbitals.density();
        int n = functions.size();
        double[][] p = new double[n][n];
        for (int row = 0; row < n; row++)
        {
            for (int column = 0; column < n; column++)
            {
                p[row][column] = density.get(row, column);
            }
        }

        float[] values = new float[grid.size()];
        IntStream.range(0, grid.nz()).parallel().forEach(k ->
        {
            check.run();
            double z = Units.toBohr(grid.origin().z() + k * grid.spacing());
            double[] chi = new double[n];
            for (int j = 0; j < grid.ny(); j++)
            {
                double y = Units.toBohr(grid.origin().y() + j * grid.spacing());
                for (int i = 0; i < grid.nx(); i++)
                {
                    double x = Units.toBohr(grid.origin().x() + i * grid.spacing());
                    for (int mu = 0; mu < n; mu++)
                    {
                        chi[mu] = functions.get(mu).valueAt(x, y, z);
                    }

                    double sum = 0.0;
                    for (int mu = 0; mu < n; mu++)
                    {
                        double row = 0.0;
                        for (int nu = 0; nu < n; nu++)
                        {
                            row += p[mu][nu] * chi[nu];
                        }
                        sum += chi[mu] * row;
                    }
                    values[grid.indexOf(i, j, k)] = (float) sum;
                }
            }
        });
        return values;
    }

    /**
     * The electrostatic potential at the points of a grid where the density is within a band.
     *
     * @param orbitals the calculation
     * @param grid the points, in ångström
     * @param density the density at each point of the grid, as {@link #evaluate} gives it
     * @param least the least density at which the potential is found, in electrons per cubic bohr
     * @param most the greatest density at which the potential is found
     * @param check called once for each slice of the grid, and able to stop the evaluation by throwing
     * @return the potential at each point in hartree per elementary charge, running along x fastest, then y, then z, which is NaN at the points
     *     where the density is outside the band
     * @throws IllegalArgumentException if there is not a density for each point
     */
    public static float[] potential(MolecularOrbitals orbitals, FieldGrid grid, float[] density, double least, double most, Runnable check)
    {
        if (density.length != grid.size())
        {
            throw new IllegalArgumentException("expected a density for each of " + grid.size() + " points, but got " + density.length);
        }

        ElectrostaticPotential potential = new ElectrostaticPotential(orbitals.basis().functions(), orbitals.density(), orbitals.nuclei());
        float[] values = new float[grid.size()];
        IntStream.range(0, grid.nz()).parallel().forEach(k ->
        {
            check.run();
            double z = Units.toBohr(grid.origin().z() + k * grid.spacing());
            for (int j = 0; j < grid.ny(); j++)
            {
                double y = Units.toBohr(grid.origin().y() + j * grid.spacing());
                for (int i = 0; i < grid.nx(); i++)
                {
                    int at = grid.indexOf(i, j, k);
                    values[at] = density[at] >= least && density[at] <= most
                            ? (float) potential.at(Units.toBohr(grid.origin().x() + i * grid.spacing()), y, z)
                            : Float.NaN;
                }
            }
        });
        return values;
    }
}
