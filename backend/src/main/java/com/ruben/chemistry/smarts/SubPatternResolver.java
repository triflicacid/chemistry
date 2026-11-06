package com.ruben.chemistry.smarts;

import com.ruben.chemistry.query.Query;

/**
 * Resolves a recursive SMARTS primitive ({@code $(...)}) into the {@link Query} it denotes.
 * <p>
 * The cursor is positioned at the first token after the {@link TokenType#EXPRESSION_START}
 * marker. An implementation consumes everything up to and including {@link TokenType#EXPRESSION_END}.
 */
@FunctionalInterface
interface SubPatternResolver
{
    /**
     * @param cursor a cursor positioned at the recursive primitive's first token
     * @return the pattern it denotes
     */
    Query resolve(TokenCursor cursor);
}
