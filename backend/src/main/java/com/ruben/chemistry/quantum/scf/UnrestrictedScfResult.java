package com.ruben.chemistry.quantum.scf;

import com.ruben.chemistry.quantum.linalg.Matrix;

/**
 * The outcome of an unrestricted Hartree-Fock calculation.
 *
 * @param converged whether both convergence tests were met within the iteration limit
 * @param iterations the number of Fock builds made
 * @param totalEnergy electronic plus nuclear repulsion energy, in hartree
 * @param electronicEnergy the energy of the electrons in the field of the nuclei, in hartree
 * @param nuclearRepulsion the repulsion between the point charges, in hartree
 * @param alphaElectrons the number of spin-up electrons
 * @param betaElectrons the number of spin-down electrons
 * @param alphaOrbitalEnergies the spin-up orbital energies in ascending order, in hartree
 * @param betaOrbitalEnergies the spin-down orbital energies in ascending order, in hartree
 * @param alphaCoefficients the spin-up molecular orbitals as columns over the basis functions, in the order of the energies
 * @param betaCoefficients the spin-down molecular orbitals as columns over the basis functions, in the order of the energies
 * @param alphaDensity the spin-up density matrix, the sum over occupied spin-up orbitals of {@code c c^T}
 * @param betaDensity the spin-down density matrix, the sum over occupied spin-down orbitals of {@code c c^T}
 * @param overlap the basis-function overlap matrix {@code S}
 */
public record UnrestrictedScfResult(
        boolean converged,
        int iterations,
        double totalEnergy,
        double electronicEnergy,
        double nuclearRepulsion,
        int alphaElectrons,
        int betaElectrons,
        double[] alphaOrbitalEnergies,
        double[] betaOrbitalEnergies,
        Matrix alphaCoefficients,
        Matrix betaCoefficients,
        Matrix alphaDensity,
        Matrix betaDensity,
        Matrix overlap) implements SpinDensities
{
}
