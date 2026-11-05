package com.ruben.chemistry.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BondTest
{
    @Test
    void matches_is_true_for_endpoints_in_declared_order()
    {
        Atom first = Atom.organic("C", false);
        Atom second = Atom.organic("O", false);
        Bond bond = new Bond(first, second, BondOrder.SINGLE);

        assertThat(bond.matches(first, second)).isTrue();
    }

    @Test
    void matches_is_true_for_endpoints_in_reversed_order()
    {
        Atom first = Atom.organic("C", false);
        Atom second = Atom.organic("O", false);
        Bond bond = new Bond(first, second, BondOrder.SINGLE);

        assertThat(bond.matches(second, first)).isTrue();
    }

    @Test
    void matches_is_false_when_an_atom_is_not_part_of_the_bond()
    {
        Atom first = Atom.organic("C", false);
        Atom second = Atom.organic("O", false);
        Atom unrelated = Atom.organic("N", false);
        Bond bond = new Bond(first, second, BondOrder.SINGLE);

        assertThat(bond.matches(first, unrelated)).isFalse();
    }

    @Test
    void matches_distinguishes_value_equal_but_distinct_atom_instances()
    {
        Atom first = Atom.organic("C", false);
        Atom second = Atom.organic("O", false);
        Atom duplicateOfFirst = Atom.organic("C", false);
        Bond bond = new Bond(first, second, BondOrder.SINGLE);

        assertThat(first).isEqualTo(duplicateOfFirst);
        assertThat(bond.matches(duplicateOfFirst, second)).isFalse();
    }

    @Test
    void get_other_returns_second_when_given_first()
    {
        Atom first = Atom.organic("C", false);
        Atom second = Atom.organic("O", false);
        Bond bond = new Bond(first, second, BondOrder.SINGLE);

        assertThat(bond.getOther(first)).isSameAs(second);
    }

    @Test
    void get_other_returns_first_when_given_second()
    {
        Atom first = Atom.organic("C", false);
        Atom second = Atom.organic("O", false);
        Bond bond = new Bond(first, second, BondOrder.SINGLE);

        assertThat(bond.getOther(second)).isSameAs(first);
    }

    @Test
    void get_other_throws_when_the_atom_is_not_part_of_the_bond()
    {
        Atom first = Atom.organic("C", false);
        Atom second = Atom.organic("O", false);
        Atom unrelated = Atom.organic("N", false);
        Bond bond = new Bond(first, second, BondOrder.SINGLE);

        assertThatThrownBy(() -> bond.getOther(unrelated))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void order_is_exposed_as_given()
    {
        Atom first = Atom.organic("C", false);
        Atom second = Atom.organic("O", false);
        Bond bond = new Bond(first, second, BondOrder.DOUBLE);

        assertThat(bond.order()).isEqualTo(BondOrder.DOUBLE);
    }
}
