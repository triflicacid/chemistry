package com.ruben.chemistry.quantum.linalg;

/**
 * Builds transformations {@code X} that make a basis orthonormal, so that {@code X^T S X} is the identity for
 * an overlap matrix {@code S}. The generalised eigenproblem {@code F C = S C e} then reduces to an ordinary
 * symmetric one for {@code X^T F X}.
 */
public final class Orthogonaliser
{
    private Orthogonaliser()
    {
    }

    /**
     * Löwdin symmetric orthogonalisation, {@code S^(-1/2)}.
     *
     * @param overlap a symmetric positive-definite overlap matrix
     * @param threshold the smallest eigenvalue accepted, which must be positive
     * @return the symmetric matrix {@code S^(-1/2)}
     * @throws IllegalArgumentException if the threshold is not positive or the matrix is not symmetric
     * @throws ArithmeticException if an eigenvalue is at or below {@code threshold}
     */
    public static Matrix symmetric(Matrix overlap, double threshold)
    {
        requirePositive(threshold);
        SymmetricEigen eigen = SymmetricEigen.of(overlap);
        if (eigen.values().length > 0 && eigen.values()[0] <= threshold)
        {
            throw new ArithmeticException("overlap has eigenvalue " + eigen.values()[0] + ", at or below " + threshold);
        }
        return transform(eigen, 0, 0.5, true);
    }

    /**
     * Canonical orthogonalisation, which divides each eigenvector of {@code S} by the square root of its eigenvalue.
     *
     * @param overlap a symmetric positive semi-definite overlap matrix
     * @param threshold the smallest eigenvalue kept, which must be positive
     * @return an {@code n x m} matrix, {@code m <= n}, with {@code X^T S X} equal to the identity of size {@code m}
     * @throws IllegalArgumentException if the threshold is not positive or the matrix is not symmetric
     */
    public static Matrix canonical(Matrix overlap, double threshold)
    {
        requirePositive(threshold);
        SymmetricEigen eigen = SymmetricEigen.of(overlap);
        int dropped = 0;
        while (dropped < eigen.values().length && eigen.values()[dropped] <= threshold)
        {
            dropped++;
        }
        return transform(eigen, dropped, 0.5, false);
    }

    /**
     * Builds {@code V s^(-power)} from the kept eigenpairs, and optionally multiplies by {@code V^T} on the right.
     */
    private static Matrix transform(SymmetricEigen eigen, int dropped, double power, boolean symmetric)
    {
        int n = eigen.vectors().rows();
        int kept = eigen.values().length - dropped;
        Matrix scaled = new Matrix(n, kept);
        for (int k = 0; k < kept; k++)
        {
            double factor = Math.pow(eigen.values()[dropped + k], -power);
            for (int i = 0; i < n; i++)
            {
                scaled.set(i, k, eigen.vectors().get(i, dropped + k) * factor);
            }
        }
        if (!symmetric)
        {
            return scaled;
        }
        return scaled.times(eigen.vectors().transpose());
    }

    private static void requirePositive(double threshold)
    {
        if (!(threshold > 0.0))
        {
            throw new IllegalArgumentException("threshold must be positive, was " + threshold);
        }
    }
}
