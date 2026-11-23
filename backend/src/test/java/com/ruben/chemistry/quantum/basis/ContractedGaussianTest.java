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
    void should_find_derivatives_that_agree_with_differences_of_values_for_every_angular_momentum()
    {
        List<GaussianPrimitive> primitives = List.of(new GaussianPrimitive(1.4, 0.6), new GaussianPrimitive(0.35, 0.5));
        double x = 0.37;
        double y = -0.52;
        double z = 0.81;
        double h = 1.0e-4;
        for (AngularMomentum angular : List.of(new AngularMomentum(0, 0, 0), new AngularMomentum(1, 0, 0), new AngularMomentum(0, 1, 0),
                new AngularMomentum(1, 1, 0), new AngularMomentum(2, 0, 0), new AngularMomentum(0, 1, 2), new AngularMomentum(1, 1, 1)))
        {
            ContractedGaussian function = new ContractedGaussian(new Vec3(0.1, 0.2, -0.3), angular, primitives);
            double[] exact = new double[ContractedGaussian.DERIVATIVES];

            function.derivativesAt(x, y, z, exact);

            double[][] steps = {{h, 0, 0}, {0, h, 0}, {0, 0, h}};
            assertThat(exact[0]).isCloseTo(function.valueAt(x, y, z), within(1e-14));
            for (int a = 0; a < 3; a++)
            {
                double[] s = steps[a];
                double first = (function.valueAt(x + s[0], y + s[1], z + s[2]) - function.valueAt(x - s[0], y - s[1], z - s[2])) / (2 * h);
                double second = (function.valueAt(x + s[0], y + s[1], z + s[2]) - 2 * function.valueAt(x, y, z) + function.valueAt(x - s[0], y - s[1], z - s[2])) / (h * h);
                assertThat(exact[1 + a]).as("first derivative %d of %s", a, angular).isCloseTo(first, within(1e-7));
                assertThat(exact[4 + a]).as("second derivative %d of %s", a, angular).isCloseTo(second, within(1e-5));
            }
            int[][] mixed = {{0, 1}, {0, 2}, {1, 2}};
            for (int m = 0; m < 3; m++)
            {
                double[] p = steps[mixed[m][0]];
                double[] q = steps[mixed[m][1]];
                double cross = (function.valueAt(x + p[0] + q[0], y + p[1] + q[1], z + p[2] + q[2]) - function.valueAt(x + p[0] - q[0], y + p[1] - q[1], z + p[2] - q[2])
                        - function.valueAt(x - p[0] + q[0], y - p[1] + q[1], z - p[2] + q[2]) + function.valueAt(x - p[0] - q[0], y - p[1] - q[1], z - p[2] - q[2])) / (4 * h * h);
                assertThat(exact[7 + m]).as("mixed derivative %d of %s", m, angular).isCloseTo(cross, within(1e-5));
            }
        }
    }

    @Test
    void should_have_no_derivative_of_a_p_function_along_its_own_axis_at_its_centre_except_its_slope()
    {
        ContractedGaussian px = new ContractedGaussian(Vec3.ZERO, new AngularMomentum(1, 0, 0), List.of(new GaussianPrimitive(0.9, 1.0)));
        double[] at = new double[ContractedGaussian.DERIVATIVES];

        px.derivativesAt(0, 0, 0, at);

        assertThat(at[0]).isEqualTo(0.0);
        assertThat(at[1]).isCloseTo(px.coefficient(0), within(1e-14));
        assertThat(at[4]).isEqualTo(0.0);
        assertThat(at[2]).isEqualTo(0.0);
    }

    @Test
    void should_reject_a_derivative_buffer_that_is_too_small()
    {
        ContractedGaussian s = new ContractedGaussian(Vec3.ZERO, new AngularMomentum(0, 0, 0), List.of(new GaussianPrimitive(1.0, 1.0)));

        assertThatThrownBy(() -> s.derivativesAt(0, 0, 0, new double[3])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_a_contraction_that_cancels_to_nothing()
    {
        List<GaussianPrimitive> cancelling = List.of(new GaussianPrimitive(1.0, 1.0), new GaussianPrimitive(1.0, -1.0));

        assertThatThrownBy(() -> new ContractedGaussian(Vec3.ZERO, new AngularMomentum(0, 0, 0), cancelling)).isInstanceOf(IllegalArgumentException.class);
    }
}
