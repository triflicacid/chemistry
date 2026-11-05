package com.ruben.chemistry.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoleculeTest
{
    @Nested
    class BondsOf
    {
        @Test
        void returns_the_single_bond_incident_to_an_atom()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.SINGLE)
                .build();

            assertThat(molecule.bondsOf(c)).containsExactly(molecule.bondsOf(o).iterator().next());
        }

        @Test
        void returns_all_bonds_incident_to_an_atom_regardless_of_declared_order()
        {
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom c3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c3, c1, BondOrder.DOUBLE)
                .build();

            assertThat(molecule.bondsOf(c1))
                .extracting(bond -> bond.getOther(c1))
                .containsExactlyInAnyOrder(c2, c3);
        }

        @Test
        void returns_an_empty_iterable_for_an_unbonded_atom()
        {
            Atom c = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtom(c)
                .build();

            assertThat(molecule.bondsOf(c)).isEmpty();
        }

        @Test
        void throws_for_an_atom_not_part_of_the_molecule()
        {
            Atom c = Atom.organic("C");
            Atom unrelated = Atom.organic("N");
            Molecule molecule = new Molecule.Builder()
                .addAtom(c)
                .build();

            assertThatThrownBy(() -> molecule.bondsOf(unrelated))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class NeighboursOf
    {
        @Test
        void returns_the_atom_bonded_at_the_other_end_of_each_bond()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.SINGLE)
                .build();

            assertThat(molecule.neighboursOf(c)).containsExactly(o);
            assertThat(molecule.neighboursOf(o)).containsExactly(c);
        }

        @Test
        void returns_every_directly_bonded_atom()
        {
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom c3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c3, c1, BondOrder.DOUBLE)
                .build();

            assertThat(molecule.neighboursOf(c1)).containsExactlyInAnyOrder(c2, c3);
        }

        @Test
        void returns_an_empty_iterable_for_an_unbonded_atom()
        {
            Atom c = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtom(c)
                .build();

            assertThat(molecule.neighboursOf(c)).isEmpty();
        }

        @Test
        void throws_for_an_atom_not_part_of_the_molecule()
        {
            Atom c = Atom.organic("C");
            Atom unrelated = Atom.organic("N");
            Molecule molecule = new Molecule.Builder()
                .addAtom(c)
                .build();

            assertThatThrownBy(() -> molecule.neighboursOf(unrelated))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
