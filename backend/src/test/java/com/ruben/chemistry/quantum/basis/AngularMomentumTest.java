package com.ruben.chemistry.quantum.basis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AngularMomentumTest
{
    @Test
    void should_sum_the_powers()
    {
        assertThat(new AngularMomentum(2, 1, 0).total()).isEqualTo(3);
    }

    @Test
    void should_list_the_s_shell_as_a_single_component()
    {
        assertThat(AngularMomentum.shell(0)).containsExactly(new AngularMomentum(0, 0, 0));
    }

    @Test
    void should_list_the_p_shell_as_x_y_z()
    {
        assertThat(AngularMomentum.shell(1)).containsExactly(new AngularMomentum(1, 0, 0), new AngularMomentum(0, 1, 0), new AngularMomentum(0, 0, 1));
    }

    @Test
    void should_list_the_d_shell_in_gaussian_order()
    {
        List<AngularMomentum> d = AngularMomentum.shell(2);

        assertThat(d).containsExactly(
                new AngularMomentum(2, 0, 0), new AngularMomentum(1, 1, 0), new AngularMomentum(1, 0, 1),
                new AngularMomentum(0, 2, 0), new AngularMomentum(0, 1, 1), new AngularMomentum(0, 0, 2));
    }
}
