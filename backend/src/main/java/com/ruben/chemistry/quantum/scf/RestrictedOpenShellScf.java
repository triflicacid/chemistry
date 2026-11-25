package com.ruben.chemistry.quantum.scf;

import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.integrals.EcpCentre;
import com.ruben.chemistry.quantum.integrals.EcpIntegrals;
import com.ruben.chemistry.quantum.integrals.ElectronRepulsionIntegrals;
import com.ruben.chemistry.quantum.integrals.ElectronRepulsionTensor;
import com.ruben.chemistry.quantum.integrals.OneElectronIntegrals;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.quantum.linalg.Orthogonaliser;

import java.util.List;

/**
 * Restricted open-shell Hartree-Fock: one set of spatial orbitals for both spins, the lowest {@code N_beta} doubly
 * occupied, the next {@code N_alpha - N_beta} singly occupied by spin-up electrons, and the rest empty.
 *
 * <p>Unlike {@link UnrestrictedScf} the wavefunction is an eigenfunction of the total spin, but it cannot
 * polarise the paired electrons, and its energy is never below the unrestricted one. There is no single Fock
 * operator for the orbitals, so the iteration diagonalises Roothaan's effective Fock operator, assembled from the
 * spin-up and spin-down Fock matrices in blocks that make its off-diagonal blocks vanish exactly when the energy
 * is stationary.
 */
public final class RestrictedOpenShellScf
{
    private static final int CLOSED = 0;
    private static final int OPEN = 1;
    private static final int VIRTUAL = 2;

    private RestrictedOpenShellScf()
    {
    }

    /**
     * Runs the calculation with {@link ScfSettings#defaults()} and no core potentials.
     *
     * @param basis the basis functions, in bohr
     * @param nuclei the point charges
     * @param alphaElectrons the number of spin-up electrons, at least {@code betaElectrons}
     * @param betaElectrons the number of spin-down electrons
     * @return the energy, orbitals and densities
     * @throws IllegalArgumentException if a count is negative, there are no electrons, there are more spin-down than spin-up electrons, or the spin-up electrons outnumber the independent functions
     */
    public static RestrictedOpenShellScfResult run(List<ContractedGaussian> basis, List<PointCharge> nuclei, int alphaElectrons, int betaElectrons)
    {
        return run(basis, nuclei, List.of(), alphaElectrons, betaElectrons, ScfSettings.defaults());
    }

    /**
     * @param basis the basis functions, in bohr
     * @param nuclei the point charges, which carry the effective nuclear charge when core electrons are replaced
     * @param cores the effective core potentials added to the one-electron Hamiltonian, empty for an all-electron calculation
     * @param alphaElectrons the number of spin-up electrons treated explicitly, at least {@code betaElectrons}
     * @param betaElectrons the number of spin-down electrons treated explicitly
     * @param settings the convergence controls
     * @return the energy, orbitals and densities; {@code converged} is false if the iteration limit was reached
     * @throws IllegalArgumentException if a count is negative, there are no electrons, there are more spin-down than spin-up electrons, or the spin-up electrons outnumber the independent functions
     */
    public static RestrictedOpenShellScfResult run(List<ContractedGaussian> basis, List<PointCharge> nuclei, List<EcpCentre> cores, int alphaElectrons, int betaElectrons, ScfSettings settings)
    {
        return run(basis, nuclei, cores, alphaElectrons, betaElectrons, settings, null);
    }

    /**
     * @param basis the basis functions, in bohr
     * @param nuclei the point charges, which carry the effective nuclear charge when core electrons are replaced
     * @param cores the effective core potentials added to the one-electron Hamiltonian, empty for an all-electron calculation
     * @param alphaElectrons the number of spin-up electrons treated explicitly, at least {@code betaElectrons}
     * @param betaElectrons the number of spin-down electrons treated explicitly
     * @param settings the convergence controls
     * @param guess the starting orbitals as columns over the basis functions, orthonormal under the overlap matrix, the closed orbitals first, then the open ones; {@code null} for the orbitals of the core Hamiltonian
     * @return the energy, orbitals and densities; {@code converged} is false if the iteration limit was reached
     * @throws IllegalArgumentException if a count is negative, there are no electrons, there are more spin-down than spin-up electrons, the spin-up electrons outnumber the independent functions, or the guess does not match the basis
     */
    public static RestrictedOpenShellScfResult run(List<ContractedGaussian> basis, List<PointCharge> nuclei, List<EcpCentre> cores, int alphaElectrons, int betaElectrons, ScfSettings settings, Matrix guess)
    {
        if (alphaElectrons < 0 || betaElectrons < 0 || alphaElectrons + betaElectrons == 0)
        {
            throw new IllegalArgumentException("need a positive number of electrons, got " + alphaElectrons + " spin-up and " + betaElectrons + " spin-down");
        }
        if (betaElectrons > alphaElectrons)
        {
            throw new IllegalArgumentException("give the spin with more electrons first: " + alphaElectrons + " spin-up and " + betaElectrons + " spin-down");
        }
        int closed = betaElectrons;
        int open = alphaElectrons - betaElectrons;

        settings.listener().stage(ScfListener.INTEGRALS);
        Matrix overlap = OneElectronIntegrals.overlapMatrix(basis);
        Matrix core = OneElectronIntegrals.coreHamiltonian(basis, nuclei);
        if (!cores.isEmpty())
        {
            core = core.plus(EcpIntegrals.matrix(basis, cores));
        }
        ElectronRepulsionTensor repulsion = ElectronRepulsionIntegrals.tensor(basis);
        double nuclear = ClosedShellScf.nuclearRepulsion(nuclei);

        // the transformation drops near-dependent directions, so it may have fewer columns than there are functions
        Matrix transform = Orthogonaliser.canonical(overlap, settings.linearDependencyThreshold());
        if (alphaElectrons > transform.columns())
        {
            throw new IllegalArgumentException(alphaElectrons + " spin-up electrons do not fit in " + transform.columns() + " independent functions");
        }
        Matrix transformTransposeOverlap = transform.transpose().times(overlap);

        // the guess needs no density, so it does not depend on electron repulsion
        Matrix coefficients = guess == null ? UnrestrictedScf.diagonalise(InitialGuess.fock(core, overlap), transform).coefficients() : guess;
        if (coefficients.rows() != overlap.rows() || coefficients.columns() < alphaElectrons)
        {
            throw new IllegalArgumentException("the guess is " + coefficients.rows() + " by " + coefficients.columns() + ", the basis has " + overlap.rows() + " functions and " + alphaElectrons + " spin-up electrons");
        }

        settings.listener().stage(ScfListener.ITERATING);
        Diis diis = new Diis(Math.max(settings.diisSize(), 1));
        double previousEnergy = 0.0;
        for (int iteration = 1; iteration <= settings.maxIterations(); iteration++)
        {
            Matrix betaDensity = UnrestrictedScf.density(coefficients, closed);
            Matrix alphaDensity = UnrestrictedScf.density(coefficients, closed + open);
            Matrix total = alphaDensity.plus(betaDensity);
            int n = total.rows();
            Matrix coulomb = new Matrix(n, n);
            Matrix exchangeAlpha = new Matrix(n, n);
            Matrix exchangeBeta = new Matrix(n, n);
            UnrestrictedScf.twoElectron(repulsion, total, alphaDensity, betaDensity, coulomb, exchangeAlpha, exchangeBeta);
            Matrix fockAlpha = core.plus(coulomb).minus(exchangeAlpha);
            Matrix fockBeta = core.plus(coulomb).minus(exchangeBeta);

            // the same energy as the unrestricted theory, evaluated for densities that share their orbitals
            double electronic = 0.5 * (total.dot(core) + alphaDensity.dot(fockAlpha) + betaDensity.dot(fockBeta));
            double totalEnergy = electronic + nuclear;

            // in the molecular-orbital basis the Fock matrices of the two spins are combined block by block
            Matrix transposed = coefficients.transpose();
            Matrix alphaMo = transposed.times(fockAlpha).times(coefficients);
            Matrix betaMo = transposed.times(fockBeta).times(coefficients);
            int m = coefficients.columns();
            Matrix effective = new Matrix(m, m);
            Matrix gradient = new Matrix(m, m);
            for (int p = 0; p < m; p++)
            {
                for (int q = 0; q < m; q++)
                {
                    double mean = 0.5 * (alphaMo.get(p, q) + betaMo.get(p, q));
                    int first = block(p, closed, open);
                    int second = block(q, closed, open);
                    if (first == second)
                    {
                        // rotations within a group leave the energy unchanged, so these blocks are free to choose
                        effective.set(p, q, mean);
                    }
                    else if (first + second == CLOSED + OPEN)
                    {
                        // a closed to open rotation changes only the spin-down determinant
                        effective.set(p, q, betaMo.get(p, q));
                        gradient.set(p, q, betaMo.get(p, q));
                    }
                    else if (first + second == OPEN + VIRTUAL)
                    {
                        // an open to virtual rotation changes only the spin-up determinant
                        effective.set(p, q, alphaMo.get(p, q));
                        gradient.set(p, q, alphaMo.get(p, q));
                    }
                    else
                    {
                        // a closed to virtual rotation changes both, equally
                        effective.set(p, q, mean);
                        gradient.set(p, q, mean);
                    }
                }
            }

            // back in the basis functions, where Fock matrices from different iterations can be combined
            Matrix overlapCoefficients = overlap.times(coefficients);
            Matrix fock = overlapCoefficients.times(effective).times(overlapCoefficients.transpose());
            // the gradient in the orthonormal basis, which does not depend on how the orbitals are ordered or rotated within a group
            Matrix orthonormal = transformTransposeOverlap.times(coefficients);
            Matrix error = orthonormal.times(gradient).times(orthonormal.transpose());
            double rms = error.frobeniusNorm() / m;

            settings.listener().iteration(iteration, totalEnergy, rms);
            boolean converged = iteration > 1 && Math.abs(totalEnergy - previousEnergy) < settings.energyTolerance() && rms < settings.errorTolerance();
            if (converged || iteration == settings.maxIterations())
            {
                // orbitals come from the effective Fock matrix itself rather than an extrapolation
                UnrestrictedScf.Orbitals last = UnrestrictedScf.diagonalise(fock, transform);
                return new RestrictedOpenShellScfResult(converged, iteration, totalEnergy, electronic, nuclear, alphaElectrons, betaElectrons,
                        last.energies(), last.coefficients(),
                        UnrestrictedScf.density(last.coefficients(), closed + open), UnrestrictedScf.density(last.coefficients(), closed), overlap);
            }
            previousEnergy = totalEnergy;

            Matrix next = fock;
            if (settings.diisSize() > 0)
            {
                diis.add(fock, error);
                next = diis.extrapolate();
            }
            coefficients = UnrestrictedScf.diagonalise(next, transform).coefficients();
        }
        throw new IllegalStateException("the last iteration always returns");
    }

    private static int block(int orbital, int closed, int open)
    {
        if (orbital < closed)
        {
            return CLOSED;
        }
        return orbital < closed + open ? OPEN : VIRTUAL;
    }
}
