package com.ruben.chemistry.quantum.linalg;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrthogonaliserTest
{
    private static final Matrix OVERLAP = Matrix.of(new double[][] {{1.0, 0.5, 0.2}, {0.5, 1.0, 0.4}, {0.2, 0.4, 1.0}});

    private static double deviationFromIdentity(Matrix x, Matrix overlap)
    {
        Matrix product = x.transpose().times(overlap).times(x);
        return product.minus(Matrix.identity(product.rows())).frobeniusNorm();
    }

    @Test
    void should_make_the_basis_orthonormal_with_the_symmetric_transformation()
    {
        Matrix x = Orthogonaliser.symmetric(OVERLAP, 1e-8);

        assertThat(deviationFromIdentity(x, OVERLAP)).isLessThan(1e-12);
    }

    @Test
    void should_return_a_symmetric_matrix_whose_square_is_the_inverse_overlap()
    {
        Matrix x = Orthogonaliser.symmetric(OVERLAP, 1e-8);

        assertThat(x.isSymmetric(1e-12)).isTrue();
        assertThat(x.times(x).times(OVERLAP).minus(Matrix.identity(3)).frobeniusNorm()).isLessThan(1e-12);
    }

    @Test
    void should_make_the_basis_orthonormal_with_the_canonical_transformation()
    {
        Matrix x = Orthogonaliser.canonical(OVERLAP, 1e-8);

        assertThat(x.rows()).isEqualTo(3);
        assertThat(x.columns()).isEqualTo(3);
        assertThat(deviationFromIdentity(x, OVERLAP)).isLessThan(1e-12);
    }

    @Test
    void should_drop_a_redundant_direction_in_the_canonical_transformation()
    {
        Matrix duplicated = Matrix.of(new double[][] {{1.0, 1.0}, {1.0, 1.0}});

        Matrix x = Orthogonaliser.canonical(duplicated, 1e-8);

        assertThat(x.rows()).isEqualTo(2);
        assertThat(x.columns()).isEqualTo(1);
        assertThat(deviationFromIdentity(x, duplicated)).isLessThan(1e-12);
    }

    @Test
    void should_refuse_a_linearly_dependent_basis_in_the_symmetric_transformation()
    {
        Matrix duplicated = Matrix.of(new double[][] {{1.0, 1.0}, {1.0, 1.0}});

        assertThatThrownBy(() -> Orthogonaliser.symmetric(duplicated, 1e-8)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void should_leave_an_identity_overlap_unchanged()
    {
        Matrix x = Orthogonaliser.symmetric(Matrix.identity(3), 1e-8);

        assertThat(x.minus(Matrix.identity(3)).frobeniusNorm()).isLessThan(1e-14);
    }

    @Test
    void should_reject_a_nonpositive_threshold()
    {
        assertThatThrownBy(() -> Orthogonaliser.symmetric(OVERLAP, 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Orthogonaliser.canonical(OVERLAP, -1.0)).isInstanceOf(IllegalArgumentException.class);
    }
}
