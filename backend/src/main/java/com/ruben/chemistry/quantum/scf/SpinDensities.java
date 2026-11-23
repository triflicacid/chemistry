package com.ruben.chemistry.quantum.scf;

import com.ruben.chemistry.quantum.linalg.Matrix;

/**
 * What the spin-up and spin-down electrons of an open-shell determinant look like.
 */
public interface SpinDensities
{
    /**
     * @return the spin-up density matrix
     */
    Matrix alphaDensity();

    /**
     * @return the spin-down density matrix
     */
    Matrix betaDensity();

    /**
     * @return the basis-function overlap matrix {@code S}
     */
    Matrix overlap();

    /**
     * @return the number of spin-up electrons
     */
    int alphaElectrons();

    /**
     * @return the number of spin-down electrons
     */
    int betaElectrons();

    /**
     * @return the density of all electrons, {@code P_alpha + P_beta}
     */
    default Matrix totalDensity()
    {
        return alphaDensity().plus(betaDensity());
    }

    /**
     * @return the density of the unpaired electrons, {@code P_alpha - P_beta}
     */
    default Matrix spinDensity()
    {
        return alphaDensity().minus(betaDensity());
    }

    /**
     * @return the expectation value of the spin component, {@code (N_alpha - N_beta) / 2}
     */
    default double spinProjection()
    {
        return 0.5 * (alphaElectrons() - betaElectrons());
    }

    /**
     * @return the spin eigenvalue the state would have if it were a pure spin state, {@code S (S + 1)} with {@code S} the spin projection
     */
    default double idealSpinSquared()
    {
        double s = spinProjection();
        return s * (s + 1.0);
    }

    /**
     * The expectation value of the total spin is {@code S_z (S_z + 1) + N_beta - sum_ij |<i_alpha|j_beta>|^2}, the last
     * sum running over occupied orbitals and equal to {@code Tr(P_alpha S P_beta S)}.
     *
     * @return the expectation value of the squared total spin
     */
    default double spinSquared()
    {
        double overlapSum = alphaDensity().times(overlap()).times(betaDensity()).times(overlap()).trace();
        return idealSpinSquared() + betaElectrons() - overlapSum;
    }

    /**
     * @return how far the expectation value of the squared total spin lies above the pure-state value, zero for no contamination
     */
    default double spinContamination()
    {
        return spinSquared() - idealSpinSquared();
    }
}
