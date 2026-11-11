package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryTest
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
    class Matches
    {
        @Test
        void returns_true_when_the_molecule_contains_the_pattern()
        {
            assertThat(query("[#8]").matches(molecule("CCO"))).isTrue();
        }

        @Test
        void returns_false_when_the_molecule_does_not_contain_the_pattern()
        {
            assertThat(query("[#8]").matches(molecule("CC"))).isFalse();
        }
    }

    @Nested
    class FindMatches
    {
        @Test
        void returns_one_match_per_atom_the_pattern_can_bind_to()
        {
            List<Match> matches = query("[#6]").findMatches(molecule("CCO"));

            assertThat(matches).hasSize(2);
        }

        @Test
        void returns_no_matches_when_the_pattern_does_not_occur()
        {
            assertThat(query("[#8]").findMatches(molecule("CC"))).isEmpty();
        }
    }

    @Nested
    class And
    {
        @Test
        void matches_when_the_same_atom_satisfies_both_patterns()
        {
            // ethane: both carbons are plain carbons and have exactly 3 hydrogens
            MatchingQuery both = query("[#6]").and(query("[CH3]"));

            assertThat(both.matches(molecule("CC"))).isTrue();
        }

        @Test
        void does_not_match_when_no_single_atom_satisfies_both_patterns()
        {
            // no atom can be both carbon and oxygen at once, even though each pattern occurs
            MatchingQuery both = query("[#6]").and(query("[#8]"));

            assertThat(both.matches(molecule("CCO"))).isFalse();
        }

        @Test
        void findMatches_returns_only_the_occurrences_common_to_both_patterns()
        {
            // propane: the 2 terminal carbons are [CH3], the middle one is not
            MatchingQuery both = query("[#6]").and(query("[CH3]"));

            assertThat(both.findMatches(molecule("CCC"))).hasSize(2);
        }

        @Test
        void findMatches_is_empty_when_no_atom_satisfies_both_patterns()
        {
            MatchingQuery both = query("[#6]").and(query("[#8]"));

            assertThat(both.findMatches(molecule("CCO"))).isEmpty();
        }
    }

    @Nested
    class Or
    {
        @Test
        void matches_when_either_pattern_occurs_somewhere_in_the_molecule()
        {
            MatchingQuery either = query("[#6]").or(query("[#8]"));

            assertThat(either.matches(molecule("CC"))).isTrue();
            assertThat(either.matches(molecule("OO"))).isTrue();
            assertThat(either.matches(molecule("N"))).isFalse();
        }

        @Test
        void findMatches_returns_the_matches_of_both_patterns_together()
        {
            // CCO: 2 carbon matches plus 1 oxygen match, none shared between the two patterns
            MatchingQuery either = query("[#6]").or(query("[#8]"));

            assertThat(either.findMatches(molecule("CCO"))).hasSize(3);
        }

        @Test
        void findMatches_does_not_duplicate_an_occurrence_both_patterns_found()
        {
            // propane: both patterns match the 2 terminal carbons, so the union still has 3
            // entries (one per carbon), not 5
            MatchingQuery either = query("[#6]").or(query("[CH3]"));

            assertThat(either.findMatches(molecule("CCC"))).hasSize(3);
        }
    }

    @Nested
    class ExclusiveOr
    {
        @Test
        void matches_when_exactly_one_pattern_occurs_in_the_molecule()
        {
            MatchingQuery either = query("[#6]").exclusiveOr(query("[#8]"));

            assertThat(either.matches(molecule("CC"))).isTrue();
            assertThat(either.matches(molecule("OO"))).isTrue();
        }

        @Test
        void does_not_match_when_neither_pattern_occurs_in_the_molecule()
        {
            MatchingQuery either = query("[#6]").exclusiveOr(query("[#8]"));

            assertThat(either.matches(molecule("N"))).isFalse();
        }

        @Test
        void does_not_match_when_both_patterns_found_exactly_the_same_occurrences()
        {
            MatchingQuery same = query("[#6]").exclusiveOr(query("[#6]"));

            assertThat(same.matches(molecule("CC"))).isFalse();
        }

        @Test
        void matches_when_both_patterns_occur_but_find_different_occurrences()
        {
            // propane: [#6] matches all 3 carbons, [CH3] only the 2 terminal ones -
            // the middle carbon is found by exactly one of the two
            MatchingQuery partiallyShared = query("[#6]").exclusiveOr(query("[CH3]"));

            assertThat(partiallyShared.matches(molecule("CCC"))).isTrue();
        }

        @Test
        void findMatches_excludes_occurrences_both_patterns_found()
        {
            // only the middle carbon (not a [CH3]) is found by exactly one pattern
            MatchingQuery partiallyShared = query("[#6]").exclusiveOr(query("[CH3]"));

            assertThat(partiallyShared.findMatches(molecule("CCC"))).hasSize(1);
        }
    }

    @Nested
    class Negate
    {
        @Test
        void matches_a_molecule_the_original_query_does_not()
        {
            MoleculeQuery notOxygen = query("[#8]").negate();

            assertThat(notOxygen.matches(molecule("CC"))).isTrue();
            assertThat(notOxygen.matches(molecule("CCO"))).isFalse();
        }

        @Test
        void negating_a_matching_query_has_no_matches_to_report()
        {
            MatchingQuery negated = query("[#8]").negate();

            assertThatThrownBy(() -> negated.findMatches(molecule("CC")))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
