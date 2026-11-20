package com.ruben.chemistry.quantum.scf;

/**
 * Convergence controls for a self-consistent-field calculation.
 *
 * @param maxIterations the number of Fock builds allowed before giving up
 * @param energyTolerance the largest change in total energy between iterations that counts as converged, in hartree
 * @param errorTolerance the largest root-mean-square element of the Fock-density commutator that counts as converged
 * @param linearDependencyThreshold overlap eigenvalues at or below this are removed from the basis
 * @param diisSize the number of previous Fock matrices used to extrapolate, 0 to switch extrapolation off
 */
public record ScfSettings(int maxIterations, double energyTolerance, double errorTolerance, double linearDependencyThreshold, int diisSize)
{
    public static ScfSettings defaults()
    {
        return new ScfSettings(100, 1.0e-10, 1.0e-8, 1.0e-8, 8);
    }
}
