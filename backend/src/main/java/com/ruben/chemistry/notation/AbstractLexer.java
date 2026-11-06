package com.ruben.chemistry.notation;

import java.util.ArrayList;
import java.util.List;

/**
 * Lookahead-buffering mechanics shared by any lexer that tokenizes a source string on demand,
 * one token at a time, with unbounded lookahead.
 *
 * @param <T> the token type this lexer produces
 */
public abstract class AbstractLexer<T>
{
    private final List<T> lookahead = new ArrayList<>();

    /**
     * Consumes and returns the next token.
     *
     * @return the next token in source order
     */
    public T next()
    {
        ensureBuffered(1);
        return lookahead.removeFirst();
    }

    /**
     * Looks at the next token without consuming it. Equivalent to {@code peek(0)}.
     *
     * @return the next token in source order
     */
    public T peek()
    {
        return peek(0);
    }

    /**
     * Looks ahead at a token without consuming it or any token before it.
     *
     * @param distance 0 for the next token, 1 for the one after it, and so on
     * @return the token {@code distance} positions ahead of the current one
     * @throws IllegalArgumentException if {@code distance} is negative
     */
    public T peek(int distance)
    {
        if (distance < 0)
        {
            throw new IllegalArgumentException("distance must be non-negative, was " + distance);
        }

        ensureBuffered(distance + 1);
        return lookahead.get(distance);
    }

    /**
     * @return {@code true} if {@link #peek()} would return the end-of-source marker token
     */
    public boolean isAtEnd()
    {
        return isEof(peek());
    }

    /**
     * Consumes and returns every remaining token, terminated by a single end-of-source marker token.
     *
     * @return the remaining tokens in source order
     */
    public List<T> tokenize()
    {
        List<T> tokens = new ArrayList<>();
        T token;
        do
        {
            token = next();
            tokens.add(token);
        }
        while (!isEof(token));

        return tokens;
    }

    /**
     * Lexes additional tokens into the lookahead buffer until it holds at least {@code count}.
     *
     * @param count the minimum number of buffered tokens required
     */
    private void ensureBuffered(int count)
    {
        while (lookahead.size() < count)
        {
            lookahead.add(lexNext());
        }
    }

    /**
     * Lexes the single next token from the current scan position, advancing it.
     *
     * @return the lexed token, or the end-of-source marker token if the source is exhausted
     */
    protected abstract T lexNext();

    /**
     * @param token a token produced by {@link #lexNext()}
     * @return {@code true} if {@code token} is the end-of-source marker
     */
    protected abstract boolean isEof(T token);
}
