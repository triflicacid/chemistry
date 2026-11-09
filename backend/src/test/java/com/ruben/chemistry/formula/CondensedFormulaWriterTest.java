package com.ruben.chemistry.formula;

import com.ruben.chemistry.canonical.Ranker;
import com.ruben.chemistry.canonical.Ranking;
import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.ruben.chemistry.formula.CondensedFormulaWriter.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CondensedFormulaWriterTest
{
    private static String unicode(Molecule molecule)
    {
        return new CondensedFormulaWriter(molecule).setUnicode(true).write();
    }

    @Test
    void writes_nothing_for_an_empty_molecule()
    {
        Molecule molecule = new Molecule.Builder().build();

        assertThat(write(molecule)).isEmpty();
        assertThat(unicode(molecule)).isEmpty();
    }

    @Test
    void writes_a_lone_atom_with_its_implicit_hydrogens()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.organic("C"))
            .build();

        assertThat(write(molecule)).isEqualTo("CH4");
        assertThat(unicode(molecule)).isEqualTo("CH₄");
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
        assertThat(unicode(molecule)).isEqualTo("CH#CH");
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

        assertThat(write(molecule)).isEqualTo("CCl4");
        assertThat(unicode(molecule)).isEqualTo("CCl₄");
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
        assertThat(unicode(molecule)).isEqualTo("CH₃CH₃");
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
        assertThat(unicode(molecule)).isEqualTo("CH₃CH₂OH");
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

        assertThat(write(molecule)).isEqualTo("CH3CH(CH3)2");
        assertThat(unicode(molecule)).isEqualTo("CH₃CH(CH₃)₂");
    }

    @Test
    void writes_disconnected_fragments_separated_by_a_dot()
    {
        // C.O
        Molecule molecule = new Molecule.Builder()
            .addAtoms(Atom.organic("C"), Atom.organic("O"))
            .build();

        assertThat(write(molecule)).isEqualTo("CH4.H2O");
        assertThat(unicode(molecule)).isEqualTo("CH₄.H₂O");
    }

    @Test
    void writes_an_atoms_charge_after_its_hydrogens()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.builder("N").charge(1).hydrogenCount(4).build())
            .build();

        assertThat(write(molecule)).isEqualTo("NH4+");
        assertThat(unicode(molecule)).isEqualTo("NH₄⁺");
    }

    @Test
    void writes_a_multiple_charges_magnitude_before_its_sign()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.builder("O").charge(-2).hydrogenCount(0).build())
            .build();

        assertThat(write(molecule)).isEqualTo("O2-");
        assertThat(unicode(molecule)).isEqualTo("O²⁻");
    }

    @Nested
    class LeadingGroups
    {
        @Test
        void writes_a_leading_groups_hydrogens_before_its_symbol()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.organic("O"))
                .build();

            assertThat(write(molecule)).isEqualTo("H2O");
            assertThat(unicode(molecule)).isEqualTo("H₂O");
        }

        @Test
        void keeps_an_element_that_outranks_hydrogen_first()
        {
            Molecule nitrogen = new Molecule.Builder()
                .addAtom(Atom.organic("N"))
                .build();
            Molecule carbon = new Molecule.Builder()
                .addAtom(Atom.organic("C"))
                .build();

            assertThat(write(nitrogen)).isEqualTo("NH3");
            assertThat(unicode(nitrogen)).isEqualTo("NH₃");
            assertThat(write(carbon)).isEqualTo("CH4");
            assertThat(unicode(carbon)).isEqualTo("CH₄");
        }

        @Test
        void leads_with_a_hydroxyl_rooted_at_its_oxygen()
        {
            // OCC
            Atom o = Atom.organic("O");
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(o, c1, c2)
                .addBond(o, c1, BondOrder.SINGLE)
                .addBond(c1, c2, BondOrder.SINGLE)
                .build();

            assertThat(write(molecule)).isEqualTo("HOCH2CH3");
            assertThat(unicode(molecule)).isEqualTo("HOCH₂CH₃");
        }

        @Test
        void keeps_a_group_reached_through_a_bond_symbol_first()
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
            assertThat(unicode(molecule)).isEqualTo("CH₃CH₂OH");
        }

        @Test
        void follows_strict_hill_when_asked_for_it()
        {
            Molecule hydrogenChloride = new Molecule.Builder()
                .addAtom(Atom.organic("Cl"))
                .build();
            Molecule ammonia = new Molecule.Builder()
                .addAtom(Atom.organic("N"))
                .build();

            assertThat(new CondensedFormulaWriter(hydrogenChloride)
                .setElementOrder(MolecularFormula.ElementOrder.HILL).write()).isEqualTo("ClH");
            assertThat(new CondensedFormulaWriter(ammonia)
                .setElementOrder(MolecularFormula.ElementOrder.HILL).write()).isEqualTo("H3N");
            assertThat(new CondensedFormulaWriter(ammonia)
                .setElementOrder(MolecularFormula.ElementOrder.HILL).setUnicode(true).write()).isEqualTo("H₃N");
        }
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
            assertThat(unicode(molecule)).isEqualTo("CH₂=CH₂");
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
            String displayed = new CondensedFormulaWriter(molecule).setRenderBondSymbols(false).setUnicode(true).write();

            assertThat(formula).isEqualTo("CH2CH2");
            assertThat(displayed).isEqualTo("CH₂CH₂");
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
            assertThat(unicode(molecule)).isEqualTo("CH₂CH₂");
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
            // the ring number is a label, so it keeps plain digits while the hydrogens do not
            assertThat(unicode(molecule)).isEqualTo("CH₂%1CH₂CH₂%1");
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
            assertThat(unicode(molecule)).isEqualTo("CH=%1CH₂CH=%1");
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
            assertThat(unicode(molecule)).isEqualTo("C%1%2(CH₂CH₂%1)CH₂CH₂%2");
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

            Molecule molecule = builder.build();

            assertThat(write(molecule)).isEqualTo("CH%1CHCHCHCHCH%1");
            assertThat(unicode(molecule)).isEqualTo("CH%1CHCHCHCHCH%1");
        }
    }

    @Nested
    class RepeatGroups
    {
        private static Molecule alkaneChain(int carbons)
        {
            Molecule.Builder builder = new Molecule.Builder();
            Atom[] atoms = new Atom[carbons];
            for (int i = 0; i < carbons; i++)
            {
                atoms[i] = Atom.organic("C");
                builder.addAtom(atoms[i]);
            }
            for (int i = 1; i < carbons; i++)
            {
                builder.addBond(atoms[i - 1], atoms[i], BondOrder.SINGLE);
            }
            return builder.build();
        }

        @Test
        void collapses_a_run_of_identical_groups()
        {
            Molecule hexane = alkaneChain(6);

            assertThat(write(hexane)).isEqualTo("CH3(CH2)4CH3");
            assertThat(unicode(hexane)).isEqualTo("CH₃(CH₂)₄CH₃");
        }

        @Test
        void writes_out_a_run_that_saves_no_characters()
        {
            // (CH2)2 is no shorter than CH2CH2, so the brackets buy nothing
            Molecule butane = alkaneChain(4);

            assertThat(write(butane)).isEqualTo("CH3CH2CH2CH3");
            assertThat(unicode(butane)).isEqualTo("CH₃CH₂CH₂CH₃");
        }

        @Test
        void writes_out_a_run_shorter_than_a_raised_minimum()
        {
            Molecule hexane = alkaneChain(6);

            assertThat(new CondensedFormulaWriter(hexane).setMinimumRepeat(5).write())
                .isEqualTo("CH3CH2CH2CH2CH2CH3");
        }

        @Test
        void writes_out_every_group_when_collapsing_is_disabled()
        {
            Molecule decane = alkaneChain(10);

            assertThat(new CondensedFormulaWriter(decane).setCollapseRepeats(false).write())
                .isEqualTo("CH3CH2CH2CH2CH2CH2CH2CH2CH2CH3");
        }

        @Test
        void rejects_a_minimum_below_two()
        {
            CondensedFormulaWriter writer = new CondensedFormulaWriter(alkaneChain(6));

            assertThatThrownBy(() -> writer.setMinimumRepeat(1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void collapses_the_groups_between_a_rings_two_markers()
        {
            // C1CCCCC1
            Molecule.Builder builder = new Molecule.Builder();
            Atom[] carbons = new Atom[6];
            for (int i = 0; i < carbons.length; i++)
            {
                carbons[i] = Atom.organic("C");
                builder.addAtom(carbons[i]);
            }
            for (int i = 0; i < carbons.length; i++)
            {
                builder.addBond(carbons[i], carbons[(i + 1) % carbons.length], BondOrder.SINGLE);
            }

            Molecule cyclohexane = builder.build();

            assertThat(write(cyclohexane)).isEqualTo("CH2%1(CH2)4CH2%1");
            assertThat(unicode(cyclohexane)).isEqualTo("CH₂%1(CH₂)₄CH₂%1");
        }

        @Test
        void leaves_an_aromatic_ring_written_out_in_full()
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

            Molecule benzene = builder.build();

            assertThat(write(benzene)).isEqualTo("CH%1CHCHCHCHCH%1");
        }

        @Test
        void breaks_a_run_where_the_bond_symbols_differ()
        {
            // C=CC=CC=C
            Molecule.Builder builder = new Molecule.Builder();
            Atom[] carbons = new Atom[6];
            for (int i = 0; i < carbons.length; i++)
            {
                carbons[i] = Atom.organic("C");
                builder.addAtom(carbons[i]);
            }
            for (int i = 1; i < carbons.length; i++)
            {
                builder.addBond(carbons[i - 1], carbons[i], i % 2 == 1 ? BondOrder.DOUBLE : BondOrder.SINGLE);
            }

            Molecule hexatriene = builder.build();

            assertThat(write(hexatriene)).isEqualTo("CH2=CHCH=CHCH=CH2");
        }

        @Test
        void collapses_a_run_of_groups_carrying_the_same_branch()
        {
            // CC(C)C(C)C(C)C
            Molecule.Builder builder = new Molecule.Builder();
            Atom start = Atom.organic("C");
            Atom end = Atom.organic("C");
            Atom[] backbone = new Atom[3];
            Atom[] methyls = new Atom[3];
            builder.addAtom(start);
            for (int i = 0; i < backbone.length; i++)
            {
                backbone[i] = Atom.organic("C");
                methyls[i] = Atom.organic("C");
                builder.addAtoms(backbone[i], methyls[i]);
                builder.addBond(i == 0 ? start : backbone[i - 1], backbone[i], BondOrder.SINGLE);
                builder.addBond(backbone[i], methyls[i], BondOrder.SINGLE);
            }
            builder.addAtom(end);
            builder.addBond(backbone[backbone.length - 1], end, BondOrder.SINGLE);

            Molecule trimethylpentane = builder.build();

            assertThat(write(trimethylpentane)).isEqualTo("CH3(CH(CH3))2CH(CH3)2");
        }

        @Test
        void collapses_a_repeating_motif_of_more_than_one_group()
        {
            // CC=CC=CC
            Molecule.Builder builder = new Molecule.Builder();
            Atom[] carbons = new Atom[6];
            for (int i = 0; i < carbons.length; i++)
            {
                carbons[i] = Atom.organic("C");
                builder.addAtom(carbons[i]);
            }
            for (int i = 1; i < carbons.length; i++)
            {
                builder.addBond(carbons[i - 1], carbons[i], i % 2 == 0 ? BondOrder.DOUBLE : BondOrder.SINGLE);
            }

            Molecule hexadiene = builder.build();

            assertThat(write(hexadiene)).isEqualTo("CH3(CH=CH)2CH3");
            assertThat(unicode(hexadiene)).isEqualTo("CH₃(CH=CH)₂CH₃");
        }

        @Test
        void refuses_a_motif_that_would_open_with_a_bond_symbol()
        {
            // C=CC=CC=C, where the repeating motif can only start after a double bond
            Molecule.Builder builder = new Molecule.Builder();
            Atom[] carbons = new Atom[6];
            for (int i = 0; i < carbons.length; i++)
            {
                carbons[i] = Atom.organic("C");
                builder.addAtom(carbons[i]);
            }
            for (int i = 1; i < carbons.length; i++)
            {
                builder.addBond(carbons[i - 1], carbons[i], i % 2 == 1 ? BondOrder.DOUBLE : BondOrder.SINGLE);
            }

            Molecule hexatriene = builder.build();

            assertThat(write(hexatriene)).isEqualTo("CH2=CHCH=CHCH=CH2");
        }

        @Nested
        class Branches
        {
            private static Molecule centredOn(String element, int branches, String branchElement)
            {
                Molecule.Builder builder = new Molecule.Builder();
                Atom centre = Atom.organic(element);
                builder.addAtom(centre);
                for (int i = 0; i < branches; i++)
                {
                    Atom branch = Atom.organic(branchElement);
                    builder.addAtom(branch);
                    builder.addBond(centre, branch, BondOrder.SINGLE);
                }
                return builder.build();
            }

            @Test
            void drops_the_brackets_round_a_single_atom_branch()
            {
                assertThat(write(centredOn("C", 2, "Cl"))).isEqualTo("CH2Cl2");
                assertThat(write(centredOn("C", 3, "Cl"))).isEqualTo("CHCl3");
                assertThat(write(centredOn("C", 4, "Cl"))).isEqualTo("CCl4");
                assertThat(unicode(centredOn("C", 2, "Cl"))).isEqualTo("CH₂Cl₂");
            }

            @Test
            void keeps_the_brackets_round_a_branch_of_more_than_one_atom()
            {
                // a count straight after CH3 would read as its hydrogens
                assertThat(write(centredOn("C", 4, "C"))).isEqualTo("C(CH3)4");
                assertThat(write(centredOn("N", 3, "C"))).isEqualTo("N(CH3)3");
                assertThat(write(centredOn("C", 3, "C"))).isEqualTo("CH(CH3)3");
            }

            @Test
            void writes_a_collapsed_group_ahead_of_its_atom_when_the_chain_carries_on()
            {
                // CC(C)O
                Atom centre = Atom.organic("C");
                Atom methyl1 = Atom.organic("C");
                Atom methyl2 = Atom.organic("C");
                Atom oxygen = Atom.organic("O");
                Molecule propanol = new Molecule.Builder()
                    .addAtoms(centre, methyl1, methyl2, oxygen)
                    .addBond(centre, methyl1, BondOrder.SINGLE)
                    .addBond(centre, methyl2, BondOrder.SINGLE)
                    .addBond(centre, oxygen, BondOrder.SINGLE)
                    .build();

                assertThat(write(propanol)).isEqualTo("(CH3)2CHOH");
                assertThat(unicode(propanol)).isEqualTo("(CH₃)₂CHOH");
            }

            @Test
            void gathers_interchangeable_branches_that_are_not_next_to_each_other()
            {
                // CC(Cl)C, the chlorine traced between the two methyls
                Atom centre = Atom.organic("C");
                Atom methyl1 = Atom.organic("C");
                Atom chlorine = Atom.organic("Cl");
                Atom methyl2 = Atom.organic("C");
                Molecule chloropropane = new Molecule.Builder()
                    .addAtoms(centre, methyl1, chlorine, methyl2)
                    .addBond(centre, methyl1, BondOrder.SINGLE)
                    .addBond(centre, chlorine, BondOrder.SINGLE)
                    .addBond(centre, methyl2, BondOrder.SINGLE)
                    .build();

                assertThat(write(chloropropane)).isEqualTo("(CH3)2CHCl");
            }

            @Test
            void writes_out_every_branch_when_collapsing_is_disabled()
            {
                Molecule tetrachloromethane = centredOn("C", 4, "Cl");

                assertThat(new CondensedFormulaWriter(tetrachloromethane).setCollapseRepeats(false).write())
                    .isEqualTo("C(Cl)(Cl)(Cl)Cl");
            }
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
            String displayed = new CondensedFormulaWriter(molecule).setRanker(ranker).setUnicode(true).write();

            assertThat(formula).isEqualTo("CH2(NH2)OH");
            assertThat(displayed).isEqualTo("CH₂(NH₂)OH");
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
