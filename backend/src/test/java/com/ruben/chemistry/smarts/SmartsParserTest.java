package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.query.Match;
import com.ruben.chemistry.query.Query;
import com.ruben.chemistry.query.QueryBond;
import com.ruben.chemistry.query.SubstructureMatcher;
import com.ruben.chemistry.smiles.SmilesParser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmartsParserTest
{
    private static Query parse(String smarts)
    {
        return Query.fromSmarts(smarts);
    }

    private static Molecule molecule(String smiles)
    {
        return Molecule.fromSmiles(smiles);
    }

    @Nested
    class OrganicSubsetAtoms
    {
        @Test
        void a_plain_element_atom_matches_every_atom_of_that_element()
        {
            Molecule propanol = molecule("CCCO");

            List<Match> matches = new SubstructureMatcher(propanol).findMatches(parse("C"));

            assertThat(matches).hasSize(3);
        }

        @Test
        void a_lowercase_atom_matches_every_aromatic_atom_of_that_element()
        {
            Molecule benzene = molecule("c1ccccc1");

            List<Match> matches = new SubstructureMatcher(benzene).findMatches(parse("c"));

            assertThat(matches).hasSize(6);
        }

        @Test
        void the_wildcard_matches_every_atom()
        {
            Molecule ethanol = molecule("CCO");

            List<Match> matches = new SubstructureMatcher(ethanol).findMatches(parse("*"));

            assertThat(matches).hasSize(3);
        }
    }

    @Nested
    class BracketAtomExpressions
    {
        @Test
        void an_aromatic_bracket_primitive_matches_every_aromatic_atom_of_that_element()
        {
            Molecule benzene = molecule("c1ccccc1");

            List<Match> matches = new SubstructureMatcher(benzene).findMatches(parse("[c]"));

            assertThat(matches).hasSize(6);
        }

        @Test
        void a_logical_or_matches_either_element()
        {
            Molecule glycine = molecule("NCC(=O)O");

            List<Match> matches = new SubstructureMatcher(glycine).findMatches(parse("[C,N]"));

            assertThat(matches).hasSize(3);
        }

        @Test
        void a_logical_not_excludes_the_negated_primitive()
        {
            Molecule propanol = molecule("CCCO");

            List<Match> matches = new SubstructureMatcher(propanol).findMatches(parse("[!O]"));

            assertThat(matches).hasSize(3);
        }
    }

    @Nested
    class BondsAndBranches
    {
        @Test
        void an_explicit_double_bond_only_matches_a_double_bond()
        {
            Molecule aceticAcid = molecule("CC(=O)O");

            assertThat(new SubstructureMatcher(aceticAcid).matches(parse("C=O"))).isTrue();
            assertThat(new SubstructureMatcher(aceticAcid).matches(parse("C#N"))).isFalse();
        }

        @Test
        void a_branch_followed_by_the_main_chain_matches_a_carboxylic_acid()
        {
            Molecule aceticAcid = molecule("CC(=O)O");

            assertThat(new SubstructureMatcher(aceticAcid).matches(parse("C(=O)O"))).isTrue();
        }

        @Test
        void an_unspecified_bond_matches_single_or_aromatic_but_not_double()
        {
            Molecule aceticAcid = molecule("CC(=O)O");

            // the unspecified bond between the two query atoms must not match the real C=O bond
            assertThat(new SubstructureMatcher(aceticAcid).matches(parse("CC"))).isTrue();
            assertThat(new SubstructureMatcher(aceticAcid).matches(parse("OO"))).isFalse();
        }
    }

    @Nested
    class RingClosures
    {
        @Test
        void a_ring_closure_matches_a_ring_of_the_same_shape()
        {
            Molecule cyclohexane = molecule("C1CCCCC1");

            assertThat(new SubstructureMatcher(cyclohexane).matches(parse("C1CCCCC1"))).isTrue();
        }

        @Test
        void a_ring_closure_does_not_match_an_open_chain()
        {
            Molecule hexane = molecule("CCCCCC");

            assertThat(new SubstructureMatcher(hexane).matches(parse("C1CCCCC1"))).isFalse();
        }
    }

    @Nested
    class RecursivePrimitives
    {
        @Test
        void matches_only_the_atom_that_itself_satisfies_the_sub_pattern()
        {
            // only the carbonyl carbon has a double bond to oxygen; the methyl carbon doesn't
            Molecule aceticAcid = molecule("CC(=O)O");

            List<Match> matches = new SubstructureMatcher(aceticAcid).findMatches(parse("[$(C=O)]"));

            assertThat(matches).hasSize(1);
        }

        @Test
        void a_nested_recursive_primitive_resolves_against_real_smarts_not_a_stand_in()
        {
            // [$(C=[$(*N)])]: a carbon double-bonded to an atom that itself has a nitrogen
            // neighbour (the inner $(*N)'s own anchor is the wildcard, bound to that atom
            // itself; *its* neighbour is what must be N) - the inner $(*N) only resolves
            // correctly if the outer $(...) is parsed by a real, recursive SmartsParser
            // rather than some flat stand-in
            Query pattern = parse("[$(C=[$(*N)])]");

            Molecule matching = molecule("C=NN");
            Molecule nonMatching = molecule("C=NC"); // the double-bonded N has no nitrogen neighbour

            assertThat(new SubstructureMatcher(matching).matches(pattern)).isTrue();
            assertThat(new SubstructureMatcher(nonMatching).matches(pattern)).isFalse();
        }
    }

    @Nested
    class Chirality_
    {
        @Test
        void records_chirality_directly_relative_to_bond_insertion_order()
        {
            Query query = parse("[C@](N)(Br)(Cl)F");

            assertThat(query.chiralityOf(query.atoms().getFirst())).contains(Chirality.ANTICLOCKWISE);
        }

        @Test
        void parses_the_clockwise_marker()
        {
            Query query = parse("[C@@](N)(Br)(Cl)F");

            assertThat(query.chiralityOf(query.atoms().getFirst())).contains(Chirality.CLOCKWISE);
        }

        @Test
        void matches_a_stereocentre_whose_every_neighbour_is_bonded()
        {
            Molecule target = molecule("[C@](F)(Cl)(Br)I");

            assertThat(new SubstructureMatcher(target).matches(parse("[C@](F)(Cl)(Br)I"))).isTrue();
            assertThat(new SubstructureMatcher(target).matches(parse("[C@@](F)(Cl)(Br)I"))).isFalse();
        }

        @Test
        void matches_a_stereocentre_bearing_an_implicit_hydrogen()
        {
            // the hydrogen is one of the four neighbours the parity is measured against
            Molecule target = molecule("[C@H](F)(Cl)Br");

            assertThat(new SubstructureMatcher(target).matches(parse("[C@H](F)(Cl)Br"))).isTrue();
            assertThat(new SubstructureMatcher(target).matches(parse("[C@@H](F)(Cl)Br"))).isFalse();
        }

        @Test
        void matches_a_hydrogen_bearing_stereocentre_written_after_another_atom()
        {
            // the hydrogen is written after the atom the stereocentre came from, so the parity
            // as written is one swap away from the order it is stored in
            Molecule target = molecule("CCN[C@H](F)Cl");

            assertThat(new SubstructureMatcher(target).matches(parse("N[C@H](F)Cl"))).isTrue();
            assertThat(new SubstructureMatcher(target).matches(parse("N[C@@H](F)Cl"))).isFalse();
        }

        @Test
        void does_not_match_a_hydrogen_bearing_stereocentre_when_the_pattern_counts_no_hydrogen()
        {
            // a parity is an order of four neighbours, and [C@] names only three: it never says
            // where the hydrogen sits in that order, which is what would decide the parity
            Molecule target = molecule("[C@H](F)(Cl)Br");

            assertThat(new SubstructureMatcher(target).matches(parse("[C@](F)(Cl)Br"))).isFalse();
            assertThat(new SubstructureMatcher(target).matches(parse("[C@@](F)(Cl)Br"))).isFalse();
        }

        @Test
        void does_not_match_a_stereocentre_without_a_hydrogen_when_the_pattern_counts_one()
        {
            Molecule target = molecule("[C@](F)(Cl)(Br)I");

            assertThat(new SubstructureMatcher(target).matches(parse("[C@H](F)(Cl)Br"))).isFalse();
        }
        @Test
        void throws_when_two_directions_written_at_one_atom_contradict_each_other()
        {
            assertThatThrownBy(() -> parse("F/C(\\Cl)=C/F"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("contradicts a direction already written at the same atom");
        }
    }

    @Nested
    class DoubleBondStereo_
    {
        @Test
        void opposite_side_directional_bonds_denote_a_trans_configuration()
        {
            Query query = parse("F/C=C/F");

            QueryBond doubleBond = query.bondBetween(query.atoms().get(1), query.atoms().get(2)).orElseThrow();
            assertThat(query.doubleBondStereoOf(doubleBond)).contains(DoubleBondStereo.OPPOSITE_SIDE);
        }

        @Test
        void same_side_directional_bonds_denote_a_cis_configuration()
        {
            Query query = parse("F/C=C\\F");

            QueryBond doubleBond = query.bondBetween(query.atoms().get(1), query.atoms().get(2)).orElseThrow();
            assertThat(query.doubleBondStereoOf(doubleBond)).contains(DoubleBondStereo.SAME_SIDE);
        }
    }

    @Nested
    class Errors
    {
        @Test
        void throws_for_an_empty_source()
        {
            assertThatThrownBy(() -> parse(""))
                .isInstanceOf(InvalidNotationException.class);
        }

        @Test
        void throws_for_an_unmatched_open_branch()
        {
            assertThatThrownBy(() -> parse("C("))
                .isInstanceOf(InvalidNotationException.class);
        }

        @Test
        void throws_for_an_unmatched_close_branch()
        {
            assertThatThrownBy(() -> parse("C)"))
                .isInstanceOf(InvalidNotationException.class);
        }

        @Test
        void throws_for_an_unpaired_ring_closure()
        {
            assertThatThrownBy(() -> parse("C1CC"))
                .isInstanceOf(InvalidNotationException.class);
        }

        @Test
        void throws_for_a_ring_closure_that_bonds_an_atom_to_itself()
        {
            assertThatThrownBy(() -> parse("C11"))
                .isInstanceOf(InvalidNotationException.class);
        }

        @Test
        void throws_for_a_bond_expression_immediately_following_another_one()
        {
            assertThatThrownBy(() -> parse("C-(=C)C"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("immediately follows another bond expression");
        }

        @Test
        void throws_when_parseQuery_is_called_a_second_time()
        {
            SmartsParser parser = new SmartsParser("CCO");
            parser.parseQuery();

            assertThatThrownBy(parser::parseQuery).isInstanceOf(IllegalStateException.class);
        }
    }
}
