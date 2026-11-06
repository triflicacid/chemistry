package com.ruben.chemistry.smarts;

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
            for (char element : "BCNOPSFI".toCharArray())
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
        void tokenizes_each_bond_symbol_including_the_smarts_extensions()
        {
            for (char symbol : "-=#$:/\\~@".toCharArray())
            {
                assertThat(tokenizeWithoutEof(String.valueOf(symbol)))
                    .extracting(Token::type, Token::text)
                    .containsExactly(tuple(TokenType.BOND, String.valueOf(symbol)));
            }
        }

        @Test
        void tokenizes_logical_operators_between_bond_terms()
        {
            assertThat(tokenizeWithoutEof("-,="))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BOND, "-"),
                    tuple(TokenType.OR, ","),
                    tuple(TokenType.BOND, "="));
        }

        @Test
        void tokenizes_not_immediately_before_a_bond_symbol()
        {
            assertThat(tokenizeWithoutEof("!@"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.NOT, "!"), tuple(TokenType.BOND, "@"));
        }

        @Test
        void tokenizes_semi_and_and_between_bond_terms()
        {
            assertThat(tokenizeWithoutEof("-;="))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BOND, "-"),
                    tuple(TokenType.SEMI, ";"),
                    tuple(TokenType.BOND, "="));

            assertThat(tokenizeWithoutEof("-&="))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BOND, "-"),
                    tuple(TokenType.AND, "&"),
                    tuple(TokenType.BOND, "="));
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
        void tokenizes_a_percent_prefixed_two_digit_ring_bond_without_the_percent_sign()
        {
            assertThat(tokenizeWithoutEof("%12"))
                .extracting(Token::type, Token::text)
                .containsExactly(tuple(TokenType.RING_BOND, "12"));
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
        void tokenizes_the_atomic_number_primitive()
        {
            assertThat(tokenizeWithoutEof("[#6]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.HASH, "#"),
                    tuple(TokenType.NUMBER, "6"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_structural_primitive_letters_with_an_optional_following_count()
        {
            for (char letter : "RrDvX".toCharArray())
            {
                assertThat(tokenizeWithoutEof("[" + letter + "2]"))
                    .extracting(Token::type, Token::text)
                    .containsExactly(
                        tuple(TokenType.BRACKET_OPEN, "["),
                        tuple(TokenType.STRUCTURAL, String.valueOf(letter)),
                        tuple(TokenType.NUMBER, "2"),
                        tuple(TokenType.BRACKET_CLOSE, "]"));
            }
        }

        @Test
        void tokenizes_lowercase_h_as_a_structural_primitive()
        {
            assertThat(tokenizeWithoutEof("[h0]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.STRUCTURAL, "h"),
                    tuple(TokenType.NUMBER, "0"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_a_chirality_marker()
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
        }

        @Test
        void tokenizes_the_atom_class_colon()
        {
            assertThat(tokenizeWithoutEof("[C:1]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "C"),
                    tuple(TokenType.COLON, ":"),
                    tuple(TokenType.NUMBER, "1"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_logical_operators_between_atom_primitives()
        {
            assertThat(tokenizeWithoutEof("[C,N]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.ELEMENT, "C"),
                    tuple(TokenType.OR, ","),
                    tuple(TokenType.ELEMENT, "N"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));

            assertThat(tokenizeWithoutEof("[R;!X4]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.STRUCTURAL, "R"),
                    tuple(TokenType.SEMI, ";"),
                    tuple(TokenType.NOT, "!"),
                    tuple(TokenType.STRUCTURAL, "X"),
                    tuple(TokenType.NUMBER, "4"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_a_recursive_primitives_contents_surrounded_by_markers()
        {
            assertThat(tokenizeWithoutEof("[$(C(=O)O)]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.EXPRESSION_START, "$("),
                    tuple(TokenType.ATOM, "C"),
                    tuple(TokenType.BRANCH_OPEN, "("),
                    tuple(TokenType.BOND, "="),
                    tuple(TokenType.ATOM, "O"),
                    tuple(TokenType.BRANCH_CLOSE, ")"),
                    tuple(TokenType.ATOM, "O"),
                    tuple(TokenType.EXPRESSION_END, ")"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void combines_a_recursive_primitive_with_others_via_implicit_and()
        {
            assertThat(tokenizeWithoutEof("[$(CC)&R]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.EXPRESSION_START, "$("),
                    tuple(TokenType.ATOM, "C"),
                    tuple(TokenType.ATOM, "C"),
                    tuple(TokenType.EXPRESSION_END, ")"),
                    tuple(TokenType.AND, "&"),
                    tuple(TokenType.STRUCTURAL, "R"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void tokenizes_nested_recursive_primitives()
        {
            assertThat(tokenizeWithoutEof("[$(C[$(N)]C)]"))
                .extracting(Token::type, Token::text)
                .containsExactly(
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.EXPRESSION_START, "$("),
                    tuple(TokenType.ATOM, "C"),
                    tuple(TokenType.BRACKET_OPEN, "["),
                    tuple(TokenType.EXPRESSION_START, "$("),
                    tuple(TokenType.ATOM, "N"),
                    tuple(TokenType.EXPRESSION_END, ")"),
                    tuple(TokenType.BRACKET_CLOSE, "]"),
                    tuple(TokenType.ATOM, "C"),
                    tuple(TokenType.EXPRESSION_END, ")"),
                    tuple(TokenType.BRACKET_CLOSE, "]"));
        }

        @Test
        void a_recursive_primitives_spliced_in_tokens_carry_positions_in_the_outer_source()
        {
            // "[$(CC)]": the second 'C' sits at index 4 in the full string, not index 1
            // (its position within the extracted "CC" substring)
            List<Token> tokens = tokenizeWithoutEof("[$(CC)]");

            assertThat(tokens).extracting(Token::position).containsExactly(0, 1, 3, 4, 5, 6);
        }

        @Test
        void resumes_lexing_the_organic_subset_after_a_bracket_atom_closes()
        {
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
        void throws_for_a_bare_parenthesis_inside_brackets_not_preceded_by_a_dollar()
        {
            // '(' / ')' are only ever reached inside brackets via lexExpression(), right after
            // a '$' - a bare one is a genuine syntax error
            assertThatThrownBy(() -> tokenize("[(C)]"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Unexpected character");
        }

        @Test
        void throws_for_a_dollar_not_followed_by_an_opening_parenthesis()
        {
            assertThatThrownBy(() -> tokenize("[$C]"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected '('");
        }

        @Test
        void throws_for_an_unterminated_recursive_primitive()
        {
            assertThatThrownBy(() -> tokenize("[$(CC]"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Unterminated");
        }
    }
}
