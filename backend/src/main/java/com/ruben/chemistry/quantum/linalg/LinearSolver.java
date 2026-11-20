package com.ruben.chemistry.quantum.linalg;

/**
 * Solves small dense linear systems by Gaussian elimination with partial pivoting.
 */
public final class LinearSolver
{
    private static final double SINGULAR = 1.0e-14;

    private LinearSolver()
    {
    }

    /**
     * @param matrix the square coefficient matrix, which is not changed
     * @param rhs the right-hand side, which is not changed
     * @return {@code x} such that {@code matrix * x = rhs}
     * @throws IllegalArgumentException if the shapes do not agree
     * @throws ArithmeticException if the matrix is singular to working precision
     */
    public static double[] solve(Matrix matrix, double[] rhs)
    {
        int n = matrix.rows();
        if (matrix.columns() != n || rhs.length != n)
        {
            throw new IllegalArgumentException("need a square matrix and a matching right-hand side");
        }
        Matrix a = matrix.copy();
        double[] b = rhs.clone();
        double scale = Math.max(a.frobeniusNorm(), Double.MIN_NORMAL);

        for (int column = 0; column < n; column++)
        {
            int pivot = column;
            for (int row = column + 1; row < n; row++)
            {
                if (Math.abs(a.get(row, column)) > Math.abs(a.get(pivot, column)))
                {
                    pivot = row;
                }
            }
            if (Math.abs(a.get(pivot, column)) < SINGULAR * scale)
            {
                throw new ArithmeticException("matrix is singular");
            }
            if (pivot != column)
            {
                for (int k = 0; k < n; k++)
                {
                    double swap = a.get(column, k);
                    a.set(column, k, a.get(pivot, k));
                    a.set(pivot, k, swap);
                }
                double swap = b[column];
                b[column] = b[pivot];
                b[pivot] = swap;
            }
            for (int row = column + 1; row < n; row++)
            {
                double factor = a.get(row, column) / a.get(column, column);
                for (int k = column; k < n; k++)
                {
                    a.set(row, k, a.get(row, k) - factor * a.get(column, k));
                }
                b[row] -= factor * b[column];
            }
        }

        double[] x = new double[n];
        for (int row = n - 1; row >= 0; row--)
        {
            double sum = b[row];
            for (int k = row + 1; k < n; k++)
            {
                sum -= a.get(row, k) * x[k];
            }
            x[row] = sum / a.get(row, row);
        }
        return x;
    }
}
