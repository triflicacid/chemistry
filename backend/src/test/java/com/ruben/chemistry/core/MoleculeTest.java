package com.ruben.chemistry.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
    }

    @Nested
    class Chirality
    {
        // Stereocentre with one implicit hydrogen and three explicit substituents, bonded in this order.
        private final Atom c = Atom.builder("C").hydrogenCount(1).build();
        private final Atom n = Atom.organic("N");
        private final Atom br = Atom.organic("Br");
        private final Atom cl = Atom.organic("Cl");

        private Molecule.Builder builder()
        {
            return new Molecule.Builder()
                .addAtoms(c, n, br, cl)
                .addBond(c, n, BondOrder.SINGLE)
                .addBond(c, br, BondOrder.SINGLE)
                .addBond(c, cl, BondOrder.SINGLE);
        }

        private List<Bond> bondsOf(Molecule molecule, Atom atom)
        {
            List<Bond> bonds = new ArrayList<>();
            molecule.bondsOf(atom).forEach(bonds::add);
            return bonds;
        }

        @Nested
        class ChiralityOfAtom
        {
            @Test
            void is_empty_by_default()
            {
                Molecule molecule = builder().build();

                assertThat(molecule.chiralityOf(c)).isEmpty();
            }

            @Test
            void returns_the_declared_chirality()
            {
                Molecule molecule = builder()
                    .setChirality(c, com.ruben.chemistry.core.Chirality.ANTICLOCKWISE)
                    .build();

                assertThat(molecule.chiralityOf(c)).contains(com.ruben.chemistry.core.Chirality.ANTICLOCKWISE);
            }

            @Test
            void distinguishes_between_different_atoms()
            {
                Molecule molecule = builder()
                    .setChirality(c, com.ruben.chemistry.core.Chirality.ANTICLOCKWISE)
                    .build();

                assertThat(molecule.chiralityOf(n)).isEmpty();
            }

            @Test
            void uses_the_latest_declaration_for_an_atom()
            {
                Molecule molecule = builder()
                    .setChirality(c, com.ruben.chemistry.core.Chirality.ANTICLOCKWISE)
                    .setChirality(c, com.ruben.chemistry.core.Chirality.CLOCKWISE)
                    .build();

                assertThat(molecule.chiralityOf(c)).contains(com.ruben.chemistry.core.Chirality.CLOCKWISE);
            }

            @Test
            void is_preserved_by_a_to_builder_round_trip()
            {
                Molecule molecule = builder()
                    .setChirality(c, com.ruben.chemistry.core.Chirality.ANTICLOCKWISE)
                    .build();

                Molecule copy = molecule.toBuilder().build();

                assertThat(copy.chiralityOf(c)).contains(com.ruben.chemistry.core.Chirality.ANTICLOCKWISE);
            }

            @Test
            void throws_for_an_atom_not_part_of_the_molecule()
            {
                Molecule molecule = builder().build();
                Atom unrelated = Atom.organic("O");

                assertThatThrownBy(() -> molecule.chiralityOf(unrelated))
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        class ChiralityOfAtomAndOrder
        {
            @Test
            void returns_the_declared_chirality_unchanged_for_the_stored_order()
            {
                Molecule molecule = builder()
                    .setChirality(c, com.ruben.chemistry.core.Chirality.ANTICLOCKWISE)
                    .build();
                List<Bond> bondsOfC = bondsOf(molecule, c);

                List<Object> storedOrder = List.of(
                    Molecule.IMPLICIT_HYDROGEN, bondsOfC.get(0), bondsOfC.get(1), bondsOfC.get(2));

                assertThat(molecule.chiralityOf(c, storedOrder)).contains(com.ruben.chemistry.core.Chirality.ANTICLOCKWISE);
            }

            @Test
            void flips_the_declared_chirality_for_an_odd_permutation_of_the_stored_order()
            {
                Molecule molecule = builder()
                    .setChirality(c, com.ruben.chemistry.core.Chirality.ANTICLOCKWISE)
                    .build();
                List<Bond> bondsOfC = bondsOf(molecule, c);
                Bond bondToN = bondsOfC.get(0);
                Bond bondToBr = bondsOfC.get(1);
                Bond bondToCl = bondsOfC.get(2);

                // Single transposition (swap N and Br) relative to the stored order: H, N, Br, Cl.
                List<Object> swappedOrder = List.of(Molecule.IMPLICIT_HYDROGEN, bondToBr, bondToN, bondToCl);

                assertThat(molecule.chiralityOf(c, swappedOrder)).contains(com.ruben.chemistry.core.Chirality.CLOCKWISE);
            }

            @Test
            void does_not_flip_the_declared_chirality_for_an_even_permutation_of_the_stored_order()
            {
                Molecule molecule = builder()
                    .setChirality(c, com.ruben.chemistry.core.Chirality.ANTICLOCKWISE)
                    .build();
                List<Bond> bondsOfC = bondsOf(molecule, c);
                Bond bondToN = bondsOfC.get(0);
                Bond bondToBr = bondsOfC.get(1);
                Bond bondToCl = bondsOfC.get(2);

                // Full reversal of the stored order (H, N, Br, Cl) is two transpositions, so it's even.
                List<Object> reversedOrder = List.of(bondToCl, bondToBr, bondToN, Molecule.IMPLICIT_HYDROGEN);

                assertThat(molecule.chiralityOf(c, reversedOrder)).contains(com.ruben.chemistry.core.Chirality.ANTICLOCKWISE);
            }

            @Test
            void is_empty_when_the_atom_is_not_a_stereocentre()
            {
                Molecule molecule = builder().build();
                List<Object> order = List.of(bondsOf(molecule, n).get(0));

                assertThat(molecule.chiralityOf(n, order)).isEmpty();
            }

            @Test
            void throws_for_an_atom_not_part_of_the_molecule()
            {
                Molecule molecule = builder().build();
                Atom unrelated = Atom.organic("O");

                assertThatThrownBy(() -> molecule.chiralityOf(unrelated, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            void throws_when_the_given_order_does_not_match_the_stored_neighbour_count()
            {
                Molecule molecule = builder()
                    .setChirality(c, com.ruben.chemistry.core.Chirality.ANTICLOCKWISE)
                    .build();

                assertThatThrownBy(() -> molecule.chiralityOf(c, List.of(Molecule.IMPLICIT_HYDROGEN)))
                    .isInstanceOf(IllegalStateException.class);
            }
        }
    }
}
