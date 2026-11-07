package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.BondDirection;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.query.BondPredicate;
import com.ruben.chemistry.query.BondPredicates;

import java.util.Optional;
import java.util.Set;

/**
 * Parses one bond's primitive-expression tokens.
 *
 * <p>Examples:
 * <ul>
 *     <li>{@code -,=} - single or double</li>
 *     <li>{@code -@} - single, and a ring bond (implicit and)</li>
 *     <li>{@code !@} - not a ring bond (a chain bond)</li>
 *     <li>{@code /} - a directional single bond</li>
 * </ul>
 */
final class BondExpressionParser
{
    /**
     * The result of parsing one bond.
     *
     * @param predicate the predicate its primitives denote
     * @param direction its declared direction, if any
     */
    public record Result(BondPredicate predicate, Optional<BondDirection> direction)
    {
    }

    /** Token types that keep an {@code &}-chain going: the operator itself, or anything that can start a primitive (implicit and). */
    private static final Set<TokenType> CONTINUES_AND_CHAIN = Set.of(TokenType.AND, TokenType.BOND, TokenType.NOT);

    private final TokenCursor cursor;
    private final String source;
    private BondDirection direction;

    /**
     * @param cursor a cursor positioned at this bond expression's first token
     * @param source the full source string {@code cursor}'s tokens were lexed from, used only to render error messages
     */
    public BondExpressionParser(TokenCursor cursor, String source)
    {
        this.cursor = cursor;
        this.source = source;
    }

    /**
     * @return the predicate and direction denoted by this parser's tokens
     * @throws InvalidNotationException if the tokens don't form a valid bond expression
     */
    public Result parse()
    {
        if (!hasMore())
        {
            throw new InvalidNotationException("Bond expression must not be empty", source, 0);
        }

        BondPredicate predicate = parseSemiExpression();
        return new Result(predicate, Optional.ofNullable(direction));
    }

    /**
     * Lowest precedence: {@code ;} is logical AND.
     * E.g. {@code -,=;@} is "(single or double) and a ring bond".
     *
     * @return the predicate the expression denotes
     */
    private BondPredicate parseSemiExpression()
    {
        BondPredicate result = parseOrExpression();
        while (hasMore() && peek().type() == TokenType.SEMI)
        {
            consume();
            result = result.and(parseOrExpression());
        }
        return result;
    }

    /**
     * {@code ,} is logical OR, binding tighter than {@code ;} but looser than {@code &}.
     * E.g. {@code -,=} is "single or double".
     *
     * @return the predicate the expression denotes
     */
    private BondPredicate parseOrExpression()
    {
        BondPredicate result = parseAndExpression();
        while (hasMore() && peek().type() == TokenType.OR)
        {
            consume();
            result = result.or(parseAndExpression());
        }
        return result;
    }

    /**
     * {@code &} is logical AND, same as two primitives written with no operator between them.
     * E.g. {@code -&@} and {@code -@} both mean "single and a ring bond".
     *
     * @return the predicate the expression denotes
     */
    private BondPredicate parseAndExpression()
    {
        BondPredicate result = parseUnary();
        while (hasMore() && CONTINUES_AND_CHAIN.contains(peek().type()))
        {
            // the operator is optional: anything that could start a primitive continues the chain
            if (peek().type() == TokenType.AND)
            {
                consume();
            }
            result = result.and(parseUnary());
        }
        return result;
    }

    /**
     * Highest precedence: {@code !} is logical NOT, negating a single primitive.
     * E.g. {@code !@} is "not a ring bond" (a chain bond).
     *
     * @return the predicate the primitive denotes, negated once per {@code !}
     * @throws InvalidNotationException if a primitive was expected but the tokens ran out
     */
    private BondPredicate parseUnary()
    {
        if (!hasMore())
        {
            throw new InvalidNotationException("Expected a bond primitive", source, source.length());
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
    private BondPredicate parsePrimitive()
    {
        Token token = consume();
        if (token.type() != TokenType.BOND)
        {
            throw new InvalidNotationException("Unexpected token '" + token.text() + "' in bond expression", source, token.position());
        }

        char symbol = token.text().charAt(0);
        if (symbol == '~')
        {
            return BondPredicates.any();
        }
        if (symbol == '@')
        {
            return BondPredicates.ringBond();
        }
        if (BondDirection.isSymbol(symbol))
        {
            direction = BondDirection.fromSymbol(symbol);
            return BondPredicates.order(BondOrder.SINGLE);
        }
        return BondPredicates.order(BondOrder.fromSymbol(symbol));
    }

    /**
     * @return {@code true} if this bond expression has any token left
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
