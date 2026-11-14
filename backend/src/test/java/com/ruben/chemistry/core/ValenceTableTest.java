package com.ruben.chemistry.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValenceTableTest
{
    @Test
    void derives_the_second_period_from_the_octet()
    {
        assertThat(ValenceTable.allowedFor("C", 0)).containsExactly(4);
        assertThat(ValenceTable.allowedFor("N", 0)).containsExactly(3);
        assertThat(ValenceTable.allowedFor("O", 0)).containsExactly(2);
        assertThat(ValenceTable.allowedFor("F", 0)).containsExactly(1);
        assertThat(ValenceTable.allowedFor("B", 0)).containsExactly(3);
    }

    @Test
    void follows_a_charge_through_the_octet()
    {
        assertThat(ValenceTable.allowedFor("N", 1)).containsExactly(4);
        assertThat(ValenceTable.allowedFor("N", -1)).containsExactly(2);
        assertThat(ValenceTable.allowedFor("O", 1)).containsExactly(3);
        assertThat(ValenceTable.allowedFor("O", -1)).containsExactly(1);
        assertThat(ValenceTable.allowedFor("C", 1)).containsExactly(3);
        assertThat(ValenceTable.allowedFor("C", -1)).containsExactly(3);
        assertThat(ValenceTable.allowedFor("B", -1)).containsExactly(4);
    }

    @Test
    void expands_beyond_the_octet_from_the_third_period()
    {
        assertThat(ValenceTable.allowedFor("P", 0)).containsExactly(3, 5);
        assertThat(ValenceTable.allowedFor("S", 0)).containsExactly(2, 4, 6);
        assertThat(ValenceTable.allowedFor("Cl", 0)).containsExactly(1, 3, 5, 7);
        assertThat(ValenceTable.allowedFor("Br", 0)).containsExactly(1, 3, 5, 7);
        assertThat(ValenceTable.allowedFor("I", 0)).containsExactly(1, 3, 5, 7);
        assertThat(ValenceTable.allowedFor("Se", 0)).containsExactly(2, 4, 6);
    }

    @Test
    void expands_only_as_far_as_an_atoms_electrons_reach()
    {
        // silicon is six-coordinate only as an anion, which has the two further electrons for it
        assertThat(ValenceTable.allowedFor("Si", 0)).containsExactly(4);
        assertThat(ValenceTable.allowedFor("Si", -2)).contains(6);
        assertThat(ValenceTable.allowedFor("Al", 0)).containsExactly(3);
    }

    @Test
    void holds_no_valences_for_an_element_it_cannot_derive_them_for()
    {
        assertThat(ValenceTable.isKnownFor("Fe")).isFalse();
        assertThat(ValenceTable.isKnownFor("*")).isFalse();
        assertThat(ValenceTable.isKnownFor("Xy")).isFalse();
        assertThat(ValenceTable.allowedFor("Fe", 2)).isEmpty();
        assertThat(ValenceTable.isKnownFor("C")).isTrue();
    }

    @Test
    void holds_no_valences_for_a_charge_the_element_cannot_carry()
    {
        assertThat(ValenceTable.allowedFor("C", 5)).isEmpty();
        assertThat(ValenceTable.allowedFor("O", -3)).isEmpty();
        assertThat(ValenceTable.allowedFor("H", 2)).isEmpty();
    }

    @Test
    void counts_hydrogen_against_its_duet()
    {
        assertThat(ValenceTable.allowedFor("H", 0)).containsExactly(1);
        assertThat(ValenceTable.allowedFor("H", 1)).containsExactly(0);
        assertThat(ValenceTable.allowedFor("H", -1)).containsExactly(0);
    }

    @Test
    void names_the_charge_that_would_account_for_a_valence()
    {
        assertThat(ValenceTable.nearestChargeReaching("N", 4, 0)).hasValue(1);
        assertThat(ValenceTable.nearestChargeReaching("O", 3, 0)).hasValue(1);
        assertThat(ValenceTable.nearestChargeReaching("B", 4, 0)).hasValue(-1);
        assertThat(ValenceTable.nearestChargeReaching("S", 3, 0)).hasValue(1);
        // nothing accounts for a pentavalent nitrogen, at any charge
        assertThat(ValenceTable.nearestChargeReaching("N", 5, 0)).isEmpty();
        assertThat(ValenceTable.nearestChargeReaching("C", 5, 0)).isEmpty();
    }
}
