package com.ruben.chemistry.smarts;

/**
 * A single lexical token produced by {@link Lexer}.
 *
 * @param type the token's lexical category
 * @param text the token's source text; for a two-digit {@link TokenType#RING_BOND} the leading {@code %} is removed
 * @param position 0-based offset into the source string where this token begins
 */
public record Token(TokenType type, String text, int position)
{
}
