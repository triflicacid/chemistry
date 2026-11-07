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
    class BondBetween
    {
        @Test
        void returns_the_bond_between_two_directly_bonded_atoms()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.SINGLE)
                .build();

            assertThat(molecule.bondBetween(c, o)).contains(molecule.bondsOf(c).iterator().next());
            assertThat(molecule.bondBetween(o, c)).contains(molecule.bondsOf(c).iterator().next());
        }

        @Test
        void is_empty_for_atoms_that_are_not_directly_bonded()
        {
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom c3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3)
                .addBond(c1, c2, BondOrder.SINGLE)
                .build();

            assertThat(molecule.bondBetween(c1, c3)).isEmpty();
        }

        @Test
        void throws_for_a_first_atom_not_part_of_the_molecule()
        {
            Atom c = Atom.organic("C");
            Atom unrelated = Atom.organic("N");
            Molecule molecule = new Molecule.Builder()
                .addAtom(c)
                .build();

            assertThatThrownBy(() -> molecule.bondBetween(unrelated, c))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RingBonds
    {
        @Test
        void is_empty_for_an_acyclic_molecule()
        {
            // propane: C-C-C
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom c3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c2, c3, BondOrder.SINGLE)
                .build();

            assertThat(molecule.ringBonds()).isEmpty();
        }

        @Test
        void contains_every_bond_of_a_simple_ring()
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

            assertThat(molecule.ringBonds()).hasSize(3);
        }

        @Test
        void excludes_a_bond_connecting_two_separate_rings()
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

            assertThat(molecule.ringBonds()).hasSize(6);
            assertThat(molecule.ringBonds()).noneMatch(bond -> bond == connecting);
        }
    }

    @Nested
    class Rings
    {
        @Test
        void is_empty_for_an_acyclic_molecule()
        {
            // propane: C-C-C
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom c3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c2, c3, BondOrder.SINGLE)
                .build();

            assertThat(molecule.rings()).isEmpty();
            assertThat(molecule.ringCount(c1)).isZero();
            assertThat(molecule.ringSizes(c1)).isEmpty();
        }

        @Test
        void finds_a_simple_rings_size_and_membership()
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

            assertThat(molecule.rings()).hasSize(1);
            assertThat(molecule.ringCount(c1)).isEqualTo(1);
            assertThat(molecule.ringSizes(c1)).containsExactly(3);
        }

        @Test
        void counts_two_rings_for_the_shared_atoms_of_a_fused_bicyclic_system()
        {
            // C1C2CC12: two cyclopropane rings fused on the b-d edge
            Atom a = Atom.organic("C");
            Atom b = Atom.organic("C");
            Atom c = Atom.organic("C");
            Atom d = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(a, b, c, d)
                .addBond(a, b, BondOrder.SINGLE)
                .addBond(b, c, BondOrder.SINGLE)
                .addBond(c, d, BondOrder.SINGLE)
                .addBond(d, a, BondOrder.SINGLE)
                .addBond(d, b, BondOrder.SINGLE)
                .build();

            assertThat(molecule.rings()).hasSize(2);
            assertThat(molecule.ringCount(b)).isEqualTo(2);
            assertThat(molecule.ringCount(d)).isEqualTo(2);
            assertThat(molecule.ringCount(a)).isEqualTo(1);
            assertThat(molecule.ringCount(c)).isEqualTo(1);
            assertThat(molecule.ringSizes(a)).containsExactly(3);
        }

        @Test
        void does_not_double_count_the_perimeter_of_two_fused_rings_as_a_third_ring()
        {
            // C12CCCC1CCCC2: two fused six-membered rings sharing the shared1-shared2 edge:
            // that edge's own shortest cycle is one of the two six-rings, not the
            // ten-membered perimeter, so only two rings should be found overall
            Atom a1 = Atom.organic("C");
            Atom a2 = Atom.organic("C");
            Atom a3 = Atom.organic("C");
            Atom a4 = Atom.organic("C");
            Atom shared1 = Atom.organic("C");
            Atom shared2 = Atom.organic("C");
            Atom b1 = Atom.organic("C");
            Atom b2 = Atom.organic("C");
            Atom b3 = Atom.organic("C");
            Atom b4 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(a1, a2, a3, a4, shared1, shared2, b1, b2, b3, b4)
                .addBond(shared1, a1, BondOrder.SINGLE)
                .addBond(a1, a2, BondOrder.SINGLE)
                .addBond(a2, a3, BondOrder.SINGLE)
                .addBond(a3, a4, BondOrder.SINGLE)
                .addBond(a4, shared2, BondOrder.SINGLE)
                .addBond(shared2, shared1, BondOrder.SINGLE)
                .addBond(shared1, b1, BondOrder.SINGLE)
                .addBond(b1, b2, BondOrder.SINGLE)
                .addBond(b2, b3, BondOrder.SINGLE)
                .addBond(b3, b4, BondOrder.SINGLE)
                .addBond(b4, shared2, BondOrder.SINGLE)
                .build();

            assertThat(molecule.rings()).hasSize(2);
            assertThat(molecule.ringSizes(shared1)).containsExactly(6);
            assertThat(molecule.ringCount(shared1)).isEqualTo(2);
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

                List<NeighbourReference> storedOrder = List.of(
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
                List<NeighbourReference> swappedOrder = List.of(Molecule.IMPLICIT_HYDROGEN, bondToBr, bondToN, bondToCl);

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
                List<NeighbourReference> reversedOrder = List.of(bondToCl, bondToBr, bondToN, Molecule.IMPLICIT_HYDROGEN);

                assertThat(molecule.chiralityOf(c, reversedOrder)).contains(com.ruben.chemistry.core.Chirality.ANTICLOCKWISE);
            }

            @Test
            void is_empty_when_the_atom_is_not_a_stereocentre()
            {
                Molecule molecule = builder().build();
                List<NeighbourReference> order = List.of(bondsOf(molecule, n).get(0));

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

    @Nested
    class DoubleBondStereo
    {
        // F-CA=CB-Cl, each carbon with one other explicit substituent and an implicit hydrogen.
        private final Atom ca = Atom.builder("C").hydrogenCount(1).build();
        private final Atom cb = Atom.builder("C").hydrogenCount(1).build();
        private final Atom f = Atom.organic("F");
        private final Atom cl = Atom.organic("Cl");

        private Molecule.Builder builder()
        {
            return new Molecule.Builder()
                .addAtoms(f, ca, cb, cl)
                .addBond(f, ca, BondOrder.SINGLE)
                .addBond(ca, cb, BondOrder.DOUBLE)
                .addBond(cb, cl, BondOrder.SINGLE);
        }

        private Bond doubleBond(Molecule molecule)
        {
            return bondBetween(molecule, ca, cb);
        }

        private Bond bondBetween(Molecule molecule, Atom atom, Atom other)
        {
            return molecule.bondBetween(atom, other)
                .orElseThrow(() -> new AssertionError("No bond found between " + atom + " and " + other));
        }

        @Nested
        class DoubleBondStereoOfBond
        {
            @Test
            void is_empty_by_default()
            {
                Molecule molecule = builder().build();

                assertThat(molecule.doubleBondStereoOf(doubleBond(molecule))).isEmpty();
            }

            @Test
            void returns_the_declared_configuration()
            {
                Molecule molecule = builder()
                    .setDoubleBondStereo(ca, cb, com.ruben.chemistry.core.DoubleBondStereo.SAME_SIDE)
                    .build();

                assertThat(molecule.doubleBondStereoOf(doubleBond(molecule))).contains(com.ruben.chemistry.core.DoubleBondStereo.SAME_SIDE);
            }

            @Test
            void throws_for_a_bond_not_part_of_the_molecule()
            {
                Molecule molecule = builder().build();
                Atom unrelatedA = Atom.organic("C");
                Atom unrelatedB = Atom.organic("C");
                Bond unrelated = new Molecule.Builder()
                    .addAtoms(unrelatedA, unrelatedB)
                    .addBond(unrelatedA, unrelatedB, BondOrder.DOUBLE)
                    .build()
                    .bondsOf(unrelatedA).iterator().next();

                assertThatThrownBy(() -> molecule.doubleBondStereoOf(unrelated))
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        class DoubleBondStereoOfBondAndReferences
        {
            @Test
            void returns_the_declared_configuration_for_the_default_references()
            {
                Molecule molecule = builder()
                    .setDoubleBondStereo(ca, cb, com.ruben.chemistry.core.DoubleBondStereo.SAME_SIDE)
                    .build();
                Bond bond = doubleBond(molecule);
                Bond bondToF = bondBetween(molecule, ca, f);
                Bond bondToCl = bondBetween(molecule, cb, cl);

                assertThat(molecule.doubleBondStereoOf(bond, bondToF, bondToCl)).contains(com.ruben.chemistry.core.DoubleBondStereo.SAME_SIDE);
            }

            @Test
            void flips_when_the_reference_is_swapped_on_one_side_only()
            {
                Molecule molecule = builder()
                    .setDoubleBondStereo(ca, cb, com.ruben.chemistry.core.DoubleBondStereo.SAME_SIDE)
                    .build();
                Bond bond = doubleBond(molecule);
                Bond bondToCl = bondBetween(molecule, cb, cl);

                // Swap ca's reference from its default (the bond to F) to its implicit hydrogen.
                assertThat(molecule.doubleBondStereoOf(bond, Molecule.IMPLICIT_HYDROGEN, bondToCl))
                    .contains(com.ruben.chemistry.core.DoubleBondStereo.OPPOSITE_SIDE);
            }

            @Test
            void does_not_flip_when_the_reference_is_swapped_on_both_sides()
            {
                Molecule molecule = builder()
                    .setDoubleBondStereo(ca, cb, com.ruben.chemistry.core.DoubleBondStereo.SAME_SIDE)
                    .build();
                Bond bond = doubleBond(molecule);

                assertThat(molecule.doubleBondStereoOf(bond, Molecule.IMPLICIT_HYDROGEN, Molecule.IMPLICIT_HYDROGEN))
                    .contains(com.ruben.chemistry.core.DoubleBondStereo.SAME_SIDE);
            }

            @Test
            void is_empty_when_the_bond_has_no_declared_stereo()
            {
                Molecule molecule = builder().build();
                Bond bond = doubleBond(molecule);

                assertThat(molecule.doubleBondStereoOf(bond, Molecule.IMPLICIT_HYDROGEN, Molecule.IMPLICIT_HYDROGEN)).isEmpty();
            }

            @Test
            void throws_when_a_reference_does_not_identify_a_substituent_of_the_corresponding_atom()
            {
                Molecule molecule = builder()
                    .setDoubleBondStereo(ca, cb, com.ruben.chemistry.core.DoubleBondStereo.SAME_SIDE)
                    .build();
                Bond bond = doubleBond(molecule);
                Bond bondToCl = bondBetween(molecule, cb, cl);
                Bond bondToF = bondBetween(molecule, ca, f);

                // bondToCl is a substituent of cb, not of ca, so it is not a valid reference for ca's side.
                assertThatThrownBy(() -> molecule.doubleBondStereoOf(bond, bondToCl, bondToF))
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Nested
    class TotalHydrogenCount
    {
        @Test
        void returns_a_bracket_atoms_explicit_count_regardless_of_its_bonding()
        {
            // [CH2]C
            Atom explicit = Atom.builder("C").hydrogenCount(2).build();
            Atom neighbour = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(explicit, neighbour)
                .addBond(explicit, neighbour, BondOrder.SINGLE)
                .build();

            assertThat(molecule.totalHydrogenCount(explicit)).isEqualTo(2);
        }

        @Test
        void returns_a_bracket_atoms_explicit_zero_rather_than_inferring_one()
        {
            // [C]: no H written at all means explicitly zero, not "go infer it"
            Atom explicit = Atom.builder("C").hydrogenCount(0).build();
            Molecule molecule = new Molecule.Builder().addAtom(explicit).build();

            assertThat(molecule.totalHydrogenCount(explicit)).isZero();
        }

        @Test
        void infers_methanes_four_hydrogens_from_an_unbonded_organic_carbon()
        {
            // C
            Atom carbon = Atom.organic("C");
            Molecule molecule = new Molecule.Builder().addAtom(carbon).build();

            assertThat(molecule.totalHydrogenCount(carbon)).isEqualTo(4);
        }

        @Test
        void infers_one_hydrogen_each_for_ethanes_two_carbons()
        {
            // CC
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2)
                .addBond(c1, c2, BondOrder.SINGLE)
                .build();

            // each carbon has one other bond, leaving 3 implicit hydrogens
            assertThat(molecule.totalHydrogenCount(c1)).isEqualTo(3);
            assertThat(molecule.totalHydrogenCount(c2)).isEqualTo(3);
        }

        @Test
        void infers_zero_hydrogens_for_a_fully_substituted_carbon()
        {
            // CC(=O)C
            Atom carbonylCarbon = Atom.organic("C");
            Atom left = Atom.organic("C");
            Atom right = Atom.organic("C");
            Atom oxygen = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbonylCarbon, left, right, oxygen)
                .addBond(carbonylCarbon, left, BondOrder.SINGLE)
                .addBond(carbonylCarbon, right, BondOrder.SINGLE)
                .addBond(carbonylCarbon, oxygen, BondOrder.DOUBLE)
                .build();

            assertThat(molecule.totalHydrogenCount(carbonylCarbon)).isZero();
        }

        @Test
        void infers_one_hydrogen_for_the_hydroxyl_oxygen_of_a_carboxylic_acid()
        {
            // C(=O)O
            Atom carbon = Atom.organic("C");
            Atom carbonylOxygen = Atom.organic("O");
            Atom hydroxylOxygen = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbon, carbonylOxygen, hydroxylOxygen)
                .addBond(carbon, carbonylOxygen, BondOrder.DOUBLE)
                .addBond(carbon, hydroxylOxygen, BondOrder.SINGLE)
                .build();

            assertThat(molecule.totalHydrogenCount(carbonylOxygen)).isZero();
            assertThat(molecule.totalHydrogenCount(hydroxylOxygen)).isEqualTo(1);
        }

        @Test
        void infers_one_hydrogen_for_an_unsubstituted_benzene_ring_carbon()
        {
            // c1ccccc1
            Atom[] ring = new Atom[6];
            for (int i = 0; i < ring.length; i++)
            {
                ring[i] = Atom.aromatic("C");
            }
            Molecule.Builder builder = new Molecule.Builder().addAtoms(ring);
            for (int i = 0; i < ring.length; i++)
            {
                builder.addBond(ring[i], ring[(i + 1) % ring.length], BondOrder.AROMATIC);
            }
            Molecule molecule = builder.build();

            assertThat(molecule.totalHydrogenCount(ring[0])).isEqualTo(1);
        }

        @Test
        void infers_zero_hydrogens_for_a_two_connected_aromatic_nitrogen_assuming_pyridine_type()
        {
            // cnc fragment, i.e. the ring nitrogen's local bonding in pyridine (c1ccncc1) -
            // the same local bonding as pyrrole's N-H, which is why real SMILES spells that
            // one out as [nH] rather than leaving it to be inferred
            Atom nitrogen = Atom.aromatic("N");
            Atom left = Atom.aromatic("C");
            Atom right = Atom.aromatic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(nitrogen, left, right)
                .addBond(nitrogen, left, BondOrder.AROMATIC)
                .addBond(nitrogen, right, BondOrder.AROMATIC)
                .build();

            assertThat(molecule.totalHydrogenCount(nitrogen)).isZero();
        }

        @Test
        void infers_zero_hydrogens_for_a_two_connected_aromatic_oxygen_or_sulfur()
        {
            // coc / csc fragment, i.e. the ring heteroatom's local bonding in furan
            // (c1ccoc1) and thiophene (c1ccsc1): both lone-pair heteroatoms with two single
            // Kekule bonds and never a hydrogen, despite the aromatic weighting alone
            // suggesting more bonding than either element's lowest valence allows
            for (String element : List.of("O", "S"))
            {
                Atom heteroatom = Atom.aromatic(element);
                Atom left = Atom.aromatic("C");
                Atom right = Atom.aromatic("C");
                Molecule molecule = new Molecule.Builder()
                    .addAtoms(heteroatom, left, right)
                    .addBond(heteroatom, left, BondOrder.AROMATIC)
                    .addBond(heteroatom, right, BondOrder.AROMATIC)
                    .build();

                assertThat(molecule.totalHydrogenCount(heteroatom)).isZero();
            }
        }

        @Test
        void throws_for_an_atom_not_part_of_the_molecule()
        {
            Atom carbon = Atom.organic("C");
            Molecule molecule = new Molecule.Builder().addAtom(Atom.organic("C")).build();

            assertThatThrownBy(() -> molecule.totalHydrogenCount(carbon))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Valence
    {
        @Test
        void is_four_for_an_unbonded_organic_carbon()
        {
            // C
            Atom carbon = Atom.organic("C");
            Molecule molecule = new Molecule.Builder().addAtom(carbon).build();

            assertThat(molecule.valence(carbon)).isEqualTo(4);
        }

        @Test
        void is_four_for_a_fully_substituted_carbonyl_carbon()
        {
            // CC(=O)C
            Atom carbonylCarbon = Atom.organic("C");
            Atom left = Atom.organic("C");
            Atom right = Atom.organic("C");
            Atom oxygen = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbonylCarbon, left, right, oxygen)
                .addBond(carbonylCarbon, left, BondOrder.SINGLE)
                .addBond(carbonylCarbon, right, BondOrder.SINGLE)
                .addBond(carbonylCarbon, oxygen, BondOrder.DOUBLE)
                .build();

            // 1 (single) + 1 (single) + 2 (double) + 0 (no implicit H left) = 4
            assertThat(molecule.valence(carbonylCarbon)).isEqualTo(4);
        }

        @Test
        void matches_the_chosen_default_valence_for_an_unsubstituted_benzene_ring_carbon()
        {
            // c1ccccc1
            Atom[] ring = new Atom[6];
            for (int i = 0; i < ring.length; i++)
            {
                ring[i] = Atom.aromatic("C");
            }
            Molecule.Builder builder = new Molecule.Builder().addAtoms(ring);
            for (int i = 0; i < ring.length; i++)
            {
                builder.addBond(ring[i], ring[(i + 1) % ring.length], BondOrder.AROMATIC);
            }
            Molecule molecule = builder.build();

            // 2 aromatic bonds (weighted 1.5 each, 3 total) + 1 implicit H = 4, carbon's own valence
            assertThat(molecule.valence(ring[0])).isEqualTo(4);
        }

        @Test
        void exceeds_the_chosen_default_valence_for_a_two_connected_aromatic_sulfur()
        {
            // coc / csc fragment of c1ccsc1: two aromatic bonds = 3, so no implicit hydrogen counted
            Atom sulfur = Atom.aromatic("S");
            Atom left = Atom.aromatic("C");
            Atom right = Atom.aromatic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(sulfur, left, right)
                .addBond(sulfur, left, BondOrder.AROMATIC)
                .addBond(sulfur, right, BondOrder.AROMATIC)
                .build();

            assertThat(molecule.totalHydrogenCount(sulfur)).isZero();
            assertThat(molecule.valence(sulfur)).isEqualTo(3);
        }

        @Test
        void uses_a_bracket_atoms_explicit_hydrogen_count_rather_than_any_expected_valence()
        {
            // [CH2]C: odd value, but use it as it was explicit
            Atom explicit = Atom.builder("C").hydrogenCount(2).build();
            Atom neighbour = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(explicit, neighbour)
                .addBond(explicit, neighbour, BondOrder.SINGLE)
                .build();

            // 1 (single bond) + 2 (explicit H) = 3
            assertThat(molecule.valence(explicit)).isEqualTo(3);
        }

        @Test
        void throws_for_an_atom_not_part_of_the_molecule()
        {
            Atom carbon = Atom.organic("C");
            Molecule molecule = new Molecule.Builder().addAtom(Atom.organic("C")).build();

            assertThatThrownBy(() -> molecule.valence(carbon))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ImplicitHydrogenCount
    {
        @Test
        void is_zero_for_a_bracket_atoms_explicit_count()
        {
            // [CH2]C
            Atom explicit = Atom.builder("C").hydrogenCount(2).build();
            Atom neighbour = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(explicit, neighbour)
                .addBond(explicit, neighbour, BondOrder.SINGLE)
                .build();

            assertThat(molecule.implicitHydrogenCount(explicit)).isZero();
        }

        @Test
        void is_zero_for_a_bracket_atoms_explicit_zero()
        {
            // [C]
            Atom explicit = Atom.builder("C").hydrogenCount(0).build();
            Molecule molecule = new Molecule.Builder().addAtom(explicit).build();

            assertThat(molecule.implicitHydrogenCount(explicit)).isZero();
        }

        @Test
        void equals_the_total_hydrogen_count_for_an_organic_subset_atom()
        {
            // CC: each carbon's 3 hydrogens are entirely inferred
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2)
                .addBond(c1, c2, BondOrder.SINGLE)
                .build();

            assertThat(molecule.implicitHydrogenCount(c1)).isEqualTo(3);
            assertThat(molecule.totalHydrogenCount(c1)).isEqualTo(3);
        }

        @Test
        void throws_for_an_atom_not_part_of_the_molecule()
        {
            Atom carbon = Atom.organic("C");
            Molecule molecule = new Molecule.Builder().addAtom(Atom.organic("C")).build();

            assertThatThrownBy(() -> molecule.implicitHydrogenCount(carbon))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
