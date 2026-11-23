package com.ruben.chemistry.quantum.basis;

import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ContractedGaussianTest
{
    @Test
    void should_scale_a_single_s_primitive_to_the_standard_normalisation()
    {
        double exponent = 0.8;
        ContractedGaussian s = new ContractedGaussian(Vec3.ZERO, new AngularMomentum(0, 0, 0), List.of(new GaussianPrimitive(exponent, 1.0)));

        assertThat(s.coefficient(0)).isCloseTo(Math.pow(2.0 * exponent / Math.PI, 0.75), within(1e-14));
    }

    @Test
    void should_scale_a_single_p_primitive_to_the_standard_normalisation()
    {
        double exponent = 1.3;
        ContractedGaussian p = new ContractedGaussian(Vec3.ZERO, new AngularMomentum(1, 0, 0), List.of(new GaussianPrimitive(exponent, 1.0)));

        double expected = Math.pow(2.0 * exponent / Math.PI, 0.75) * Math.sqrt(4.0 * exponent);
        assertThat(p.coefficient(0)).isCloseTo(expected, within(1e-14));
    }

    @Test
    void should_evaluate_an_off_centre_p_function_from_its_definition()
    {
        double exponent = 0.7;
        Vec3 centre = new Vec3(0.5, -0.2, 0.3);
        ContractedGaussian py = new ContractedGaussian(centre, new AngularMomentum(0, 1, 0), List.of(new GaussianPrimitive(exponent, 1.0)));

        double dx = 0.4;
        double dy = 0.9;
        double dz = -0.6;
        double expected = py.coefficient(0) * dy * Math.exp(-exponent * (dx * dx + dy * dy + dz * dz));
        assertThat(py.valueAt(centre.x() + dx, centre.y() + dy, centre.z() + dz)).isCloseTo(expected, within(1e-14));
        assertThat(py.valueAt(centre.x() + dx, centre.y(), centre.z() + dz)).isCloseTo(0.0, within(1e-15));
    }

    @Test
    void should_reject_a_contraction_that_cancels_to_nothing()
    {
        List<GaussianPrimitive> cancelling = List.of(new GaussianPrimitive(1.0, 1.0), new GaussianPrimitive(1.0, -1.0));

        assertThatThrownBy(() -> new ContractedGaussian(Vec3.ZERO, new AngularMomentum(0, 0, 0), cancelling)).isInstanceOf(IllegalArgumentException.class);
    }
}
