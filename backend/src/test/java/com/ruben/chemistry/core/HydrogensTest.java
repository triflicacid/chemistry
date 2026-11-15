package com.ruben.chemistry.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HydrogensTest
{
    @Nested
    class AsAtoms
    {
        @Test
        void returns_the_same_molecule_when_nothing_counts_a_hydrogen()
        {
            Atom first = Atom.organic("Cl");
            Atom second = Atom.organic("Cl");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(first, second)
                .addBond(first, second, BondOrder.SINGLE)
                .build();

            assertThat(Hydrogens.asAtoms(molecule)).isSameAs(molecule);
        }

        @Test
        void leaves_a_hydrogen_that_was_already_an_atom_alone()
        {
            Atom carbon = Atom.builder("C").hydrogenCount(0).build();
            Atom hydrogen = Atom.organic("H");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbon, hydrogen)
                .addBond(carbon, hydrogen, BondOrder.SINGLE)
                .build();

            assertThat(Hydrogens.asAtoms(molecule)).isSameAs(molecule);
        }

        @Test
        void gives_every_counted_hydrogen_an_atom_of_its_own()
        {
            Atom carbon = Atom.organic("C");
            Molecule methane = new Molecule.Builder().addAtom(carbon).build();

            Molecule atoms = Hydrogens.asAtoms(methane);

            assertThat(atoms.atoms()).hasSize(5);
            assertThat(atoms.bonds()).hasSize(4);
            assertThat(atoms.atoms().subList(1, 5))
                .extracting(Atom::element)
                .containsOnly("H");
        }

        @Test
        void counts_no_hydrogens_on_an_owner_afterwards()
        {
            Atom carbon = Atom.organic("C");
            Molecule atoms = Hydrogens.asAtoms(new Molecule.Builder().addAtom(carbon).build());

            Atom owner = atoms.atoms().getFirst();

            assertThat(atoms.totalHydrogenCount(owner)).isZero();
            assertThat(owner.hydrogenCount()).hasValue(0);
        }

        @Test
        void keeps_an_atom_that_counted_no_hydrogens_by_identity()
        {
            Atom carbon = Atom.organic("C");
            Atom chlorine = Atom.organic("Cl");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbon, chlorine)
                .addBond(carbon, chlorine, BondOrder.SINGLE)
                .build();

            Molecule atoms = Hydrogens.asAtoms(molecule);

            assertThat(atoms.atoms().get(1)).isSameAs(chlorine);
            assertThat(atoms.atoms().getFirst()).isNotSameAs(carbon);
        }

        @Test
        void lists_the_new_hydrogens_after_every_original_atom()
        {
            Atom carbon = Atom.organic("C");
            Atom oxygen = Atom.organic("O");
            Molecule methanol = new Molecule.Builder()
                .addAtoms(carbon, oxygen)
                .addBond(carbon, oxygen, BondOrder.SINGLE)
                .build();

            Molecule atoms = Hydrogens.asAtoms(methanol);

            assertThat(atoms.atoms()).hasSize(6);
            assertThat(atoms.atoms().subList(0, 2))
                .extracting(Atom::element)
                .containsExactly("C", "O");
            assertThat(atoms.atoms().subList(2, 6))
                .extracting(Atom::element)
                .containsOnly("H");
        }

        @Test
        void groups_the_new_hydrogens_by_the_atom_they_hang_off()
        {
            Atom carbon = Atom.organic("C");
            Atom oxygen = Atom.organic("O");
            Molecule methanol = new Molecule.Builder()
                .addAtoms(carbon, oxygen)
                .addBond(carbon, oxygen, BondOrder.SINGLE)
                .build();

            Molecule atoms = Hydrogens.asAtoms(methanol);
            Atom owningCarbon = atoms.atoms().getFirst();
            Atom owningOxygen = atoms.atoms().get(1);

            assertThat(atoms.atoms().subList(2, 5))
                .allSatisfy(hydrogen -> assertThat(atoms.neighboursOf(hydrogen).getFirst()).isSameAs(owningCarbon));
            assertThat(atoms.neighboursOf(atoms.atoms().get(5)).getFirst()).isSameAs(owningOxygen);
        }

        @Test
        void labels_a_hydrogen_that_is_not_protium_with_its_mass_number()
        {
            Atom carbon = Atom.builder("C").hydrogenCount(2).hydrogenIsotope(2, 1).build();
            Molecule molecule = new Molecule.Builder().addAtom(carbon).build();

            Molecule atoms = Hydrogens.asAtoms(molecule);

            // the counts are split by mass number ascending, so protium leads
            assertThat(atoms.atoms().get(1).isotope()).isEmpty();
            assertThat(atoms.atoms().get(2).isotope()).hasValue(2);
        }

        @Nested
        class Stereo
        {
            // Stereocentre counting one hydrogen, bonded to three substituents in this order.
            private final Atom centre = Atom.builder("C").hydrogenCount(1).build();
            private final Atom br = Atom.organic("Br");
            private final Atom cl = Atom.organic("Cl");
            private final Atom f = Atom.organic("F");

            private Molecule declaring(Chirality chirality)
            {
                return new Molecule.Builder()
                    .addAtoms(centre, br, cl, f)
                    .addBond(centre, br, BondOrder.SINGLE)
                    .addBond(centre, cl, BondOrder.SINGLE)
                    .addBond(centre, f, BondOrder.SINGLE)
                    .setChirality(centre, chirality)
                    .build();
            }

            @Test
            void preserves_the_parity_of_a_stereocentre_whose_hydrogen_becomes_an_atom()
            {
                Molecule molecule = declaring(Chirality.ANTICLOCKWISE);

                Molecule atoms = Hydrogens.asAtoms(molecule);
                Atom moved = atoms.atoms().getFirst();

                assertThat(atoms.chiralityOf(moved, hydrogenLeading(atoms, moved))).contains(Chirality.ANTICLOCKWISE);
            }

            @Test
            void preserves_the_parity_of_the_opposite_stereocentre_too()
            {
                Molecule molecule = declaring(Chirality.CLOCKWISE);

                Molecule atoms = Hydrogens.asAtoms(molecule);
                Atom moved = atoms.atoms().getFirst();

                assertThat(atoms.chiralityOf(moved, hydrogenLeading(atoms, moved))).contains(Chirality.CLOCKWISE);
            }

            @Test
            void flips_the_stored_parity_where_the_hydrogen_crosses_an_odd_number_of_bonds()
            {
                Molecule molecule = declaring(Chirality.ANTICLOCKWISE);

                Molecule atoms = Hydrogens.asAtoms(molecule);

                // the centre has three bonds, so its hydrogen crosses three of them to reach
                // the end of the order: an odd permutation, which turns the parity over
                assertThat(atoms.chiralityOf(atoms.atoms().getFirst())).contains(Chirality.CLOCKWISE);
            }

            @Test
            void leaves_the_parity_of_a_stereocentre_counting_no_hydrogen_alone()
            {
                Atom iodine = Atom.organic("I");
                Atom quaternary = Atom.builder("C").hydrogenCount(0).build();
                Molecule molecule = new Molecule.Builder()
                    .addAtoms(quaternary, br, cl, f, iodine)
                    .addBond(quaternary, br, BondOrder.SINGLE)
                    .addBond(quaternary, cl, BondOrder.SINGLE)
                    .addBond(quaternary, f, BondOrder.SINGLE)
                    .addBond(quaternary, iodine, BondOrder.SINGLE)
                    .setChirality(quaternary, Chirality.ANTICLOCKWISE)
                    .build();

                Molecule atoms = Hydrogens.asAtoms(molecule);

                assertThat(atoms.chiralityOf(quaternary)).contains(Chirality.ANTICLOCKWISE);
            }

            @Test
            void throws_for_a_stereocentre_counting_more_than_one_hydrogen()
            {
                Atom twoHydrogens = Atom.builder("C").hydrogenCount(2).build();
                Molecule molecule = new Molecule.Builder()
                    .addAtoms(twoHydrogens, br, cl)
                    .addBond(twoHydrogens, br, BondOrder.SINGLE)
                    .addBond(twoHydrogens, cl, BondOrder.SINGLE)
                    .setChirality(twoHydrogens, Chirality.ANTICLOCKWISE)
                    .build();

                assertThatThrownBy(() -> Hydrogens.asAtoms(molecule))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2 counted hydrogens");
            }

            @Test
            void preserves_the_configuration_of_a_double_bond()
            {
                Atom ca = Atom.builder("C").hydrogenCount(1).build();
                Atom cb = Atom.builder("C").hydrogenCount(1).build();
                Molecule molecule = new Molecule.Builder()
                    .addAtoms(f, ca, cb, cl)
                    .addBond(f, ca, BondOrder.SINGLE)
                    .addBond(ca, cb, BondOrder.DOUBLE)
                    .addBond(cb, cl, BondOrder.SINGLE)
                    .setDoubleBondStereo(ca, cb, DoubleBondStereo.SAME_SIDE)
                    .build();

                Molecule atoms = Hydrogens.asAtoms(molecule);
                Bond rewritten = bondBetween(atoms, atoms.atoms().get(1), atoms.atoms().get(2));

                assertThat(atoms.doubleBondStereoOf(rewritten)).contains(DoubleBondStereo.SAME_SIDE);
            }
        }
    }

    @Nested
    class AsCounts
    {
        @Test
        void returns_the_same_molecule_when_no_hydrogen_is_an_atom()
        {
            Atom carbon = Atom.organic("C");
            Atom oxygen = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbon, oxygen)
                .addBond(carbon, oxygen, BondOrder.SINGLE)
                .build();

            assertThat(Hydrogens.asCounts(molecule)).isSameAs(molecule);
        }

        @Test
        void counts_a_bonded_hydrogen_on_the_atom_it_hangs_off()
        {
            Atom oxygen = Atom.builder("O").hydrogenCount(0).build();
            Atom first = Atom.organic("H");
            Atom second = Atom.organic("H");
            Molecule water = new Molecule.Builder()
                .addAtoms(oxygen, first, second)
                .addBond(oxygen, first, BondOrder.SINGLE)
                .addBond(oxygen, second, BondOrder.SINGLE)
                .build();

            Molecule counts = Hydrogens.asCounts(water);

            assertThat(counts.atoms()).hasSize(1);
            assertThat(counts.bonds()).isEmpty();
            assertThat(counts.totalHydrogenCount(counts.atoms().getFirst())).isEqualTo(2);
        }

        @Test
        void leaves_the_count_to_the_valence_where_writing_it_would_say_nothing()
        {
            Atom oxygen = Atom.builder("O").hydrogenCount(0).build();
            Atom first = Atom.organic("H");
            Atom second = Atom.organic("H");
            Molecule water = new Molecule.Builder()
                .addAtoms(oxygen, first, second)
                .addBond(oxygen, first, BondOrder.SINGLE)
                .addBond(oxygen, second, BondOrder.SINGLE)
                .build();

            Molecule counts = Hydrogens.asCounts(water);

            assertThat(counts.atoms().getFirst().hydrogenCount()).isEmpty();
        }

        @Test
        void writes_the_count_out_where_the_valence_would_infer_another()
        {
            Atom nitrogen = Atom.builder("N").hydrogenCount(0).build();
            Atom hydrogen = Atom.organic("H");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(nitrogen, hydrogen)
                .addBond(nitrogen, hydrogen, BondOrder.SINGLE)
                .build();

            Molecule counts = Hydrogens.asCounts(molecule);

            // a lone nitrogen with one hydrogen would otherwise be inferred to carry three
            assertThat(counts.atoms().getFirst().hydrogenCount()).hasValue(1);
        }

        @Test
        void writes_the_count_out_for_an_atom_carrying_a_charge()
        {
            Atom nitrogen = Atom.builder("N").charge(1).hydrogenCount(0).build();
            Atom hydrogen = Atom.organic("H");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(nitrogen, hydrogen)
                .addBond(nitrogen, hydrogen, BondOrder.SINGLE)
                .build();

            Molecule counts = Hydrogens.asCounts(molecule);

            assertThat(counts.atoms().getFirst().hydrogenCount()).hasValue(1);
        }

        @Test
        void keeps_a_labelled_hydrogen_as_an_atom()
        {
            Atom carbon = Atom.builder("C").hydrogenCount(0).build();
            Atom deuterium = Atom.builder("H").isotope(2).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbon, deuterium)
                .addBond(carbon, deuterium, BondOrder.SINGLE)
                .build();

            assertThat(Hydrogens.asCounts(molecule)).isSameAs(molecule);
        }

        @Test
        void counts_the_plain_hydrogens_of_an_atom_that_also_holds_a_labelled_one()
        {
            Atom carbon = Atom.builder("C").hydrogenCount(0).build();
            Atom deuterium = Atom.builder("H").isotope(2).build();
            Atom hydrogen = Atom.organic("H");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbon, deuterium, hydrogen)
                .addBond(carbon, deuterium, BondOrder.SINGLE)
                .addBond(carbon, hydrogen, BondOrder.SINGLE)
                .build();

            Molecule counts = Hydrogens.asCounts(molecule);

            assertThat(counts.atoms())
                .extracting(Atom::element)
                .containsExactly("C", "H");
            assertThat(counts.totalHydrogenCount(counts.atoms().getFirst())).isEqualTo(1);
        }

        @Test
        void keeps_a_hydride_as_an_atom()
        {
            Atom boron = Atom.builder("B").hydrogenCount(0).build();
            Atom hydride = Atom.builder("H").charge(-1).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(boron, hydride)
                .addBond(boron, hydride, BondOrder.SINGLE)
                .build();

            assertThat(Hydrogens.asCounts(molecule)).isSameAs(molecule);
        }

        @Test
        void keeps_both_halves_of_dihydrogen_as_atoms()
        {
            Atom first = Atom.builder("H").hydrogenCount(0).build();
            Atom second = Atom.builder("H").hydrogenCount(0).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(first, second)
                .addBond(first, second, BondOrder.SINGLE)
                .build();

            assertThat(Hydrogens.asCounts(molecule)).isSameAs(molecule);
        }

        @Test
        void keeps_a_bridging_hydrogen_as_an_atom()
        {
            Atom first = Atom.builder("B").hydrogenCount(0).build();
            Atom second = Atom.builder("B").hydrogenCount(0).build();
            Atom bridge = Atom.builder("H").hydrogenCount(0).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(first, second, bridge)
                .addBond(first, bridge, BondOrder.SINGLE)
                .addBond(bridge, second, BondOrder.SINGLE)
                .build();

            assertThat(Hydrogens.asCounts(molecule)).isSameAs(molecule);
        }

        @Test
        void keeps_a_hydrogen_held_by_more_than_a_single_bond_as_an_atom()
        {
            Atom carbon = Atom.builder("C").hydrogenCount(0).build();
            Atom hydrogen = Atom.builder("H").hydrogenCount(0).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbon, hydrogen)
                .addBond(carbon, hydrogen, BondOrder.DOUBLE)
                .build();

            assertThat(Hydrogens.asCounts(molecule)).isSameAs(molecule);
        }

        @Test
        void keeps_an_unbonded_hydrogen_as_an_atom()
        {
            Atom hydrogen = Atom.builder("H").hydrogenCount(0).build();
            Molecule molecule = new Molecule.Builder().addAtom(hydrogen).build();

            assertThat(Hydrogens.asCounts(molecule)).isSameAs(molecule);
        }

        @Nested
        class Stereo
        {
            @Test
            void preserves_the_parity_of_a_stereocentre_whose_hydrogen_becomes_a_count()
            {
                Atom centre = Atom.builder("C").hydrogenCount(0).build();
                Atom br = Atom.organic("Br");
                Atom cl = Atom.organic("Cl");
                Atom f = Atom.organic("F");
                Atom hydrogen = Atom.organic("H");
                Molecule molecule = new Molecule.Builder()
                    .addAtoms(centre, br, cl, f, hydrogen)
                    .addBond(centre, br, BondOrder.SINGLE)
                    .addBond(centre, cl, BondOrder.SINGLE)
                    .addBond(centre, f, BondOrder.SINGLE)
                    .addBond(centre, hydrogen, BondOrder.SINGLE)
                    .setChirality(centre, Chirality.ANTICLOCKWISE)
                    .build();
                Chirality before = molecule.chiralityOf(centre).orElseThrow();

                Molecule counts = Hydrogens.asCounts(molecule);
                Atom moved = counts.atoms().getFirst();

                assertThat(counts.chiralityOf(moved)).contains(before.flip());
                assertThat(molecule.chiralityOf(centre, hydrogenLeading(molecule, centre))).contains(counts.chiralityOf(moved).orElseThrow());
            }

            @Test
            void reads_where_the_hydrogen_was_bonded_rather_than_assuming_it_was_last()
            {
                Atom centre = Atom.builder("C").hydrogenCount(0).build();
                Atom br = Atom.organic("Br");
                Atom cl = Atom.organic("Cl");
                Atom f = Atom.organic("F");
                Atom hydrogen = Atom.organic("H");

                // the hydrogen leads, so it crosses nothing on its way to the slot
                Molecule molecule = new Molecule.Builder()
                    .addAtoms(centre, hydrogen, br, cl, f)
                    .addBond(centre, hydrogen, BondOrder.SINGLE)
                    .addBond(centre, br, BondOrder.SINGLE)
                    .addBond(centre, cl, BondOrder.SINGLE)
                    .addBond(centre, f, BondOrder.SINGLE)
                    .setChirality(centre, Chirality.ANTICLOCKWISE)
                    .build();

                Molecule counts = Hydrogens.asCounts(molecule);

                assertThat(counts.chiralityOf(counts.atoms().getFirst())).contains(Chirality.ANTICLOCKWISE);
            }

            @Test
            void throws_where_the_stereocentre_would_be_left_counting_two_hydrogens()
            {
                Atom centre = Atom.builder("C").hydrogenCount(1).build();
                Atom br = Atom.organic("Br");
                Atom cl = Atom.organic("Cl");
                Atom hydrogen = Atom.organic("H");
                Molecule molecule = new Molecule.Builder()
                    .addAtoms(centre, br, cl, hydrogen)
                    .addBond(centre, br, BondOrder.SINGLE)
                    .addBond(centre, cl, BondOrder.SINGLE)
                    .addBond(centre, hydrogen, BondOrder.SINGLE)
                    .setChirality(centre, Chirality.ANTICLOCKWISE)
                    .build();

                assertThatThrownBy(() -> Hydrogens.asCounts(molecule))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2 counted hydrogens");
            }

            @Test
            void preserves_the_configuration_of_a_double_bond_measured_from_a_substituent()
            {
                Atom ca = Atom.builder("C").hydrogenCount(0).build();
                Atom cb = Atom.builder("C").hydrogenCount(0).build();
                Atom f = Atom.organic("F");
                Atom cl = Atom.organic("Cl");
                Atom firstHydrogen = Atom.organic("H");
                Atom secondHydrogen = Atom.organic("H");
                Molecule molecule = new Molecule.Builder()
                    .addAtoms(f, ca, cb, cl, firstHydrogen, secondHydrogen)
                    .addBond(f, ca, BondOrder.SINGLE)
                    .addBond(ca, cb, BondOrder.DOUBLE)
                    .addBond(cb, cl, BondOrder.SINGLE)
                    .addBond(ca, firstHydrogen, BondOrder.SINGLE)
                    .addBond(cb, secondHydrogen, BondOrder.SINGLE)
                    .setDoubleBondStereo(ca, cb, DoubleBondStereo.SAME_SIDE)
                    .build();

                Molecule counts = Hydrogens.asCounts(molecule);
                Bond rewritten = bondBetween(counts, counts.atoms().get(1), counts.atoms().get(2));

                // both ends are still measured from their halogen, so nothing has moved
                assertThat(counts.doubleBondStereoOf(rewritten)).contains(DoubleBondStereo.SAME_SIDE);
            }

            @Test
            void flips_the_configuration_where_one_end_stops_being_measured_from_its_hydrogen()
            {
                Atom ca = Atom.builder("C").hydrogenCount(0).build();
                Atom cb = Atom.builder("C").hydrogenCount(0).build();
                Atom f = Atom.organic("F");
                Atom cl = Atom.organic("Cl");
                Atom hydrogen = Atom.organic("H");

                // ca's hydrogen is bonded before its fluorine, so ca is measured from the
                // hydrogen until that becomes a count and the fluorine takes over
                Molecule molecule = new Molecule.Builder()
                    .addAtoms(ca, cb, f, cl, hydrogen)
                    .addBond(ca, hydrogen, BondOrder.SINGLE)
                    .addBond(ca, cb, BondOrder.DOUBLE)
                    .addBond(ca, f, BondOrder.SINGLE)
                    .addBond(cb, cl, BondOrder.SINGLE)
                    .setDoubleBondStereo(ca, cb, DoubleBondStereo.SAME_SIDE)
                    .build();

                Molecule counts = Hydrogens.asCounts(molecule);
                Bond rewritten = bondBetween(counts, counts.atoms().getFirst(), counts.atoms().get(1));

                assertThat(counts.doubleBondStereoOf(rewritten)).contains(DoubleBondStereo.OPPOSITE_SIDE);
            }
        }
    }

    /**
     * @param molecule a molecule holding the bond
     * @param atom one endpoint
     * @param other the other endpoint
     * @return the bond between them
     */
    private static Bond bondBetween(Molecule molecule, Atom atom, Atom other)
    {
        return molecule.bondBetween(atom, other)
            .orElseThrow(() -> new AssertionError("No bond found between " + atom + " and " + other));
    }

    /**
     * @param molecule a molecule holding the atom
     * @param atom one of its atoms
     * @return {@code atom}'s bonds with the one to its hydrogen leading, which is where a counted hydrogen sits
     *     in the order a chirality is declared against
     */
    private static List<NeighbourReference> hydrogenLeading(Molecule molecule, Atom atom)
    {
        List<NeighbourReference> leading = new ArrayList<>();
        List<Bond> rest = new ArrayList<>();
        for (Bond bond : molecule.bondsOf(atom))
        {
            if (bond.getOther(atom).element().equals("H"))
            {
                leading.add(bond);
            }
            else
            {
                rest.add(bond);
            }
        }
        leading.addAll(rest);
        return leading;
    }
}
