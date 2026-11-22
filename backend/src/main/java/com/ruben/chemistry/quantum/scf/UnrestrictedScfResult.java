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
        Matrix overlap)
{
    /**
     * @return the density of all electrons, {@code P_alpha + P_beta}
     */
    public Matrix totalDensity()
    {
        return alphaDensity.plus(betaDensity);
    }

    /**
     * @return the density of the unpaired electrons, {@code P_alpha - P_beta}
     */
    public Matrix spinDensity()
    {
        return alphaDensity.minus(betaDensity);
    }

    /**
     * @return the expectation value of the spin component, {@code (N_alpha - N_beta) / 2}
     */
    public double spinProjection()
    {
        return 0.5 * (alphaElectrons - betaElectrons);
    }

    /**
     * @return the spin eigenvalue the state would have if it were a pure spin state, {@code S (S + 1)} with {@code S} the spin projection
     */
    public double idealSpinSquared()
    {
        double s = spinProjection();
        return s * (s + 1.0);
    }

    /**
     * @return the expectation value of the squared total spin
     */
    public double spinSquared()
    {
        double overlapSum = alphaDensity.times(overlap).times(betaDensity).times(overlap).trace();
        return idealSpinSquared() + betaElectrons - overlapSum;
    }

    /**
     * @return how far the expectation value of the squared total spin lies above the pure-state value, zero for no contamination
     */
    public double spinContamination()
    {
        return spinSquared() - idealSpinSquared();
    }
}
