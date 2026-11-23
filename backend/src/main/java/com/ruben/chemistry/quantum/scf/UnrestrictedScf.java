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
import com.ruben.chemistry.quantum.linalg.SymmetricEigen;

import java.util.List;

/**
 * Unrestricted Hartree-Fock, the Pople-Nesbet equations: spin-up and spin-down electrons occupy different
 * orbitals, so molecules with unpaired electrons can be treated.
 *
 * <p>For equal numbers of spin-up and spin-down electrons and the default starting guess the two sets of orbitals
 * stay equal and the result is that of {@link ClosedShellScf}.
 */
public final class UnrestrictedScf
{
    private UnrestrictedScf()
    {
    }

    /**
     * Runs the calculation with {@link ScfSettings#defaults()}
     *
     * @param basis the basis functions, in bohr
     * @param nuclei the point charges
     * @param alphaElectrons the number of spin-up electrons, at least {@code betaElectrons}
     * @param betaElectrons the number of spin-down electrons
     * @return the energy, orbitals and densities
     * @throws IllegalArgumentException if a count is negative, there are no electrons, there are more spin-down than spin-up electrons, or a spin has more electrons than independent functions
     */
    public static UnrestrictedScfResult run(List<ContractedGaussian> basis, List<PointCharge> nuclei, int alphaElectrons, int betaElectrons)
    {
        return run(basis, nuclei, List.of(), alphaElectrons, betaElectrons, ScfSettings.defaults(), 0.0);
    }

    /**
     * @param basis the basis functions, in bohr
     * @param nuclei the point charges, which carry the effective nuclear charge when core electrons are replaced
     * @param cores the effective core potentials added to the one-electron Hamiltonian, empty for an all-electron calculation
     * @param alphaElectrons the number of spin-up electrons treated explicitly, at least {@code betaElectrons}
     * @param betaElectrons the number of spin-down electrons treated explicitly
     * @param settings the convergence controls
     * @return the energy, orbitals and densities; {@code converged} is false if the iteration limit was reached
     * @throws IllegalArgumentException if a count is negative, there are no electrons, there are more spin-down than spin-up electrons, or a spin has more electrons than independent functions
     */
    public static UnrestrictedScfResult run(List<ContractedGaussian> basis, List<PointCharge> nuclei, List<EcpCentre> cores, int alphaElectrons, int betaElectrons, ScfSettings settings)
    {
        return run(basis, nuclei, cores, alphaElectrons, betaElectrons, settings, 0.0);
    }

    /**
     * @param basis the basis functions, in bohr
     * @param nuclei the point charges, which carry the effective nuclear charge when core electrons are replaced
     * @param cores the effective core potentials added to the one-electron Hamiltonian, empty for an all-electron calculation
     * @param alphaElectrons the number of spin-up electrons treated explicitly, at least {@code betaElectrons}
     * @param betaElectrons the number of spin-down electrons treated explicitly
     * @param settings the convergence controls
     * @param mixing the angle in radians by which the starting spin-down HOMO is rotated into the LUMO, 0 for none
     * @return the energy, orbitals and densities; {@code converged} is false if the iteration limit was reached
     * @throws IllegalArgumentException if a count is negative, there are no electrons, there are more spin-down than spin-up electrons, a spin has more electrons than independent functions, or mixing is requested with no spin-down electron or no empty orbital to mix with
     */
    public static UnrestrictedScfResult run(List<ContractedGaussian> basis, List<PointCharge> nuclei, List<EcpCentre> cores, int alphaElectrons, int betaElectrons, ScfSettings settings, double mixing)
    {
        return run(basis, nuclei, cores, alphaElectrons, betaElectrons, settings, mixing, null);
    }

    /**
     * Starts from given orbitals instead of those of the core Hamiltonian. The core guess ignores electron
     * repulsion and can put the electrons in the wrong orbitals, so that the iteration settles on a solution that
     * is not the lowest; orbitals from a restricted open-shell calculation are a better start.
     *
     * @param basis the basis functions, in bohr
     * @param nuclei the point charges, which carry the effective nuclear charge when core electrons are replaced
     * @param cores the effective core potentials added to the one-electron Hamiltonian, empty for an all-electron calculation
     * @param alphaElectrons the number of spin-up electrons treated explicitly, at least {@code betaElectrons}
     * @param betaElectrons the number of spin-down electrons treated explicitly
     * @param settings the convergence controls
     * @param mixing the angle in radians by which the starting spin-down HOMO is rotated into the LUMO, 0 for none
     * @param guess the starting orbitals as columns over the basis functions, orthonormal under the overlap matrix and ordered from the most to the least occupied, used for both spins; {@code null} for the orbitals of the core Hamiltonian
     * @return the energy, orbitals and densities; {@code converged} is false if the iteration limit was reached
     * @throws IllegalArgumentException if a count is negative, there are no electrons, there are more spin-down than spin-up electrons, a spin has more electrons than independent functions, mixing is requested with no spin-down electron or no empty orbital to mix with, or the guess does not match the basis
     */
    public static UnrestrictedScfResult run(List<ContractedGaussian> basis, List<PointCharge> nuclei, List<EcpCentre> cores, int alphaElectrons, int betaElectrons, ScfSettings settings, double mixing, Matrix guess)
    {
        if (alphaElectrons < 0 || betaElectrons < 0 || alphaElectrons + betaElectrons == 0)
        {
            throw new IllegalArgumentException("need a positive number of electrons, got " + alphaElectrons + " spin-up and " + betaElectrons + " spin-down");
        }
        if (betaElectrons > alphaElectrons)
        {
            throw new IllegalArgumentException("give the spin with more electrons first: " + alphaElectrons + " spin-up and " + betaElectrons + " spin-down");
        }

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

        // the core guess ignores electron repulsion, so both spins fill the same lowest orbitals of the core Hamiltonian
        Matrix start = guess == null ? diagonalise(core, transform).coefficients() : checkedGuess(guess, overlap.rows(), alphaElectrons);
        Orbitals alpha;
        Orbitals beta;
        Matrix alphaDensity = density(start, alphaElectrons);
        Matrix betaCoefficients = start;
        if (mixing != 0.0)
        {
            betaCoefficients = rotateFrontier(betaCoefficients, betaElectrons, mixing);
        }
        Matrix betaDensity = density(betaCoefficients, betaElectrons);

        Diis diis = new Diis(Math.max(settings.diisSize(), 1));
        double previousEnergy = 0.0;
        for (int iteration = 1; iteration <= settings.maxIterations(); iteration++)
        {
            Matrix total = alphaDensity.plus(betaDensity);
            Matrix coulomb = new Matrix(total.rows(), total.rows());
            Matrix exchangeAlpha = new Matrix(total.rows(), total.rows());
            Matrix exchangeBeta = new Matrix(total.rows(), total.rows());
            twoElectron(repulsion, total, alphaDensity, betaDensity, coulomb, exchangeAlpha, exchangeBeta);
            Matrix fockAlpha = core.plus(coulomb).minus(exchangeAlpha);
            Matrix fockBeta = core.plus(coulomb).minus(exchangeBeta);

            // the electrons' energy counted once: half of the core term plus half of each spin's Fock energy
            double electronic = 0.5 * (total.dot(core) + alphaDensity.dot(fockAlpha) + betaDensity.dot(fockBeta));
            double totalEnergy = electronic + nuclear;

            // FPS - SPF vanishes exactly when a spin's density is self-consistent with its Fock matrix
            Matrix errorAlpha = transform.transpose().times(commutator(fockAlpha, alphaDensity, overlap)).times(transform);
            Matrix errorBeta = transform.transpose().times(commutator(fockBeta, betaDensity, overlap)).times(transform);
            double rms = Math.max(errorAlpha.frobeniusNorm(), errorBeta.frobeniusNorm()) / errorAlpha.rows();

            boolean converged = iteration > 1 && Math.abs(totalEnergy - previousEnergy) < settings.energyTolerance() && rms < settings.errorTolerance();
            if (converged || iteration == settings.maxIterations())
            {
                // orbitals come from the Fock matrices themselves rather than an extrapolation
                Orbitals lastAlpha = diagonalise(fockAlpha, transform);
                Orbitals lastBeta = diagonalise(fockBeta, transform);
                return new UnrestrictedScfResult(converged, iteration, totalEnergy, electronic, nuclear, alphaElectrons, betaElectrons,
                        lastAlpha.energies(), lastBeta.energies(), lastAlpha.coefficients(), lastBeta.coefficients(),
                        density(lastAlpha.coefficients(), alphaElectrons), density(lastBeta.coefficients(), betaElectrons), overlap);
            }
            previousEnergy = totalEnergy;

            Matrix nextAlpha = fockAlpha;
            Matrix nextBeta = fockBeta;
            if (settings.diisSize() > 0)
            {
                // one extrapolation for both spins, so their coefficients move together
                diis.add(stack(fockAlpha, fockBeta), stack(errorAlpha, errorBeta));
                Matrix extrapolated = diis.extrapolate();
                nextAlpha = rows(extrapolated, 0, fockAlpha.rows());
                nextBeta = rows(extrapolated, fockAlpha.rows(), fockAlpha.rows());
            }
            alpha = diagonalise(nextAlpha, transform);
            beta = diagonalise(nextBeta, transform);
            alphaDensity = density(alpha.coefficients(), alphaElectrons);
            betaDensity = density(beta.coefficients(), betaElectrons);
        }
        throw new IllegalStateException("the last iteration always returns");
    }

    record Orbitals(double[] energies, Matrix coefficients)
    {
    }

    static Orbitals diagonalise(Matrix fock, Matrix transform)
    {
        // in the orthonormal basis the generalised problem F C = S C e is an ordinary symmetric eigenproblem
        Matrix orthogonal = transform.transpose().times(fock).times(transform);
        // rounding noise leaves the product very slightly asymmetric, which the eigensolver refuses
        SymmetricEigen eigen = SymmetricEigen.of(orthogonal.plus(orthogonal.transpose()).times(0.5));
        return new Orbitals(eigen.values(), transform.times(eigen.vectors()));
    }

    /**
     * @return the density of the lowest {@code occupied} orbitals, each holding one electron
     */
    static Matrix density(Matrix coefficients, int occupied)
    {
        int n = coefficients.rows();
        Matrix density = new Matrix(n, n);
        for (int mu = 0; mu < n; mu++)
        {
            for (int nu = 0; nu < n; nu++)
            {
                double sum = 0.0;
                for (int i = 0; i < occupied; i++)
                {
                    sum += coefficients.get(mu, i) * coefficients.get(nu, i);
                }
                density.set(mu, nu, sum);
            }
        }
        return density;
    }

    private static Matrix checkedGuess(Matrix guess, int functions, int alphaElectrons)
    {
        if (guess.rows() != functions || guess.columns() < alphaElectrons)
        {
            throw new IllegalArgumentException("the guess is " + guess.rows() + " by " + guess.columns() + ", the basis has " + functions + " functions and " + alphaElectrons + " spin-up electrons");
        }
        return guess;
    }

    private static Matrix rotateFrontier(Matrix coefficients, int occupied, double angle)
    {
        if (occupied < 1 || occupied >= coefficients.columns())
        {
            throw new IllegalArgumentException("mixing needs an occupied and an empty spin-down orbital, there are " + occupied + " electrons in " + coefficients.columns() + " orbitals");
        }
        Matrix rotated = coefficients.copy();
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        for (int mu = 0; mu < coefficients.rows(); mu++)
        {
            double homo = coefficients.get(mu, occupied - 1);
            double lumo = coefficients.get(mu, occupied);
            rotated.set(mu, occupied - 1, cosine * homo + sine * lumo);
            rotated.set(mu, occupied, -sine * homo + cosine * lumo);
        }
        return rotated;
    }

    private static Matrix commutator(Matrix fock, Matrix density, Matrix overlap)
    {
        return fock.times(density).times(overlap).minus(overlap.times(density).times(fock));
    }

    private static Matrix stack(Matrix top, Matrix bottom)
    {
        Matrix stacked = new Matrix(top.rows() + bottom.rows(), top.columns());
        for (int row = 0; row < top.rows(); row++)
        {
            for (int column = 0; column < top.columns(); column++)
            {
                stacked.set(row, column, top.get(row, column));
                stacked.set(top.rows() + row, column, bottom.get(row, column));
            }
        }
        return stacked;
    }

    private static Matrix rows(Matrix matrix, int first, int count)
    {
        Matrix block = new Matrix(count, matrix.columns());
        for (int row = 0; row < count; row++)
        {
            for (int column = 0; column < matrix.columns(); column++)
            {
                block.set(row, column, matrix.get(first + row, column));
            }
        }
        return block;
    }

    /**
     * Fills the Coulomb matrix {@code J = sum P_ls (mn|sl)} and the exchange matrices {@code K = sum P_ls (ml|sn)}
     */
    static void twoElectron(ElectronRepulsionTensor repulsion, Matrix total, Matrix alpha, Matrix beta, Matrix coulomb, Matrix exchangeAlpha, Matrix exchangeBeta)
    {
        int n = total.rows();
        for (int mu = 0; mu < n; mu++)
        {
            for (int nu = 0; nu < n; nu++)
            {
                double j = 0.0;
                double kAlpha = 0.0;
                double kBeta = 0.0;
                for (int lambda = 0; lambda < n; lambda++)
                {
                    for (int sigma = 0; sigma < n; sigma++)
                    {
                        j += total.get(lambda, sigma) * repulsion.get(mu, nu, sigma, lambda);
                        double exchange = repulsion.get(mu, lambda, sigma, nu);
                        kAlpha += alpha.get(lambda, sigma) * exchange;
                        kBeta += beta.get(lambda, sigma) * exchange;
                    }
                }
                coulomb.set(mu, nu, j);
                exchangeAlpha.set(mu, nu, kAlpha);
                exchangeBeta.set(mu, nu, kBeta);
            }
        }
    }
}
