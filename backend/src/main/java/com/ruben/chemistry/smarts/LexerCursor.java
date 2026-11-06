package com.ruben.chemistry.smarts;

/**
 * A {@link TokenCursor} that lexes its tokens on demand from a {@link Lexer}.
 */
final class LexerCursor implements TokenCursor
{
    private final Lexer lexer;

    LexerCursor(Lexer lexer)
    {
        this.lexer = lexer;
    }

    @Override
    public Token peek(int distance)
    {
        return lexer.peek(distance);
    }

    @Override
    public Token next()
    {
        return lexer.next();
    }

    @Override
    public boolean isAtEnd()
    {
        return lexer.isAtEnd();
    }
}
