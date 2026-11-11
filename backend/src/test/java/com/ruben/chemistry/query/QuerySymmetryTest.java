package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuerySymmetryTest
{
    private static Query query(String smarts)
    {
        return Query.fromSmarts(smarts);
    }

    private static Molecule molecule(String smiles)
    {
        return Molecule.fromSmiles(smiles);
    }

    @Nested
    class Of
    {
        @Test
        void a_single_position_query_has_no_symmetry()
        {
            assertThat(QuerySymmetry.of(query("C"), molecule("CC"))).isSameAs(QuerySymmetry.NONE);
        }

        @Test
        void an_empty_query_has_no_symmetry()
        {
            assertThat(QuerySymmetry.of(new Query.Builder().build(), molecule("CC"))).isSameAs(QuerySymmetry.NONE);
        }

        @Test
        void two_positions_accepting_the_same_atoms_can_be_permuted()
        {
            assertThat(QuerySymmetry.of(query("CC"), molecule("CC"))).isNotSameAs(QuerySymmetry.NONE);
        }

        @Test
        void positions_accepting_different_atoms_cannot_be_permuted()
        {
            assertThat(QuerySymmetry.of(query("CO"), molecule("CCO"))).isSameAs(QuerySymmetry.NONE);
        }

        @Test
        void a_query_whose_shape_leaves_no_position_interchangeable_has_no_symmetry()
        {
            assertThat(QuerySymmetry.of(query("CCC"), molecule("CCC"))).isNotSameAs(QuerySymmetry.NONE);
            assertThat(QuerySymmetry.of(query("CCC(C)CCC"), molecule("CCC(C)CCC"))).isSameAs(QuerySymmetry.NONE);
        }

        @Test
        void positions_are_told_apart_by_the_atoms_they_accept_not_by_their_predicates()
        {
            Query query = query("Cc");

            assertThat(QuerySymmetry.of(query, molecule("c1ccccc1"))).isNotSameAs(QuerySymmetry.NONE);
            assertThat(QuerySymmetry.of(query, molecule("Cc1ccccc1"))).isSameAs(QuerySymmetry.NONE);
        }

        @Test
        void positions_joined_by_bonds_accepting_different_bonds_cannot_be_permuted()
        {
            Molecule molecule = molecule("CC=C");

            assertThat(QuerySymmetry.of(query("C-C-C"), molecule("CCC"))).isNotSameAs(QuerySymmetry.NONE);
            assertThat(QuerySymmetry.of(query("C-C=C"), molecule)).isSameAs(QuerySymmetry.NONE);
        }

        @Test
        void a_query_declaring_chirality_has_no_symmetry_even_where_it_otherwise_would()
        {
            Molecule molecule = molecule("FC(F)(Cl)Br");

            assertThat(QuerySymmetry.of(query("C(F)(F)(Cl)Br"), molecule)).isNotSameAs(QuerySymmetry.NONE);
            assertThat(QuerySymmetry.of(query("[C@](F)(F)(Cl)Br"), molecule)).isSameAs(QuerySymmetry.NONE);
        }

        @Test
        void a_query_declaring_double_bond_stereo_has_no_symmetry_even_where_it_otherwise_would()
        {
            Molecule molecule = molecule("F/C=C/F");

            assertThat(QuerySymmetry.of(query("FC=CF"), molecule)).isNotSameAs(QuerySymmetry.NONE);
            assertThat(QuerySymmetry.of(query("F/C=C/F"), molecule)).isSameAs(QuerySymmetry.NONE);
        }

        @Test
        void each_call_tracks_the_bindings_of_its_own_search()
        {
            Molecule ethane = molecule("CC");
            Query query = query("CC");
            QuerySymmetry first = QuerySymmetry.of(query, ethane);
            QuerySymmetry second = QuerySymmetry.of(query, ethane);

            bind(first, query, ethane, 1, 0);

            assertThat(first.comesFirst()).isFalse();
            assertThat(second.comesFirst()).isTrue();
        }
    }

    @Nested
    class SymmetryCount
    {
        @Test
        void a_single_interchangeable_pair_of_positions_gives_one_symmetry()
        {
            assertThat(QuerySymmetry.of(query("CC"), molecule("CC")).symmetryCount()).isEqualTo(1);
            assertThat(QuerySymmetry.of(query("CCC"), molecule("CCC")).symmetryCount()).isEqualTo(1);
        }

        @Test
        void a_six_membered_ring_pattern_gives_the_eleven_rotations_and_reflections_that_move_it()
        {
            assertThat(QuerySymmetry.of(query("c1ccccc1"), molecule("c1ccccc1")).symmetryCount()).isEqualTo(11);
        }

        @Test
        void four_interchangeable_branches_give_every_rearrangement_of_the_four()
        {
            assertThat(QuerySymmetry.of(query("CC(C)(C)C"), molecule("CC(C)(C)C")).symmetryCount()).isEqualTo(23);
        }

        @Test
        void only_the_positions_the_molecule_cannot_tell_apart_are_counted()
        {
            assertThat(QuerySymmetry.of(query("Cc"), molecule("c1ccccc1")).symmetryCount()).isEqualTo(1);
            assertThat(QuerySymmetry.of(query("Cc"), molecule("Cc1ccccc1")).symmetryCount()).isZero();
        }

        @Test
        void a_query_with_more_symmetries_than_the_cap_allows_counts_only_as_many_as_the_cap()
        {
            assertThat(QuerySymmetry.of(query("C.C.C.C"), molecule("CCCC")).symmetryCount()).isEqualTo(23);
            assertThat(QuerySymmetry.of(query("C.C.C.C.C"), molecule("CCCCC")).symmetryCount()).isEqualTo(64);
        }
    }

    @Nested
    class ComesFirst
    {
        @Test
        void the_assignment_taking_the_earlier_atom_for_the_earlier_position_is_the_one_kept()
        {
            Molecule ethane = molecule("CC");
            Query query = query("CC");

            QuerySymmetry kept = QuerySymmetry.of(query, ethane);
            bind(kept, query, ethane, 0, 1);

            QuerySymmetry pruned = QuerySymmetry.of(query, ethane);
            bind(pruned, query, ethane, 1, 0);

            assertThat(kept.comesFirst()).isTrue();
            assertThat(pruned.comesFirst()).isFalse();
        }

        @Test
        void nothing_is_bound_yet()
        {
            Molecule ethane = molecule("CC");

            assertThat(QuerySymmetry.of(query("CC"), ethane).comesFirst()).isTrue();
        }

        @Test
        void a_partly_bound_assignment_is_judged_only_once_both_compared_positions_are_bound()
        {
            Molecule ethane = molecule("CC");
            Query query = query("CC");
            QuerySymmetry symmetry = QuerySymmetry.of(query, ethane);

            bind(symmetry, query, ethane, 1);
            assertThat(symmetry.comesFirst()).isTrue();

            bind(symmetry, query, ethane, 1, 0);
            assertThat(symmetry.comesFirst()).isFalse();
        }

        @Test
        void unbinding_a_position_undoes_the_decision_it_contributed_to()
        {
            Molecule ethane = molecule("CC");
            Query query = query("CC");
            QuerySymmetry symmetry = QuerySymmetry.of(query, ethane);

            bind(symmetry, query, ethane, 1, 0);
            assertThat(symmetry.comesFirst()).isFalse();

            symmetry.unbind(query.atoms().get(1));
            assertThat(symmetry.comesFirst()).isTrue();
        }

        @Test
        void a_query_without_symmetry_prunes_nothing()
        {
            Molecule propanol = molecule("CCCO");
            Query query = query("CO");
            QuerySymmetry symmetry = QuerySymmetry.of(query, propanol);

            bind(symmetry, query, propanol, 3, 2);

            assertThat(symmetry).isSameAs(QuerySymmetry.NONE);
            assertThat(symmetry.comesFirst()).isTrue();
        }
    }

    @Nested
    class Pruning
    {
        @Test
        void a_rotation_or_a_reflection_of_the_binding_that_comes_first_is_pruned()
        {
            Molecule benzene = molecule("c1ccccc1");
            Query ring = query("c1ccccc1");

            assertThat(comesFirst(ring, benzene, 0, 1, 2, 3, 4, 5)).isTrue();
            assertThat(comesFirst(ring, benzene, 1, 2, 3, 4, 5, 0)).isFalse();
            assertThat(comesFirst(ring, benzene, 0, 5, 4, 3, 2, 1)).isFalse();
        }

        @Test
        void a_rearrangement_of_interchangeable_branches_is_pruned()
        {
            Molecule neopentane = molecule("CC(C)(C)C");
            Query branched = query("CC(C)(C)C");

            assertThat(comesFirst(branched, neopentane, 0, 1, 2, 3, 4)).isTrue();
            assertThat(comesFirst(branched, neopentane, 0, 1, 2, 4, 3)).isFalse();
            assertThat(comesFirst(branched, neopentane, 4, 1, 3, 2, 0)).isFalse();
        }

        @Test
        void every_place_a_reversible_chain_sits_keeps_the_binding_that_comes_first()
        {
            Molecule cyclohexane = molecule("C1CCCCC1");
            Query chain = query("CCC");

            assertThat(comesFirst(chain, cyclohexane, 0, 1, 2)).isTrue();
            assertThat(comesFirst(chain, cyclohexane, 2, 1, 0)).isFalse();
            assertThat(comesFirst(chain, cyclohexane, 1, 2, 3)).isTrue();
            assertThat(comesFirst(chain, cyclohexane, 3, 2, 1)).isFalse();
        }

        @Test
        void a_query_with_more_symmetries_than_are_collected_still_keeps_the_binding_that_comes_first()
        {
            Molecule pentane = molecule("CCCCC");
            Query disconnected = query("C.C.C.C.C");

            assertThat(comesFirst(disconnected, pentane, 0, 1, 2, 3, 4)).isTrue();
            assertThat(comesFirst(disconnected, pentane, 4, 3, 2, 1, 0)).isFalse();
        }
    }

    private static boolean comesFirst(Query query, Molecule molecule, int... atomIndices)
    {
        QuerySymmetry symmetry = QuerySymmetry.of(query, molecule);
        bind(symmetry, query, molecule, atomIndices);
        return symmetry.comesFirst();
    }

    private static void bind(QuerySymmetry symmetry, Query query, Molecule molecule, int... atomIndices)
    {
        for (int position = 0; position < atomIndices.length; position++)
        {
            symmetry.bind(query.atoms().get(position), molecule.atoms().get(atomIndices[position]));
        }
    }
}
