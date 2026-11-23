package com.ruben.chemistry.quantum.linalg;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LinearSolverTest
{
    @Test
    void should_solve_a_system_that_needs_pivoting()
    {
        Matrix a = Matrix.of(new double[][] {{0, 2}, {3, 1}});

        double[] x = LinearSolver.solve(a, new double[] {4, 5});

        assertThat(x).containsExactly(new double[] {1.0, 2.0}, within(1e-13));
    }

    @Test
    void should_not_change_its_inputs()
    {
        Matrix a = Matrix.of(new double[][] {{2, 1}, {1, 3}});
        double[] b = {3, 5};

        LinearSolver.solve(a, b);

        assertThat(a).isEqualTo(Matrix.of(new double[][] {{2, 1}, {1, 3}}));
        assertThat(b).containsExactly(3, 5);
    }

    @Test
    void should_reject_a_singular_matrix()
    {
        Matrix a = Matrix.of(new double[][] {{1, 2}, {2, 4}});

        assertThatThrownBy(() -> LinearSolver.solve(a, new double[] {1, 2})).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void should_reject_mismatched_shapes()
    {
        assertThatThrownBy(() -> LinearSolver.solve(new Matrix(2, 3), new double[] {1, 2})).isInstanceOf(IllegalArgumentException.class);
    }
}
