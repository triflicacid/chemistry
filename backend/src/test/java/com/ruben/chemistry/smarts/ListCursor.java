package com.ruben.chemistry.smarts;

import java.util.List;

/**
 * A {@link TokenCursor} over a fixed list of tokens already extracted elsewhere.
 */
final class ListCursor implements TokenCursor
{
    private final List<Token> tokens;
    private final int endPosition;
    private int index;

    /**
     * @param tokens the tokens to parse, excluding any terminating end-of-source marker
     * @param source the full source string {@code tokens} were lexed from
     */
    ListCursor(List<Token> tokens, String source)
    {
        this.tokens = tokens;
        this.endPosition = source.length();
    }

    @Override
    public Token peek(int distance)
    {
        int i = index + distance;
        return i < tokens.size() ? tokens.get(i) : new Token(TokenType.EOF, "", endPosition);
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
