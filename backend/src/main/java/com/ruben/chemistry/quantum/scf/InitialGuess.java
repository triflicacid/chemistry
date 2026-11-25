package com.ruben.chemistry.quantum.scf;

import com.ruben.chemistry.quantum.linalg.Matrix;

/**
 * Where a self-consistent-field calculation starts when it has not been given orbitals to start from.
 */
final class InitialGuess
{
    /** The Wolfsberg-Helmholz constant. */
    private static final double SCALE = 1.75;

    private InitialGuess()
    {
    }

    /**
     * A guess at the Fock matrix that does not need a density, from the generalised Wolfsberg-Helmholz approximation: each
     * off-diagonal element is the overlap between two functions times the mean of their core energies, scaled up. It puts
     * the electrons where bonding is rather than where the core Hamiltonian would.
     *
     * @param core the one-electron Hamiltonian
     * @param overlap the overlap of the basis functions
     * @return the guess, which is symmetric
     */
    static Matrix fock(Matrix core, Matrix overlap)
    {
        int n = core.rows();
        Matrix guess = new Matrix(n, n);
        for (int mu = 0; mu < n; mu++)
        {
            for (int nu = 0; nu < n; nu++)
            {
                guess.set(mu, nu, mu == nu
                        ? core.get(mu, mu)
                        : 0.5 * SCALE * overlap.get(mu, nu) * (core.get(mu, mu) + core.get(nu, nu)));
            }
        }
        return guess;
    }
}
