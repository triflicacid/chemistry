package com.ruben.chemistry.quantum.scf;

import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.integrals.ElectronRepulsionIntegrals;
import com.ruben.chemistry.quantum.integrals.ElectronRepulsionTensor;
import com.ruben.chemistry.quantum.integrals.OneElectronIntegrals;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.quantum.linalg.Orthogonaliser;
import com.ruben.chemistry.quantum.linalg.SymmetricEigen;

import java.util.List;

/**
 * Restricted Hartree-Fock for a closed-shell molecule: every occupied orbital holds two electrons of opposite spin.
 */
public final class ClosedShellScf
{
    private ClosedShellScf()
    {
    }

    /**
     * @param nuclei the point charges
     * @return the Coulomb repulsion between every pair of them, in hartree
     */
    public static double nuclearRepulsion(List<PointCharge> nuclei)
    {
        double energy = 0.0;
        for (int i = 0; i < nuclei.size(); i++)
        {
            for (int j = 0; j < i; j++)
            {
                PointCharge a = nuclei.get(i);
                PointCharge b = nuclei.get(j);
                double dx = a.position().x() - b.position().x();
                double dy = a.position().y() - b.position().y();
                double dz = a.position().z() - b.position().z();
                energy += a.charge() * b.charge() / Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }
        return energy;
    }

    /**
     * Runs the calculation with {@link ScfSettings#defaults()}.
     *
     * @param basis the basis functions, in bohr
     * @param nuclei the point charges, which carry the effective nuclear charge when core electrons are replaced
     * @param electrons the number of electrons, which must be even and positive
     * @return the energy, orbitals and density
     * @throws IllegalArgumentException if the electron count is odd or not positive, or too large for the basis
     */
    public static ScfResult run(List<ContractedGaussian> basis, List<PointCharge> nuclei, int electrons)
    {
        return run(basis, nuclei, electrons, ScfSettings.defaults());
    }

    /**
     * @param basis the basis functions, in bohr
     * @param nuclei the point charges, which carry the effective nuclear charge when core electrons are replaced
     * @param electrons the number of electrons, which must be even and positive
     * @param settings the convergence controls
     * @return the energy, orbitals and density; {@code converged} is false if the iteration limit was reached
     * @throws IllegalArgumentException if the electron count is odd or not positive, or too large for the basis
     */
    public static ScfResult run(List<ContractedGaussian> basis, List<PointCharge> nuclei, int electrons, ScfSettings settings)
    {
        if (electrons <= 0 || electrons % 2 != 0)
        {
            throw new IllegalArgumentException("closed-shell needs a positive even electron count, was " + electrons);
        }
        int occupied = electrons / 2;

        Matrix overlap = OneElectronIntegrals.overlapMatrix(basis);
        Matrix core = OneElectronIntegrals.coreHamiltonian(basis, nuclei);
        ElectronRepulsionTensor repulsion = ElectronRepulsionIntegrals.tensor(basis);
        double nuclear = nuclearRepulsion(nuclei);

        // the transformation drops near-dependent directions, so it may have fewer columns than there are functions
        Matrix transform = Orthogonaliser.canonical(overlap, settings.linearDependencyThreshold());
        if (occupied > transform.columns())
        {
            throw new IllegalArgumentException(electrons + " electrons do not fit in " + transform.columns() + " independent functions");
        }

        Diis diis = new Diis(Math.max(settings.diisSize(), 1));
        // the first density comes from the core Hamiltonian alone, ignoring electron repulsion
        Orbitals orbitals = diagonalise(core, transform, occupied);
        double previousEnergy = 0.0;
        for (int iteration = 1; iteration <= settings.maxIterations(); iteration++)
        {
            Matrix fock = core.plus(twoElectron(repulsion, orbitals.density()));
            double electronic = 0.5 * orbitals.density().dot(core.plus(fock));
            double total = electronic + nuclear;

            // FPS - SPF vanishes exactly when the density is self-consistent with the Fock matrix
            Matrix commutator = fock.times(orbitals.density()).times(overlap).minus(overlap.times(orbitals.density()).times(fock));
            Matrix error = transform.transpose().times(commutator).times(transform);
            double rms = error.frobeniusNorm() / error.rows();

            boolean converged = iteration > 1 && Math.abs(total - previousEnergy) < settings.energyTolerance() && rms < settings.errorTolerance();
            if (converged || iteration == settings.maxIterations())
            {
                // orbitals come from the Fock matrix itself rather than an extrapolation
                Orbitals last = diagonalise(fock, transform, occupied);
                return new ScfResult(converged, iteration, total, electronic, nuclear, last.energies(), last.coefficients(), last.density(), overlap);
            }
            previousEnergy = total;

            Matrix next = fock;
            if (settings.diisSize() > 0)
            {
                diis.add(fock, error);
                next = diis.extrapolate();
            }
            orbitals = diagonalise(next, transform, occupied);
        }
        throw new IllegalStateException("the last iteration always returns");
    }

    private record Orbitals(double[] energies, Matrix coefficients, Matrix density)
    {
    }

    /**
     * @return the orbitals of a Fock matrix, and the density from filling the lowest {@code occupied} of them twice
     */
    private static Orbitals diagonalise(Matrix fock, Matrix transform, int occupied)
    {
        // in the orthonormal basis the generalised problem F C = S C e is an ordinary symmetric eigenproblem
        Matrix orthogonal = transform.transpose().times(fock).times(transform);
        // rounding noise leaves the product very slightly asymmetric, which the eigensolver refuses
        SymmetricEigen eigen = SymmetricEigen.of(orthogonal.plus(orthogonal.transpose()).times(0.5));
        Matrix coefficients = transform.times(eigen.vectors());

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
                density.set(mu, nu, 2.0 * sum);
            }
        }
        return new Orbitals(eigen.values(), coefficients, density);
    }

    /**
     * @return the electron-electron part of the Fock matrix, {@code G = sum P_ls [(mn|sl) - 1/2 (ml|sn)]}
     */
    private static Matrix twoElectron(ElectronRepulsionTensor repulsion, Matrix density)
    {
        int n = density.rows();
        Matrix g = new Matrix(n, n);
        for (int mu = 0; mu < n; mu++)
        {
            for (int nu = 0; nu < n; nu++)
            {
                double sum = 0.0;
                for (int lambda = 0; lambda < n; lambda++)
                {
                    for (int sigma = 0; sigma < n; sigma++)
                    {
                        // repulsion by the whole density, less half of it for the exchange of same-spin electrons
                        sum += density.get(lambda, sigma) * (repulsion.get(mu, nu, sigma, lambda) - 0.5 * repulsion.get(mu, lambda, sigma, nu));
                    }
                }
                g.set(mu, nu, sum);
            }
        }
        return g;
    }
}
