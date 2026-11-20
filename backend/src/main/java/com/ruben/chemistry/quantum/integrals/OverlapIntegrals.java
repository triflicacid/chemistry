package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.linalg.Matrix;

import java.util.List;

/**
 * Calculates overlap integrals between Cartesian Gaussian basis functions.
 */
public final class OverlapIntegrals
{
    private OverlapIntegrals()
    {
    }

    /**
     * Calculates the overlap integral between two contracted Gaussians.
     *
     * @param a first basis function
     * @param b second basis function
     * @return the integral of their product over all space
     */
    public static double between(ContractedGaussian a, ContractedGaussian b)
    {
        double sum = 0.0;
        for (int i = 0; i < a.size(); i++)
        {
            for (int j = 0; j < b.size(); j++)
            {
                sum += a.coefficient(i) * b.coefficient(j) * primitive(a, i, b, j);
            }
        }
        return sum;
    }

    /**
     * Calculates the overlap matrix for a set of basis functions.
     * <p>
     * The matrix is symmetric, with each element S[i,j] representing the overlap between basis functions i and j.
     *
     * @param basis the basis functions
     * @return the overlap matrix
     */
    public static Matrix matrix(List<ContractedGaussian> basis)
    {
        int n = basis.size();
        Matrix overlap = new Matrix(n, n);
        for (int row = 0; row < n; row++)
        {
            for (int column = row; column < n; column++)
            {
                double value = between(basis.get(row), basis.get(column));
                overlap.set(row, column, value);
                overlap.set(column, row, value);
            }
        }
        return overlap;
    }

    /**
     * Calculates the overlap between two unnormalised primitive Cartesian Gaussians.
     *
     * @return the primitive overlap integral
     */
    private static double primitive(ContractedGaussian a, int i, ContractedGaussian b, int j)
    {
        double exponentA = a.exponent(i);
        double exponentB = b.exponent(j);
        double p = exponentA + exponentB;
        double x = HermiteExpansion.coefficient(a.angular().lx(), b.angular().lx(), 0, a.centre().x() - b.centre().x(), exponentA, exponentB);
        double y = HermiteExpansion.coefficient(a.angular().ly(), b.angular().ly(), 0, a.centre().y() - b.centre().y(), exponentA, exponentB);
        double z = HermiteExpansion.coefficient(a.angular().lz(), b.angular().lz(), 0, a.centre().z() - b.centre().z(), exponentA, exponentB);
        return x * y * z * Math.pow(Math.PI / p, 1.5);
    }
}
