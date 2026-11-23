package com.ruben.chemistry.forcefield;

import java.util.List;

/**
 * The result and convergence history of a geometry optimisation.
 *
 * @param molecules the molecules at the final geometry
 * @param energy the final energy
 * @param iterations the number of iterations completed
 * @param converged whether both tolerances were met
 * @param maximumForce the largest net molecular force at the end
 * @param energyHistory the energy before the first iteration and after each one
 */
public record OptimisationResult(List<ForceFieldMolecule> molecules, double energy, int iterations, boolean converged, double maximumForce, List<Double> energyHistory)
{
    public OptimisationResult
    {
        molecules = List.copyOf(molecules);
        energyHistory = List.copyOf(energyHistory);
    }
}
