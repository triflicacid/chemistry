package com.ruben.chemistry.forcefield;

import java.util.Set;

/**
 * Controls the limited-memory BFGS geometry optimiser.
 *
 * @param maxIterations the most iterations to run
 * @param memory the number of correction pairs to keep
 * @param forceTolerance the largest net molecular force at which the search has converged
 * @param energyTolerance the largest energy change at which the search has converged
 * @param maxLineSearch the most backtracking steps per iteration
 * @param initialStep the step length each line search starts from
 * @param fixedMolecules the molecules that must not move
 */
public record OptimiserOptions(int maxIterations, int memory, double forceTolerance, double energyTolerance, int maxLineSearch, double initialStep, Set<MoleculeRef> fixedMolecules)
{
    public OptimiserOptions
    {
        if (maxIterations < 1 || memory < 1 || maxLineSearch < 1)
        {
            throw new IllegalArgumentException("iteration and memory limits must be positive");
        }
        if (!(forceTolerance > 0.0) || !(energyTolerance >= 0.0) || !(initialStep > 0.0)
                || !Double.isFinite(forceTolerance) || !Double.isFinite(energyTolerance) || !Double.isFinite(initialStep))
        {
            throw new IllegalArgumentException("invalid optimiser tolerance or step");
        }
        fixedMolecules = Set.copyOf(fixedMolecules);
    }

    /**
     * @return practical default settings for molecular geometry optimisation
     */
    public static OptimiserOptions defaults()
    {
        return new OptimiserOptions(500, 8, 1.0e-5, 1.0e-10, 30, 1.0, Set.of());
    }
}
