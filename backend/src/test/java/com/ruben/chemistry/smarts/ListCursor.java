package com.ruben.chemistry.smarts;

import java.util.List;

/**
 * A {@link TokenCursor} over a fixed list of tokens already extracted elsewhere.
 */
final class ListCursor implements TokenCursor
{
    private final List<Token> tokens;
    private int index;

    /**
     * @param tokens the tokens to parse, excluding any terminating end-of-source marker
     */
    ListCursor(List<Token> tokens)
    {
        this.tokens = tokens;
    }

    @Override
    public Token peek(int distance)
    {
        int i = index + distance;
        return i < tokens.size() ? tokens.get(i) : new Token(TokenType.EOF, "", -1);
    }

    @Override
    public Token next()
    {
        Token token = peek();
        if (index < tokens.size())
        {
            index++;
        }
        return token;
    }

    @Override
    public boolean isAtEnd()
    {
        return index >= tokens.size();
    }
}
