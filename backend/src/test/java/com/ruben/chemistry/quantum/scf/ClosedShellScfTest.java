package com.ruben.chemistry.quantum.scf;

import com.ruben.chemistry.quantum.basis.AngularMomentum;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.basis.GaussianPrimitive;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ClosedShellScfTest
{
    private static final AngularMomentum S = new AngularMomentum(0, 0, 0);

    private static ContractedGaussian contracted(Vec3 centre, AngularMomentum angular, double[] exponents, double[] coefficients)
    {
        List<GaussianPrimitive> primitives = new ArrayList<>();
        for (int i = 0; i < exponents.length; i++)
        {
            primitives.add(new GaussianPrimitive(exponents[i], coefficients[i]));
        }
        return new ContractedGaussian(centre, angular, primitives);
    }

    private static final double[] SLATER_1S = {0.15432897, 0.53532814, 0.44463454};

    private static List<ContractedGaussian> sto3gHydrogen(Vec3 centre)
    {
        return List.of(contracted(centre, S, new double[] {3.42525091, 0.62391373, 0.16885540}, SLATER_1S));
    }

    private static List<ContractedGaussian> sto3gHelium(Vec3 centre)
    {
        return List.of(contracted(centre, S, new double[] {6.36242139, 1.15892300, 0.31364979}, SLATER_1S));
    }

    private static List<ContractedGaussian> sto3gOxygen(Vec3 centre)
    {
        double[] valence = {5.0331513, 1.1695961, 0.3803890};
        List<ContractedGaussian> functions = new ArrayList<>();
        functions.add(contracted(centre, S, new double[] {130.7093200, 23.8088610, 6.4436083}, SLATER_1S));
        functions.add(contracted(centre, S, valence, new double[] {-0.09996723, 0.39951283, 0.70011547}));
        for (AngularMomentum p : AngularMomentum.shell(1))
        {
            functions.add(contracted(centre, p, valence, new double[] {0.15591627, 0.60768372, 0.39195739}));
        }
        return functions;
    }

    private static final Vec3 OXYGEN = new Vec3(0.0, -0.143225816552, 0.0);
    private static final Vec3 HYDROGEN_ONE = new Vec3(1.638036840407, 1.136548822547, 0.0);
    private static final Vec3 HYDROGEN_TWO = new Vec3(-1.638036840407, 1.136548822547, 0.0);

    private static List<ContractedGaussian> waterBasis(Vec3 oxygen, Vec3 one, Vec3 two)
    {
        List<ContractedGaussian> basis = new ArrayList<>(sto3gOxygen(oxygen));
        basis.addAll(sto3gHydrogen(one));
        basis.addAll(sto3gHydrogen(two));
        return basis;
    }

    private static List<PointCharge> waterNuclei(Vec3 oxygen, Vec3 one, Vec3 two)
    {
        return List.of(new PointCharge(oxygen, 8.0), new PointCharge(one, 1.0), new PointCharge(two, 1.0));
    }

    private static ScfResult water()
    {
        return ClosedShellScf.run(waterBasis(OXYGEN, HYDROGEN_ONE, HYDROGEN_TWO), waterNuclei(OXYGEN, HYDROGEN_ONE, HYDROGEN_TWO), 10);
    }

    @Test
    void should_reproduce_the_sto_3g_hydrogen_molecule_energy_and_orbital_energies()
    {
        Vec3 first = Vec3.ZERO;
        Vec3 second = new Vec3(0, 0, 1.4);
        List<ContractedGaussian> basis = new ArrayList<>(sto3gHydrogen(first));
        basis.addAll(sto3gHydrogen(second));

        ScfResult result = ClosedShellScf.run(basis, List.of(new PointCharge(first, 1.0), new PointCharge(second, 1.0)), 2);

        assertThat(result.converged()).isTrue();
        assertThat(result.nuclearRepulsion()).isCloseTo(1.0 / 1.4, within(1e-14));
        assertThat(result.totalEnergy()).isCloseTo(-1.1167, within(5e-4));
        assertThat(result.orbitalEnergies()[0]).isCloseTo(-0.5782, within(5e-4));
        assertThat(result.orbitalEnergies()[1]).isCloseTo(0.6703, within(5e-4));
    }

    @Test
    void should_reproduce_the_sto_3g_water_nuclear_repulsion_and_energy()
    {
        ScfResult result = water();

        assertThat(result.converged()).isTrue();
        assertThat(result.nuclearRepulsion()).isCloseTo(8.00236706181, within(1e-8));
        assertThat(result.totalEnergy()).isCloseTo(-74.942079928192, within(1e-6));
    }

    @Test
    void should_keep_the_electron_count_and_a_density_that_is_idempotent_in_the_overlap_metric()
    {
        ScfResult result = water();
        Matrix p = result.density();
        Matrix s = result.overlap();

        // electron count, N_e = Tr(P S)
        assertThat(p.times(s).trace()).isCloseTo(10.0, within(1e-10));
        // for a doubly occupied closed shell, P S P = 2 P
        assertThat(p.times(s).times(p).minus(p.times(2.0)).frobeniusNorm()).isLessThan(1e-9);
    }

    @Test
    void should_give_orbitals_that_are_orthonormal_and_ordered_with_a_gap_above_the_occupied_ones()
    {
        ScfResult result = water();
        Matrix c = result.coefficients();

        Matrix identity = c.transpose().times(result.overlap()).times(c);
        assertThat(identity.minus(Matrix.identity(7)).frobeniusNorm()).isLessThan(1e-9);
        for (int i = 1; i < 7; i++)
        {
            assertThat(result.orbitalEnergies()[i]).isGreaterThanOrEqualTo(result.orbitalEnergies()[i - 1]);
        }
        assertThat(result.orbitalEnergies()[4]).isNegative();
        assertThat(result.orbitalEnergies()[5]).isPositive();
    }

    @Test
    void should_give_the_same_energy_after_translating_and_turning_the_molecule()
    {
        Vec3 shift = new Vec3(0.4, -0.9, 1.3);
        Vec3 oxygen = turnAndShift(OXYGEN, shift);
        Vec3 one = turnAndShift(HYDROGEN_ONE, shift);
        Vec3 two = turnAndShift(HYDROGEN_TWO, shift);

        ScfResult moved = ClosedShellScf.run(waterBasis(oxygen, one, two), waterNuclei(oxygen, one, two), 10);

        assertThat(moved.totalEnergy()).isCloseTo(water().totalEnergy(), within(1e-8));
    }

    /**
     * @return the point turned a quarter of a turn about the z axis, then moved by {@code shift}
     */
    private static Vec3 turnAndShift(Vec3 point, Vec3 shift)
    {
        return new Vec3(-point.y() + shift.x(), point.x() + shift.y(), point.z() + shift.z());
    }

    @Test
    void should_reach_the_same_energy_with_and_without_extrapolation()
    {
        List<ContractedGaussian> basis = waterBasis(OXYGEN, HYDROGEN_ONE, HYDROGEN_TWO);
        List<PointCharge> nuclei = waterNuclei(OXYGEN, HYDROGEN_ONE, HYDROGEN_TWO);

        ScfResult plain = ClosedShellScf.run(basis, nuclei, 10, new ScfSettings(500, 1e-11, 1e-9, 1e-8, 0));

        assertThat(plain.converged()).isTrue();
        assertThat(plain.totalEnergy()).isCloseTo(water().totalEnergy(), within(1e-8));
    }

    @Test
    void should_converge_for_a_polar_heteronuclear_molecule_with_charge_pulled_towards_helium()
    {
        Vec3 helium = Vec3.ZERO;
        Vec3 hydrogen = new Vec3(0, 0, 1.4632);
        List<ContractedGaussian> basis = new ArrayList<>(sto3gHelium(helium));
        basis.addAll(sto3gHydrogen(hydrogen));

        ScfResult result = ClosedShellScf.run(basis, List.of(new PointCharge(helium, 2.0), new PointCharge(hydrogen, 1.0)), 2);

        assertThat(result.converged()).isTrue();
        assertThat(result.totalEnergy()).isLessThan(-2.8);
        assertThat(result.density().times(result.overlap()).trace()).isCloseTo(2.0, within(1e-10));
        // the Mulliken population on helium, the sum of (P S) over its function, exceeds one electron
        Matrix ps = result.density().times(result.overlap());
        assertThat(ps.get(0, 0)).isGreaterThan(1.0);
    }

    @Test
    void should_report_when_the_iteration_limit_is_reached()
    {
        List<ContractedGaussian> basis = waterBasis(OXYGEN, HYDROGEN_ONE, HYDROGEN_TWO);

        ScfResult result = ClosedShellScf.run(basis, waterNuclei(OXYGEN, HYDROGEN_ONE, HYDROGEN_TWO), 10, new ScfSettings(2, 1e-10, 1e-8, 1e-8, 8));

        assertThat(result.converged()).isFalse();
        assertThat(result.iterations()).isEqualTo(2);
    }

    @Test
    void should_reject_an_odd_or_impossible_electron_count()
    {
        List<ContractedGaussian> basis = sto3gHydrogen(Vec3.ZERO);
        List<PointCharge> nuclei = List.of(new PointCharge(Vec3.ZERO, 1.0));

        assertThatThrownBy(() -> ClosedShellScf.run(basis, nuclei, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClosedShellScf.run(basis, nuclei, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClosedShellScf.run(basis, nuclei, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
