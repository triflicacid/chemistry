package com.ruben.chemistry.quantum.scf;

import com.ruben.chemistry.quantum.linalg.Matrix;

/**
 * The outcome of a restricted open-shell Hartree-Fock calculation, in which both spins share one set of spatial orbitals.
 *
 * <p>The orbitals fall into three groups that follow one another in the columns of {@code coefficients}: the
 * doubly occupied (closed) orbitals, the singly occupied (open) orbitals, whose electrons all have spin up, and the
 * empty (virtual) ones.
 *
 * @param converged whether both convergence tests were met within the iteration limit
 * @param iterations the number of Fock builds made
 * @param totalEnergy electronic plus nuclear repulsion energy, in hartree
 * @param electronicEnergy the energy of the electrons in the field of the nuclei, in hartree
 * @param nuclearRepulsion the repulsion between the point charges, in hartree
 * @param alphaElectrons the number of spin-up electrons, which is the number of closed plus open orbitals
 * @param betaElectrons the number of spin-down electrons, which is the number of closed orbitals
 * @param orbitalEnergies the eigenvalues of the effective Fock operator in ascending order, in hartree
 * @param coefficients the molecular orbitals as columns over the basis functions, in the order of the energies
 * @param alphaDensity the spin-up density matrix, the sum over the closed and open orbitals of {@code c c^T}
 * @param betaDensity the spin-down density matrix, the sum over the closed orbitals of {@code c c^T}
 * @param overlap the basis-function overlap matrix {@code S}
 */
public record RestrictedOpenShellScfResult(
        boolean converged,
        int iterations,
        double totalEnergy,
        double electronicEnergy,
        double nuclearRepulsion,
        int alphaElectrons,
        int betaElectrons,
        double[] orbitalEnergies,
        Matrix coefficients,
        Matrix alphaDensity,
        Matrix betaDensity,
        Matrix overlap) implements SpinDensities
{
    /**
     * @return the number of doubly occupied orbitals
     */
    public int closedOrbitals()
    {
        return betaElectrons;
    }

    /**
     * @return the number of singly occupied orbitals, equal to the number of unpaired electrons
     */
    public int openOrbitals()
    {
        return alphaElectrons - betaElectrons;
    }
}
