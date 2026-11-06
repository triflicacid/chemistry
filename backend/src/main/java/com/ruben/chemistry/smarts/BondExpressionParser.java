package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.BondDirection;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.query.BondPredicate;
import com.ruben.chemistry.query.BondPredicates;

import java.util.List;
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
public final class BondExpressionParser
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

    private final List<Token> tokens;
    private final String source;
    private BondDirection direction;
    private int index;

    /**
     * @param tokens the bond's tokens
     * @param source the full source string {@code tokens} were lexed from, used only to render error messages
     */
    public BondExpressionParser(List<Token> tokens, String source)
    {
        this.tokens = tokens;
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
        if (hasMore())
        {
            Token trailing = peek();
            throw new InvalidNotationException("Expected the bond expression to end, but found '" + trailing.text() + "'", source, trailing.position());
        }
        return new Result(predicate, Optional.ofNullable(direction));
    }

    /**
     * Lowest precedence: {@code ;} is logical AND.
     * E.g. {@code -,=;@} is "(single or double) and a ring bond".
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
     * {@code ,} is logical OR.
     * E.g. {@code -,=} is "single or double".
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
     */
    private BondPredicate parseAndExpression()
    {
        BondPredicate result = parseUnary();
        while (hasMore() && CONTINUES_AND_CHAIN.contains(peek().type()))
        {
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

    private boolean hasMore()
    {
        return index < tokens.size();
    }

    private Token peek()
    {
        return tokens.get(index);
    }

    private Token consume()
    {
        return tokens.get(index++);
    }
}
