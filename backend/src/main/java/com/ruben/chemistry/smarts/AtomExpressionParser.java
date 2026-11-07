package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.query.AtomPredicate;
import com.ruben.chemistry.query.AtomPredicates;

import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Parses one bracket atom's primitive-expression tokens.
 *
 * <p>Examples:
 * <ul>
 *     <li>{@code [C,N]} - carbon or nitrogen</li>
 *     <li>{@code [CH2]} - carbon with exactly 2 hydrogens (implicit and)</li>
 *     <li>{@code [R;!X4]} - in a ring, and not 4-connected</li>
 *     <li>{@code [$(C=O)]} - bound to a carbon that itself has a double bond to oxygen</li>
 * </ul>
 */
final class AtomExpressionParser
{
    /**
     * The result of parsing one bracket atom.
     *
     * @param predicate the predicate its primitives denote
     * @param chirality its declared chirality, if any
     */
    public record Result(AtomPredicate predicate, Optional<Chirality> chirality)
    {
    }

    /**
     * Token types that keep an {@code &}-chain going: the operator itself, anything that can
     * start a primitive (implicit and), or a chirality marker (absorbed).
     */
    private static final Set<TokenType> CONTINUES_AND_CHAIN = Set.of(
        TokenType.AND, TokenType.ELEMENT, TokenType.HASH, TokenType.STRUCTURAL, TokenType.SIGN,
        TokenType.COLON, TokenType.NUMBER, TokenType.NOT, TokenType.EXPRESSION_START, TokenType.CHIRALITY);

    private final TokenCursor cursor;
    private final String source;
    private final SubPatternResolver subPatternResolver;
    private Chirality chirality;

    /**
     * @param cursor a cursor positioned at this bracket atom's first inner token
     * @param source the full source string {@code cursor}'s tokens were lexed from, used to render error messages
     * @param subPatternResolver resolves any {@code $(...)} recursive primitives found
     */
    public AtomExpressionParser(TokenCursor cursor, String source, SubPatternResolver subPatternResolver)
    {
        this.cursor = cursor;
        this.source = source;
        this.subPatternResolver = subPatternResolver;
    }

    /**
     * @return the predicate and chirality denoted by this parser's tokens
     * @throws InvalidNotationException if the tokens don't form a valid atom expression
     */
    public Result parse()
    {
        if (!hasMore())
        {
            throw new InvalidNotationException("Atom expression must not be empty", source, 0);
        }

        AtomPredicate predicate = parseSemiExpression();
        return new Result(predicate, Optional.ofNullable(chirality));
    }

    /**
     * Lowest precedence: {@code ;} is logical AND.
     * E.g. {@code [C,N;R]} is "(carbon or nitrogen) and in a ring".
     *
     * @return the predicate the expression denotes
     */
    private AtomPredicate parseSemiExpression()
    {
        AtomPredicate result = parseOrExpression();
        while (hasMore() && peek().type() == TokenType.SEMI)
        {
            consume();
            result = result.and(parseOrExpression());
        }
        return result;
    }

    /**
     * {@code ,} is logical OR, binding tighter than {@code ;} but looser than {@code &}.
     * E.g. {@code [C,N]} is "carbon or nitrogen".
     *
     * @return the predicate the expression denotes
     */
    private AtomPredicate parseOrExpression()
    {
        AtomPredicate result = parseAndExpression();
        while (hasMore() && peek().type() == TokenType.OR)
        {
            consume();
            result = result.or(parseAndExpression());
        }
        return result;
    }

    /**
     * {@code &} is logical AND, same as two primitives written with no operator between them.
     * E.g. {@code [C&R]} and {@code [CR]} both mean "carbon and in a ring".
     *
     * @return the predicate the expression denotes
     */
    private AtomPredicate parseAndExpression()
    {
        // a chirality marker is not a predicate, so remove it
        absorbChiralityIfPresent();
        AtomPredicate result = parseUnary();
        while (hasMore() && CONTINUES_AND_CHAIN.contains(peek().type()))
        {
            if (!absorbChiralityIfPresent())
            {
                // the operator is optional; anything that could start a primitive continues the chain
                if (peek().type() == TokenType.AND)
                {
                    consume();
                }
                result = result.and(parseUnary());
            }
        }
        return result;
    }

    /**
     * Consumes and records the chirality marker at the current position, if there is one.
     *
     * @return {@code true} if a chirality marker was there to absorb
     * @throws InvalidNotationException if a chirality marker was already recorded
     */
    private boolean absorbChiralityIfPresent()
    {
        if (hasMore() && peek().type() == TokenType.CHIRALITY)
        {
            Token token = consume();
            if (chirality != null)
            {
                throw new InvalidNotationException("An atom cannot declare chirality twice", source, token.position());
            }
            chirality = Chirality.fromSymbol(token.text());
            return true;
        }
        return false;
    }

    /**
     * Highest precedence: {@code !} is logical NOT, negating a single primitive.
     * E.g. {@code [!C]} is "not carbon".
     *
     * @return the predicate the primitive denotes, negated once per {@code !}
     * @throws InvalidNotationException if a primitive was expected but the tokens ran out
     */
    private AtomPredicate parseUnary()
    {
        if (!hasMore())
        {
            throw new InvalidNotationException("Expected an atom primitive", source, source.length());
        }
        if (peek().type() == TokenType.NOT)
        {
            consume();
            return parseUnary().negate();
        }
        return parsePrimitive();
    }

    /**
     * @return the predicate denoted by the single primitive at the current position
     * @throws InvalidNotationException if the current token isn't a recognised primitive
     */
    private AtomPredicate parsePrimitive()
    {
        Token token = consume();
        return switch (token.type())
        {
            case NUMBER -> AtomPredicates.isotope(Integer.parseInt(token.text()));
            case ELEMENT -> elementPrimitive(token.text());
            case HASH -> AtomPredicates.atomicNumber(requireNumber('#'));
            case STRUCTURAL -> structuralPrimitive(token.text());
            case SIGN -> chargePrimitive(token);
            case COLON -> AtomPredicates.atomClass(requireNumber(':'));
            case EXPRESSION_START -> AtomPredicates.recursive(subPatternResolver.resolve(cursor));
            default -> throw new InvalidNotationException(
                "Unexpected token '" + token.text() + "' in atom expression", source, token.position());
        };
    }

    /**
     * @param text a just-consumed {@link TokenType#ELEMENT} token's text: {@code *}, an element symbol, or {@code H}
     * @return the predicate that element primitive denotes
     */
    private AtomPredicate elementPrimitive(String text)
    {
        if (text.equals("*"))
        {
            return AtomPredicates.any();
        }
        // H inside brackets is the hydrogen count, not the element (even just [H])
        // TODO this is what Daylight does, but feels off
        if (text.equals("H"))
        {
            return countedPrimitive(AtomPredicates::hydrogenCount, 1);
        }
        if (Character.isLowerCase(text.charAt(0)))
        {
            return AtomPredicates.aromatic(canonicalElement(text));
        }
        return AtomPredicates.element(text);
    }

    /**
     * @param letter a just-consumed {@link TokenType#STRUCTURAL} token's text: one of {@code R r D v X h}
     * @return the predicate that structural primitive denotes
     */
    private AtomPredicate structuralPrimitive(String letter)
    {
        // R and h stand alone as well as taking a count, the others require a count
        return switch (letter)
        {
            case "R" -> hasNumberNext() ? AtomPredicates.ringCount(consumeNumber()) : AtomPredicates.inRing();
            case "h" -> countedPrimitive(AtomPredicates::implicitHydrogenCount, 1);
            case "r" -> AtomPredicates.ringSize(requireNumber('r'));
            case "D" -> AtomPredicates.degree(requireNumber('D'));
            case "v" -> AtomPredicates.valence(requireNumber('v'));
            case "X" -> AtomPredicates.connectivity(requireNumber('X'));
            default -> throw new IllegalStateException("Unreachable structural letter '" + letter + "'");
        };
    }

    /**
     * @param firstSign a just-consumed {@link TokenType#SIGN} token
     * @return the predicate matching the charge the sign (and anything after it) denotes
     */
    private AtomPredicate chargePrimitive(Token firstSign)
    {
        int sign = firstSign.text().equals("+") ? 1 : -1;

        if (hasNumberNext())
        {
            return AtomPredicates.charge(sign * consumeNumber());
        }

        // no number, so this is the repeated-sign notation: [Ca++] means the same as [Ca+2]
        int magnitude = 1;
        while (hasMore() && peek().type() == TokenType.SIGN && peek().text().equals(firstSign.text()))
        {
            consume();
            magnitude++;
        }
        return AtomPredicates.charge(sign * magnitude);
    }

    /**
     * @param factory builds the predicate from an explicit count
     * @param defaultCount the count to use when no {@link TokenType#NUMBER} follows
     * @return the predicate for the count written, or for {@code defaultCount} if none was
     */
    private AtomPredicate countedPrimitive(IntFunction<AtomPredicate> factory, int defaultCount)
    {
        return factory.apply(hasNumberNext() ? consumeNumber() : defaultCount);
    }

    /**
     * @param primitiveLetter the primitive character a number is required after, for the error message
     * @return the following number's value
     * @throws InvalidNotationException if the next token isn't a {@link TokenType#NUMBER}
     */
    private int requireNumber(char primitiveLetter)
    {
        if (!hasNumberNext())
        {
            int position = hasMore() ? peek().position() : source.length();
            throw new InvalidNotationException("Expected a number after '" + primitiveLetter + "'", source, position);
        }
        return consumeNumber();
    }

    /**
     * @return {@code true} if a {@link TokenType#NUMBER} is next
     */
    private boolean hasNumberNext()
    {
        return hasMore() && peek().type() == TokenType.NUMBER;
    }

    /**
     * @return the value of the next token, which must be a {@link TokenType#NUMBER}
     */
    private int consumeNumber()
    {
        return Integer.parseInt(consume().text());
    }

    /**
     * @param text an element symbol as lexed, lower case
     * @return {@code text} with its first letter upper-cased, matching {@link com.ruben.chemistry.core.Elements}'s canonical casing
     */
    private static String canonicalElement(String text)
    {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase();
    }

    /**
     * @return {@code true} if this atom expression has any token left
     */
    private boolean hasMore()
    {
        return !cursor.isAtEnd();
    }

    /**
     * @return the next token, without consuming it
     */
    private Token peek()
    {
        return cursor.peek();
    }

    /**
     * @return the next token, consuming it
     */
    private Token consume()
    {
        return cursor.next();
    }
}
