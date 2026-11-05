package com.ruben.chemistry.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AtomTest
{
    @Test
    void plain_atom_renders_as_bare_element_symbol()
    {
        Atom atom = Atom.organic("C");

        assertThat(atom.toString()).isEqualTo("C");
    }

    @Test
    void aromatic_atom_renders_in_lower_case()
    {
        Atom atom = Atom.organic("C", true);

        assertThat(atom.toString()).isEqualTo("c");
    }

    @Test
    void charged_atom_is_rendered_in_brackets()
    {
        Atom atom = Atom.builder("O").charge(-1).build();

        assertThat(atom.toString()).isEqualTo("[O-]");
    }

    @Test
    void single_unit_positive_charge_omits_the_magnitude()
    {
        Atom atom = Atom.builder("N").charge(1).build();

        assertThat(atom.toString()).isEqualTo("[N+]");
    }

    @Test
    void single_unit_negative_charge_omits_the_magnitude()
    {
        Atom atom = Atom.builder("N").charge(-1).build();

        assertThat(atom.toString()).isEqualTo("[N-]");
    }

    @Test
    void multi_unit_positive_charge_includes_the_magnitude()
    {
        Atom atom = Atom.builder("Mg").charge(2).build();

        assertThat(atom.toString()).isEqualTo("[Mg+2]");
    }

    @Test
    void multi_unit_negative_charge_includes_the_magnitude()
    {
        Atom atom = Atom.builder("O").charge(-2).build();

        assertThat(atom.toString()).isEqualTo("[O-2]");
    }

    @Test
    void single_hydrogen_count_renders_without_a_number()
    {
        Atom atom = Atom.builder("N").hydrogenCount(1).build();

        assertThat(atom.toString()).isEqualTo("[NH]");
    }

    @Test
    void multiple_hydrogen_count_renders_with_a_number()
    {
        Atom atom = Atom.builder("C").hydrogenCount(3).build();

        assertThat(atom.toString()).isEqualTo("[CH3]");
    }

    @Test
    void zero_hydrogen_count_renders_as_no_hydrogens_at_all()
    {
        Atom atom = Atom.builder("C").hydrogenCount(0).build();

        assertThat(atom.toString()).isEqualTo("[C]");
    }

    @Test
    void isotope_is_rendered_as_a_prefix()
    {
        Atom atom = Atom.builder("C").isotope(13).build();

        assertThat(atom.toString()).isEqualTo("[13C]");
    }

    @Test
    void atom_class_is_rendered_as_a_colon_suffix()
    {
        Atom atom = Atom.builder("C").atomClass(1).build();

        assertThat(atom.toString()).isEqualTo("[C:1]");
    }

    @Test
    void bracket_form_combines_isotope_symbol_hydrogens_charge_and_atom_class_in_order()
    {
        Atom atom = Atom.builder("N").charge(1).isotope(15).hydrogenCount(1).atomClass(2).build();

        assertThat(atom.toString()).isEqualTo("[15NH+:2]");
    }

    @Test
    void ring_closure_tokens_are_appended_verbatim_in_order()
    {
        Atom atom = Atom.organic("C");

        assertThat(atom.toString(List.of("1", "%12"), null)).isEqualTo("C1%12");
    }

    @Test
    void ring_closure_tokens_are_appended_after_bracket_form()
    {
        Atom atom = Atom.builder("O").charge(-1).build();

        assertThat(atom.toString(List.of("1"), null)).isEqualTo("[O-]1");
    }

    @Test
    void chirality_token_is_rendered_immediately_after_the_symbol()
    {
        Atom atom = Atom.builder("C").hydrogenCount(1).build();

        assertThat(atom.toString(List.of(), Chirality.ANTICLOCKWISE)).isEqualTo("[C@H]");
    }

    @Test
    void double_chirality_token_renders_as_at_at()
    {
        Atom atom = Atom.builder("C").hydrogenCount(1).build();

        assertThat(atom.toString(List.of(), Chirality.CLOCKWISE)).isEqualTo("[C@@H]");
    }

    @Test
    void chirality_token_forces_bracket_form_even_without_charge_isotope_hydrogens_or_atom_class()
    {
        Atom atom = Atom.organic("C");

        assertThat(atom.toString(List.of(), Chirality.ANTICLOCKWISE)).isEqualTo("[C@]");
    }

    @Test
    void chirality_token_combines_with_ring_closure_tokens()
    {
        Atom atom = Atom.organic("C");

        assertThat(atom.toString(List.of("1"), Chirality.ANTICLOCKWISE)).isEqualTo("[C@]1");
    }

    @Test
    void null_chirality_token_renders_as_before()
    {
        Atom atom = Atom.organic("C");

        assertThat(atom.toString(List.of(), null)).isEqualTo("C");
    }
}
