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

class UnrestrictedScfTest
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

    private static UnrestrictedScfResult stretchedHydrogenMolecule(double separation, double mixing)
    {
        Vec3 second = new Vec3(0, 0, separation);
        return UnrestrictedScf.run(List.of(hydrogen(Vec3.ZERO), hydrogen(second)), protons(Vec3.ZERO, second), List.of(), 1, 1, ScfSettings.defaults(), mixing);
    }

    @Test
    void should_give_the_hydrogen_atom_its_sto_3g_energy_and_a_pure_doublet()
    {
        UnrestrictedScfResult atom = UnrestrictedScf.run(List.of(hydrogen(Vec3.ZERO)), protons(Vec3.ZERO), 1, 0);

        assertThat(atom.converged()).isTrue();
        assertThat(atom.totalEnergy()).isCloseTo(HYDROGEN_ATOM_STO_3G, within(1e-8));
        assertThat(atom.spinSquared()).isCloseTo(0.75, within(1e-10));
        assertThat(atom.spinContamination()).isCloseTo(0.0, within(1e-10));
    }

    @Test
    void should_match_the_closed_form_for_the_one_electron_hydrogen_molecule_ion()
    {
        double separation = 2.0;
        Vec3 second = new Vec3(0, 0, separation);
        List<ContractedGaussian> basis = List.of(hydrogen(Vec3.ZERO), hydrogen(second));
        List<PointCharge> nuclei = protons(Vec3.ZERO, second);
        Matrix overlap = OneElectronIntegrals.overlapMatrix(basis);
        Matrix core = OneElectronIntegrals.coreHamiltonian(basis, nuclei);

        UnrestrictedScfResult ion = UnrestrictedScf.run(basis, nuclei, 1, 0);

        // with no second electron there is no repulsion, so the bonding orbital is the lowest root of the 2x2 core problem
        double bonding = (core.get(0, 0) + core.get(0, 1)) / (1.0 + overlap.get(0, 1));
        assertThat(ion.totalEnergy()).isCloseTo(bonding + 1.0 / separation, within(1e-9));
        assertThat(ion.spinSquared()).isCloseTo(0.75, within(1e-10));
    }

    @Test
    void should_match_the_determinant_energy_of_the_fully_polarised_hydrogen_molecule()
    {
        double separation = 1.4;
        Vec3 second = new Vec3(0, 0, separation);
        List<ContractedGaussian> basis = List.of(hydrogen(Vec3.ZERO), hydrogen(second));
        List<PointCharge> nuclei = protons(Vec3.ZERO, second);

        UnrestrictedScfResult triplet = UnrestrictedScf.run(basis, nuclei, 2, 0);

        // both electrons spin up and the two orbitals span the whole basis, so the density is the inverse overlap whatever the orbitals are
        Matrix overlap = OneElectronIntegrals.overlapMatrix(basis);
        double s = overlap.get(0, 1);
        Matrix density = Matrix.of(new double[][] {{1.0, -s}, {-s, 1.0}}).times(1.0 / (1.0 - s * s));
        Matrix core = OneElectronIntegrals.coreHamiltonian(basis, nuclei);
        ElectronRepulsionTensor repulsion = ElectronRepulsionIntegrals.tensor(basis);
        double energy = density.dot(core) + 1.0 / separation;
        for (int mu = 0; mu < 2; mu++)
        {
            for (int nu = 0; nu < 2; nu++)
            {
                for (int lambda = 0; lambda < 2; lambda++)
                {
                    for (int sigma = 0; sigma < 2; sigma++)
                    {
                        energy += 0.5 * density.get(mu, nu) * density.get(lambda, sigma) * (repulsion.get(mu, nu, sigma, lambda) - repulsion.get(mu, lambda, sigma, nu));
                    }
                }
            }
        }
        assertThat(triplet.totalEnergy()).isCloseTo(energy, within(1e-9));
        assertThat(triplet.spinSquared()).isCloseTo(2.0, within(1e-10));
        assertThat(triplet.betaDensity().frobeniusNorm()).isCloseTo(0.0, within(1e-14));
    }

    @Test
    void should_reproduce_the_restricted_result_for_a_closed_shell_molecule()
    {
        ScfResult restricted = ClosedShellScf.run(waterBasis(), waterNuclei(), 10);

        UnrestrictedScfResult unrestricted = UnrestrictedScf.run(waterBasis(), waterNuclei(), 5, 5);

        assertThat(unrestricted.converged()).isTrue();
        assertThat(unrestricted.totalEnergy()).isCloseTo(restricted.totalEnergy(), within(1e-9));
        assertThat(unrestricted.totalDensity().minus(restricted.density()).frobeniusNorm()).isCloseTo(0.0, within(1e-6));
        assertThat(unrestricted.alphaDensity().minus(unrestricted.betaDensity()).frobeniusNorm()).isCloseTo(0.0, within(1e-6));
        assertThat(unrestricted.spinSquared()).isCloseTo(0.0, within(1e-8));
    }

    @Test
    void should_hold_the_requested_number_of_electrons_of_each_spin()
    {
        UnrestrictedScfResult cation = UnrestrictedScf.run(waterBasis(), waterNuclei(), 5, 4);

        assertThat(cation.converged()).isTrue();
        assertThat(cation.alphaDensity().times(cation.overlap()).trace()).isCloseTo(5.0, within(1e-9));
        assertThat(cation.betaDensity().times(cation.overlap()).trace()).isCloseTo(4.0, within(1e-9));
        assertThat(cation.spinProjection()).isCloseTo(0.5, within(1e-15));
    }

    @Test
    void should_show_a_small_spin_contamination_for_a_doublet_molecule()
    {
        UnrestrictedScfResult cation = UnrestrictedScf.run(waterBasis(), waterNuclei(), 5, 4);

        // never below the pure-state value, and for a well-behaved doublet only slightly above it
        assertThat(cation.spinContamination()).isGreaterThanOrEqualTo(-1e-10);
        assertThat(cation.spinSquared()).isBetween(0.75, 0.80);
    }

    @Test
    void should_lower_the_energy_of_the_stretched_hydrogen_molecule_by_breaking_spin_symmetry()
    {
        UnrestrictedScfResult symmetric = stretchedHydrogenMolecule(6.0, 0.0);
        UnrestrictedScfResult broken = stretchedHydrogenMolecule(6.0, 0.3);

        assertThat(broken.converged()).isTrue();
        assertThat(broken.totalEnergy()).isLessThan(symmetric.totalEnergy() - 0.2);
        assertThat(broken.spinSquared()).isGreaterThan(0.99);
    }

    @Test
    void should_dissociate_the_hydrogen_molecule_to_two_hydrogen_atoms_when_spin_symmetry_is_broken()
    {
        UnrestrictedScfResult broken = stretchedHydrogenMolecule(10.0, 0.3);

        assertThat(broken.totalEnergy()).isCloseTo(2.0 * HYDROGEN_ATOM_STO_3G, within(1e-5));
        assertThat(broken.spinSquared()).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void should_return_to_the_restricted_solution_near_the_equilibrium_bond_length_even_with_mixing()
    {
        UnrestrictedScfResult mixed = stretchedHydrogenMolecule(1.4, 0.3);
        UnrestrictedScfResult plain = stretchedHydrogenMolecule(1.4, 0.0);

        assertThat(mixed.totalEnergy()).isCloseTo(plain.totalEnergy(), within(1e-8));
        assertThat(mixed.spinSquared()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void should_stay_in_the_restricted_solution_without_mixing_however_far_the_atoms_are_apart()
    {
        UnrestrictedScfResult symmetric = stretchedHydrogenMolecule(6.0, 0.0);
        ScfResult restricted = ClosedShellScf.run(List.of(hydrogen(Vec3.ZERO), hydrogen(new Vec3(0, 0, 6.0))), protons(Vec3.ZERO, new Vec3(0, 0, 6.0)), 2);

        assertThat(symmetric.totalEnergy()).isCloseTo(restricted.totalEnergy(), within(1e-9));
    }

    @Test
    void should_reject_impossible_electron_counts()
    {
        List<ContractedGaussian> basis = List.of(hydrogen(Vec3.ZERO));
        List<PointCharge> nuclei = protons(Vec3.ZERO);

        assertThatThrownBy(() -> UnrestrictedScf.run(basis, nuclei, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnrestrictedScf.run(basis, nuclei, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnrestrictedScf.run(basis, nuclei, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnrestrictedScf.run(basis, nuclei, 2, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_mixing_when_there_is_no_spin_down_electron_or_no_empty_orbital()
    {
        List<ContractedGaussian> basis = List.of(hydrogen(Vec3.ZERO));
        List<PointCharge> nuclei = protons(Vec3.ZERO);

        assertThatThrownBy(() -> UnrestrictedScf.run(basis, nuclei, List.of(), 1, 0, ScfSettings.defaults(), 0.3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnrestrictedScf.run(basis, protons(Vec3.ZERO), List.of(), 1, 1, ScfSettings.defaults(), 0.3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_report_a_failure_to_converge_when_the_iteration_limit_is_too_small()
    {
        ScfSettings tight = new ScfSettings(2, 1.0e-10, 1.0e-8, 1.0e-8, 8);

        UnrestrictedScfResult result = UnrestrictedScf.run(waterBasis(), waterNuclei(), List.of(), 5, 4, tight);

        assertThat(result.converged()).isFalse();
    }

    @Test
    void should_end_no_higher_than_the_restricted_open_shell_energy_when_started_from_its_orbitals()
    {
        RestrictedOpenShellScfResult restricted = RestrictedOpenShellScf.run(waterBasis(), waterNuclei(), 5, 4);

        UnrestrictedScfResult unrestricted = UnrestrictedScf.run(waterBasis(), waterNuclei(), List.of(), 5, 4, ScfSettings.defaults(), 0.0, restricted.coefficients());

        assertThat(unrestricted.converged()).isTrue();
        assertThat(unrestricted.totalEnergy()).isLessThan(restricted.totalEnergy() + 1e-9);
    }

    @Test
    void should_reject_a_guess_that_does_not_match_the_basis()
    {
        List<ContractedGaussian> basis = List.of(hydrogen(Vec3.ZERO));

        assertThatThrownBy(() -> UnrestrictedScf.run(basis, protons(Vec3.ZERO), List.of(), 1, 0, ScfSettings.defaults(), 0.0, new Matrix(3, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
