package com.ruben.chemistry.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoleculeFragmenterTest
{
    @Nested
    class Fragments
    {
        @Test
        void returns_nothing_for_a_molecule_with_no_atoms()
        {
            assertThat(new Molecule.Builder().build().fragments()).isEmpty();
        }

        @Test
        void returns_the_molecule_itself_when_it_is_already_connected()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.SINGLE)
                .build();

            assertThat(molecule.fragments()).containsExactly(molecule);
        }

        @Test
        void returns_one_fragment_per_unbonded_atom()
        {
            Atom first = Atom.organic("O");
            Atom second = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(first, second)
                .build();

            assertThat(molecule.fragments())
                .extracting(Molecule::atoms)
                .containsExactly(List.of(first), List.of(second));
        }

        @Test
        void separates_a_salt_from_its_waters_of_crystallisation()
        {
            Atom copper = Atom.organic("Cu");
            Atom sulfur = Atom.organic("S");
            Atom oxygen = Atom.organic("O");
            Atom water = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(copper, sulfur, oxygen, water)
                .addBond(sulfur, oxygen, BondOrder.SINGLE)
                .build();

            List<Molecule> fragments = molecule.fragments();

            assertThat(fragments).hasSize(3);
            assertThat(fragments.get(0).atoms()).containsExactly(copper);
            assertThat(fragments.get(1).atoms()).containsExactly(sulfur, oxygen);
            assertThat(fragments.get(2).atoms()).containsExactly(water);
        }

        @Test
        void gives_each_fragment_only_its_own_bonds()
        {
            Atom first = Atom.organic("C");
            Atom second = Atom.organic("O");
            Atom third = Atom.organic("N");
            Atom fourth = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(first, second, third, fourth)
                .addBond(first, second, BondOrder.SINGLE)
                .addBond(third, fourth, BondOrder.TRIPLE)
                .build();

            List<Molecule> fragments = molecule.fragments();

            assertThat(fragments.get(0).bonds()).containsExactly(molecule.bonds().get(0));
            assertThat(fragments.get(1).bonds()).containsExactly(molecule.bonds().get(1));
        }

        @Test
        void orders_fragments_by_where_their_first_atom_appears()
        {
            Atom lone = Atom.organic("O");
            Atom c = Atom.organic("C");
            Atom n = Atom.organic("N");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, lone, n)
                // bonding the first atom to the last leaves the lone atom's fragment in the middle
                .addBond(c, n, BondOrder.SINGLE)
                .build();

            assertThat(molecule.fragments())
                .extracting(Molecule::atoms)
                .containsExactly(List.of(c, n), List.of(lone));
        }

        @Test
        void counts_implicit_hydrogens_within_the_fragment_as_the_molecule_does()
        {
            Atom first = Atom.organic("O");
            Atom second = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(first, second)
                .build();

            Molecule fragment = molecule.fragments().getFirst();

            assertThat(fragment.implicitHydrogenCount(first)).isEqualTo(molecule.implicitHydrogenCount(first));
        }
    }

    @Nested
    class Stereochemistry
    {
        @Test
        void carries_declared_chirality_over_to_the_fragment()
        {
            Atom centre = Atom.organic("C");
            Atom f = Atom.organic("F");
            Atom cl = Atom.organic("Cl");
            Atom br = Atom.organic("Br");
            Atom i = Atom.organic("I");
            Atom lone = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(centre, f, cl, br, i, lone)
                .addBond(centre, f, BondOrder.SINGLE)
                .addBond(centre, cl, BondOrder.SINGLE)
                .addBond(centre, br, BondOrder.SINGLE)
                .addBond(centre, i, BondOrder.SINGLE)
                .setChirality(centre, Chirality.CLOCKWISE)
                .build();

            Molecule fragment = molecule.fragments().getFirst();

            assertThat(fragment.chiralityOf(centre)).contains(Chirality.CLOCKWISE);
        }

        @Test
        void carries_declared_double_bond_stereo_over_to_the_fragment()
        {
            Atom left = Atom.organic("C");
            Atom right = Atom.organic("C");
            Atom f = Atom.organic("F");
            Atom cl = Atom.organic("Cl");
            Atom lone = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(left, right, f, cl, lone)
                .addBond(left, right, BondOrder.DOUBLE)
                .addBond(left, f, BondOrder.SINGLE)
                .addBond(right, cl, BondOrder.SINGLE)
                .setDoubleBondStereo(left, right, DoubleBondStereo.OPPOSITE_SIDE)
                .build();

            Molecule fragment = molecule.fragments().getFirst();

            assertThat(fragment.doubleBondStereoOf(fragment.bonds().getFirst()))
                .contains(DoubleBondStereo.OPPOSITE_SIDE);
        }
    }
}
