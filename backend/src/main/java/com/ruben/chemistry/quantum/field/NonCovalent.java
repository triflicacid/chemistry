package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.quantum.Units;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.linalg.Matrix;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The two fields a plot of non-covalent interactions is made of.
 *
 * <p>The reduced density gradient {@code s = |∇ρ| / (2 (3π²)^(1/3) ρ^(4/3))} is small wherever the density neither rises nor falls steeply for its
 * size, which is true at a bond critical point and in the space where two molecules touch. Where it is small, the sign of the middle eigenvalue
 * of the density's Hessian says what kind of contact it is: negative where the density is drawn together along the contact, as in a hydrogen
 * bond, and positive where it is pushed apart, as in a clash or inside a ring.
 */
public final class NonCovalent
{
    /** The value the reduced density gradient is given where there is no density to speak of, which is more than any surface is drawn at. */
    public static final float FAR = 100.0f;

    /** The density, in electrons per cubic bohr, below which a point is taken to be in empty space. */
    private static final double EMPTY = 1.0e-10;

    /** The factor that makes the reduced density gradient dimensionless: {@code 1 / (2 (3π²)^(1/3))}. */
    private static final double SCALE = 1.0 / (2.0 * Math.cbrt(3.0 * Math.PI * Math.PI));

    private NonCovalent()
    {
    }

    /**
     * Both fields of a grid.
     *
     * @param reducedGradient the reduced density gradient at each point, which is {@link #FAR} where there is no density, running along x fastest, then y, then z
     * @param signedDensity the density at each point, in electrons per cubic bohr, with the sign of the middle eigenvalue of its Hessian
     */
    public record Fields(float[] reducedGradient, float[] signedDensity)
    {
    }

    /**
     * @param orbitals the calculation, whose density matrix holds all the electrons
     * @param grid the points to evaluate it at, in ångström
     * @param check called once for each slice of the grid, and able to stop the evaluation by throwing
     * @return the reduced density gradient and the signed density at each point
     */
    public static Fields evaluate(MolecularOrbitals orbitals, FieldGrid grid, Runnable check)
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

        float[] gradient = new float[grid.size()];
        float[] signed = new float[grid.size()];
        IntStream.range(0, grid.nz()).parallel().forEach(k ->
        {
            check.run();
            double z = Units.toBohr(grid.origin().z() + k * grid.spacing());
            double[][] chi = new double[ContractedGaussian.DERIVATIVES][n];
            double[][] applied = new double[4][n];
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

                    // the density matrix applied to the values and to the three slopes, which is all the second derivatives of the density need
                    for (int part = 0; part < 4; part++)
                    {
                        for (int mu = 0; mu < n; mu++)
                        {
                            double sum = 0.0;
                            for (int nu = 0; nu < n; nu++)
                            {
                                sum += p[mu][nu] * chi[part][nu];
                            }
                            applied[part][mu] = sum;
                        }
                    }

                    int index = grid.indexOf(i, j, k);
                    double[] result = fieldsAt(chi, applied, n);
                    gradient[index] = (float) result[0];
                    signed[index] = (float) result[1];
                }
            }
        });
        return new Fields(gradient, signed);
    }

    /**
     * @param chi the value and derivatives of every basis function at the point, in the order {@link ContractedGaussian#derivativesAt} gives them
     * @param applied the density matrix times the values of the functions, then times each of their three slopes
     * @param n how many basis functions there are
     * @return the reduced density gradient and the signed density at the point
     */
    private static double[] fieldsAt(double[][] chi, double[][] applied, int n)
    {
        double rho = 0.0;
        double[] slope = new double[3];
        double[][] hessian = new double[3][3];
        // the second derivatives of a basis function are paired with the density matrix applied to the values
        int[][] pairs = {{0, 0, 4}, {1, 1, 5}, {2, 2, 6}, {0, 1, 7}, {0, 2, 8}, {1, 2, 9}};
        for (int mu = 0; mu < n; mu++)
        {
            rho += chi[0][mu] * applied[0][mu];
            for (int a = 0; a < 3; a++)
            {
                slope[a] += 2.0 * chi[1 + a][mu] * applied[0][mu];
            }
            for (int[] pair : pairs)
            {
                int a = pair[0];
                int b = pair[1];
                hessian[a][b] += 2.0 * (chi[1 + a][mu] * applied[1 + b][mu] + applied[0][mu] * chi[pair[2]][mu]);
            }
        }

        if (!(rho > EMPTY))
        {
            return new double[]{FAR, 0.0};
        }

        double length = Math.sqrt(slope[0] * slope[0] + slope[1] * slope[1] + slope[2] * slope[2]);
        double reduced = Math.min(FAR, SCALE * length / Math.pow(rho, 4.0 / 3.0));
        double middle = middleEigenvalue(hessian[0][0], hessian[1][1], hessian[2][2], hessian[0][1], hessian[0][2], hessian[1][2]);
        return new double[]{reduced, rho * Math.signum(middle)};
    }

    /**
     * @param xx the second derivative along x
     * @param yy the second derivative along y
     * @param zz the second derivative along z
     * @param xy the mixed derivative in x and y
     * @param xz the mixed derivative in x and z
     * @param yz the mixed derivative in y and z
     * @return the middle one of the three eigenvalues of that symmetric matrix
     */
    static double middleEigenvalue(double xx, double yy, double zz, double xy, double xz, double yz)
    {
        double offDiagonal = xy * xy + xz * xz + yz * yz;
        double mean = (xx + yy + zz) / 3.0;
        if (offDiagonal == 0.0)
        {
            double[] diagonal = {xx, yy, zz};
            Arrays.sort(diagonal);
            return diagonal[1];
        }

        double spread = Math.sqrt(((xx - mean) * (xx - mean) + (yy - mean) * (yy - mean) + (zz - mean) * (zz - mean) + 2.0 * offDiagonal) / 6.0);
        // the determinant of (matrix - mean) / spread, whose eigenvalues are 2 cos(angle + 2πk/3)
        double bxx = (xx - mean) / spread;
        double byy = (yy - mean) / spread;
        double bzz = (zz - mean) / spread;
        double bxy = xy / spread;
        double bxz = xz / spread;
        double byz = yz / spread;
        double determinant = bxx * (byy * bzz - byz * byz) - bxy * (bxy * bzz - byz * bxz) + bxz * (bxy * byz - byy * bxz);
        double angle = Math.acos(Math.clamp(determinant / 2.0, -1.0, 1.0)) / 3.0;
        double largest = mean + spread * 2.0 * Math.cos(angle);
        double smallest = mean + spread * 2.0 * Math.cos(angle + 2.0 * Math.PI / 3.0);
        // the three add up to the trace, so the one that is neither of the other two is what is left
        return 3.0 * mean - largest - smallest;
    }
}
