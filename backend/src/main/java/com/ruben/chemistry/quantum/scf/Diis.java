package com.ruben.chemistry.quantum.scf;

import com.ruben.chemistry.quantum.linalg.LinearSolver;
import com.ruben.chemistry.quantum.linalg.Matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulay's direct inversion in the iterative subspace: remembers recent Fock matrices with their error vectors
 * and returns the combination whose error is smallest.
 */
final class Diis
{
    private final int capacity;
    private final List<Matrix> focks = new ArrayList<>();
    private final List<Matrix> errors = new ArrayList<>();

    /**
     * @param capacity the number of entries kept; the oldest is dropped when it is exceeded
     */
    Diis(int capacity)
    {
        this.capacity = capacity;
    }

    /**
     * @param fock a Fock matrix
     * @param error its error vector, which is zero at self-consistency
     */
    void add(Matrix fock, Matrix error)
    {
        focks.add(fock);
        errors.add(error);
        if (focks.size() > capacity)
        {
            focks.removeFirst();
            errors.removeFirst();
        }
    }

    /**
     * @return the combination of stored Fock matrices, with coefficients summing to one, that minimises the norm of the combined error
     */
    Matrix extrapolate()
    {
        int count = focks.size();
        if (count < 2)
        {
            return focks.get(count - 1);
        }
        // the last row and column make the coefficients sum to one, through a Lagrange multiplier
        Matrix system = new Matrix(count + 1, count + 1);
        double[] rhs = new double[count + 1];
        for (int i = 0; i < count; i++)
        {
            for (int j = 0; j < count; j++)
            {
                system.set(i, j, errors.get(i).dot(errors.get(j)));
            }
            system.set(i, count, -1.0);
            system.set(count, i, -1.0);
        }
        rhs[count] = -1.0;

        double[] coefficients;
        try
        {
            coefficients = LinearSolver.solve(system, rhs);
        }
        catch (ArithmeticException singular)
        {
            return focks.get(count - 1);
        }
        Matrix result = new Matrix(focks.getFirst().rows(), focks.getFirst().columns());
        for (int i = 0; i < count; i++)
        {
            result = result.plus(focks.get(i).times(coefficients[i]));
        }
        return result;
    }
}
