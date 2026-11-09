package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smarts.SmartsParser;
import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryTest
{
    private static Query query(String smarts)
    {
        return new SmartsParser(smarts).parseQuery();
    }

    private static Molecule molecule(String smiles)
    {
        return new SmilesParser(smiles).parse();
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
        void matches_only_when_both_patterns_occur_somewhere_in_the_molecule()
        {
            MatchingQuery both = query("[#6]").and(query("[#8]"));

            assertThat(both.matches(molecule("CCO"))).isTrue();
            assertThat(both.matches(molecule("CC"))).isFalse();
        }

        @Test
        void findMatches_pairs_every_match_of_one_pattern_with_every_match_of_the_other()
        {
            // CCOO: 2 carbons and 2 oxygens, so each carbon-match pairs with each oxygen-match
            MatchingQuery both = query("[#6]").and(query("[#8]"));

            assertThat(both.findMatches(molecule("CCOO"))).hasSize(4);
        }

        @Test
        void findMatches_is_empty_when_either_pattern_does_not_occur()
        {
            MatchingQuery both = query("[#6]").and(query("[#8]"));

            assertThat(both.findMatches(molecule("CC"))).isEmpty();
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
            // CCO: 2 carbon matches plus 1 oxygen match
            MatchingQuery either = query("[#6]").or(query("[#8]"));

            assertThat(either.findMatches(molecule("CCO"))).hasSize(3);
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
