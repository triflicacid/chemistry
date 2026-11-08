package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.Ranker;
import com.ruben.chemistry.core.Ranking;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ruben.chemistry.formula.CondensedFormulaWriter.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CondensedFormulaWriterTest
{
    @Test
    void writes_nothing_for_an_empty_molecule()
    {
        assertThat(write(new Molecule.Builder().build())).isEmpty();
    }

    @Test
    void writes_a_lone_atom_with_its_implicit_hydrogens()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.organic("C"))
            .build();

        assertThat(write(molecule)).isEqualTo("CH4");
    }

    @Test
    void omits_the_hydrogen_count_of_an_atom_with_one_hydrogen()
    {
        // C#C
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2)
            .addBond(c1, c2, BondOrder.TRIPLE)
            .build();

        assertThat(write(molecule)).isEqualTo("CH#CH");
    }

    @Test
    void omits_the_hydrogen_marker_of_an_atom_with_no_hydrogens()
    {
        // C(Cl)(Cl)(Cl)Cl
        Atom c = Atom.organic("C");
        Atom cl1 = Atom.organic("Cl");
        Atom cl2 = Atom.organic("Cl");
        Atom cl3 = Atom.organic("Cl");
        Atom cl4 = Atom.organic("Cl");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c, cl1, cl2, cl3, cl4)
            .addBond(c, cl1, BondOrder.SINGLE)
            .addBond(c, cl2, BondOrder.SINGLE)
            .addBond(c, cl3, BondOrder.SINGLE)
            .addBond(c, cl4, BondOrder.SINGLE)
            .build();

        assertThat(write(molecule)).isEqualTo("C(Cl)(Cl)(Cl)Cl");
    }

    @Test
    void writes_ethane()
    {
        // CC
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2)
            .addBond(c1, c2, BondOrder.SINGLE)
            .build();

        assertThat(write(molecule)).isEqualTo("CH3CH3");
    }

    @Test
    void writes_ethanol()
    {
        // CCO
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom o = Atom.organic("O");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2, o)
            .addBond(c1, c2, BondOrder.SINGLE)
            .addBond(c2, o, BondOrder.SINGLE)
            .build();

        assertThat(write(molecule)).isEqualTo("CH3CH2OH");
    }

    @Test
    void writes_a_branch_in_parentheses()
    {
        // CC(C)C
        Atom methyl1 = Atom.organic("C");
        Atom central = Atom.organic("C");
        Atom methyl2 = Atom.organic("C");
        Atom methyl3 = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(methyl1, central, methyl2, methyl3)
            .addBond(methyl1, central, BondOrder.SINGLE)
            .addBond(central, methyl2, BondOrder.SINGLE)
            .addBond(central, methyl3, BondOrder.SINGLE)
            .build();

        assertThat(write(molecule)).isEqualTo("CH3CH(CH3)CH3");
    }

    @Test
    void writes_disconnected_fragments_separated_by_a_dot()
    {
        // C.O
        Molecule molecule = new Molecule.Builder()
            .addAtoms(Atom.organic("C"), Atom.organic("O"))
            .build();

        assertThat(write(molecule)).isEqualTo("CH4.OH2");
    }

    @Test
    void writes_an_atoms_charge_after_its_hydrogens()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.builder("N").charge(1).hydrogenCount(4).build())
            .build();

        assertThat(write(molecule)).isEqualTo("NH4+");
    }

    @Test
    void writes_a_multiple_charges_magnitude_before_its_sign()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.builder("O").charge(-2).hydrogenCount(0).build())
            .build();

        assertThat(write(molecule)).isEqualTo("O2-");
    }

    @Nested
    class BondSymbols
    {
        @Test
        void marks_a_double_bond_by_default()
        {
            // C=C
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2)
                .addBond(c1, c2, BondOrder.DOUBLE)
                .build();

            assertThat(write(molecule)).isEqualTo("CH2=CH2");
        }

        @Test
        void omits_the_double_bond_mark_when_rendering_is_disabled()
        {
            // C=C
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2)
                .addBond(c1, c2, BondOrder.DOUBLE)
                .build();

            String formula = new CondensedFormulaWriter(molecule).setRenderBondSymbols(false).write();

            assertThat(formula).isEqualTo("CH2CH2");
        }

        @Test
        void omits_the_mark_of_an_aromatic_bond()
        {
            // cc
            Atom c1 = Atom.aromatic("C");
            Atom c2 = Atom.aromatic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2)
                .addBond(c1, c2, BondOrder.AROMATIC)
                .build();

            assertThat(write(molecule)).isEqualTo("CH2CH2");
        }
    }

    @Nested
    class RingClosures
    {
        @Test
        void writes_a_numbered_marker_at_each_end_of_a_ring_closure()
        {
            // C1CC1
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom c3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c2, c3, BondOrder.SINGLE)
                .addBond(c3, c1, BondOrder.SINGLE)
                .build();

            assertThat(write(molecule)).isEqualTo("CH2%1CH2CH2%1");
        }

        @Test
        void writes_a_ring_closures_bond_symbol_at_both_of_its_ends()
        {
            // C=1CC=1
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom c3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c2, c3, BondOrder.SINGLE)
                .addBond(c3, c1, BondOrder.DOUBLE)
                .build();

            assertThat(write(molecule)).isEqualTo("CH=%1CH2CH=%1");
        }

        @Test
        void numbers_two_rings_sharing_an_atom_separately()
        {
            // C12(CC1)CC2
            Atom shared = Atom.organic("C");
            Atom firstRing1 = Atom.organic("C");
            Atom firstRing2 = Atom.organic("C");
            Atom secondRing1 = Atom.organic("C");
            Atom secondRing2 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(shared, firstRing1, firstRing2, secondRing1, secondRing2)
                .addBond(shared, firstRing1, BondOrder.SINGLE)
                .addBond(firstRing1, firstRing2, BondOrder.SINGLE)
                .addBond(firstRing2, shared, BondOrder.SINGLE)
                .addBond(shared, secondRing1, BondOrder.SINGLE)
                .addBond(secondRing1, secondRing2, BondOrder.SINGLE)
                .addBond(secondRing2, shared, BondOrder.SINGLE)
                .build();

            assertThat(write(molecule)).isEqualTo("C%1%2(CH2CH2%1)CH2CH2%2");
        }

        @Test
        void writes_benzene_as_a_ring_of_aromatic_carbons()
        {
            // c1ccccc1
            Molecule.Builder builder = new Molecule.Builder();
            Atom[] carbons = new Atom[6];
            for (int i = 0; i < carbons.length; i++)
            {
                carbons[i] = Atom.aromatic("C");
                builder.addAtom(carbons[i]);
            }
            for (int i = 0; i < carbons.length; i++)
            {
                builder.addBond(carbons[i], carbons[(i + 1) % carbons.length], BondOrder.AROMATIC);
            }

            assertThat(write(builder.build())).isEqualTo("CH%1CHCHCHCHCH%1");
        }
    }

    @Nested
    class RankerDrivenOrdering
    {
        @Test
        void orders_branches_by_the_supplied_rankers_rank_instead_of_bond_insertion_order()
        {
            // C(O)N
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Atom n = Atom.organic("N");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o, n)
                .addBond(c, o, BondOrder.SINGLE)
                .addBond(c, n, BondOrder.SINGLE)
                .build();

            Ranking ranking = mock(Ranking.class);
            when(ranking.rankOf(c)).thenReturn(0);
            when(ranking.rankOf(n)).thenReturn(1);
            when(ranking.rankOf(o)).thenReturn(2);
            when(ranking.atomsByAscendingRank()).thenReturn(List.of(c, n, o));

            Ranker ranker = mock(Ranker.class);
            when(ranker.rank(molecule)).thenReturn(ranking);

            String formula = new CondensedFormulaWriter(molecule).setRanker(ranker).write();

            assertThat(formula).isEqualTo("CH2(NH2)OH");
        }

        @Test
        void writes_the_same_canonical_formula_for_two_differently_built_molecules()
        {
            // CCO
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule builtFromCarbon = new Molecule.Builder()
                .addAtoms(c1, c2, o)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c2, o, BondOrder.SINGLE)
                .build();

            // OCC, the same molecule built from its oxygen
            Atom otherC1 = Atom.organic("C");
            Atom otherC2 = Atom.organic("C");
            Atom otherO = Atom.organic("O");
            Molecule builtFromOxygen = new Molecule.Builder()
                .addAtoms(otherO, otherC2, otherC1)
                .addBond(otherO, otherC2, BondOrder.SINGLE)
                .addBond(otherC2, otherC1, BondOrder.SINGLE)
                .build();

            assertThat(CondensedFormulaWriter.writeCanonical(builtFromCarbon))
                .isEqualTo(CondensedFormulaWriter.writeCanonical(builtFromOxygen));
        }
    }
}
