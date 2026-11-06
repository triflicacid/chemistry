package com.ruben.chemistry.smiles;

import com.ruben.chemistry.notation.AbstractLexer;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.notation.NotationSymbols;

/**
 * A lexer for SMILES strings.
 */
public final class Lexer extends AbstractLexer<Token>
{
    private final String source;

    private int position;
    private boolean inBracket;

    public Lexer(String source)
    {
        this.source = source;
    }

    @Override
    protected boolean isEof(Token token)
    {
        return token.type() == TokenType.EOF;
    }

    /**
     * Lexes the single next token from the current scan position, advancing it.
     *
     * @return the lexed token, or a {@link TokenType#EOF} token if the source is exhausted
     * @throws InvalidNotationException if the upcoming text is not a valid token
     */
    @Override
    protected Token lexNext()
    {
        if (position >= source.length())
        {
            return new Token(TokenType.EOF, "", source.length());
        }

        if (inBracket)
        {
            if (source.charAt(position) == ']')
            {
                Token token = emit(TokenType.BRACKET_CLOSE, "]");
                inBracket = false;
                return token;
            }
            return lexBracketToken();
        }

        if (source.charAt(position) == '[')
        {
            Token token = emit(TokenType.BRACKET_OPEN, "[");
            inBracket = true;
            return token;
        }

        return lexOutsideBracketToken();
    }

    /**
     * Lexes a single token outside any bracket atom, from the current scan position.
     *
     * @return the lexed token
     * @throws InvalidNotationException if the upcoming text is not a valid token
     */
    private Token lexOutsideBracketToken()
    {
        char ch = source.charAt(position);

        if (ch == '(')
        {
            return emit(TokenType.BRANCH_OPEN, "(");
        }
        if (ch == ')')
        {
            return emit(TokenType.BRANCH_CLOSE, ")");
        }
        if (ch == '.')
        {
            return emit(TokenType.DOT, ".");
        }
        if (NotationSymbols.isBondSymbol(ch))
        {
            return emit(TokenType.BOND, String.valueOf(ch));
        }
        if (ch == '%')
        {
            return lexPercentRingBond();
        }
        if (Character.isDigit(ch))
        {
            return emit(TokenType.RING_BOND, String.valueOf(ch));
        }
        if (ch == '*')
        {
            return emit(TokenType.ATOM, "*");
        }
        if (NotationSymbols.startsWithTwoCharOrganicAtom(source, position))
        {
            return emit(TokenType.ATOM, source.substring(position, position + 2));
        }
        if (NotationSymbols.isSingleCharOrganicAtom(ch))
        {
            return emit(TokenType.ATOM, String.valueOf(ch));
        }

        throw new InvalidNotationException("Unexpected character '" + ch + "'", source, position);
    }

    /**
     * Lexes a single token inside a bracket atom, from the current scan position
     * (the closing {@code ]} is handled by {@link #lexNext()}).
     *
     * @return the lexed token
     * @throws InvalidNotationException if the upcoming text is not a valid token
     */
    private Token lexBracketToken()
    {
        char ch = source.charAt(position);

        if (Character.isDigit(ch))
        {
            return lexNumberRun();
        }
        if (ch == '@')
        {
            return lexChirality();
        }
        if (ch == '+' || ch == '-')
        {
            return emit(TokenType.SIGN, String.valueOf(ch));
        }
        if (ch == ':')
        {
            return emit(TokenType.COLON, ":");
        }
        if (ch == '*')
        {
            return emit(TokenType.ELEMENT, "*");
        }

        int elementLength = NotationSymbols.elementSymbolLengthAt(source, position);
        if (elementLength > 0)
        {
            return emit(TokenType.ELEMENT, source.substring(position, position + elementLength));
        }

        throw new InvalidNotationException("Unexpected character '" + ch + "' in bracket atom", source, position);
    }

    /**
     * Lexes a run of one or more digits at the current scan position into a single
     * {@link TokenType#NUMBER} token.
     *
     * @return the lexed token
     */
    private Token lexNumberRun()
    {
        int start = position;
        int end = start;
        while (end < source.length() && Character.isDigit(source.charAt(end)))
        {
            end++;
        }

        String text = source.substring(start, end);
        position = end;
        return new Token(TokenType.NUMBER, text, start);
    }

    /**
     * Lexes a chirality marker ({@code @} or {@code @@}) at the current scan position.
     *
     * @return the lexed token
     */
    private Token lexChirality()
    {
        int start = position;
        boolean doubled = start + 1 < source.length() && source.charAt(start + 1) == '@';
        String text = doubled ? "@@" : "@";
        position = start + text.length();
        return new Token(TokenType.CHIRALITY, text, start);
    }

    /**
     * Lexes a {@code %nn} ring-closure number at the current scan position, producing a
     * single {@link TokenType#RING_BOND} token whose text is the two-digit number without
     * the {@code %}.
     *
     * @return the lexed token
     * @throws InvalidNotationException if {@code %} is not followed by exactly two digits
     */
    private Token lexPercentRingBond()
    {
        int start = position;
        if (start + 3 > source.length()
            || !Character.isDigit(source.charAt(start + 1))
            || !Character.isDigit(source.charAt(start + 2)))
        {
            throw new InvalidNotationException("Expected two digits after '%' in ring-closure number", source, start);
        }

        String text = source.substring(start + 1, start + 3);
        position = start + 3;
        return new Token(TokenType.RING_BOND, text, start);
    }

    /**
     * Builds a token of known fixed text at the current scan position and advances the
     * scan position past it.
     *
     * @param type the token's lexical category
     * @param text the token's source text
     * @return the built token
     */
    private Token emit(TokenType type, String text)
    {
        Token token = new Token(type, text, position);
        position += text.length();
        return token;
    }
}
