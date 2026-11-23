package com.ruben.chemistry.quantum.linalg;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SymmetricEigenTest
{
    private static Matrix randomSymmetric(int n, long seed)
    {
        Random random = new Random(seed);
        Matrix m = new Matrix(n, n);
        for (int i = 0; i < n; i++)
        {
            for (int j = i; j < n; j++)
            {
                double value = random.nextDouble() * 2.0 - 1.0;
                m.set(i, j, value);
                m.set(j, i, value);
            }
        }
        return m;
    }

    @Test
    void should_find_the_eigenvalues_of_a_two_by_two_matrix()
    {
        SymmetricEigen eigen = SymmetricEigen.of(Matrix.of(new double[][] {{2, 1}, {1, 2}}));

        assertThat(eigen.values()).containsExactly(new double[] {1.0, 3.0}, within(1e-13));
    }

    @Test
    void should_return_a_diagonal_matrix_sorted_without_rotating()
    {
        SymmetricEigen eigen = SymmetricEigen.of(Matrix.of(new double[][] {{3, 0}, {0, 1}}));

        assertThat(eigen.values()).containsExactly(1.0, 3.0);
    }

    @Test
    void should_satisfy_the_eigenvalue_equation_for_a_random_matrix()
    {
        Matrix a = randomSymmetric(25, 7);

        SymmetricEigen eigen = SymmetricEigen.of(a);

        for (int k = 0; k < 25; k++)
        {
            Matrix vector = new Matrix(25, 1);
            for (int i = 0; i < 25; i++)
            {
                vector.set(i, 0, eigen.vectors().get(i, k));
            }
            Matrix residual = a.times(vector).minus(vector.times(eigen.values()[k]));
            assertThat(residual.frobeniusNorm()).isLessThan(1e-12);
        }
    }

    @Test
    void should_return_orthonormal_vectors()
    {
        SymmetricEigen eigen = SymmetricEigen.of(randomSymmetric(20, 11));

        Matrix gram = eigen.vectors().transpose().times(eigen.vectors());

        assertThat(gram.minus(Matrix.identity(20)).frobeniusNorm()).isLessThan(1e-12);
    }

    @Test
    void should_preserve_the_trace()
    {
        Matrix a = randomSymmetric(15, 3);

        double sum = 0.0;
        for (double value : SymmetricEigen.of(a).values())
        {
            sum += value;
        }

        assertThat(sum).isCloseTo(a.trace(), within(1e-12));
    }

    @Test
    void should_handle_repeated_eigenvalues()
    {
        SymmetricEigen eigen = SymmetricEigen.of(Matrix.identity(4).times(2.0));

        assertThat(eigen.values()).containsExactly(2.0, 2.0, 2.0, 2.0);
    }

    @Test
    void should_reject_an_asymmetric_matrix()
    {
        assertThatThrownBy(() -> SymmetricEigen.of(Matrix.of(new double[][] {{1, 2}, {0, 1}}))).isInstanceOf(IllegalArgumentException.class);
    }
}
