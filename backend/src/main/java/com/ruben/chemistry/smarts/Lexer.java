package com.ruben.chemistry.smarts;

import com.ruben.chemistry.notation.AbstractLexer;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.notation.NotationSymbols;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A lexer for SMARTS strings.
 */
public final class Lexer extends AbstractLexer<Token>
{
    /** Bracket-only structural primitive letters that aren't element symbols: {@code R r D v X h}. */
    private static final String STRUCTURAL_LETTERS = "RrDvXh";

    private final String source;

    /** Tokens already lexed from a recursive primitive, handed out before scanning continues. */
    private final Deque<Token> pending = new ArrayDeque<>();

    private int position;
    /** Which of the two token sets to lex against; toggled by the brackets themselves. */
    private boolean inBracket;

    /**
     * @param source the SMARTS string to lex
     */
    public Lexer(String source)
    {
        this.source = source;
    }

    @Override
    protected boolean isEof(Token token)
    {
        return token.type() == TokenType.EOF;
    }

    @Override
    protected Token lexNext()
    {
        // drain any previously-parsed tokens first
        if (!pending.isEmpty())
        {
            return pending.poll();
        }

        if (position >= source.length())
        {
            return new Token(TokenType.EOF, "", source.length());
        }

        char ch = source.charAt(position);

        if (inBracket && ch == ']')
        {
            Token token = emit(TokenType.BRACKET_CLOSE, "]");
            inBracket = false;
            return token;
        }

        // the logical operators mean the same thing in both modes, so this now
        Token logical = lexLogicalOperator(ch);
        if (logical != null)
        {
            return logical;
        }

        if (inBracket)
        {
            return lexBracketToken();
        }

        if (ch == '[')
        {
            Token token = emit(TokenType.BRACKET_OPEN, "[");
            inBracket = true;
            return token;
        }

        return lexOutsideBracketToken();
    }

    /**
     * Lexes a logical-combination operator, valid both inside and outside a bracket atom.
     *
     * @param ch the character at the current scan position
     * @return the lexed token, or {@code null} if {@code ch} is not a logical operator
     */
    private Token lexLogicalOperator(char ch)
    {
        return switch (ch)
        {
            case '!' -> emit(TokenType.NOT, "!");
            case ';' -> emit(TokenType.SEMI, ";");
            case '&' -> emit(TokenType.AND, "&");
            case ',' -> emit(TokenType.OR, ",");
            default -> null;
        };
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
        if (ch == '~' || ch == '@' || NotationSymbols.isBondSymbol(ch))
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
        // two-character symbols first, so Cl lexes as one atom rather than C followed by a stray l
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
     * (the closing {@code ]} and logical operators are handled by {@link #lexNext()}).
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
        if (ch == '#')
        {
            return emit(TokenType.HASH, "#");
        }
        if (ch == '$')
        {
            return lexExpression();
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

        if (STRUCTURAL_LETTERS.indexOf(ch) >= 0)
        {
            return emit(TokenType.STRUCTURAL, String.valueOf(ch));
        }

        throw new InvalidNotationException("Unexpected character '" + ch + "' in bracket atom", source, position);
    }

    /**
     * Lexes a {@code $(...)} recursive primitive at the current scan position.
     *
     * @return an {@link TokenType#EXPRESSION_START} token
     * @throws InvalidNotationException if {@code $} isn't followed by {@code (}, or its matching {@code )} is never found
     */
    private Token lexExpression()
    {
        int dollarPosition = position;
        if (position + 1 >= source.length() || source.charAt(position + 1) != '(')
        {
            throw new InvalidNotationException("Expected '(' after '$' in recursive SMARTS primitive", source, position + 1);
        }

        // scan for the matching ')', counting nested brackets
        int start = position + 2;
        int depth = 1;
        int end = start;
        while (end < source.length() && depth > 0)
        {
            char ch = source.charAt(end);
            if (ch == '(')
            {
                depth++;
            }
            else if (ch == ')')
            {
                depth--;
            }
            end++;
        }

        if (depth != 0)
        {
            throw new InvalidNotationException("Unterminated recursive SMARTS primitive, missing ')'", source, dollarPosition);
        }

        // tokenize the contents of $(...), queueing up the tokens
        String inner = source.substring(start, end - 1);
        List<Token> innerTokens = new Lexer(inner).tokenize();
        for (int i = 0; i < innerTokens.size() - 1; i++) // the last token is always EOF, which isn't spliced in
        {
            Token token = innerTokens.get(i);
            pending.add(new Token(token.type(), token.text(), token.position() + start));
        }
        // mark the end of the expression
        pending.add(new Token(TokenType.EXPRESSION_END, ")", end - 1));

        position = end;
        // indicate that we are now in an expression
        return new Token(TokenType.EXPRESSION_START, "$(", dollarPosition);
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
