package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BondPredicatesTest
{
    @Nested
    class Any
    {
        @Test
        void matches_any_bond_regardless_of_order()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.DOUBLE)
                .build();
            Bond bond = molecule.bondBetween(c, o).orElseThrow();

            assertThat(BondPredicates.any().matches(molecule, bond)).isTrue();
        }
    }

    @Nested
    class Order
    {
        @Test
        void matches_bonds_of_exactly_that_order()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.DOUBLE)
                .build();
            Bond doubleBond = molecule.bondBetween(c, o).orElseThrow();

            assertThat(BondPredicates.order(BondOrder.DOUBLE).matches(molecule, doubleBond)).isTrue();
            assertThat(BondPredicates.order(BondOrder.SINGLE).matches(molecule, doubleBond)).isFalse();
        }
    }

    @Nested
    class RingBond
    {
        @Test
        void matches_a_bond_that_lies_on_a_ring()
        {
            // cyclopropane: C1CC1
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom c3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c2, c3, BondOrder.SINGLE)
                .addBond(c3, c1, BondOrder.SINGLE)
                .build();
            Bond ringBond = molecule.bondBetween(c1, c2).orElseThrow();

            assertThat(BondPredicates.ringBond().matches(molecule, ringBond)).isTrue();
        }

        @Test
        void does_not_match_a_bond_connecting_two_separate_rings()
        {
            // C1CC1C1CC1: two cyclopropane rings joined by a single bond
            Atom a1 = Atom.organic("C");
            Atom a2 = Atom.organic("C");
            Atom a3 = Atom.organic("C");
            Atom b1 = Atom.organic("C");
            Atom b2 = Atom.organic("C");
            Atom b3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(a1, a2, a3, b1, b2, b3)
                .addBond(a1, a2, BondOrder.SINGLE)
                .addBond(a2, a3, BondOrder.SINGLE)
                .addBond(a3, a1, BondOrder.SINGLE)
                .addBond(a3, b1, BondOrder.SINGLE)
                .addBond(b1, b2, BondOrder.SINGLE)
                .addBond(b2, b3, BondOrder.SINGLE)
                .addBond(b3, b1, BondOrder.SINGLE)
                .build();
            Bond connecting = molecule.bondBetween(a3, b1).orElseThrow();

            assertThat(BondPredicates.ringBond().matches(molecule, connecting)).isFalse();
        }
    }

    @Nested
    class ChainBond
    {
        @Test
        void matches_a_bond_connecting_two_separate_rings()
        {
            // C1CC1C1CC1: two cyclopropane rings joined by a single bond
            Atom a1 = Atom.organic("C");
            Atom a2 = Atom.organic("C");
            Atom a3 = Atom.organic("C");
            Atom b1 = Atom.organic("C");
            Atom b2 = Atom.organic("C");
            Atom b3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(a1, a2, a3, b1, b2, b3)
                .addBond(a1, a2, BondOrder.SINGLE)
                .addBond(a2, a3, BondOrder.SINGLE)
                .addBond(a3, a1, BondOrder.SINGLE)
                .addBond(a3, b1, BondOrder.SINGLE)
                .addBond(b1, b2, BondOrder.SINGLE)
                .addBond(b2, b3, BondOrder.SINGLE)
                .addBond(b3, b1, BondOrder.SINGLE)
                .build();
            Bond connecting = molecule.bondBetween(a3, b1).orElseThrow();

            assertThat(BondPredicates.chainBond().matches(molecule, connecting)).isTrue();
        }

        @Test
        void does_not_match_a_bond_that_lies_on_a_ring()
        {
            // cyclopropane: C1CC1
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom c3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c2, c3, BondOrder.SINGLE)
                .addBond(c3, c1, BondOrder.SINGLE)
                .build();
            Bond ringBond = molecule.bondBetween(c1, c2).orElseThrow();

            assertThat(BondPredicates.chainBond().matches(molecule, ringBond)).isFalse();
        }
    }

    @Nested
    class Exactly
    {
        @Test
        void matches_a_bond_with_the_same_order()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.DOUBLE)
                .build();
            Bond actual = molecule.bondBetween(c, o).orElseThrow();
            // the template's own endpoints are irrelevant - only its order is compared
            Bond template = new Bond(Atom.organic("N"), Atom.organic("N"), BondOrder.DOUBLE);

            assertThat(BondPredicates.exactly(template).matches(molecule, actual)).isTrue();
        }

        @Test
        void does_not_match_a_bond_with_a_different_order()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.SINGLE)
                .build();
            Bond actual = molecule.bondBetween(c, o).orElseThrow();
            Bond template = new Bond(Atom.organic("N"), Atom.organic("N"), BondOrder.DOUBLE);

            assertThat(BondPredicates.exactly(template).matches(molecule, actual)).isFalse();
        }
    }
}
