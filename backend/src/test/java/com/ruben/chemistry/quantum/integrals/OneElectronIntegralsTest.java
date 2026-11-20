package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.quantum.basis.AngularMomentum;
import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.quantum.basis.BasisSet;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.basis.GaussianPrimitive;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.quantum.linalg.SymmetricEigen;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class OneElectronIntegralsTest
{
    private static ContractedGaussian single(Vec3 centre, AngularMomentum angular, double exponent)
    {
        return new ContractedGaussian(centre, angular, List.of(new GaussianPrimitive(exponent, 1.0)));
    }

    private static ContractedGaussian sto3gHydrogen(Vec3 centre)
    {
        return new ContractedGaussian(centre, new AngularMomentum(0, 0, 0), List.of(
                new GaussianPrimitive(3.42525091, 0.15432897),
                new GaussianPrimitive(0.62391373, 0.53532814),
                new GaussianPrimitive(0.16885540, 0.44463454)));
    }

    @Test
    void should_give_unit_self_overlap_for_s_p_and_d_functions()
    {
        for (int l = 0; l <= 3; l++)
        {
            for (AngularMomentum angular : AngularMomentum.shell(l))
            {
                ContractedGaussian function = new ContractedGaussian(new Vec3(0.3, -0.2, 0.9), angular, List.of(new GaussianPrimitive(0.9, 0.6), new GaussianPrimitive(0.25, 0.5)));

                assertThat(OneElectronIntegrals.overlap(function, function)).isCloseTo(1.0, within(1e-12));
            }
        }
    }

    @Test
    void should_match_the_closed_form_overlap_of_two_s_gaussians()
    {
        double a = 0.8;
        double b = 1.7;
        double distance = 1.3;
        ContractedGaussian first = single(Vec3.ZERO, new AngularMomentum(0, 0, 0), a);
        ContractedGaussian second = single(new Vec3(distance, 0, 0), new AngularMomentum(0, 0, 0), b);

        double expected = Math.pow(2.0 * Math.sqrt(a * b) / (a + b), 1.5) * Math.exp(-a * b / (a + b) * distance * distance);

        assertThat(OneElectronIntegrals.overlap(first, second)).isCloseTo(expected, within(1e-13));
    }

    @Test
    void should_reproduce_the_sto_3g_hydrogen_molecule_overlap()
    {
        ContractedGaussian first = sto3gHydrogen(Vec3.ZERO);
        ContractedGaussian second = sto3gHydrogen(new Vec3(0, 0, 1.4));

        assertThat(OneElectronIntegrals.overlap(first, second)).isCloseTo(0.6593, within(1e-4));
    }

    @Test
    void should_match_the_closed_form_overlap_of_an_s_and_a_displaced_p_gaussian()
    {
        double alpha = 0.8;
        double beta = 1.3;
        double distance = 1.1;
        ContractedGaussian s = single(Vec3.ZERO, new AngularMomentum(0, 0, 0), alpha);
        ContractedGaussian p = single(new Vec3(distance, 0, 0), new AngularMomentum(1, 0, 0), beta);

        double p0 = alpha + beta;
        double normalisation = Math.pow(2.0 * alpha / Math.PI, 0.75) * Math.pow(2.0 * beta / Math.PI, 0.75) * Math.sqrt(4.0 * beta);
        double expected = normalisation * -(alpha * distance / p0) * Math.pow(Math.PI / p0, 1.5) * Math.exp(-alpha * beta / p0 * distance * distance);

        assertThat(OneElectronIntegrals.overlap(s, p)).isCloseTo(expected, within(1e-13));
    }

    @Test
    void should_have_zero_overlap_between_p_functions_on_the_same_centre_along_different_axes()
    {
        ContractedGaussian px = single(Vec3.ZERO, new AngularMomentum(1, 0, 0), 1.1);
        ContractedGaussian py = single(Vec3.ZERO, new AngularMomentum(0, 1, 0), 1.1);

        assertThat(OneElectronIntegrals.overlap(px, py)).isCloseTo(0.0, within(1e-15));
    }

    @Test
    void should_have_zero_overlap_between_an_s_function_and_a_p_function_perpendicular_to_the_separation()
    {
        ContractedGaussian s = single(Vec3.ZERO, new AngularMomentum(0, 0, 0), 0.9);
        ContractedGaussian py = single(new Vec3(1.5, 0, 0), new AngularMomentum(0, 1, 0), 0.9);

        assertThat(OneElectronIntegrals.overlap(s, py)).isCloseTo(0.0, within(1e-15));
    }

    @Test
    void should_build_a_symmetric_positive_definite_overlap_matrix_with_unit_diagonal()
    {
        List<ContractedGaussian> basis = new ArrayList<>();
        Vec3 oxygen = Vec3.ZERO;
        Vec3 hydrogen = new Vec3(0.0, 1.43, 1.11);
        basis.add(single(oxygen, new AngularMomentum(0, 0, 0), 5.0));
        basis.addAll(AngularMomentum.shell(1).stream().map(angular -> single(oxygen, angular, 1.2)).toList());
        basis.addAll(AngularMomentum.shell(2).stream().map(angular -> single(hydrogen, angular, 0.9)).toList());

        Matrix overlap = OneElectronIntegrals.overlapMatrix(basis);

        assertThat(overlap.isSymmetric(1e-14)).isTrue();
        for (int i = 0; i < basis.size(); i++)
        {
            assertThat(overlap.get(i, i)).isCloseTo(1.0, within(1e-12));
        }
        assertThat(SymmetricEigen.of(overlap).values()[0]).isGreaterThan(0.0);
    }

    @Test
    void should_give_the_mean_kinetic_energy_of_a_pure_angular_momentum_gaussian()
    {
        double exponent = 0.9;
        for (int l = 0; l <= 3; l++)
        {
            for (AngularMomentum angular : AngularMomentum.shell(l))
            {
                // a power of 2 or more mixes in a lower angular momentum (x^2 holds an s-like r^2 part), which
                // changes the energy, so only components with each power at most 1 are pure
                if (angular.lx() > 1 || angular.ly() > 1 || angular.lz() > 1)
                {
                    continue;
                }
                ContractedGaussian function = single(new Vec3(0.2, -0.4, 0.7), angular, exponent);

                assertThat(OneElectronIntegrals.kinetic(function, function)).as(angular.toString()).isCloseTo((2 * l + 3) * exponent / 2.0, within(1e-12));
            }
        }
    }

    @Test
    void should_match_the_hand_derived_kinetic_energy_of_a_cartesian_xx_gaussian()
    {
        double exponent = 0.9;
        ContractedGaussian xx = single(Vec3.ZERO, new AngularMomentum(2, 0, 0), exponent);

        // the x factor x^2 exp(-a x^2) has mean kinetic energy 7a/6 and the y and z factors have a/2 each
        assertThat(OneElectronIntegrals.kinetic(xx, xx)).isCloseTo(13.0 * exponent / 6.0, within(1e-12));
    }

    @Test
    void should_match_the_closed_form_kinetic_energy_of_two_s_gaussians_on_different_centres()
    {
        double a = 0.8;
        double b = 1.7;
        double distance = 1.3;
        ContractedGaussian first = single(Vec3.ZERO, new AngularMomentum(0, 0, 0), a);
        ContractedGaussian second = single(new Vec3(0, distance, 0), new AngularMomentum(0, 0, 0), b);

        double reduced = a * b / (a + b);
        double expected = reduced * (3.0 - 2.0 * reduced * distance * distance) * OneElectronIntegrals.overlap(first, second);

        assertThat(OneElectronIntegrals.kinetic(first, second)).isCloseTo(expected, within(1e-13));
    }

    @Test
    void should_reproduce_the_sto_3g_hydrogen_molecule_kinetic_energies()
    {
        ContractedGaussian first = sto3gHydrogen(Vec3.ZERO);
        ContractedGaussian second = sto3gHydrogen(new Vec3(0, 0, 1.4));

        assertThat(OneElectronIntegrals.kinetic(first, first)).isCloseTo(0.7600, within(1e-4));
        assertThat(OneElectronIntegrals.kinetic(first, second)).isCloseTo(0.2365, within(1e-4));
    }

    @Test
    void should_have_symmetric_kinetic_integrals_for_mixed_shells_and_centres()
    {
        ContractedGaussian a = single(new Vec3(0.1, 0.2, 0.3), new AngularMomentum(1, 1, 0), 0.7);
        ContractedGaussian b = single(new Vec3(-0.5, 0.9, 0.4), new AngularMomentum(0, 2, 1), 1.2);

        assertThat(OneElectronIntegrals.kinetic(a, b)).isCloseTo(OneElectronIntegrals.kinetic(b, a), within(1e-13));
    }

    @Test
    void should_have_zero_kinetic_integral_between_p_functions_on_one_centre_along_different_axes()
    {
        ContractedGaussian px = single(Vec3.ZERO, new AngularMomentum(1, 0, 0), 1.1);
        ContractedGaussian py = single(Vec3.ZERO, new AngularMomentum(0, 1, 0), 1.1);

        assertThat(OneElectronIntegrals.kinetic(px, py)).isCloseTo(0.0, within(1e-14));
    }

    @Test
    void should_give_water_a_symmetric_positive_definite_kinetic_matrix()
    {
        BasisSet basis = BasisSet.build(Embedder.embed(Molecule.fromSmiles("O")), 0, BasisLibrary.cep31g());

        Matrix kinetic = OneElectronIntegrals.kineticMatrix(basis.functions());

        assertThat(kinetic.rows()).isEqualTo(12);
        assertThat(kinetic.isSymmetric(1e-12)).isTrue();
        assertThat(SymmetricEigen.of(kinetic).values()[0]).isGreaterThan(0.0);
    }

    @Test
    void should_match_the_closed_form_attraction_of_an_s_gaussian_to_a_nucleus_at_its_centre()
    {
        double exponent = 0.9;
        double charge = 2.0;
        ContractedGaussian s = single(Vec3.ZERO, new AngularMomentum(0, 0, 0), exponent);

        double attraction = OneElectronIntegrals.nuclearAttraction(s, s, List.of(new PointCharge(Vec3.ZERO, charge)));

        assertThat(attraction).isCloseTo(-charge * 2.0 * Math.sqrt(2.0 * exponent / Math.PI), within(1e-13));
    }

    @Test
    void should_match_the_closed_form_attraction_of_a_p_gaussian_to_a_nucleus_at_its_centre()
    {
        double exponent = 0.9;
        ContractedGaussian px = single(Vec3.ZERO, new AngularMomentum(1, 0, 0), exponent);

        double attraction = OneElectronIntegrals.nuclearAttraction(px, px, List.of(new PointCharge(Vec3.ZERO, 1.0)));

        assertThat(attraction).isCloseTo(-4.0 / 3.0 * Math.sqrt(2.0 * exponent / Math.PI), within(1e-13));
    }

    @Test
    void should_approach_the_point_charge_limit_far_from_the_nucleus()
    {
        double distance = 100.0;
        ContractedGaussian s = single(Vec3.ZERO, new AngularMomentum(0, 0, 0), 1.0);

        double attraction = OneElectronIntegrals.nuclearAttraction(s, s, List.of(new PointCharge(new Vec3(0, 0, distance), 3.0)));

        assertThat(attraction).isCloseTo(-3.0 / distance, within(1e-5));
    }

    @Test
    void should_reproduce_the_sto_3g_hydrogen_molecule_attraction_and_core_hamiltonian()
    {
        ContractedGaussian first = sto3gHydrogen(Vec3.ZERO);
        ContractedGaussian second = sto3gHydrogen(new Vec3(0, 0, 1.4));
        PointCharge nucleusOne = new PointCharge(Vec3.ZERO, 1.0);
        PointCharge nucleusTwo = new PointCharge(new Vec3(0, 0, 1.4), 1.0);

        assertThat(OneElectronIntegrals.nuclearAttraction(first, first, List.of(nucleusOne))).isCloseTo(-1.2266, within(1e-4));
        assertThat(OneElectronIntegrals.nuclearAttraction(first, first, List.of(nucleusTwo))).isCloseTo(-0.6538, within(1e-4));
        assertThat(OneElectronIntegrals.nuclearAttraction(first, second, List.of(nucleusOne))).isCloseTo(-0.5974, within(1e-4));

        Matrix core = OneElectronIntegrals.coreHamiltonian(List.of(first, second), List.of(nucleusOne, nucleusTwo));
        assertThat(core.get(0, 0)).isCloseTo(-1.1204, within(2e-4));
        assertThat(core.get(0, 1)).isCloseTo(-0.9584, within(2e-4));
        assertThat(core.get(1, 1)).isCloseTo(-1.1204, within(2e-4));
    }

    @Test
    void should_not_change_when_everything_is_translated_together()
    {
        Vec3 shift = new Vec3(1.1, -0.7, 2.3);
        Vec3 centreA = new Vec3(0.2, 0.5, -0.3);
        Vec3 centreB = new Vec3(-0.6, 0.1, 0.8);
        Vec3 nucleus = new Vec3(0.4, -0.9, 0.3);

        double original = OneElectronIntegrals.nuclearAttraction(
                single(centreA, new AngularMomentum(1, 1, 0), 0.8), single(centreB, new AngularMomentum(0, 1, 0), 1.3), List.of(new PointCharge(nucleus, 1.5)));
        double shifted = OneElectronIntegrals.nuclearAttraction(
                single(centreA.plus(shift), new AngularMomentum(1, 1, 0), 0.8), single(centreB.plus(shift), new AngularMomentum(0, 1, 0), 1.3), List.of(new PointCharge(nucleus.plus(shift), 1.5)));

        assertThat(shifted).isCloseTo(original, within(1e-12));
    }

    @Test
    void should_map_px_to_py_and_py_to_minus_px_under_a_quarter_turn_about_z()
    {
        Vec3 centreA = new Vec3(0.3, 0.8, -0.2);
        Vec3 centreB = new Vec3(-0.5, 0.2, 0.6);
        Vec3 nucleus = new Vec3(0.7, -0.4, 0.5);
        PointCharge before = new PointCharge(nucleus, 1.0);
        PointCharge after = new PointCharge(turn(nucleus), 1.0);
        ContractedGaussian sBefore = single(centreB, new AngularMomentum(0, 0, 0), 1.1);
        ContractedGaussian sAfter = single(turn(centreB), new AngularMomentum(0, 0, 0), 1.1);

        double px = OneElectronIntegrals.nuclearAttraction(single(centreA, new AngularMomentum(1, 0, 0), 0.9), sBefore, List.of(before));
        double py = OneElectronIntegrals.nuclearAttraction(single(centreA, new AngularMomentum(0, 1, 0), 0.9), sBefore, List.of(before));
        double pxTurned = OneElectronIntegrals.nuclearAttraction(single(turn(centreA), new AngularMomentum(1, 0, 0), 0.9), sAfter, List.of(after));
        double pyTurned = OneElectronIntegrals.nuclearAttraction(single(turn(centreA), new AngularMomentum(0, 1, 0), 0.9), sAfter, List.of(after));

        assertThat(pyTurned).isCloseTo(px, within(1e-12));
        assertThat(pxTurned).isCloseTo(-py, within(1e-12));
    }

    /**
     * @return the point turned a quarter of a turn about the z axis, so that x goes to y and y to minus x
     */
    private static Vec3 turn(Vec3 point)
    {
        return new Vec3(-point.y(), point.x(), point.z());
    }

    @Test
    void should_add_the_attraction_of_each_nucleus()
    {
        ContractedGaussian s = single(Vec3.ZERO, new AngularMomentum(0, 0, 0), 0.9);
        PointCharge one = new PointCharge(new Vec3(0, 0, 1.0), 1.0);
        PointCharge two = new PointCharge(new Vec3(0.5, 0, -1.0), 2.0);

        double both = OneElectronIntegrals.nuclearAttraction(s, s, List.of(one, two));

        assertThat(both).isCloseTo(OneElectronIntegrals.nuclearAttraction(s, s, List.of(one)) + OneElectronIntegrals.nuclearAttraction(s, s, List.of(two)), within(1e-14));
    }

    @Test
    void should_give_water_a_symmetric_attractive_nuclear_matrix_and_a_core_hamiltonian_equal_to_the_sum()
    {
        BasisSet basis = BasisSet.build(Embedder.embed(Molecule.fromSmiles("O")), 0, BasisLibrary.cep31g());
        List<PointCharge> nuclei = List.of(
                new PointCharge(new Vec3(0.0, 0.0, 0.0), 6.0),
                new PointCharge(new Vec3(1.43, 0.0, 1.11), 1.0),
                new PointCharge(new Vec3(-1.43, 0.0, 1.11), 1.0));

        Matrix attraction = OneElectronIntegrals.nuclearAttractionMatrix(basis.functions(), nuclei);
        Matrix core = OneElectronIntegrals.coreHamiltonian(basis.functions(), nuclei);

        assertThat(attraction.isSymmetric(1e-12)).isTrue();
        for (int i = 0; i < attraction.rows(); i++)
        {
            assertThat(attraction.get(i, i)).isNegative();
        }
        assertThat(core.minus(OneElectronIntegrals.kineticMatrix(basis.functions()).plus(attraction)).frobeniusNorm()).isLessThan(1e-14);
    }

    @Test
    void should_reject_a_nonfinite_point_charge()
    {
        assertThatThrownBy(() -> new PointCharge(Vec3.ZERO, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
