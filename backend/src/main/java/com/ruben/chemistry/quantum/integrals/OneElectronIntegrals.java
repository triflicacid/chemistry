package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.linalg.Matrix;

import java.util.List;

/**
 * Integrals over a pair of Cartesian Gaussian basis functions, for any angular momentum.
 * Lengths are in bohr and energies in hartree.
 */
public final class OneElectronIntegrals
{
    private OneElectronIntegrals()
    {
    }

    @FunctionalInterface
    private interface PrimitiveIntegral
    {
        /**
         * @param a the function the first primitive belongs to
         * @param i the index of the first primitive within {@code a}
         * @param b the function the second primitive belongs to
         * @param j the index of the second primitive within {@code b}
         * @return the integral between the unnormalised Cartesian primitive {@code a_i} and {@code b_j}
         */
        double evaluate(ContractedGaussian a, int i, ContractedGaussian b, int j);
    }

    /**
     * @param a one basis function
     * @param b another basis function
     * @return the integral over all space of the product of the two functions
     */
    public static double overlap(ContractedGaussian a, ContractedGaussian b)
    {
        return contract(OneElectronIntegrals::overlapPrimitive, a, b);
    }

    /**
     * @param basis the basis functions, in order
     * @return the symmetric matrix of overlaps between every pair
     */
    public static Matrix overlapMatrix(List<ContractedGaussian> basis)
    {
        return matrix(OneElectronIntegrals::overlapPrimitive, basis);
    }

    /**
     * @param a the function on the left of the integral
     * @param b the function the kinetic-energy operator acts on
     * @return the integral of {@code a} times {@code -1/2 nabla^2} of {@code b}
     */
    public static double kinetic(ContractedGaussian a, ContractedGaussian b)
    {
        return contract(OneElectronIntegrals::kineticPrimitive, a, b);
    }

    /**
     * @param basis the basis functions, in order
     * @return the symmetric matrix of kinetic-energy integrals between every pair
     */
    public static Matrix kineticMatrix(List<ContractedGaussian> basis)
    {
        return matrix(OneElectronIntegrals::kineticPrimitive, basis);
    }

    private static double contract(PrimitiveIntegral integral, ContractedGaussian a, ContractedGaussian b)
    {
        double sum = 0.0;
        for (int i = 0; i < a.size(); i++)
        {
            for (int j = 0; j < b.size(); j++)
            {
                sum += a.coefficient(i) * b.coefficient(j) * integral.evaluate(a, i, b, j);
            }
        }
        return sum;
    }

    private static Matrix matrix(PrimitiveIntegral integral, List<ContractedGaussian> basis)
    {
        int n = basis.size();
        Matrix result = new Matrix(n, n);
        for (int row = 0; row < n; row++)
        {
            // the operators are symmetric, so only the upper triangle is worked out
            for (int column = row; column < n; column++)
            {
                double value = contract(integral, basis.get(row), basis.get(column));
                result.set(row, column, value);
                result.set(column, row, value);
            }
        }
        return result;
    }

    /**
     * @return the overlap of two unnormalised Cartesian primitives, one from each function
     */
    private static double overlapPrimitive(ContractedGaussian a, int i, ContractedGaussian b, int j)
    {
        double[] overlap = overlapsByAxis(a, i, b, j);
        return overlap[0] * overlap[1] * overlap[2];
    }

    /**
     * @return the kinetic-energy integral between two unnormalised Cartesian primitives, one from each function
     */
    private static double kineticPrimitive(ContractedGaussian a, int i, ContractedGaussian b, int j)
    {
        double exponentA = a.exponent(i);
        double exponentB = b.exponent(j);
        int[] powersA = powers(a);
        int[] powersB = powers(b);
        double[] separation = separation(a, b);
        double[] overlap = overlapsByAxis(a, i, b, j);

        double[] kinetic = new double[3];
        for (int axis = 0; axis < 3; axis++)
        {
            kinetic[axis] = kinetic1d(powersA[axis], powersB[axis], separation[axis], exponentA, exponentB);
        }

        // the three-dimensional operator is a sum over axes, and each term only changes the factor on its own axis
        return kinetic[0] * overlap[1] * overlap[2] + overlap[0] * kinetic[1] * overlap[2] + overlap[0] * overlap[1] * kinetic[2];
    }

    /**
     * @return the one-dimensional integral of {@code -1/2 d^2/dx^2} between two Cartesian Gaussians
     */
    private static double kinetic1d(int i, int j, double separation, double a, double b)
    {
        // differentiating (x - B)^j exp(-b (x - B)^2) twice gives j(j-1) times the power j-2, minus 2b(2j+1) times
        // the same power, plus 4b^2 times the power j+2, so the result needs only overlaps with shifted powers
        double lower = j >= 2 ? j * (j - 1) * overlap1d(i, j - 2, separation, a, b) : 0.0;
        double same = -2.0 * b * (2 * j + 1) * overlap1d(i, j, separation, a, b);
        double upper = 4.0 * b * b * overlap1d(i, j + 2, separation, a, b);
        return -0.5 * (lower + same + upper);
    }

    /**
     * @return the one-dimensional overlap of two unnormalised Cartesian Gaussians
     */
    private static double overlap1d(int i, int j, double separation, double a, double b)
    {
        return HermiteExpansion.coefficient(i, j, 0, separation, a, b) * Math.sqrt(Math.PI / (a + b));
    }

    /**
     * @return the one-dimensional overlap on each of x, y and z between one primitive of each function
     */
    private static double[] overlapsByAxis(ContractedGaussian a, int i, ContractedGaussian b, int j)
    {
        int[] powersA = powers(a);
        int[] powersB = powers(b);
        double[] separation = separation(a, b);
        double[] overlap = new double[3];
        for (int axis = 0; axis < 3; axis++)
        {
            overlap[axis] = overlap1d(powersA[axis], powersB[axis], separation[axis], a.exponent(i), b.exponent(j));
        }
        return overlap;
    }

    private static int[] powers(ContractedGaussian function)
    {
        return new int[] {function.angular().lx(), function.angular().ly(), function.angular().lz()};
    }

    private static double[] separation(ContractedGaussian a, ContractedGaussian b)
    {
        return new double[] {a.centre().x() - b.centre().x(), a.centre().y() - b.centre().y(), a.centre().z() - b.centre().z()};
    }
}
