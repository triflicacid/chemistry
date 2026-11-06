package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.query.AtomPredicate;
import com.ruben.chemistry.query.AtomPredicates;
import com.ruben.chemistry.query.BondPredicates;
import com.ruben.chemistry.query.Query;
import com.ruben.chemistry.query.QueryAtom;
import com.ruben.chemistry.smiles.SmilesParser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtomExpressionParserTest
{
    /** A resolver for tests that have no {@link TokenType#EXPRESSION_START} token and so should never call it. */
    private static final SubPatternResolver UNUSED_RESOLVER =
        cursor -> { throw new AssertionError("sub-pattern resolver should not be called, but was"); };

    private static AtomPredicate parse(Token... tokens)
    {
        return result(List.of(tokens), UNUSED_RESOLVER).predicate();
    }

    private static AtomExpressionParser.Result result(List<Token> tokens, SubPatternResolver subPatternResolver)
    {
        return new AtomExpressionParser(new ListCursor(tokens), "source", subPatternResolver).parse();
    }

    private static List<Token> collectRecursiveSpan(TokenCursor cursor)
    {
        List<Token> collected = new ArrayList<>();
        while (!cursor.isAtEnd())
        {
            Token token = cursor.next();
            if (token.type() == TokenType.EXPRESSION_END)
            {
                return collected;
            }
            collected.add(token);
        }
        throw new InvalidNotationException("Unterminated recursive SMARTS primitive, missing its end marker", "irrelevant for these tests", 0);
    }

    private static SubPatternResolver fixedResult(Query query)
    {
        return cursor ->
        {
            collectRecursiveSpan(cursor);
            return query;
        };
    }

    private static Token element(String text)
    {
        return new Token(TokenType.ELEMENT, text, 0);
    }

    private static Token structural(String letter)
    {
        return new Token(TokenType.STRUCTURAL, letter, 0);
    }

    private static Token number(int value)
    {
        return new Token(TokenType.NUMBER, String.valueOf(value), 0);
    }

    private static Token sign(String symbol)
    {
        return new Token(TokenType.SIGN, symbol, 0);
    }

    private static Token chirality(String symbol)
    {
        return new Token(TokenType.CHIRALITY, symbol, 0);
    }

    /** An unbracketed organic-subset atom token, as the lexer would produce for e.g. {@code CC} inside a {@code $(...)} span. */
    private static Token atom(String text)
    {
        return new Token(TokenType.ATOM, text, 0);
    }

    private static final Token HASH = new Token(TokenType.HASH, "#", 0);
    private static final Token COLON = new Token(TokenType.COLON, ":", 0);
    private static final Token AND = new Token(TokenType.AND, "&", 0);
    private static final Token OR = new Token(TokenType.OR, ",", 0);
    private static final Token SEMI = new Token(TokenType.SEMI, ";", 0);
    private static final Token NOT = new Token(TokenType.NOT, "!", 0);
    private static final Token EXPRESSION_START = new Token(TokenType.EXPRESSION_START, "$(", 0);
    private static final Token EXPRESSION_END = new Token(TokenType.EXPRESSION_END, ")", 0);

    private static Atom firstAtom(Molecule molecule)
    {
        return molecule.atoms().get(0);
    }

    private static Atom secondAtom(Molecule molecule)
    {
        return molecule.atoms().get(1);
    }

    @Nested
    class Primitives
    {
        @Test
        void wildcard_matches_any_atom()
        {
            Molecule molecule = new SmilesParser("O").parse();

            assertThat(parse(element("*")).matches(molecule, firstAtom(molecule))).isTrue();
        }

        @Test
        void element_matches_exactly_that_element()
        {
            Molecule molecule = new SmilesParser("CO").parse();

            assertThat(parse(element("C")).matches(molecule, firstAtom(molecule))).isTrue();
            assertThat(parse(element("C")).matches(molecule, secondAtom(molecule))).isFalse();
        }

        @Test
        void lowercase_element_matches_an_aromatic_atom_of_that_element()
        {
            Molecule benzene = new SmilesParser("c1ccccc1").parse();
            Molecule aliphatic = new SmilesParser("C").parse();

            assertThat(parse(element("c")).matches(benzene, firstAtom(benzene))).isTrue();
            assertThat(parse(element("c")).matches(aliphatic, firstAtom(aliphatic))).isFalse();
        }

        @Test
        void hash_and_number_matches_that_atomic_number()
        {
            Molecule molecule = new SmilesParser("C").parse();

            assertThat(parse(HASH, number(6)).matches(molecule, firstAtom(molecule))).isTrue();
            assertThat(parse(HASH, number(7)).matches(molecule, firstAtom(molecule))).isFalse();
        }

        @Test
        void leading_number_matches_that_isotope()
        {
            Atom labelled = Atom.builder("C").isotope(13).build();
            Molecule molecule = new Molecule.Builder().addAtom(labelled).build();

            assertThat(parse(number(13)).matches(molecule, labelled)).isTrue();
            assertThat(parse(number(12)).matches(molecule, labelled)).isFalse();
        }

        @Test
        void bare_sign_matches_a_charge_of_one()
        {
            Atom charged = Atom.builder("N").charge(1).build();
            Molecule molecule = new Molecule.Builder().addAtom(charged).build();

            assertThat(parse(sign("+")).matches(molecule, charged)).isTrue();
        }

        @Test
        void repeated_signs_count_as_their_magnitude()
        {
            Atom doublyCharged = Atom.builder("Fe").charge(2).build();
            Molecule molecule = new Molecule.Builder().addAtom(doublyCharged).build();

            assertThat(parse(sign("+"), sign("+")).matches(molecule, doublyCharged)).isTrue();
        }

        @Test
        void sign_and_number_matches_that_signed_charge()
        {
            Atom charged = Atom.builder("O").charge(-2).build();
            Molecule molecule = new Molecule.Builder().addAtom(charged).build();

            assertThat(parse(sign("-"), number(2)).matches(molecule, charged)).isTrue();
        }

        @Test
        void colon_and_number_matches_that_atom_class()
        {
            Atom classified = Atom.builder("C").atomClass(5).build();
            Molecule molecule = new Molecule.Builder().addAtom(classified).build();

            assertThat(parse(COLON, number(5)).matches(molecule, classified)).isTrue();
            assertThat(parse(COLON, number(6)).matches(molecule, classified)).isFalse();
        }

        @Test
        void bare_r_matches_any_ring_atom()
        {
            Molecule ring = new SmilesParser("C1CC1").parse();
            Molecule chain = new SmilesParser("CCC").parse();

            assertThat(parse(structural("R")).matches(ring, firstAtom(ring))).isTrue();
            assertThat(parse(structural("R")).matches(chain, firstAtom(chain))).isFalse();
        }

        @Test
        void r_with_a_number_matches_that_ring_count()
        {
            Molecule ring = new SmilesParser("C1CC1").parse();

            assertThat(parse(structural("R"), number(1)).matches(ring, firstAtom(ring))).isTrue();
            assertThat(parse(structural("R"), number(2)).matches(ring, firstAtom(ring))).isFalse();
        }

        @Test
        void r_lowercase_matches_that_ring_size()
        {
            Molecule ring = new SmilesParser("C1CC1").parse();

            assertThat(parse(structural("r"), number(3)).matches(ring, firstAtom(ring))).isTrue();
            assertThat(parse(structural("r"), number(4)).matches(ring, firstAtom(ring))).isFalse();
        }

        @Test
        void d_matches_that_degree()
        {
            Molecule molecule = new SmilesParser("CC(=O)C").parse(); // acetone
            Atom carbonylCarbon = secondAtom(molecule);

            assertThat(parse(structural("D"), number(3)).matches(molecule, carbonylCarbon)).isTrue();
            assertThat(parse(structural("D"), number(2)).matches(molecule, carbonylCarbon)).isFalse();
        }

        @Test
        void v_matches_that_valence()
        {
            Molecule ethane = new SmilesParser("CC").parse();

            assertThat(parse(structural("v"), number(4)).matches(ethane, firstAtom(ethane))).isTrue();
        }

        @Test
        void x_matches_that_connectivity()
        {
            Molecule ethane = new SmilesParser("CC").parse();

            assertThat(parse(structural("X"), number(4)).matches(ethane, firstAtom(ethane))).isTrue();
        }

        @Test
        void bare_h_matches_one_hydrogen()
        {
            Molecule molecule = new SmilesParser("[CH1]=O").parse();

            assertThat(parse(element("H")).matches(molecule, firstAtom(molecule))).isTrue();
        }

        @Test
        void h_with_a_number_matches_that_total_hydrogen_count()
        {
            Molecule ethane = new SmilesParser("CC").parse(); // each carbon has 3 implicit H

            assertThat(parse(element("H"), number(3)).matches(ethane, firstAtom(ethane))).isTrue();
            assertThat(parse(element("H"), number(4)).matches(ethane, firstAtom(ethane))).isFalse();
        }

        @Test
        void bare_lowercase_h_matches_one_implicit_hydrogen()
        {
            Molecule molecule = new SmilesParser("[CH1]=O").parse();

            assertThat(parse(structural("h")).matches(molecule, firstAtom(molecule))).isFalse(); // explicit, not implicit
        }

        @Test
        void lowercase_h_with_a_number_matches_that_implicit_hydrogen_count()
        {
            Molecule ethane = new SmilesParser("CC").parse();

            assertThat(parse(structural("h"), number(3)).matches(ethane, firstAtom(ethane))).isTrue();
        }
    }

    @Nested
    class LogicalCombination
    {
        @Test
        void and_requires_both_sides()
        {
            Molecule ring = new SmilesParser("C1CC1").parse();
            Molecule chain = new SmilesParser("CCC").parse();

            AtomPredicate carbonInRing = parse(element("C"), AND, structural("R"));

            assertThat(carbonInRing.matches(ring, firstAtom(ring))).isTrue();
            assertThat(carbonInRing.matches(chain, firstAtom(chain))).isFalse();
        }

        @Test
        void implicit_and_behaves_like_explicit_and()
        {
            Molecule ring = new SmilesParser("C1CC1").parse();
            Molecule chain = new SmilesParser("CCC").parse();

            AtomPredicate carbonInRing = parse(element("C"), structural("R"));

            assertThat(carbonInRing.matches(ring, firstAtom(ring))).isTrue();
            assertThat(carbonInRing.matches(chain, firstAtom(chain))).isFalse();
        }

        @Test
        void or_requires_either_side()
        {
            Molecule molecule = new SmilesParser("CO").parse();
            AtomPredicate carbonOrOxygen = parse(element("C"), OR, element("O"));

            assertThat(carbonOrOxygen.matches(molecule, firstAtom(molecule))).isTrue();
            assertThat(carbonOrOxygen.matches(molecule, secondAtom(molecule))).isTrue();
        }

        @Test
        void semi_also_combines_with_and_but_binds_looser_than_or()
        {
            // "C,N;O" means (C or N) and O - with just a carbon it should fail either way
            Molecule molecule = new SmilesParser("C").parse();
            AtomPredicate expression = parse(element("C"), OR, element("N"), SEMI, element("O"));

            assertThat(expression.matches(molecule, firstAtom(molecule))).isFalse();
        }

        @Test
        void not_negates_the_single_following_primitive()
        {
            Molecule molecule = new SmilesParser("CO").parse();
            AtomPredicate notCarbon = parse(NOT, element("C"));

            assertThat(notCarbon.matches(molecule, firstAtom(molecule))).isFalse();
            assertThat(notCarbon.matches(molecule, secondAtom(molecule))).isTrue();
        }

        @Test
        void not_binds_tighter_than_implicit_and()
        {
            // "!C" and "R" combined by implicit-and: not-carbon AND in-ring
            Molecule ring = new SmilesParser("C1CC1").parse(); // no nitrogen here, just checking it doesn't throw/misparse
            AtomPredicate expression = parse(NOT, element("C"), structural("R"));

            assertThat(expression.matches(ring, firstAtom(ring))).isFalse(); // carbon, so "!C" fails
        }
    }

    @Nested
    class Chirality_
    {
        @Test
        void is_absent_when_not_declared()
        {
            assertThat(result(List.of(element("C")), UNUSED_RESOLVER).chirality()).isEmpty();
        }

        @Test
        void is_reported_alongside_the_predicate_without_affecting_it()
        {
            Molecule molecule = new SmilesParser("[CH1]=O").parse();
            AtomExpressionParser.Result parsed =
                result(List.of(element("C"), chirality("@"), element("H")), UNUSED_RESOLVER);

            assertThat(parsed.chirality()).contains(Chirality.ANTICLOCKWISE);
            // the predicate is still "C and H1", unaffected by the chirality token sitting between them
            assertThat(parsed.predicate().matches(molecule, firstAtom(molecule))).isTrue();
        }

        @Test
        void doubled_at_sign_is_clockwise()
        {
            assertThat(result(List.of(element("C"), chirality("@@")), UNUSED_RESOLVER).chirality())
                .contains(Chirality.CLOCKWISE);
        }

        @Test
        void a_leading_marker_before_any_primitive_is_still_captured()
        {
            Molecule molecule = new SmilesParser("C").parse();
            AtomExpressionParser.Result parsed = result(List.of(chirality("@"), element("C")), UNUSED_RESOLVER);

            assertThat(parsed.chirality()).contains(Chirality.ANTICLOCKWISE);
            assertThat(parsed.predicate().matches(molecule, firstAtom(molecule))).isTrue();
        }

        @Test
        void a_marker_nested_inside_a_recursive_primitive_is_not_attributed_to_this_atom()
        {
            // "[C$([N@])]"-shaped: the @ belongs to the nitrogen, not the carbon
            List<Token> captured = new ArrayList<>();
            SubPatternResolver capturingResolver = cursor ->
            {
                captured.addAll(collectRecursiveSpan(cursor));
                return new Query.Builder().addAtom(new QueryAtom(AtomPredicates.any())).build();
            };

            List<Token> outer = List.of(
                element("C"), EXPRESSION_START,
                new Token(TokenType.BRACKET_OPEN, "[", 0), element("N"), chirality("@"), new Token(TokenType.BRACKET_CLOSE, "]", 0),
                EXPRESSION_END);

            AtomExpressionParser.Result parsed = result(outer, capturingResolver);

            assertThat(parsed.chirality()).isEmpty();
            assertThat(captured).extracting(Token::type).contains(TokenType.CHIRALITY);
        }
    }

    @Nested
    class Recursive
    {
        /** Anchored at a carbon with a bonded carbon neighbour - stands in for {@code $(CC)}. */
        private Query carbonBondedToCarbon()
        {
            QueryAtom anchor = new QueryAtom(AtomPredicates.element("C"));
            QueryAtom neighbour = new QueryAtom(AtomPredicates.element("C"));
            return new Query.Builder().addAtom(anchor).addAtom(neighbour).addBond(anchor, neighbour, BondPredicates.any()).build();
        }

        @Test
        void expression_tokens_resolve_via_the_given_function_and_match_recursively()
        {
            Molecule ethane = new SmilesParser("CC").parse();
            List<Token> tokens = List.of(EXPRESSION_START, atom("C"), atom("C"), EXPRESSION_END);

            assertThat(result(tokens, fixedResult(carbonBondedToCarbon())).predicate().matches(ethane, firstAtom(ethane))).isTrue();
        }

        @Test
        void expression_tokens_do_not_match_when_the_sub_pattern_does_not()
        {
            Molecule methanol = new SmilesParser("CO").parse();
            List<Token> tokens = List.of(EXPRESSION_START, atom("C"), atom("C"), EXPRESSION_END);

            // the sub-pattern's anchor is carbon; methanol's second atom is oxygen
            assertThat(result(tokens, fixedResult(carbonBondedToCarbon())).predicate().matches(methanol, secondAtom(methanol))).isFalse();
        }

        @Test
        void combines_with_other_primitives_via_implicit_and()
        {
            Molecule ring = new SmilesParser("C1CC1").parse();
            Molecule chain = new SmilesParser("CC").parse();
            List<Token> tokens = List.of(EXPRESSION_START, atom("C"), atom("C"), EXPRESSION_END, structural("R"));

            AtomPredicate ringCarbonNextToCarbon = result(tokens, fixedResult(carbonBondedToCarbon())).predicate();

            assertThat(ringCarbonNextToCarbon.matches(ring, firstAtom(ring))).isTrue();
            assertThat(ringCarbonNextToCarbon.matches(chain, firstAtom(chain))).isFalse(); // matches "CC" but not in a ring
        }
    }

    @Nested
    class Errors
    {
        @Test
        void throws_for_an_empty_expression()
        {
            assertThatThrownBy(() -> new AtomExpressionParser(new ListCursor(List.of()), "[]", UNUSED_RESOLVER).parse())
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("must not be empty");
        }

        @Test
        void throws_for_a_trailing_unconsumed_token()
        {
            // OR with nothing valid left dangling after it would be caught by parseUnary instead;
            // this exercises the top-level leftover-token check with two complete expressions
            assertThatThrownBy(() -> parse(element("C"), element("O"), OR))
                .isInstanceOf(InvalidNotationException.class);
        }

        @Test
        void throws_for_a_second_chirality_marker_on_the_same_atom()
        {
            assertThatThrownBy(() -> parse(element("C"), chirality("@"), element("H"), chirality("@@")))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("cannot declare chirality twice");
        }

        @Test
        void throws_when_an_expression_start_has_no_matching_end_marker()
        {
            // detecting this is now the resolver's own job - it's the one walking the span
            Query irrelevant = new Query.Builder().addAtom(new QueryAtom(AtomPredicates.any())).build();

            assertThatThrownBy(() -> result(List.of(EXPRESSION_START, atom("C")), fixedResult(irrelevant)))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Unterminated");
        }

        @Test
        void throws_when_r_lowercase_has_no_following_number()
        {
            assertThatThrownBy(() -> parse(structural("r")))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected a number after 'r'");
        }

        @Test
        void throws_when_degree_has_no_following_number()
        {
            assertThatThrownBy(() -> parse(structural("D")))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected a number after 'D'");
        }

        @Test
        void throws_when_hash_has_no_following_number()
        {
            assertThatThrownBy(() -> parse(HASH))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected a number after '#'");
        }

        @Test
        void throws_when_colon_has_no_following_number()
        {
            assertThatThrownBy(() -> parse(COLON))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected a number after ':'");
        }

        @Test
        void throws_for_not_with_nothing_following_it()
        {
            assertThatThrownBy(() -> parse(NOT))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected an atom primitive");
        }
    }
}
