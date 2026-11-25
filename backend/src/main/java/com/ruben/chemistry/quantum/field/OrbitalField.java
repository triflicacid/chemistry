package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.quantum.Units;
import com.ruben.chemistry.quantum.basis.BasisSet;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;

import java.util.stream.IntStream;

/**
 * Evaluates a molecular orbital on a grid.
 */
public final class OrbitalField
{
    private OrbitalField()
    {
    }

    /**
     * @param basis the basis functions the orbital is a combination of, placed on the molecule, in bohr
     * @param coefficients the orbital's coefficient on each basis function, in the order of {@code basis}
     * @param grid the points to evaluate it at, in ångström
     * @param check called once for each slice of the grid, and able to stop the evaluation by throwing
     * @return the orbital's value at each point, in bohr to the power minus three halves, running along x fastest, then y, then z
     * @throws IllegalArgumentException if there is not a coefficient for each basis function
     */
    public static float[] evaluate(BasisSet basis, double[] coefficients, FieldGrid grid, Runnable check)
    {
        if (coefficients.length != basis.size())
        {
            throw new IllegalArgumentException("expected a coefficient for each of " + basis.size() + " basis functions, but got " + coefficients.length);
        }

        float[] values = new float[grid.size()];
        // a slice at a time, so that the slices can be worked on together and the work can be stopped between them
        IntStream.range(0, grid.nz()).parallel().forEach(k ->
        {
            check.run();
            double z = Units.toBohr(grid.origin().z() + k * grid.spacing());
            for (int j = 0; j < grid.ny(); j++)
            {
                double y = Units.toBohr(grid.origin().y() + j * grid.spacing());
                for (int i = 0; i < grid.nx(); i++)
                {
                    double x = Units.toBohr(grid.origin().x() + i * grid.spacing());
                    double sum = 0.0;
                    for (int f = 0; f < coefficients.length; f++)
                    {
                        ContractedGaussian function = basis.functions().get(f);
                        sum += coefficients[f] * function.valueAt(x, y, z);
                    }
                    values[grid.indexOf(i, j, k)] = (float) sum;
                }
            }
        });
        return values;
    }
}
