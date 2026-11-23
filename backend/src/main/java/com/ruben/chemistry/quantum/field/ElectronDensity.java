package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.quantum.Units;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.integrals.ElectrostaticPotential;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.space.Vec3;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The electron density of a molecule, its spin density and its electrostatic potential, evaluated on a grid.
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
        return evaluate(orbitals.basis().functions(), orbitals.density(), grid, check);
    }

    /**
     * @param orbitals the calculation
     * @param grid the points to evaluate it at, in ångström
     * @param check called once for each slice of the grid, and able to stop the evaluation by throwing
     * @return the spin density at each point, the electrons of spin up less those of spin down, in electrons per cubic bohr, running along x
     *     fastest, then y, then z; which is positive where spin up is in excess and negative where spin down is
     */
    public static float[] spin(MolecularOrbitals orbitals, FieldGrid grid, Runnable check)
    {
        return evaluate(orbitals.basis().functions(), orbitals.spinDensity(), grid, check);
    }

    private static float[] evaluate(List<ContractedGaussian> functions, Matrix density, FieldGrid grid, Runnable check)
    {
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
     * The negative of the Laplacian of the electron density, which is positive where charge is concentrated, as in a bond, a lone pair or an
     * inner shell, and negative where it is depleted.
     *
     * @param orbitals the calculation, whose density matrix holds all the electrons
     * @param grid the points to evaluate it at, in ångström
     * @param check called once for each slice of the grid, and able to stop the evaluation by throwing
     * @return minus the sum of the second derivatives of the density at each point, in electrons per fifth power of a bohr, running along x
     *     fastest, then y, then z
     */
    public static float[] negativeLaplacian(MolecularOrbitals orbitals, FieldGrid grid, Runnable check)
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
            double[][] chi = new double[ContractedGaussian.DERIVATIVES][n];
            double[] at = new double[ContractedGaussian.DERIVATIVES];
            for (int j = 0; j < grid.ny(); j++)
            {
                double y = Units.toBohr(grid.origin().y() + j * grid.spacing());
                for (int i = 0; i < grid.nx(); i++)
                {
                    double x = Units.toBohr(grid.origin().x() + i * grid.spacing());
                    for (int mu = 0; mu < n; mu++)
                    {
                        functions.get(mu).derivativesAt(x, y, z, at);
                        for (int d = 0; d < ContractedGaussian.DERIVATIVES; d++)
                        {
                            chi[d][mu] = at[d];
                        }
                    }

                    // the Laplacian of the density is 2 (the sum over x, y and z of slope · P slope, plus (P value) · the Laplacian of each function)
                    double laplacian = 0.0;
                    for (int mu = 0; mu < n; mu++)
                    {
                        double value = 0.0;
                        double[] slope = new double[3];
                        for (int nu = 0; nu < n; nu++)
                        {
                            value += p[mu][nu] * chi[0][nu];
                            for (int a = 0; a < 3; a++)
                            {
                                slope[a] += p[mu][nu] * chi[1 + a][nu];
                            }
                        }
                        laplacian += 2.0 * (chi[1][mu] * slope[0] + chi[2][mu] * slope[1] + chi[3][mu] * slope[2] + value * (chi[4][mu] + chi[5][mu] + chi[6][mu]));
                    }
                    values[grid.indexOf(i, j, k)] = (float) -laplacian;
                }
            }
        });
        return values;
    }

    /**
     * The electron localisation function, which is near one where electrons are paired and apart from others, as in a bond, a lone pair or a
     * core, and about a half where they are as free as in a uniform gas of the same density. It is found from the density matrices alone: the
     * density, its gradient and the kinetic energy density of each spin, which a set of orbitals gives as {@code sum over the density matrix of the
     * gradient of one basis function times the gradient of another}.
     *
     * <p>Each spin's Pauli kinetic energy {@code D = tau - |grad rho|^2 / (4 rho)} is compared with that of a uniform gas of its density, and the
     * function is {@code 1 / (1 + (D / D0)^2)}, with both spins' parts added before they are compared so that a closed shell gives the usual
     * function and an open shell gives one value for the whole.
     *
     * @param orbitals the calculation
     * @param grid the points to evaluate it at, in ångström
     * @param least the density, in electrons per cubic bohr, below which the function is not worked out and is zero, since in the tail of a
     *     density it tends to one whatever is there
     * @param check called once for each slice of the grid, and able to stop the evaluation by throwing
     * @return the function at each point, from zero to one, running along x fastest, then y, then z
     */
    public static float[] localisation(MolecularOrbitals orbitals, FieldGrid grid, double least, Runnable check)
    {
        List<ContractedGaussian> functions = orbitals.basis().functions();
        Matrix total = orbitals.density();
        Matrix difference = orbitals.spinDensity();
        int n = functions.size();
        double[][] up = new double[n][n];
        double[][] down = new double[n][n];
        for (int row = 0; row < n; row++)
        {
            for (int column = 0; column < n; column++)
            {
                up[row][column] = 0.5 * (total.get(row, column) + difference.get(row, column));
                down[row][column] = 0.5 * (total.get(row, column) - difference.get(row, column));
            }
        }

        float[] values = new float[grid.size()];
        IntStream.range(0, grid.nz()).parallel().forEach(k ->
        {
            check.run();
            double z = Units.toBohr(grid.origin().z() + k * grid.spacing());
            double[][] chi = new double[4][n];
            double[] at = new double[ContractedGaussian.DERIVATIVES];
            for (int j = 0; j < grid.ny(); j++)
            {
                double y = Units.toBohr(grid.origin().y() + j * grid.spacing());
                for (int i = 0; i < grid.nx(); i++)
                {
                    double x = Units.toBohr(grid.origin().x() + i * grid.spacing());
                    for (int mu = 0; mu < n; mu++)
                    {
                        functions.get(mu).derivativesAt(x, y, z, at);
                        for (int d = 0; d < 4; d++)
                        {
                            chi[d][mu] = at[d];
                        }
                    }

                    double[] spinUp = spinTerms(up, chi, n);
                    double[] spinDown = spinTerms(down, chi, n);
                    double rho = spinUp[0] + spinDown[0];
                    double pauli = 0.0;
                    double gas = 0.0;
                    for (double[] spin : new double[][]{spinUp, spinDown})
                    {
                        if (spin[0] > 1.0e-12)
                        {
                            pauli += spin[1] - spin[2] / (4.0 * spin[0]);
                            gas += UNIFORM_GAS * Math.pow(spin[0], 5.0 / 3.0);
                        }
                    }
                    double ratio = gas > 0.0 ? Math.max(0.0, pauli) / gas : 0.0;
                    values[grid.indexOf(i, j, k)] = rho < least ? 0.0f : (float) (1.0 / (1.0 + ratio * ratio));
                }
            }
        });
        return values;
    }

    /** The kinetic energy density of a uniform gas of one spin, divided by its density to the five thirds: {@code (3/5) (6 pi^2)^(2/3)}. */
    private static final double UNIFORM_GAS = 0.6 * Math.cbrt(36.0 * Math.PI * Math.PI * Math.PI * Math.PI);

    /**
     * @param p one spin's density matrix
     * @param chi the values of the basis functions at a point, then their slopes along x, y and z
     * @param n how many basis functions there are
     * @return the density of that spin there, its kinetic energy density {@code sum of P (slope of one) . (slope of another)}, and the square of
     *     the length of the gradient of its density
     */
    private static double[] spinTerms(double[][] p, double[][] chi, int n)
    {
        double rho = 0.0;
        double tau = 0.0;
        double[] gradient = new double[3];
        for (int mu = 0; mu < n; mu++)
        {
            double value = 0.0;
            double[] slope = new double[3];
            for (int nu = 0; nu < n; nu++)
            {
                value += p[mu][nu] * chi[0][nu];
                for (int a = 0; a < 3; a++)
                {
                    slope[a] += p[mu][nu] * chi[1 + a][nu];
                }
            }
            rho += chi[0][mu] * value;
            for (int a = 0; a < 3; a++)
            {
                gradient[a] += 2.0 * chi[1 + a][mu] * value;
                tau += chi[1 + a][mu] * slope[a];
            }
        }
        return new double[]{rho, tau, gradient[0] * gradient[0] + gradient[1] * gradient[1] + gradient[2] * gradient[2]};
    }

    /**
     * The electrostatic potential at every point of a flat grid, wherever the plane is.
     *
     * @param orbitals the calculation
     * @param origin the point at the low end of both axes of the plane, in ångström
     * @param stepU the step from one point to the next along the first axis, in ångström
     * @param stepV the step along the second axis, in ångström
     * @param width how many points there are along the first axis
     * @param height how many along the second
     * @param check called once for each row of the plane, and able to stop the evaluation by throwing
     * @return the potential at each point in hartree per elementary charge, running along the first axis fastest
     */
    public static float[] potentialOnPlane(MolecularOrbitals orbitals, Vec3 origin, Vec3 stepU, Vec3 stepV, int width, int height, Runnable check)
    {
        ElectrostaticPotential potential = new ElectrostaticPotential(orbitals.basis().functions(), orbitals.density(), orbitals.nuclei());
        float[] values = new float[width * height];
        IntStream.range(0, height).parallel().forEach(row ->
        {
            check.run();
            for (int column = 0; column < width; column++)
            {
                double x = Units.toBohr(origin.x() + column * stepU.x() + row * stepV.x());
                double y = Units.toBohr(origin.y() + column * stepU.y() + row * stepV.y());
                double z = Units.toBohr(origin.z() + column * stepU.z() + row * stepV.z());
                values[row * width + column] = (float) potential.at(x, y, z);
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
