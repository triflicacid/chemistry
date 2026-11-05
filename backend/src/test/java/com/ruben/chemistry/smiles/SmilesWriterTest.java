package com.ruben.chemistry.smiles;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.Ranker;
import com.ruben.chemistry.core.Ranking;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

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
        Atom c1 = Atom.organic("c", true);
        Atom c2 = Atom.organic("c", true);
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
            .addAtom(new Atom("O", false, -1, OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty()))
            .build();

        assertThat(write(molecule)).isEqualTo("[O-]");
    }

    @Test
    void writes_a_bracketed_atom_for_an_isotope()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(new Atom("C", false, 0, OptionalInt.of(13), OptionalInt.empty(), OptionalInt.empty()))
            .build();

        assertThat(write(molecule)).isEqualTo("[13C]");
    }

    @Test
    void writes_a_bracketed_atom_for_an_explicit_hydrogen_count()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(new Atom("N", false, 0, OptionalInt.empty(), OptionalInt.of(2), OptionalInt.empty()))
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
            Atom c = new Atom("C", false, 0, OptionalInt.empty(), OptionalInt.of(1), OptionalInt.empty());
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
            Atom c = new Atom("C", false, 0, OptionalInt.empty(), OptionalInt.of(1), OptionalInt.empty());
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
            Atom c = new Atom("C", false, 0, OptionalInt.empty(), OptionalInt.of(1), OptionalInt.empty());
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
    }
}
