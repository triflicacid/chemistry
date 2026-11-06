package com.ruben.chemistry.smarts;

import com.ruben.chemistry.query.Query;

import java.util.List;

/**
 * Resolves a recursive SMARTS primitive ({@code $(...)}) into the {@link Query} it denotes.
 * <p>
 * The tokens are the span between a matched {@link TokenType#EXPRESSION_START} and
 * {@link TokenType#EXPRESSION_END}, excluding both markers.
 */
@FunctionalInterface
public interface SubPatternResolver
{
    /**
     * @param tokens the recursive primitive's tokens, excluding both markers
     * @return the pattern they denote
     */
    Query resolve(List<Token> tokens);
}
