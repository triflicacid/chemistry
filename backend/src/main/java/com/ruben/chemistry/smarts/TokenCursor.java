package com.ruben.chemistry.smarts;

/**
 * A source of tokens consumed one at a time, with a small bounded look-ahead.
 */
interface TokenCursor
{
    /**
     * Looks ahead at a token without consuming it or any token before it.
     *
     * @param distance 0 for the next token, 1 for the one after it, and so on
     * @return the token {@code distance} positions ahead of the current one, or EOF
     */
    Token peek(int distance);

    /**
     * @return the next token, without consuming it. Equivalent to {@code peek(0)}
     */
    default Token peek()
    {
        return peek(0);
    }

    /**
     * @return the next token, consuming it
     */
    Token next();

    /**
     * @return {@code true} if {@link #peek()} would return the end-of-source marker token
     */
    boolean isAtEnd();
}
