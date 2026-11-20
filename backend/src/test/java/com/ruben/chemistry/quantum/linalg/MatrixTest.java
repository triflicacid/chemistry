package com.ruben.chemistry.quantum.linalg;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MatrixTest
{
    @Test
    void should_multiply_rectangular_matrices()
    {
        Matrix a = Matrix.of(new double[][] {{1, 2, 3}, {4, 5, 6}});
        Matrix b = Matrix.of(new double[][] {{7, 8}, {9, 10}, {11, 12}});

        assertThat(a.times(b)).isEqualTo(Matrix.of(new double[][] {{58, 64}, {139, 154}}));
    }

    @Test
    void should_leave_a_matrix_unchanged_when_multiplied_by_the_identity()
    {
        Matrix a = Matrix.of(new double[][] {{1, 2}, {3, 4}});

        assertThat(a.times(Matrix.identity(2))).isEqualTo(a);
    }

    @Test
    void should_transpose_a_rectangular_matrix()
    {
        Matrix a = Matrix.of(new double[][] {{1, 2, 3}, {4, 5, 6}});

        assertThat(a.transpose()).isEqualTo(Matrix.of(new double[][] {{1, 4}, {2, 5}, {3, 6}}));
    }

    @Test
    void should_add_subtract_and_scale_entrywise()
    {
        Matrix a = Matrix.of(new double[][] {{1, 2}, {3, 4}});

        assertThat(a.plus(a)).isEqualTo(a.times(2.0));
        assertThat(a.minus(a)).isEqualTo(new Matrix(2, 2));
    }

    @Test
    void should_take_the_trace_and_the_entrywise_dot()
    {
        Matrix a = Matrix.of(new double[][] {{1, 2}, {3, 4}});

        assertThat(a.trace()).isEqualTo(5.0);
        assertThat(a.dot(a)).isEqualTo(30.0);
        assertThat(a.frobeniusNorm()).isCloseTo(Math.sqrt(30.0), within(1e-14));
    }

    @Test
    void should_detect_symmetry()
    {
        assertThat(Matrix.of(new double[][] {{1, 2}, {2, 3}}).isSymmetric(1e-12)).isTrue();
        assertThat(Matrix.of(new double[][] {{1, 2}, {2.1, 3}}).isSymmetric(1e-12)).isFalse();
        assertThat(new Matrix(2, 3).isSymmetric(1e-12)).isFalse();
    }

    @Test
    void should_copy_independently()
    {
        Matrix a = Matrix.identity(2);
        Matrix b = a.copy();
        b.set(0, 0, 9.0);

        assertThat(a.get(0, 0)).isEqualTo(1.0);
    }

    @Test
    void should_reject_mismatched_shapes()
    {
        assertThatThrownBy(() -> new Matrix(2, 3).times(new Matrix(2, 3))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Matrix(2, 3).plus(new Matrix(3, 2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Matrix(2, 3).trace()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Matrix(2, 2).get(2, 0)).isInstanceOf(IndexOutOfBoundsException.class);
    }
}
