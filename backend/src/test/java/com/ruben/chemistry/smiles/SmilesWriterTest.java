package com.ruben.chemistry.smiles;

import com.ruben.chemistry.canonical.Ranker;
import com.ruben.chemistry.canonical.Ranking;
import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.ruben.chemistry.smiles.SmilesWriter.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmilesWriterTest
{
    @Test
    void writes_a_single_atom()
    {
        Atom c = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtom(c)
            .build();

        assertThat(write(molecule)).isEqualTo("C");
    }

    @Test
    void writes_a_simple_chain_with_default_single_bonds()
    {
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom o = Atom.organic("O");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2, o)
            .addBond(c1, c2, BondOrder.SINGLE)
            .addBond(c2, o, BondOrder.SINGLE)
            .build();

        assertThat(write(molecule)).isEqualTo("CCO");
    }

    @Test
    void writes_a_double_bond_symbol()
    {
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtom(c1)
            .addAtom(c2)
            .addBond(c1, c2, BondOrder.DOUBLE)
            .build();

        assertThat(write(molecule)).isEqualTo("C=C");
    }

    @Test
    void writes_a_triple_bond_symbol()
    {
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtom(c1)
            .addAtom(c2)
            .addBond(c1, c2, BondOrder.TRIPLE)
            .build();

        assertThat(write(molecule)).isEqualTo("C#C");
    }

    @Test
    void omits_the_aromatic_bond_symbol_between_two_aromatic_atoms()
    {
        Atom c1 = Atom.aromatic("c");
        Atom c2 = Atom.aromatic("c");
        Molecule molecule = new Molecule.Builder()
            .addAtom(c1)
            .addAtom(c2)
            .addBond(c1, c2, BondOrder.AROMATIC)
            .build();

        assertThat(write(molecule)).isEqualTo("cc");
    }

    @Test
    void writes_a_branch_in_parentheses()
    {
        // CC(O)C : propan-2-ol skeleton
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom o = Atom.organic("O");
        Atom c3 = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2, o, c3)
            .addBond(c1, c2, BondOrder.SINGLE)
            .addBond(c2, o, BondOrder.SINGLE)
            .addBond(c2, c3, BondOrder.SINGLE)
            .build();

        assertThat(write(molecule)).isEqualTo("CC(O)C");
    }

    @Test
    void writes_a_ring_closure_for_a_cycle()
    {
        // C1CC1 : cyclopropane
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom c3 = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2, c3)
            .addBond(c1, c2, BondOrder.SINGLE)
            .addBond(c2, c3, BondOrder.SINGLE)
            .addBond(c3, c1, BondOrder.SINGLE)
            .build();

        assertThat(write(molecule)).isEqualTo("C1CC1");
    }

    @Test
    void writes_a_second_ring_closure_number_when_two_rings_share_an_atom()
    {
        // bicyclic: two fused three-membered rings sharing atom c1
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom c3 = Atom.organic("C");
        Atom c4 = Atom.organic("C");
        Atom c5 = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2, c3, c4, c5)
            .addBond(c1, c2, BondOrder.SINGLE)
            .addBond(c2, c3, BondOrder.SINGLE)
            .addBond(c3, c1, BondOrder.SINGLE)
            .addBond(c1, c4, BondOrder.SINGLE)
            .addBond(c4, c5, BondOrder.SINGLE)
            .addBond(c5, c1, BondOrder.SINGLE)
            .build();

        assertThat(write(molecule)).isEqualTo("C12(CC1)CC2");
    }

    @Test
    void writes_both_ring_closures_from_a_bridgehead_atom_without_a_branch()
    {
        // fused bicyclic: c1 and c3 are bridgeheads, so c1 has only one chain
        // branch (c1-c2) left after its two ring-closure bonds (to c3 and c5)
        // looks like a house
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom c3 = Atom.organic("C");
        Atom c4 = Atom.organic("C");
        Atom c5 = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2, c3, c4, c5)
            .addBond(c1, c2, BondOrder.SINGLE)
            .addBond(c2, c3, BondOrder.SINGLE)
            .addBond(c3, c1, BondOrder.SINGLE)
            .addBond(c3, c4, BondOrder.SINGLE)
            .addBond(c4, c5, BondOrder.SINGLE)
            .addBond(c5, c1, BondOrder.SINGLE)
            .build();

        assertThat(write(molecule)).isEqualTo("C12CC1CC2");
    }

    @Test
    void writes_triflic_acid()
    {
        Atom c = Atom.organic("C");
        Atom f1 = Atom.organic("F");
        Atom f2 = Atom.organic("F");
        Atom f3 = Atom.organic("F");
        Atom s = Atom.organic("S");
        Atom o1 = Atom.organic("O");
        Atom o2 = Atom.organic("O");
        Atom o3 = Atom.organic("O");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c, f1, f2, f3, s, o1, o2, o3)
            .addBond(c, f1, BondOrder.SINGLE)
            .addBond(c, f2, BondOrder.SINGLE)
            .addBond(c, f3, BondOrder.SINGLE)
            .addBond(c, s, BondOrder.SINGLE)
            .addBond(s, o1, BondOrder.DOUBLE)
            .addBond(s, o2, BondOrder.DOUBLE)
            .addBond(s, o3, BondOrder.SINGLE)
            .build();

        assertThat(write(molecule)).isEqualTo("C(F)(F)(F)S(=O)(=O)O");
    }

    @Test
    void writes_disconnected_fragments_separated_by_a_dot()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtoms(Atom.organic("C"), Atom.organic("O"))
            .build();

        assertThat(write(molecule)).isEqualTo("C.O");
    }

    @Test
    void writes_a_bracketed_atom_for_a_charge()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.builder("O").charge(-1).build())
            .build();

        assertThat(write(molecule)).isEqualTo("[O-]");
    }

    @Test
    void writes_a_bracketed_atom_for_an_isotope()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.builder("C").isotope(13).build())
            .build();

        assertThat(write(molecule)).isEqualTo("[13C]");
    }

    @Test
    void writes_a_bracketed_atom_for_an_explicit_hydrogen_count()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.builder("N").hydrogenCount(2).build())
            .build();

        assertThat(write(molecule)).isEqualTo("[NH2]");
    }

    @Test
    void writes_a_wildcard_atom()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.organic("*"))
            .build();

        assertThat(write(molecule)).isEqualTo("*");
    }

    @Test
    void writes_nothing_for_an_empty_molecule()
    {
        Molecule molecule = new Molecule.Builder().build();

        assertThat(write(molecule)).isEmpty();
    }

    @Nested
    class RankerDrivenOrdering
    {
        @Test
        void orders_branches_by_the_supplied_rankers_rank_instead_of_bond_insertion_order()
        {
            // C bonded to O then N, so default insertion order would write O before N; a
            // ranker that ranks N below O should flip the branch order
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

            String smiles = new SmilesWriter(molecule).setRanker(ranker).write();

            assertThat(smiles).isEqualTo("C(N)O");
        }

        @Test
        void roots_each_fragment_at_the_rankers_lowest_ranked_unvisited_atom()
        {
            // two disconnected single atoms; insertion order would write C first, but a
            // ranker that ranks O below C should flip the fragment order
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o)
                .build();

            Ranking ranking = mock(Ranking.class);
            when(ranking.rankOf(c)).thenReturn(1);
            when(ranking.rankOf(o)).thenReturn(0);
            when(ranking.atomsByAscendingRank()).thenReturn(List.of(o, c));

            Ranker ranker = mock(Ranker.class);
            when(ranker.rank(molecule)).thenReturn(ranking);

            String smiles = new SmilesWriter(molecule).setRanker(ranker).write();

            assertThat(smiles).isEqualTo("O.C");
        }
    }

    @Nested
    class ChiralityRendering
    {
        @Test
        void flips_the_declared_chirality_when_the_implicit_hydrogen_displaces_the_arriving_bond()
        {
            // a non-root stereocentre: the declared order is H-first, but the written
            // order has the arriving bond from N before the implicit hydrogen, an odd
            // reordering, so the declared chirality must be flipped when written
            Atom n = Atom.organic("N");
            Atom c = Atom.builder("C").hydrogenCount(1).build();
            Atom br = Atom.organic("Br");
            Atom cl = Atom.organic("Cl");
            Molecule.Builder builder = new Molecule.Builder()
                .addAtoms(n, c, br, cl)
                .addBond(n, c, BondOrder.SINGLE)
                .addBond(c, br, BondOrder.SINGLE)
                .addBond(c, cl, BondOrder.SINGLE);
            builder.setChirality(c, Chirality.ANTICLOCKWISE);
            Molecule molecule = builder.build();

            assertThat(write(molecule)).isEqualTo("N[C@@H](Br)Cl");
        }

        @Test
        void keeps_the_declared_chirality_when_the_stereocentre_is_the_root_atom()
        {
            // a root stereocentre has no arriving bond, so the written order (implicit
            // hydrogen, then bonds in declaration order) exactly matches the declared
            // order, leaving the chirality unchanged
            Atom c = Atom.builder("C").hydrogenCount(1).build();
            Atom n = Atom.organic("N");
            Atom br = Atom.organic("Br");
            Atom cl = Atom.organic("Cl");
            Molecule.Builder builder = new Molecule.Builder()
                .addAtoms(c, n, br, cl)
                .addBond(c, n, BondOrder.SINGLE)
                .addBond(c, br, BondOrder.SINGLE)
                .addBond(c, cl, BondOrder.SINGLE);
            builder.setChirality(c, Chirality.ANTICLOCKWISE);
            Molecule molecule = builder.build();

            // stored/declared order: [H, N, Br, Cl]; written order, starting the walk at C itself: [H, N, Br, Cl]
            // the two orders are identical, so no flip is needed
            assertThat(write(molecule)).isEqualTo("[C@H](N)(Br)Cl");
        }

        @Test
        void flips_the_declared_chirality_when_a_different_bond_is_visited_first()
        {
            // four explicit substituents, no implicit hydrogen: writing from Br instead
            // of N moves the arriving bond ahead of N in the written order, an odd
            // reordering relative to the declared bond order
            Atom c = Atom.organic("C");
            Atom n = Atom.organic("N");
            Atom br = Atom.organic("Br");
            Atom cl = Atom.organic("Cl");
            Atom f = Atom.organic("F");
            Molecule.Builder builder = new Molecule.Builder()
                .addAtoms(br, c, n, cl, f)
                .addBond(c, n, BondOrder.SINGLE)
                .addBond(c, br, BondOrder.SINGLE)
                .addBond(c, cl, BondOrder.SINGLE)
                .addBond(c, f, BondOrder.SINGLE);
            builder.setChirality(c, Chirality.ANTICLOCKWISE);
            Molecule molecule = builder.build();

            // stored/declared order: [N, Br, Cl, F]; written order, starting the walk from Br: [Br, N, Cl, F]
            // (Br arrives first, the rest follow in declared order)
            // swapping N and Br is a single transposition (odd), so the written token must be the flip of the declared one
            assertThat(write(molecule)).isEqualTo("Br[C@@](N)(Cl)F");
        }

        @Test
        void omits_the_chirality_marker_when_rendering_is_disabled()
        {
            Atom n = Atom.organic("N");
            Atom c = Atom.builder("C").hydrogenCount(1).build();
            Atom br = Atom.organic("Br");
            Atom cl = Atom.organic("Cl");
            Molecule.Builder builder = new Molecule.Builder()
                .addAtoms(n, c, br, cl)
                .addBond(n, c, BondOrder.SINGLE)
                .addBond(c, br, BondOrder.SINGLE)
                .addBond(c, cl, BondOrder.SINGLE);
            builder.setChirality(c, Chirality.ANTICLOCKWISE);
            Molecule molecule = builder.build();

            String smiles = new SmilesWriter(molecule).setRenderChirality(false).write();

            assertThat(smiles).isEqualTo("N[CH](Br)Cl");
        }

        @Test
        void keeps_the_declared_chirality_when_a_labelled_hydrogen_takes_the_same_slot()
        {
            // as the root stereocentre above, except the hydrogen is deuterated and so written
            // as an atom of its own, which leaves it in the same position in the written order
            Atom c = Atom.builder("C").hydrogenCount(1).hydrogenIsotope(2, 1).build();
            Atom n = Atom.organic("N");
            Atom br = Atom.organic("Br");
            Atom cl = Atom.organic("Cl");
            Molecule.Builder builder = new Molecule.Builder()
                .addAtoms(c, n, br, cl)
                .addBond(c, n, BondOrder.SINGLE)
                .addBond(c, br, BondOrder.SINGLE)
                .addBond(c, cl, BondOrder.SINGLE);
            builder.setChirality(c, Chirality.ANTICLOCKWISE);
            Molecule molecule = builder.build();

            assertThat(write(molecule)).isEqualTo("[C@]([2H])(N)(Br)Cl");
        }

        @Test
        void flips_the_declared_chirality_of_a_labelled_hydrogen_displaced_by_the_arriving_bond()
        {
            Atom n = Atom.organic("N");
            Atom c = Atom.builder("C").hydrogenCount(1).hydrogenIsotope(2, 1).build();
            Atom br = Atom.organic("Br");
            Atom cl = Atom.organic("Cl");
            Molecule.Builder builder = new Molecule.Builder()
                .addAtoms(n, c, br, cl)
                .addBond(n, c, BondOrder.SINGLE)
                .addBond(c, br, BondOrder.SINGLE)
                .addBond(c, cl, BondOrder.SINGLE);
            builder.setChirality(c, Chirality.ANTICLOCKWISE);
            Molecule molecule = builder.build();

            assertThat(write(molecule)).isEqualTo("N[C@@]([2H])(Br)Cl");
        }
    }

    @Nested
    class HydrogenIsotopeExpansion
    {
        @Test
        void writes_a_labelled_hydrogen_as_an_atom_of_its_own()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.builder("C").hydrogenIsotope(2, 1).build())
                .build();

            assertThat(write(molecule)).isEqualTo("C[2H]");
        }

        @Test
        void writes_every_labelled_hydrogen_of_a_fully_labelled_atom()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.builder("C").hydrogenCount(4).hydrogenIsotope(2, 4).build())
                .build();

            assertThat(write(molecule)).isEqualTo("[C]([2H])([2H])([2H])[2H]");
        }

        @Test
        void leaves_the_remaining_hydrogens_inferred_where_they_already_were()
        {
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.builder("C").hydrogenIsotope(2, 1).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2)
                .addBond(c1, c2, BondOrder.SINGLE)
                .build();

            assertThat(write(molecule)).isEqualTo("CC[2H]");
        }
    }

    @Nested
    class DoubleBondStereoRendering
    {
        @Test
        void renders_opposite_side_substituents_as_a_trans_pair_of_matching_symbols()
        {
            Atom f1 = Atom.organic("F");
            Atom ca = Atom.organic("C");
            Atom cb = Atom.organic("C");
            Atom f2 = Atom.organic("F");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(f1, ca, cb, f2)
                .addBond(f1, ca, BondOrder.SINGLE)
                .addBond(ca, cb, BondOrder.DOUBLE)
                .addBond(cb, f2, BondOrder.SINGLE)
                .setDoubleBondStereo(ca, cb, DoubleBondStereo.OPPOSITE_SIDE)
                .build();

            assertThat(write(molecule)).isEqualTo("F/C=C/F");
        }

        @Test
        void renders_same_side_substituents_as_a_cis_pair_of_opposite_symbols()
        {
            Atom f1 = Atom.organic("F");
            Atom ca = Atom.organic("C");
            Atom cb = Atom.organic("C");
            Atom f2 = Atom.organic("F");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(f1, ca, cb, f2)
                .addBond(f1, ca, BondOrder.SINGLE)
                .addBond(ca, cb, BondOrder.DOUBLE)
                .addBond(cb, f2, BondOrder.SINGLE)
                .setDoubleBondStereo(ca, cb, DoubleBondStereo.SAME_SIDE)
                .build();

            assertThat(write(molecule)).isEqualTo("F/C=C\\F");
        }

        @Test
        void propagates_a_shared_reference_bond_through_a_conjugated_diene()
        {
            // F-Ca=Cb-Cc=Cd-F2: the Cb-Cc single bond is a reference substituent for both
            // double bonds (Cb's side of the first, Cc's side of the second), so its one
            // written character must satisfy both declared configurations at once
            Atom f1 = Atom.organic("F");
            Atom ca = Atom.organic("C");
            Atom cb = Atom.organic("C");
            Atom cc = Atom.organic("C");
            Atom cd = Atom.organic("C");
            Atom f2 = Atom.organic("F");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(f1, ca, cb, cc, cd, f2)
                .addBond(f1, ca, BondOrder.SINGLE)
                .addBond(ca, cb, BondOrder.DOUBLE)
                .addBond(cb, cc, BondOrder.SINGLE)
                .addBond(cc, cd, BondOrder.DOUBLE)
                .addBond(cd, f2, BondOrder.SINGLE)
                .setDoubleBondStereo(ca, cb, DoubleBondStereo.OPPOSITE_SIDE)
                .setDoubleBondStereo(cc, cd, DoubleBondStereo.OPPOSITE_SIDE)
                .build();

            assertThat(write(molecule)).isEqualTo("F/C=C/C=C/F");
        }

        @Test
        void resolves_consistently_regardless_of_double_bond_processing_order()
        {
            // Same all-trans conjugated triene as above but with one extra double bond, with
            // the two outer double bonds added to the builder before the middle one
            Atom f1 = Atom.organic("F");
            Atom ca = Atom.organic("C");
            Atom cb = Atom.organic("C");
            Atom cc = Atom.organic("C");
            Atom cd = Atom.organic("C");
            Atom ce = Atom.organic("C");
            Atom cf = Atom.organic("C");
            Atom f2 = Atom.organic("F");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(f1, ca, cb, cc, cd, ce, cf, f2)
                .addBond(ca, cb, BondOrder.DOUBLE)
                .addBond(ce, cf, BondOrder.DOUBLE)
                .addBond(f1, ca, BondOrder.SINGLE)
                .addBond(cb, cc, BondOrder.SINGLE)
                .addBond(cc, cd, BondOrder.DOUBLE)
                .addBond(cd, ce, BondOrder.SINGLE)
                .addBond(cf, f2, BondOrder.SINGLE)
                .setDoubleBondStereo(ca, cb, DoubleBondStereo.OPPOSITE_SIDE)
                .setDoubleBondStereo(ce, cf, DoubleBondStereo.OPPOSITE_SIDE)
                .setDoubleBondStereo(cc, cd, DoubleBondStereo.OPPOSITE_SIDE)
                .build();

            assertThat(write(molecule)).isEqualTo("F/C=C/C=C/C=C/F");
        }

        @Test
        void falls_back_to_a_ring_closure_bond_as_the_reference_when_no_other_substituent_exists()
        {
            // Cyclobutene-shaped ring A=B-C-D-A: A's only non-double-bond substituent is the
            // ring-closure bond back to A itself, so the reference falls back to it
            Atom a = Atom.organic("C");
            Atom b = Atom.organic("C");
            Atom c = Atom.organic("C");
            Atom d = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(a, b, c, d)
                .addBond(a, b, BondOrder.DOUBLE)
                .addBond(b, c, BondOrder.SINGLE)
                .addBond(c, d, BondOrder.SINGLE)
                .addBond(d, a, BondOrder.SINGLE)
                .setDoubleBondStereo(a, b, DoubleBondStereo.SAME_SIDE)
                .build();

            assertThat(write(molecule)).isEqualTo("C/1=C/CC\\1");
        }

        // Structurally the same pattern as the OpenSMILES spec's own cyclooctatetraene example
        // (C/1=C/C=C\C=C/C=C\1), just a 6-membered ring instead of 8 - see the Proposed
        // Extensions section: https://github.com/timvdm/OpenSMILES/blob/master/opensmiles.asciidoc
        @Test
        void alternates_direction_symbols_across_a_ring_closure_in_a_conjugated_ring()
        {
            // A1=A2-A3=A4-A5=A6-A1: every reference bond is shared between two double
            // bonds, closing into a full cycle of constraints, so the ring-closure
            // bond's declared configuration is pinned down by the other two rather than freely chosen.
            Atom a1 = Atom.organic("C");
            Atom a2 = Atom.organic("C");
            Atom a3 = Atom.organic("C");
            Atom a4 = Atom.organic("C");
            Atom a5 = Atom.organic("C");
            Atom a6 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(a1, a2, a3, a4, a5, a6)
                .addBond(a1, a2, BondOrder.DOUBLE)
                .addBond(a2, a3, BondOrder.SINGLE)
                .addBond(a3, a4, BondOrder.DOUBLE)
                .addBond(a4, a5, BondOrder.SINGLE)
                .addBond(a5, a6, BondOrder.DOUBLE)
                .addBond(a6, a1, BondOrder.SINGLE)
                .setDoubleBondStereo(a1, a2, DoubleBondStereo.OPPOSITE_SIDE)
                .setDoubleBondStereo(a3, a4, DoubleBondStereo.SAME_SIDE)
                .setDoubleBondStereo(a5, a6, DoubleBondStereo.SAME_SIDE)
                .build();

            assertThat(write(molecule)).isEqualTo("C/1=C\\C=C/C=C\\1");
        }

        @Test
        void omits_the_direction_symbol_when_an_endpoint_has_no_explicit_substituent()
        {
            Atom ca = Atom.organic("C");
            Atom cb = Atom.organic("C");
            Atom f = Atom.organic("F");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(ca, cb, f)
                .addBond(ca, cb, BondOrder.DOUBLE)
                .addBond(cb, f, BondOrder.SINGLE)
                .setDoubleBondStereo(ca, cb, DoubleBondStereo.SAME_SIDE)
                .build();

            assertThat(write(molecule)).isEqualTo("C=CF");
        }

        @Test
        void omits_the_direction_symbols_when_rendering_is_disabled()
        {
            Atom f1 = Atom.organic("F");
            Atom ca = Atom.organic("C");
            Atom cb = Atom.organic("C");
            Atom f2 = Atom.organic("F");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(f1, ca, cb, f2)
                .addBond(f1, ca, BondOrder.SINGLE)
                .addBond(ca, cb, BondOrder.DOUBLE)
                .addBond(cb, f2, BondOrder.SINGLE)
                .setDoubleBondStereo(ca, cb, DoubleBondStereo.OPPOSITE_SIDE)
                .build();

            String smiles = new SmilesWriter(molecule).setRenderDoubleBondStereo(false).write();

            assertThat(smiles).isEqualTo("FC=CF");
        }
    }
}
