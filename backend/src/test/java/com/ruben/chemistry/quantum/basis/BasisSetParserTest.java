package com.ruben.chemistry.quantum.basis;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BasisSetParserTest
{
    private static final String SAMPLE = """
            ! a comment
            H     0
            S    2   1.00
                  5.4471780              0.1562850
                  0.8245470              0.9046910
            S    1   1.00
                  0.1831920              1.0000000
            ****
            O     0
            SP   3   1.00
                  8.519000               -.145510               0.110070
                  2.073000               0.082860               0.349690
                  0.647100               0.743250               0.480930
            SP   1   1.00
                  0.200000               0.284720               0.307270
            ****

            O     0
            O-ECP     1     2
            p potential
              1
            1     16.1171800             -0.9255000
            s-p potential
              2
            0      5.0534800              1.9606900
            2     15.9533300             29.1344200
            """;

    @Test
    void should_read_an_s_shell_with_its_primitives()
    {
        Shell first = BasisSetParser.parse(SAMPLE).get("H").shells().getFirst();

        assertThat(first.angularMomentum()).isEqualTo(0);
        assertThat(first.primitives()).containsExactly(new GaussianPrimitive(5.447178, 0.156285), new GaussianPrimitive(0.824547, 0.904691));
    }

    @Test
    void should_split_a_combined_sp_shell_into_an_s_shell_and_a_p_shell_sharing_exponents()
    {
        ElementBasis oxygen = BasisSetParser.parse(SAMPLE).get("O");

        assertThat(oxygen.shells()).hasSize(4);
        assertThat(oxygen.shells().get(0).angularMomentum()).isEqualTo(0);
        assertThat(oxygen.shells().get(1).angularMomentum()).isEqualTo(1);
        assertThat(oxygen.shells().get(0).primitives().getFirst()).isEqualTo(new GaussianPrimitive(8.519, -0.14551));
        assertThat(oxygen.shells().get(1).primitives().getFirst()).isEqualTo(new GaussianPrimitive(8.519, 0.11007));
        assertThat(oxygen.shells().get(3).primitives()).containsExactly(new GaussianPrimitive(0.2, 0.30727));
    }

    @Test
    void should_read_the_core_potential_with_its_local_and_projector_channels()
    {
        EffectiveCorePotential ecp = BasisSetParser.parse(SAMPLE).get("O").ecp();

        assertThat(ecp.coreElectrons()).isEqualTo(2);
        assertThat(ecp.maxAngularMomentum()).isEqualTo(1);
        assertThat(ecp.local().angularMomentum()).isEqualTo(1);
        assertThat(ecp.local().terms()).containsExactly(new EcpTerm(1, 16.11718, -0.9255));
        assertThat(ecp.projectors()).hasSize(1);
        assertThat(ecp.projectors().get(0).angularMomentum()).isEqualTo(0);
        assertThat(ecp.projectors().get(0).terms()).containsExactly(new EcpTerm(0, 5.05348, 1.96069), new EcpTerm(2, 15.95333, 29.13442));
    }

    @Test
    void should_report_no_core_potential_and_full_charge_for_an_element_without_one()
    {
        ElementBasis hydrogen = BasisSetParser.parse(SAMPLE).get("H");

        assertThat(hydrogen.ecp()).isNull();
        assertThat(hydrogen.coreElectrons()).isZero();
        assertThat(hydrogen.effectiveNuclearCharge()).isEqualTo(1);
    }

    @Test
    void should_subtract_the_core_electrons_to_give_the_effective_nuclear_charge()
    {
        ElementBasis oxygen = BasisSetParser.parse(SAMPLE).get("O");

        assertThat(oxygen.atomicNumber()).isEqualTo(8);
        assertThat(oxygen.effectiveNuclearCharge()).isEqualTo(6);
    }

    @Test
    void should_count_the_basis_functions_an_element_contributes()
    {
        Map<String, ElementBasis> basis = BasisSetParser.parse(SAMPLE);

        assertThat(basis.get("H").functionCount()).isEqualTo(2);
        assertThat(basis.get("O").functionCount()).isEqualTo(8);
    }

    @Test
    void should_standardise_the_capitalisation_of_symbols_and_accept_fortran_exponents()
    {
        String text = """
                NE     0
                S    1   1.00
                      1.0D+00              1.0
                ****
                """;

        assertThat(BasisSetParser.parse(text).get("Ne").shells().getFirst().primitives().getFirst().exponent()).isCloseTo(1.0, within(1e-15));
    }

    @Test
    void should_reject_a_scale_factor_other_than_one_naming_the_line()
    {
        String text = """
                H     0
                S    1   2.00
                      1.0              1.0
                ****
                """;

        assertThatThrownBy(() -> BasisSetParser.parse(text)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line 2");
    }

    @Test
    void should_reject_a_shell_with_too_few_primitive_lines()
    {
        String text = """
                H     0
                S    3   1.00
                      1.0              1.0
                ****
                """;

        assertThatThrownBy(() -> BasisSetParser.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_a_missing_terminator()
    {
        String text = """
                H     0
                S    1   1.00
                      1.0              1.0
                """;

        assertThatThrownBy(() -> BasisSetParser.parse(text)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("****");
    }

    @Test
    void should_reject_an_unknown_element_symbol()
    {
        String text = """
                Xx     0
                S    1   1.00
                      1.0              1.0
                ****
                """;

        assertThatThrownBy(() -> BasisSetParser.parse(text)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Xx");
    }

    @Test
    void should_reject_a_nonpositive_exponent()
    {
        String text = """
                H     0
                S    1   1.00
                      -1.0              1.0
                ****
                """;

        assertThatThrownBy(() -> BasisSetParser.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_a_core_potential_channels_out_of_order()
    {
        String text = SAMPLE.replace("s-p potential", "p-p potential");

        assertThatThrownBy(() -> BasisSetParser.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_a_core_potential_for_an_element_without_shells()
    {
        String text = """
                O     0
                O-ECP     0     2
                s potential
                  1
                2     1.0     1.0
                """;

        assertThatThrownBy(() -> BasisSetParser.parse(text)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no shells");
    }
}
