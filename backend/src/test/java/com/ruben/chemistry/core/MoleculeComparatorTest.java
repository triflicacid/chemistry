package com.ruben.chemistry.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoleculeComparatorTest
{
    @Test
    void treats_identical_molecules_built_in_the_same_order_as_equal()
    {
        Molecule first = ccoBuiltForwards();
        Molecule second = ccoBuiltForwards();

        assertThat(compare(first, second)).isZero();
    }

    @Test
    void treats_the_same_molecule_built_in_reverse_order_as_equal()
    {
        Molecule forward = ccoBuiltForwards();
        Molecule backward = ccoBuiltBackwards();

        assertThat(compare(forward, backward)).isZero();
    }

    @Test
    void treats_molecules_with_different_atom_counts_as_unequal()
    {
        Molecule ethanol = ccoBuiltForwards();

        Atom c = Atom.organic("C");
        Atom o = Atom.organic("O");
        Molecule methanol = new Molecule.Builder()
            .addAtoms(c, o)
            .addBond(c, o, BondOrder.SINGLE)
            .build();

        assertThat(compare(ethanol, methanol)).isNotZero();
    }

    @Test
    void treats_molecules_with_the_same_atoms_but_different_bonding_as_unequal()
    {
        // propane (C-C-C) vs cyclopropane (C1CC1): same atoms, different connectivity
        Atom pc1 = Atom.organic("C");
        Atom pc2 = Atom.organic("C");
        Atom pc3 = Atom.organic("C");
        Molecule propane = new Molecule.Builder()
            .addAtoms(pc1, pc2, pc3)
            .addBond(pc1, pc2, BondOrder.SINGLE)
            .addBond(pc2, pc3, BondOrder.SINGLE)
            .build();

        Atom rc1 = Atom.organic("C");
        Atom rc2 = Atom.organic("C");
        Atom rc3 = Atom.organic("C");
        Molecule cyclopropane = new Molecule.Builder()
            .addAtoms(rc1, rc2, rc3)
            .addBond(rc1, rc2, BondOrder.SINGLE)
            .addBond(rc2, rc3, BondOrder.SINGLE)
            .addBond(rc3, rc1, BondOrder.SINGLE)
            .build();

        assertThat(compare(propane, cyclopropane)).isNotZero();
    }

    @Test
    void treats_molecules_differing_only_by_charge_as_unequal()
    {
        Atom neutral = Atom.organic("C");
        Molecule neutralMolecule = new Molecule.Builder().addAtom(neutral).build();

        Atom charged = Atom.builder("C").charge(1).build();
        Molecule chargedMolecule = new Molecule.Builder().addAtom(charged).build();

        assertThat(compare(neutralMolecule, chargedMolecule)).isNotZero();
    }

    @Test
    void is_consistent_whichever_molecule_is_given_first()
    {
        Molecule forward = ccoBuiltForwards();
        Molecule backward = ccoBuiltBackwards();

        assertThat(compare(forward, backward)).isEqualTo(-compare(backward, forward));
    }

    @Nested
    class ChiralityComparison
    {
        @Test
        void treats_the_same_connectivity_built_in_the_same_order_with_opposite_declared_chirality_as_unequal()
        {
            Molecule anticlockwise = bromochlorofluoromethane(Chirality.ANTICLOCKWISE);
            Molecule clockwise = bromochlorofluoromethane(Chirality.CLOCKWISE);

            assertThat(compare(anticlockwise, clockwise)).isNotZero();
        }

        @Test
        void treats_the_same_declared_chirality_as_unequal_when_insertion_order_makes_it_mean_the_opposite_configuration()
        {
            // Both molecules declare the stereocentre ANTICLOCKWISE, but relative to different
            // bond-insertion orders: swapping N and Br's insertion order is a single
            // transposition, so once both are reprojected onto the same canonical neighbour
            // order (by element: Br, Cl, N), they actually represent opposite configurations.
            Atom n1 = Atom.organic("N");
            Atom c1 = stereocentreCarbon();
            Atom br1 = Atom.organic("Br");
            Atom cl1 = Atom.organic("Cl");
            Molecule.Builder builderA = new Molecule.Builder()
                .addAtoms(n1, c1, br1, cl1)
                .addBond(c1, n1, BondOrder.SINGLE)
                .addBond(c1, br1, BondOrder.SINGLE)
                .addBond(c1, cl1, BondOrder.SINGLE);
            builderA.setChirality(c1, Chirality.ANTICLOCKWISE);
            Molecule declaredNFirst = builderA.build();

            Atom br2 = Atom.organic("Br");
            Atom c2 = stereocentreCarbon();
            Atom n2 = Atom.organic("N");
            Atom cl2 = Atom.organic("Cl");
            Molecule.Builder builderB = new Molecule.Builder()
                .addAtoms(br2, c2, n2, cl2)
                .addBond(c2, br2, BondOrder.SINGLE)
                .addBond(c2, n2, BondOrder.SINGLE)
                .addBond(c2, cl2, BondOrder.SINGLE);
            builderB.setChirality(c2, Chirality.ANTICLOCKWISE);
            Molecule declaredBrFirst = builderB.build();

            assertThat(compare(declaredNFirst, declaredBrFirst)).isNotZero();
        }

        private Molecule bromochlorofluoromethane(Chirality chirality)
        {
            Atom n = Atom.organic("N");
            Atom c = stereocentreCarbon();
            Atom br = Atom.organic("Br");
            Atom cl = Atom.organic("Cl");
            Molecule.Builder builder = new Molecule.Builder()
                .addAtoms(n, c, br, cl)
                .addBond(c, n, BondOrder.SINGLE)
                .addBond(c, br, BondOrder.SINGLE)
                .addBond(c, cl, BondOrder.SINGLE);
            builder.setChirality(c, chirality);
            return builder.build();
        }

        private Atom stereocentreCarbon()
        {
            return Atom.builder("C").hydrogenCount(1).build();
        }
    }

    @Nested
    class TotalOrdering
    {
        @Test
        void sorts_fewer_atoms_before_more_atoms()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule methanol = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.SINGLE)
                .build();

            assertThat(compare(methanol, ccoBuiltForwards())).isNegative();
        }

        @Test
        void sorts_fewer_bonds_before_more_bonds_when_atom_counts_match()
        {
            // propane (C-C-C, 2 bonds) vs cyclopropane (C1CC1, 3 bonds)
            Atom pc1 = Atom.organic("C");
            Atom pc2 = Atom.organic("C");
            Atom pc3 = Atom.organic("C");
            Molecule propane = new Molecule.Builder()
                .addAtoms(pc1, pc2, pc3)
                .addBond(pc1, pc2, BondOrder.SINGLE)
                .addBond(pc2, pc3, BondOrder.SINGLE)
                .build();

            Atom rc1 = Atom.organic("C");
            Atom rc2 = Atom.organic("C");
            Atom rc3 = Atom.organic("C");
            Molecule cyclopropane = new Molecule.Builder()
                .addAtoms(rc1, rc2, rc3)
                .addBond(rc1, rc2, BondOrder.SINGLE)
                .addBond(rc2, rc3, BondOrder.SINGLE)
                .addBond(rc3, rc1, BondOrder.SINGLE)
                .build();

            assertThat(compare(propane, cyclopropane)).isNegative();
        }

        @Test
        void falls_back_to_atom_signature_when_atom_and_bond_counts_match()
        {
            // single-atom molecules: same atom and bond counts, so compare atoms themselves (Cl vs Br)
            Molecule bromine = new Molecule.Builder().addAtom(Atom.organic("Br")).build();
            Molecule chlorine = new Molecule.Builder().addAtom(Atom.organic("Cl")).build();

            assertThat(compare(bromine, chlorine)).isNegative();
        }

        @Test
        void falls_back_to_bond_signature_when_atom_and_atom_signature_tiers_match()
        {
            // same two carbons either way, differing only in bond order
            Atom sc1 = Atom.organic("C");
            Atom sc2 = Atom.organic("C");
            Molecule singleBonded = new Molecule.Builder()
                .addAtoms(sc1, sc2)
                .addBond(sc1, sc2, BondOrder.SINGLE)
                .build();

            Atom dc1 = Atom.organic("C");
            Atom dc2 = Atom.organic("C");
            Molecule doubleBonded = new Molecule.Builder()
                .addAtoms(dc1, dc2)
                .addBond(dc1, dc2, BondOrder.DOUBLE)
                .build();

            assertThat(compare(singleBonded, doubleBonded)).isNegative();
        }

        @Test
        void sorts_a_mixed_list_into_a_deterministic_order_regardless_of_build_order()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule methanol = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.SINGLE)
                .build();

            List<Molecule> shuffled = new ArrayList<>(List.of(ccoBuiltBackwards(), methanol, ccoBuiltForwards()));
            Collections.shuffle(shuffled);

            shuffled.sort(new MoleculeComparator());

            // methanol (2 atoms) sorts before both equal-and-adjacent ethanol instances (3 atoms)
            assertThat(shuffled.get(0).atomCount()).isEqualTo(2);
            assertThat(compare(shuffled.get(1), shuffled.get(2))).isZero();
        }
    }

    private static int compare(Molecule first, Molecule second)
    {
        return new MoleculeComparator().compare(first, second);
    }

    private static Molecule ccoBuiltForwards()
    {
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom o = Atom.organic("O");
        return new Molecule.Builder()
            .addAtoms(c1, c2, o)
            .addBond(c1, c2, BondOrder.SINGLE)
            .addBond(c2, o, BondOrder.SINGLE)
            .build();
    }

    private static Molecule ccoBuiltBackwards()
    {
        Atom o = Atom.organic("O");
        Atom c2 = Atom.organic("C");
        Atom c1 = Atom.organic("C");
        return new Molecule.Builder()
            .addAtoms(o, c2, c1)
            .addBond(c2, o, BondOrder.SINGLE)
            .addBond(c1, c2, BondOrder.SINGLE)
            .build();
    }
}
