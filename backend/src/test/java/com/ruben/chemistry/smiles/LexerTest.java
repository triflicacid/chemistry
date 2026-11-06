package com.ruben.chemistry.smiles;

import com.ruben.chemistry.notation.InvalidNotationException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class LexerTest
{
    private static List<Token> tokenize(String source)
    {
        return new Lexer(source).tokenize();
    }

    /** Tokenizes and strips the trailing {@link TokenType#EOF} token, for tests that don't care about it. */
    private static List<Token> tokenizeWithoutEof(String source)
    {
        List<Token> tokens = tokenize(source);
        return tokens.subList(0, tokens.size() - 1);
    }

    @Nested
    class OutsideBrackets
    {
        @Test
        void tokenizes_single_character_organic_atoms()
        {
            assertThat(tokenizeWithoutEof("B"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.ATOM, "B"));

            for (char element : "CNOPSFI".toCharArray())
            {
                assertThat(tokenizeWithoutEof(String.valueOf(element)))
                    .extracting(Token::type, Token::text)
                    .containsExactly(tuple(TokenType.ATOM, String.valueOf(element)));
            }
        }

        @Test
        void tokenizes_two_character_organic_atoms()
        {
            assertThat(tokenizeWithoutEof("Cl"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.ATOM, "Cl"));

            assertThat(tokenizeWithoutEof("Br"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.ATOM, "Br"));
        }

        @Test
        void tokenizes_lowercase_aromatic_organic_atoms()
        {
            for (char element : "bcnops".toCharArray())
            {
                assertThat(tokenizeWithoutEof(String.valueOf(element)))
                    .extracting(Token::type, Token::text)
                    .containsExactly(tuple(TokenType.ATOM, String.valueOf(element)));
            }
        }

        @Test
        void tokenizes_a_wildcard_atom()
        {
            assertThat(tokenizeWithoutEof("*"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.ATOM, "*"));
        }

        @Test
        void does_not_greedily_match_a_two_character_atom_that_is_not_in_the_organic_subset()
        {
            // "Cs" is not an organic-subset symbol, so this should lex as "C" then "s"
            // (aromatic sulfur), not fail to match at all
            assertThat(tokenizeWithoutEof("Cs"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.ATOM, "C"), tuple(TokenType.ATOM, "s"));
        }

        @Test
        void tokenizes_each_bond_symbol()
        {
            for (char symbol : "-=#$:/\\".toCharArray())
            {
                assertThat(tokenizeWithoutEof(String.valueOf(symbol)))
                    .extracting(Token::type, Token::text)
                    .containsExactly(tuple(TokenType.BOND, String.valueOf(symbol)));
            }
        }

        @Test
        void tokenizes_branch_delimiters()
        {
            assertThat(tokenizeWithoutEof("()"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.BRANCH_OPEN, "("), tuple(TokenType.BRANCH_CLOSE, ")"));
        }

        @Test
        void tokenizes_the_disconnection_dot()
        {
            assertThat(tokenizeWithoutEof("."))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.DOT, "."));
        }

        @Test
        void tokenizes_a_single_digit_ring_bond()
        {
            assertThat(tokenizeWithoutEof("1"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.RING_BOND, "1"));
        }

        @Test
        void tokenizes_multiple_single_digit_ring_bonds_as_separate_tokens()
        {
            // consecutive ring-closure digits are each their own ring bond, not a multi-digit number
            assertThat(tokenizeWithoutEof("12"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.RING_BOND, "1"), tuple(TokenType.RING_BOND, "2"));
        }

        @Test
        void tokenizes_a_percent_prefixed_two_digit_ring_bond_without_the_percent_sign()
        {
            assertThat(tokenizeWithoutEof("%12"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.RING_BOND, "12"));
        }

        @Test
        void tokenizes_a_full_chain_with_mixed_elements_and_bonds()
        {
            assertThat(tokenizeWithoutEof("CC(=O)O"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.ATOM, "C"),
                    tuple(TokenType.ATOM, "C"),
                    tuple(TokenType.BRANCH_OPEN, "("),
                    tuple(TokenType.BOND, "="),
                    tuple(TokenType.ATOM, "O"),
                    tuple(TokenType.BRANCH_CLOSE, ")"),
                    tuple(TokenType.ATOM, "O"));
        }

        @Test
        void tokenizes_a_bracket_open_character()
        {
            assertThat(tokenizeWithoutEof("["))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.BRACKET_OPEN, "["));
        }
    }

    @Nested
    class InsideBrackets
    {
        @Test
        void tokenizes_a_bare_bracket_atom()
        {
            assertThat(tokenizeWithoutEof("[C]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "C"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_a_multi_letter_element_symbol()
        {
            assertThat(tokenizeWithoutEof("[Fe]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "Fe"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_aromatic_extension_elements_not_in_the_organic_subset()
        {
            for (String element : List.of("se", "as", "te"))
            {
                assertThat(tokenizeWithoutEof("[" + element + "]"))
                    .extracting(Token::type, Token::text)
                    .containsExactly(
                        tuple(TokenType.BRACKET_OPEN, "["),
                        tuple(TokenType.ELEMENT, element),
                        tuple(TokenType.BRACKET_CLOSE, "]"));
            }
        }

        @Test
        void tokenizes_a_wildcard_element()
        {
            assertThat(tokenizeWithoutEof("[*]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "*"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_a_digit_run_as_a_single_number_token()
        {
            assertThat(tokenizeWithoutEof("[13C]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.NUMBER, "13"),
                    tuple(TokenType.ELEMENT, "C"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_a_single_at_sign_as_anticlockwise_chirality()
        {
            assertThat(tokenizeWithoutEof("[C@H]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "C"),
                    tuple(TokenType.CHIRALITY, "@"),
                    tuple(TokenType.ELEMENT, "H"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_a_doubled_at_sign_as_a_single_clockwise_chirality_token()
        {
            assertThat(tokenizeWithoutEof("[C@@H]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "C"),
                    tuple(TokenType.CHIRALITY, "@@"),
                    tuple(TokenType.ELEMENT, "H"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_charge_signs()
        {
            assertThat(tokenizeWithoutEof("[N+]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "N"),
                    tuple(TokenType.SIGN, "+"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));

            assertThat(tokenizeWithoutEof("[O-]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "O"),
                    tuple(TokenType.SIGN, "-"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_repeated_charge_signs_as_separate_tokens()
        {
            assertThat(tokenizeWithoutEof("[Fe++]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "Fe"),
                    tuple(TokenType.SIGN, "+"),
                    tuple(TokenType.SIGN, "+"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_the_atom_class_colon()
        {
            assertThat(tokenizeWithoutEof("[CH4:1]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "C"),
                    tuple(TokenType.ELEMENT, "H"),
                    tuple(TokenType.NUMBER, "4"),
                    tuple(TokenType.COLON, ":"),
                    tuple(TokenType.NUMBER, "1"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_every_bracket_field_together_in_order()
        {
            assertThat(tokenizeWithoutEof("[13CH3+:5]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.NUMBER, "13"),
                    tuple(TokenType.ELEMENT, "C"),
                    tuple(TokenType.ELEMENT, "H"),
                    tuple(TokenType.NUMBER, "3"),
                    tuple(TokenType.SIGN, "+"),
                    tuple(TokenType.COLON, ":"),
                    tuple(TokenType.NUMBER, "5"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void resumes_lexing_the_organic_subset_after_a_bracket_atom_closes()
        {
            // regression test: the lexer must leave bracket mode once ']' is consumed,
            // otherwise subsequent organic-subset text is misread as bracket contents
            assertThat(tokenizeWithoutEof("[O-]CC"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "O"),
                    tuple(TokenType.SIGN, "-"),
                    tuple(TokenType.BRACKET_CLOSE, "]"),
                    tuple(TokenType.ATOM, "C"),
                    tuple(TokenType.ATOM, "C"));
        }

        @Test
        void resumes_lexing_bond_symbols_after_a_bracket_atom_closes()
        {
            assertThat(tokenizeWithoutEof("[CH4]=[CH2]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "C"),
                    tuple(TokenType.ELEMENT, "H"),
                    tuple(TokenType.NUMBER, "4"),
                    tuple(TokenType.BRACKET_CLOSE, "]"),
                    tuple(TokenType.BOND, "="),
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "C"),
                    tuple(TokenType.ELEMENT, "H"),
                    tuple(TokenType.NUMBER, "2"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void handles_two_consecutive_bracket_atoms_each_independently()
        {
            assertThat(tokenizeWithoutEof("[Na+].[Cl-]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "Na"),
                    tuple(TokenType.SIGN, "+"),
                    tuple(TokenType.BRACKET_CLOSE, "]"),
                    tuple(TokenType.DOT, "."),
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "Cl"),
                    tuple(TokenType.SIGN, "-"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }
    }

    @Nested
    class LookaheadAndConsumption
    {
        @Test
        void peek_does_not_consume_the_token()
        {
            Lexer lexer = new Lexer("CO");

            Token peeked = lexer.peek();

            assertThat(peeked.text()).isEqualTo("C");
            assertThat(lexer.next().text()).isEqualTo("C");
        }

        @Test
        void peek_with_a_distance_looks_further_ahead_without_consuming()
        {
            Lexer lexer = new Lexer("CO.N");

            assertThat(lexer.peek(0).text()).isEqualTo("C");
            assertThat(lexer.peek(1).text()).isEqualTo("O");
            assertThat(lexer.peek(2).text()).isEqualTo(".");
            assertThat(lexer.peek(3).text()).isEqualTo("N");

            // nothing was consumed by peeking ahead
            assertThat(lexer.next().text()).isEqualTo("C");
        }

        @Test
        void peek_with_a_negative_distance_throws()
        {
            Lexer lexer = new Lexer("C");

            assertThatThrownBy(() -> lexer.peek(-1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void next_advances_through_tokens_in_source_order()
        {
            Lexer lexer = new Lexer("CO");

            assertThat(lexer.next().text()).isEqualTo("C");
            assertThat(lexer.next().text()).isEqualTo("O");
        }

        @Test
        void is_at_end_is_false_before_the_last_token_is_consumed()
        {
            Lexer lexer = new Lexer("C");

            assertThat(lexer.isAtEnd()).isFalse();
        }

        @Test
        void is_at_end_is_true_once_only_eof_remains()
        {
            Lexer lexer = new Lexer("C");
            lexer.next();

            assertThat(lexer.isAtEnd()).isTrue();
        }

        @Test
        void is_at_end_is_true_immediately_for_an_empty_source()
        {
            Lexer lexer = new Lexer("");

            assertThat(lexer.isAtEnd()).isTrue();
        }

        @Test
        void tokenize_terminates_with_a_single_eof_token()
        {
            List<Token> tokens = tokenize("CO");

            assertThat(tokens).extracting(Token::type)
                .containsExactly(TokenType.ATOM, TokenType.ATOM, TokenType.EOF);
        }

        @Test
        void records_the_zero_based_source_position_of_each_token()
        {
            List<Token> tokens = tokenizeWithoutEof("CC=O");

            assertThat(tokens).extracting(Token::position).containsExactly(0, 1, 2, 3);
        }

        @Test
        void records_the_source_position_of_a_percent_ring_bond_at_the_percent_sign()
        {
            Token token = tokenizeWithoutEof("%12").getFirst();

            assertThat(token.position()).isZero();
        }
    }

    @Nested
    class Errors
    {
        @Test
        void throws_for_an_unexpected_character_outside_brackets()
        {
            assertThatThrownBy(() -> tokenize("?"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Unexpected character");
        }

        @Test
        void throws_for_an_unexpected_character_inside_brackets()
        {
            assertThatThrownBy(() -> tokenize("[?]"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Unexpected character");
        }

        @Test
        void does_not_itself_validate_that_a_bracket_atom_is_terminated()
        {
            // the lexer leaves bracket-closure validation to the parser; it just lexes
            // through to EOF
            assertThat(tokenizeWithoutEof("[C"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "C"));
        }

        @Test
        void throws_for_a_percent_sign_not_followed_by_two_digits()
        {
            assertThatThrownBy(() -> tokenize("%1"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected two digits after '%'");
        }

        @Test
        void throws_for_a_percent_sign_followed_by_a_non_digit()
        {
            assertThatThrownBy(() -> tokenize("%1C"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected two digits after '%'");
        }

        @Test
        void throws_for_a_trailing_percent_sign_with_no_digits()
        {
            assertThatThrownBy(() -> tokenize("%"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected two digits after '%'");
        }

        @Test
        void exception_carries_the_offending_source_position()
        {
            InvalidNotationException exception = catchInvalidSmiles(() -> tokenize("CC?"));

            assertThat(exception.position()).isEqualTo(2);
        }

        private InvalidNotationException catchInvalidSmiles(Runnable action)
        {
            try
            {
                action.run();
                throw new AssertionError("Expected an InvalidNotationException to be thrown");
            }
            catch (InvalidNotationException e)
            {
                return e;
            }
        }
    }
}
