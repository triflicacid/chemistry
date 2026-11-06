package com.ruben.chemistry.smiles;

import com.ruben.chemistry.core.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Incrementally tokenizes a SMILES string into {@link Token}s, lexing each token on
 * demand.
 *
 * <p>Outside brackets, only organic-subset atoms ({@code B C N O P S F Cl Br I}, their
 * aromatic lowercase forms, and {@code *}) are recognised, alongside bonds, branches,
 * ring-closure numbers, and the disconnected-structure dot.
 *
 * <p>Inside a {@code [...]} bracket atom, contents are decomposed into element symbols
 * (the full periodic table plus a small set of aromatic extensions), digit runs,
 * chirality markers, charge signs, and the atom-class colon, so that a parser can
 * assemble isotope, element, chirality, hydrogen count, charge, and atom class without
 * re-lexing a raw string.
 */
public final class Lexer
{
    private static final Set<String> TWO_CHAR_ATOMS = Set.of("Cl", "Br");
    private static final String SINGLE_CHAR_ATOMS = "BCNOPSFI";
    private static final String AROMATIC_ATOMS = "bcnops";
    private static final String BOND_SYMBOLS = "-=#$:/\\";

    /** Aromatic extensions beyond the organic subset, recognised only inside brackets. */
    private static final Set<String> AROMATIC_TWO_CHAR_ELEMENTS = Set.of("se", "as", "te");

    private final String source;
    private final List<Token> lookahead = new ArrayList<>();

    private int position;
    private boolean inBracket;

    public Lexer(String source)
    {
        this.source = source;
    }

    /**
     * Consumes and returns the next token.
     *
     * @return the next token in source order
     * @throws InvalidSmilesException if the upcoming source text is not a valid token
     */
    public Token next()
    {
        ensureBuffered(1);
        return lookahead.removeFirst();
    }

    /**
     * Looks at the next token without consuming it. Equivalent to {@code peek(0)}.
     *
     * @return the next token in source order
     * @throws InvalidSmilesException if the upcoming source text is not a valid token
     */
    public Token peek()
    {
        return peek(0);
    }

    /**
     * Looks ahead at a token without consuming it or any token before it.
     *
     * @param distance 0 for the next token, 1 for the one after it, and so on
     * @return the token {@code distance} positions ahead of the current one
     * @throws IllegalArgumentException if {@code distance} is negative
     * @throws InvalidSmilesException if lexing up to {@code distance} encounters invalid source text
     */
    public Token peek(int distance)
    {
        if (distance < 0)
        {
            throw new IllegalArgumentException("distance must be non-negative, was " + distance);
        }

        ensureBuffered(distance + 1);
        return lookahead.get(distance);
    }

    /**
     * Checks whether the next token is the end-of-source marker.
     *
     * @return {@code true} if {@link #peek()} would return a {@link TokenType#EOF} token
     * @throws InvalidSmilesException if the upcoming source text is not a valid token
     */
    public boolean isAtEnd()
    {
        return peek().type() == TokenType.EOF;
    }

    /**
     * Consumes and returns every remaining token, terminated by a single
     * {@link TokenType#EOF} token.
     *
     * @return the remaining tokens in source order
     * @throws InvalidSmilesException if the upcoming source text is not a valid token
     */
    public List<Token> tokenize()
    {
        List<Token> tokens = new ArrayList<>();
        Token token;
        do
        {
            token = next();
            tokens.add(token);
        }
        while (token.type() != TokenType.EOF);

        return tokens;
    }

    /**
     * Lexes additional tokens into {@link #lookahead} until it holds at least {@code count}.
     *
     * @param count the minimum number of buffered tokens required
     * @throws InvalidSmilesException if lexing a required token encounters invalid source text
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
     * @return the lexed token, or a {@link TokenType#EOF} token if the source is exhausted
     * @throws InvalidSmilesException if the upcoming text is not a valid token
     */
    private Token lexNext()
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
     * @throws InvalidSmilesException if the upcoming text is not a valid token
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
        if (BOND_SYMBOLS.indexOf(ch) >= 0)
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
        if (startsWithTwoCharAtom())
        {
            return emit(TokenType.ATOM, source.substring(position, position + 2));
        }
        if (SINGLE_CHAR_ATOMS.indexOf(ch) >= 0 || AROMATIC_ATOMS.indexOf(ch) >= 0)
        {
            return emit(TokenType.ATOM, String.valueOf(ch));
        }

        throw new InvalidSmilesException("Unexpected character '" + ch + "'", source, position);
    }

    /**
     * Lexes a single token inside a bracket atom, from the current scan position
     * (the closing {@code ]} is handled by {@link #lexNext()}).
     *
     * @return the lexed token
     * @throws InvalidSmilesException if the upcoming text is not a valid token
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

        int elementLength = matchElementSymbol();
        if (elementLength > 0)
        {
            return emit(TokenType.ELEMENT, source.substring(position, position + elementLength));
        }

        throw new InvalidSmilesException("Unexpected character '" + ch + "' in bracket atom", source, position);
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
     * Matches an element symbol at the current scan position against the periodic table
     * and the aromatic-extension set, preferring a two-character match over a
     * one-character match.
     *
     * @return the matched length (1 or 2), or 0 if no element symbol starts there
     */
    private int matchElementSymbol()
    {
        if (position + 2 <= source.length())
        {
            String twoChars = source.substring(position, position + 2);
            if (Elements.isSymbol(twoChars) || AROMATIC_TWO_CHAR_ELEMENTS.contains(twoChars))
            {
                return 2;
            }
        }

        String oneChar = String.valueOf(source.charAt(position));
        if (Elements.isSymbol(oneChar) || AROMATIC_ATOMS.indexOf(source.charAt(position)) >= 0)
        {
            return 1;
        }

        return 0;
    }

    /**
     * Checks whether a two-character organic-subset atom symbol starts at the current
     * scan position.
     *
     * @return {@code true} if a matching two-character symbol starts there
     */
    private boolean startsWithTwoCharAtom()
    {
        if (position + 2 > source.length())
        {
            return false;
        }

        return TWO_CHAR_ATOMS.contains(source.substring(position, position + 2));
    }

    /**
     * Lexes a {@code %nn} ring-closure number at the current scan position, producing a
     * single {@link TokenType#RING_BOND} token whose text is the two-digit number without
     * the {@code %}.
     *
     * @return the lexed token
     * @throws InvalidSmilesException if {@code %} is not followed by exactly two digits
     */
    private Token lexPercentRingBond()
    {
        int start = position;
        if (start + 3 > source.length()
            || !Character.isDigit(source.charAt(start + 1))
            || !Character.isDigit(source.charAt(start + 2)))
        {
            throw new InvalidSmilesException("Expected two digits after '%' in ring-closure number", source, start);
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
