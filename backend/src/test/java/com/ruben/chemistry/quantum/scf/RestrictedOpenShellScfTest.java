package com.ruben.chemistry.quantum.scf;

import com.ruben.chemistry.quantum.basis.AngularMomentum;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.basis.GaussianPrimitive;
import com.ruben.chemistry.quantum.integrals.ElectronRepulsionIntegrals;
import com.ruben.chemistry.quantum.integrals.ElectronRepulsionTensor;
import com.ruben.chemistry.quantum.integrals.OneElectronIntegrals;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RestrictedOpenShellScfTest
{
    private static final AngularMomentum S = new AngularMomentum(0, 0, 0);
    private static final double[] SLATER_1S = {0.15432897, 0.53532814, 0.44463454};
    private static final double HYDROGEN_ATOM_STO_3G = -0.466581850;

    private static ContractedGaussian contracted(Vec3 centre, AngularMomentum angular, double[] exponents, double[] coefficients)
    {
        List<GaussianPrimitive> primitives = new ArrayList<>();
        for (int i = 0; i < exponents.length; i++)
        {
            primitives.add(new GaussianPrimitive(exponents[i], coefficients[i]));
        }
        return new ContractedGaussian(centre, angular, primitives);
    }

    private static ContractedGaussian hydrogen(Vec3 centre)
    {
        return contracted(centre, S, new double[] {3.42525091, 0.62391373, 0.16885540}, SLATER_1S);
    }

    private static List<PointCharge> protons(Vec3... centres)
    {
        List<PointCharge> nuclei = new ArrayList<>();
        for (Vec3 centre : centres)
        {
            nuclei.add(new PointCharge(centre, 1.0));
        }
        return nuclei;
    }

    private static final Vec3 OXYGEN = new Vec3(0.0, -0.143225816552, 0.0);
    private static final Vec3 HYDROGEN_ONE = new Vec3(1.638036840407, 1.136548822547, 0.0);
    private static final Vec3 HYDROGEN_TWO = new Vec3(-1.638036840407, 1.136548822547, 0.0);

    private static List<ContractedGaussian> waterBasis()
    {
        double[] valence = {5.0331513, 1.1695961, 0.3803890};
        List<ContractedGaussian> basis = new ArrayList<>();
        basis.add(contracted(OXYGEN, S, new double[] {130.7093200, 23.8088610, 6.4436083}, SLATER_1S));
        basis.add(contracted(OXYGEN, S, valence, new double[] {-0.09996723, 0.39951283, 0.70011547}));
        for (AngularMomentum p : AngularMomentum.shell(1))
        {
            basis.add(contracted(OXYGEN, p, valence, new double[] {0.15591627, 0.60768372, 0.39195739}));
        }
        basis.add(hydrogen(HYDROGEN_ONE));
        basis.add(hydrogen(HYDROGEN_TWO));
        return basis;
    }

    private static List<PointCharge> waterNuclei()
    {
        return List.of(new PointCharge(OXYGEN, 8.0), new PointCharge(HYDROGEN_ONE, 1.0), new PointCharge(HYDROGEN_TWO, 1.0));
    }

    private static final Vec3[] CHAIN = {Vec3.ZERO, new Vec3(0, 0, 1.8), new Vec3(0, 0, 3.6)};

    private static List<ContractedGaussian> chainBasis()
    {
        return List.of(hydrogen(CHAIN[0]), hydrogen(CHAIN[1]), hydrogen(CHAIN[2]));
    }

    /**
     * The energy of the determinant with the given orbitals, evaluated from the integrals alone, with the first
     * {@code closed} orbitals doubly occupied and the next {@code open} orbitals occupied by a spin-up electron.
     */
    private static double energy(Matrix orbitals, int closed, int open, List<ContractedGaussian> basis, List<PointCharge> nuclei)
    {
        Matrix core = OneElectronIntegrals.coreHamiltonian(basis, nuclei);
        ElectronRepulsionTensor repulsion = ElectronRepulsionIntegrals.tensor(basis);
        int n = orbitals.rows();
        Matrix beta = new Matrix(n, n);
        Matrix alpha = new Matrix(n, n);
        for (int mu = 0; mu < n; mu++)
        {
            for (int nu = 0; nu < n; nu++)
            {
                for (int i = 0; i < closed + open; i++)
                {
                    double product = orbitals.get(mu, i) * orbitals.get(nu, i);
                    alpha.set(mu, nu, alpha.get(mu, nu) + product);
                    if (i < closed)
                    {
                        beta.set(mu, nu, beta.get(mu, nu) + product);
                    }
                }
            }
        }
        double energy = ClosedShellScf.nuclearRepulsion(nuclei) + alpha.dot(core) + beta.dot(core);
        for (int mu = 0; mu < n; mu++)
        {
            for (int nu = 0; nu < n; nu++)
            {
                for (int lambda = 0; lambda < n; lambda++)
                {
                    for (int sigma = 0; sigma < n; sigma++)
                    {
                        double coulomb = (alpha.get(mu, nu) + beta.get(mu, nu)) * (alpha.get(lambda, sigma) + beta.get(lambda, sigma)) * repulsion.get(mu, nu, sigma, lambda);
                        double exchange = (alpha.get(mu, nu) * alpha.get(lambda, sigma) + beta.get(mu, nu) * beta.get(lambda, sigma)) * repulsion.get(mu, lambda, sigma, nu);
                        energy += 0.5 * (coulomb - exchange);
                    }
                }
            }
        }
        return energy;
    }

    private static Matrix rotated(Matrix orbitals, int first, int second, double angle)
    {
        Matrix result = orbitals.copy();
        for (int mu = 0; mu < orbitals.rows(); mu++)
        {
            double a = orbitals.get(mu, first);
            double b = orbitals.get(mu, second);
            result.set(mu, first, Math.cos(angle) * a + Math.sin(angle) * b);
            result.set(mu, second, -Math.sin(angle) * a + Math.cos(angle) * b);
        }
        return result;
    }

    @Test
    void should_give_the_hydrogen_atom_its_sto_3g_energy_and_a_pure_doublet()
    {
        RestrictedOpenShellScfResult atom = RestrictedOpenShellScf.run(List.of(hydrogen(Vec3.ZERO)), protons(Vec3.ZERO), 1, 0);

        assertThat(atom.converged()).isTrue();
        assertThat(atom.totalEnergy()).isCloseTo(HYDROGEN_ATOM_STO_3G, within(1e-8));
        assertThat(atom.spinSquared()).isCloseTo(0.75, within(1e-10));
        assertThat(atom.openOrbitals()).isEqualTo(1);
        assertThat(atom.closedOrbitals()).isEqualTo(0);
    }

    @Test
    void should_match_the_unrestricted_energy_when_every_electron_has_the_same_spin()
    {
        Vec3 second = new Vec3(0, 0, 1.4);
        List<ContractedGaussian> basis = List.of(hydrogen(Vec3.ZERO), hydrogen(second));
        List<PointCharge> nuclei = protons(Vec3.ZERO, second);

        RestrictedOpenShellScfResult restricted = RestrictedOpenShellScf.run(basis, nuclei, 2, 0);
        UnrestrictedScfResult unrestricted = UnrestrictedScf.run(basis, nuclei, 2, 0);

        // with no spin-down electron there is nothing for the unrestricted method to relax
        assertThat(restricted.totalEnergy()).isCloseTo(unrestricted.totalEnergy(), within(1e-9));
        assertThat(restricted.spinSquared()).isCloseTo(2.0, within(1e-10));
    }

    @Test
    void should_reproduce_the_restricted_closed_shell_result_when_there_are_no_open_orbitals()
    {
        ScfResult closedShell = ClosedShellScf.run(waterBasis(), waterNuclei(), 10);

        RestrictedOpenShellScfResult result = RestrictedOpenShellScf.run(waterBasis(), waterNuclei(), 5, 5);

        assertThat(result.converged()).isTrue();
        assertThat(result.openOrbitals()).isZero();
        assertThat(result.totalEnergy()).isCloseTo(closedShell.totalEnergy(), within(1e-9));
        assertThat(result.totalDensity().minus(closedShell.density()).frobeniusNorm()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void should_give_a_pure_spin_state_for_an_open_shell_molecule()
    {
        RestrictedOpenShellScfResult cation = RestrictedOpenShellScf.run(waterBasis(), waterNuclei(), 5, 4);

        assertThat(cation.converged()).isTrue();
        assertThat(cation.spinSquared()).isCloseTo(0.75, within(1e-9));
        assertThat(cation.spinContamination()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void should_share_its_orbitals_between_the_spins()
    {
        RestrictedOpenShellScfResult cation = RestrictedOpenShellScf.run(waterBasis(), waterNuclei(), 5, 4);
        Matrix alpha = cation.alphaDensity();
        Matrix beta = cation.betaDensity();
        Matrix overlap = cation.overlap();

        // each density is idempotent, and the spin-down orbitals are among the spin-up ones
        assertThat(alpha.times(overlap).times(alpha).minus(alpha).frobeniusNorm()).isCloseTo(0.0, within(1e-8));
        assertThat(beta.times(overlap).times(beta).minus(beta).frobeniusNorm()).isCloseTo(0.0, within(1e-8));
        assertThat(alpha.times(overlap).times(beta).minus(beta).frobeniusNorm()).isCloseTo(0.0, within(1e-8));
        assertThat(alpha.times(overlap).trace()).isCloseTo(5.0, within(1e-9));
        assertThat(beta.times(overlap).trace()).isCloseTo(4.0, within(1e-9));
    }

    @Test
    void should_lie_above_the_unrestricted_energy_when_the_unrestricted_iteration_starts_from_its_orbitals()
    {
        RestrictedOpenShellScfResult restricted = RestrictedOpenShellScf.run(waterBasis(), waterNuclei(), 5, 4);

        UnrestrictedScfResult unrestricted = UnrestrictedScf.run(waterBasis(), waterNuclei(), List.of(), 5, 4, ScfSettings.defaults(), 0.0, restricted.coefficients());

        // the restricted determinants are a subset of the unrestricted ones
        assertThat(unrestricted.converged()).isTrue();
        assertThat(unrestricted.totalEnergy()).isLessThan(restricted.totalEnergy() + 1e-9);
        assertThat(restricted.totalEnergy() - unrestricted.totalEnergy()).isGreaterThan(1e-6);
    }

    @Test
    void should_be_stationary_under_every_rotation_that_changes_the_energy_and_invariant_under_the_others()
    {
        // linear H3 with three electrons has one closed, one open and one virtual orbital
        List<ContractedGaussian> basis = chainBasis();
        List<PointCharge> nuclei = protons(CHAIN);
        RestrictedOpenShellScfResult result = RestrictedOpenShellScf.run(basis, nuclei, 2, 1);
        Matrix orbitals = result.coefficients();
        double step = 1.0e-3;

        assertThat(result.converged()).isTrue();
        assertThat(energy(orbitals, 1, 1, basis, nuclei)).isCloseTo(result.totalEnergy(), within(1e-8));
        int[][] rotations = {{0, 1}, {1, 2}, {0, 2}};
        for (int[] pair : rotations)
        {
            double forward = energy(rotated(orbitals, pair[0], pair[1], step), 1, 1, basis, nuclei);
            double backward = energy(rotated(orbitals, pair[0], pair[1], -step), 1, 1, basis, nuclei);
            // a vanishing first derivative makes the two energies equal; at a stationary point they differ at the order of the step cubed
            assertThat(forward - backward).as("rotation of orbitals %d and %d", pair[0], pair[1]).isCloseTo(0.0, within(1e-7));
        }
    }

    @Test
    void should_not_change_the_energy_when_orbitals_of_one_group_are_rotated_among_themselves()
    {
        // water cation: five spin-up electrons, four spin-down, so four closed and one open orbital
        List<ContractedGaussian> basis = waterBasis();
        List<PointCharge> nuclei = waterNuclei();
        RestrictedOpenShellScfResult result = RestrictedOpenShellScf.run(basis, nuclei, 5, 4);
        Matrix orbitals = result.coefficients();

        double within = energy(rotated(orbitals, 0, 1, 0.3), 4, 1, basis, nuclei);

        assertThat(within).isCloseTo(energy(orbitals, 4, 1, basis, nuclei), within(1e-9));
    }

    @Test
    void should_converge_the_open_shell_water_cation_with_the_default_settings_and_report_it_unconverged_when_starved()
    {
        ScfSettings starved = new ScfSettings(2, 1.0e-10, 1.0e-8, 1.0e-8, 8);

        assertThat(RestrictedOpenShellScf.run(waterBasis(), waterNuclei(), 5, 4).converged()).isTrue();
        assertThat(RestrictedOpenShellScf.run(waterBasis(), waterNuclei(), List.of(), 5, 4, starved, null).converged()).isFalse();
    }

    @Test
    void should_reject_impossible_electron_counts_and_a_guess_of_the_wrong_shape()
    {
        List<ContractedGaussian> basis = List.of(hydrogen(Vec3.ZERO));
        List<PointCharge> nuclei = protons(Vec3.ZERO);

        assertThatThrownBy(() -> RestrictedOpenShellScf.run(basis, nuclei, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestrictedOpenShellScf.run(basis, nuclei, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestrictedOpenShellScf.run(basis, nuclei, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestrictedOpenShellScf.run(basis, nuclei, 2, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestrictedOpenShellScf.run(basis, nuclei, List.of(), 1, 0, ScfSettings.defaults(), new Matrix(3, 3))).isInstanceOf(IllegalArgumentException.class);
    }
}
