package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.quantum.basis.AngularMomentum;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.basis.GaussianPrimitive;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.quantum.linalg.SymmetricEigen;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OverlapIntegralsTest
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

                assertThat(OverlapIntegrals.between(function, function)).isCloseTo(1.0, within(1e-12));
            }
        }
    }

    @Test
    void should_match_the_closed_form_for_two_s_gaussians()
    {
        double a = 0.8;
        double b = 1.7;
        double distance = 1.3;
        ContractedGaussian first = single(Vec3.ZERO, new AngularMomentum(0, 0, 0), a);
        ContractedGaussian second = single(new Vec3(distance, 0, 0), new AngularMomentum(0, 0, 0), b);

        double expected = Math.pow(2.0 * Math.sqrt(a * b) / (a + b), 1.5) * Math.exp(-a * b / (a + b) * distance * distance);

        assertThat(OverlapIntegrals.between(first, second)).isCloseTo(expected, within(1e-13));
    }

    @Test
    void should_reproduce_the_sto_3g_hydrogen_molecule_overlap()
    {
        ContractedGaussian first = sto3gHydrogen(Vec3.ZERO);
        ContractedGaussian second = sto3gHydrogen(new Vec3(0, 0, 1.4));

        assertThat(OverlapIntegrals.between(first, second)).isCloseTo(0.6593, within(1e-4));
    }

    @Test
    void should_match_the_closed_form_for_an_s_and_a_displaced_p_gaussian()
    {
        double alpha = 0.8;
        double beta = 1.3;
        double distance = 1.1;
        ContractedGaussian s = single(Vec3.ZERO, new AngularMomentum(0, 0, 0), alpha);
        ContractedGaussian p = single(new Vec3(distance, 0, 0), new AngularMomentum(1, 0, 0), beta);

        double p0 = alpha + beta;
        double normalisation = Math.pow(2.0 * alpha / Math.PI, 0.75) * Math.pow(2.0 * beta / Math.PI, 0.75) * Math.sqrt(4.0 * beta);
        double expected = normalisation * -(alpha * distance / p0) * Math.pow(Math.PI / p0, 1.5) * Math.exp(-alpha * beta / p0 * distance * distance);

        assertThat(OverlapIntegrals.between(s, p)).isCloseTo(expected, within(1e-13));
    }

    @Test
    void should_be_zero_for_p_functions_on_the_same_centre_along_different_axes()
    {
        ContractedGaussian px = single(Vec3.ZERO, new AngularMomentum(1, 0, 0), 1.1);
        ContractedGaussian py = single(Vec3.ZERO, new AngularMomentum(0, 1, 0), 1.1);

        assertThat(OverlapIntegrals.between(px, py)).isCloseTo(0.0, within(1e-15));
    }

    @Test
    void should_be_zero_between_an_s_function_and_a_p_function_perpendicular_to_the_separation()
    {
        ContractedGaussian s = single(Vec3.ZERO, new AngularMomentum(0, 0, 0), 0.9);
        ContractedGaussian py = single(new Vec3(1.5, 0, 0), new AngularMomentum(0, 1, 0), 0.9);

        assertThat(OverlapIntegrals.between(s, py)).isCloseTo(0.0, within(1e-15));
    }

    @Test
    void should_build_a_symmetric_positive_definite_matrix_with_unit_diagonal()
    {
        List<ContractedGaussian> basis = new ArrayList<>();
        Vec3 oxygen = Vec3.ZERO;
        Vec3 hydrogen = new Vec3(0.0, 1.43, 1.11);
        basis.add(single(oxygen, new AngularMomentum(0, 0, 0), 5.0));
        basis.addAll(AngularMomentum.shell(1).stream().map(angular -> single(oxygen, angular, 1.2)).toList());
        basis.addAll(AngularMomentum.shell(2).stream().map(angular -> single(hydrogen, angular, 0.9)).toList());

        Matrix overlap = OverlapIntegrals.matrix(basis);

        assertThat(overlap.isSymmetric(1e-14)).isTrue();
        for (int i = 0; i < basis.size(); i++)
        {
            assertThat(overlap.get(i, i)).isCloseTo(1.0, within(1e-12));
        }
        assertThat(SymmetricEigen.of(overlap).values()[0]).isGreaterThan(0.0);
    }
}
