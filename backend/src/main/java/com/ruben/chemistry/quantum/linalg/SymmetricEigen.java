package com.ruben.chemistry.quantum.linalg;

import java.util.Arrays;
import java.util.Comparator;

/**
 * The eigendecomposition of a real symmetric matrix, by cyclic Jacobi rotations (TODO use tridiagonalisation?)
 *
 * @param values the eigenvalues, in ascending order
 * @param vectors a matrix whose column {@code k} is the unit eigenvector for {@code values[k]}
 */
public record SymmetricEigen(double[] values, Matrix vectors)
{
    private static final int MAX_SWEEPS = 100;
    private static final double SYMMETRY_TOLERANCE = 1.0e-10;
    private static final double CONVERGENCE = 1.0e-15;

    /**
     * @param matrix a square, symmetric matrix
     * @return its eigenvalues and orthonormal eigenvectors
     * @throws IllegalArgumentException if the matrix is not symmetric
     * @throws ArithmeticException if the rotations have not converged after {@value #MAX_SWEEPS} sweeps
     */
    public static SymmetricEigen of(Matrix matrix)
    {
        if (!matrix.isSymmetric(SYMMETRY_TOLERANCE))
        {
            throw new IllegalArgumentException("matrix is not symmetric");
        }
        int n = matrix.rows();
        Matrix a = matrix.copy();
        Matrix v = Matrix.identity(n);
        double norm = a.frobeniusNorm();

        boolean converged = n < 2 || norm == 0.0;
        for (int sweep = 0; sweep < MAX_SWEEPS && !converged; sweep++)
        {
            if (offDiagonalNorm(a) <= CONVERGENCE * norm)
            {
                converged = true;
                break;
            }
            for (int p = 0; p < n - 1; p++)
            {
                for (int q = p + 1; q < n; q++)
                {
                    rotate(a, v, p, q);
                }
            }
        }
        if (!converged && offDiagonalNorm(a) > CONVERGENCE * norm)
        {
            throw new ArithmeticException("Jacobi eigensolver did not converge in " + MAX_SWEEPS + " sweeps");
        }
        return sorted(a, v);
    }

    /**
     * Zeroes the entry at {@code (p, q)} with a plane rotation, applied to the matrix and the accumulated vectors.
     */
    private static void rotate(Matrix a, Matrix v, int p, int q)
    {
        double apq = a.get(p, q);
        if (apq == 0.0)
        {
            return;
        }
        double theta = (a.get(q, q) - a.get(p, p)) / (2.0 * apq);
        double t;
        if (Math.abs(theta) > 1.0e150)
        {
            // theta squared would overflow, and the root is then 1 / (2 theta) to full precision
            t = 0.5 / theta;
        }
        else
        {
            t = Math.signum(theta == 0.0 ? 1.0 : theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1.0));
        }
        double c = 1.0 / Math.sqrt(t * t + 1.0);
        double s = t * c;
        int n = a.rows();
        for (int k = 0; k < n; k++)
        {
            double akp = a.get(k, p);
            double akq = a.get(k, q);
            a.set(k, p, c * akp - s * akq);
            a.set(k, q, s * akp + c * akq);
        }
        for (int k = 0; k < n; k++)
        {
            double apk = a.get(p, k);
            double aqk = a.get(q, k);
            a.set(p, k, c * apk - s * aqk);
            a.set(q, k, s * apk + c * aqk);
        }
        for (int k = 0; k < n; k++)
        {
            double vkp = v.get(k, p);
            double vkq = v.get(k, q);
            v.set(k, p, c * vkp - s * vkq);
            v.set(k, q, s * vkp + c * vkq);
        }
    }

    private static double offDiagonalNorm(Matrix a)
    {
        double sum = 0.0;
        for (int i = 0; i < a.rows(); i++)
        {
            for (int j = 0; j < a.columns(); j++)
            {
                if (i != j)
                {
                    sum += a.get(i, j) * a.get(i, j);
                }
            }
        }
        return Math.sqrt(sum);
    }

    private static SymmetricEigen sorted(Matrix a, Matrix v)
    {
        int n = a.rows();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++)
        {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(x -> a.get(x, x)));
        double[] values = new double[n];
        Matrix vectors = new Matrix(n, n);
        for (int k = 0; k < n; k++)
        {
            values[k] = a.get(order[k], order[k]);
            for (int i = 0; i < n; i++)
            {
                vectors.set(i, k, v.get(i, order[k]));
            }
        }
        return new SymmetricEigen(values, vectors);
    }
}
