package com.ruben.chemistry.quantum.scf;

import com.ruben.chemistry.quantum.linalg.Matrix;

/**
 * The outcome of a closed-shell SCF calculation.
 *
 * @param converged whether both convergence tests were met within the iteration limit
 * @param iterations the number of Fock builds made
 * @param totalEnergy electronic plus nuclear repulsion energy, in hartree
 * @param electronicEnergy the energy of the electrons in the field of the nuclei, in hartree
 * @param nuclearRepulsion the repulsion between the point charges, in hartree
 * @param orbitalEnergies the orbital energies in ascending order, in hartree
 * @param coefficients the molecular orbitals as columns over the basis functions, in the same order as the energies
 * @param density the density matrix {@code P}, twice the sum over occupied orbitals of {@code c c^T}
 * @param overlap the basis-function overlap matrix {@code S}
 */
public record ScfResult(
        boolean converged,
        int iterations,
        double totalEnergy,
        double electronicEnergy,
        double nuclearRepulsion,
        double[] orbitalEnergies,
        Matrix coefficients,
        Matrix density,
        Matrix overlap)
{
    /**
     * @return the electron count
     */
    public int countElectrons()
    {
        return (int) density.times(overlap).trace();
    }
}
