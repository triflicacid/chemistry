package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.BondDirection;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.notation.NotationSymbols;
import com.ruben.chemistry.notation.StereoTracker;
import com.ruben.chemistry.query.AtomPredicate;
import com.ruben.chemistry.query.AtomPredicates;
import com.ruben.chemistry.query.BondPredicate;
import com.ruben.chemistry.query.BondPredicates;
import com.ruben.chemistry.query.Query;
import com.ruben.chemistry.query.QueryAtom;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses a SMARTS string into a {@link Query}.
 * <p>
 * A parser consumes its source as it goes, so it cannot be reused: {@link #parseQuery()} throws if called a second time.
 */
public final class SmartsParser
{
    /** Token types that can appear in a bond expression written outside a bracket atom. */
    private static final Set<TokenType> BOND_EXPRESSION_TOKENS =
        Set.of(TokenType.BOND, TokenType.NOT, TokenType.SEMI, TokenType.AND, TokenType.OR);

    private final String source;
    private final TokenCursor cursor;
    private final Query.Builder builder = new Query.Builder();

    /** The atoms branches were opened from, innermost last, to return to as each one closes. */
    private final Deque<QueryAtom> branchStack = new ArrayDeque<>();

    /** Collects the double bonds and directional bonds parsed so far, and declares their stereo. */
    private final StereoTracker<QueryAtom> stereo = new StereoTracker<>(builder::setDoubleBondStereo);

    /** Ring-closure digits still waiting for their second occurrence, in the order they were opened. */
    private final Map<String, PendingRingClosure> openRingBonds = new LinkedHashMap<>();

    /** The atom the next one bonds to, or {@code null} at the start of a disconnected fragment. */
    private QueryAtom previousAtom;

    /** A bond expression read but not yet applied, since the atom it bonds to comes after it. */
    private BondExpressionParser.Result pendingBondResult;

    /** Whether {@link #pendingBondResult} is a plain {@code =}, which is the only form stereo can be declared for. */
    private boolean pendingIsDoubleBond;
    private int pendingBondPosition;

    /**
     * Creates a parser for the given SMARTS source string.
     *
     * @param source the SMARTS string to parse
     */
    public SmartsParser(String source)
    {
        this(new LexerCursor(new Lexer(source)), source);
    }

    /**
     * Creates a parser that consumes directly from an existing cursor.
     *
     * @param cursor the cursor to consume tokens from
     * @param source the full source string {@code cursor}'s tokens were lexed from, used only to render error messages
     */
    private SmartsParser(TokenCursor cursor, String source)
    {
        this.cursor = cursor;
        this.source = source;
    }

    /**
     * Parses this parser's tokens into a query.
     *
     * @return the parsed query
     * @throws InvalidNotationException if the tokens do not form a valid SMARTS pattern
     */
    public Query parseQuery()
    {
        return parseUntil(TokenType.EOF);
    }

    /**
     * Parses tokens up to a terminator token type, consuming it too.
     *
     * @param terminator the token type that ends this parse
     * @return the parsed query
     * @throws InvalidNotationException if the tokens do not form a valid SMARTS pattern
     */
    private Query parseUntil(TokenType terminator)
    {
        if (peek().type() == terminator)
        {
            String what = terminator == TokenType.EOF ? "SMARTS string" : "Recursive SMARTS primitive";
            throw new InvalidNotationException(what + " must not be empty", source, peek().position());
        }

        while (peek().type() != terminator)
        {
            parseNext();
        }
        // do not consume EOF
        if (terminator != TokenType.EOF)
        {
            next();
        }

        if (!branchStack.isEmpty())
        {
            throw new InvalidNotationException("Unmatched '('", source, source.length());
        }
        if (pendingBondResult != null)
        {
            throw new InvalidNotationException("Bond expression not followed by an atom", source, pendingBondPosition);
        }
        if (!openRingBonds.isEmpty())
        {
            PendingRingClosure firstUnpaired = openRingBonds.values().iterator().next();
            throw new InvalidNotationException("Unpaired ring closure digit", source, firstUnpaired.position());
        }

        return builder.build();
    }

    /**
     * Parses a single grammar element at the current token position.
     *
     * @throws InvalidNotationException if the current token cannot start a valid element here
     */
    private void parseNext()
    {
        Token token = peek();

        switch (token.type())
        {
            case ATOM -> attachAtom(parseOrganicAtom());
            case BRACKET_OPEN -> attachAtom(parseBracketAtom());
            case BOND, NOT, SEMI, AND, OR -> parseBondExpression();
            case BRANCH_OPEN -> parseBranchOpen(token);
            case BRANCH_CLOSE -> parseBranchClose(token);
            case DOT -> parseDot();
            case RING_BOND -> parseRingBond(token);
            default -> throw new InvalidNotationException("Unexpected token '" + token.text() + "'", source, token.position());
        }
    }

    /**
     * Parses an organic-subset atom token outside brackets.
     *
     * @return the added atom position
     */
    private QueryAtom parseOrganicAtom()
    {
        Token token = next();
        QueryAtom atom = new QueryAtom(organicAtomPredicate(token.text()));
        builder.addAtom(atom);
        return atom;
    }

    /**
     * @param rawSymbol an organic-subset atom symbol, e.g. {@code "c"}, {@code "Cl"}, {@code "*"}
     * @return the predicate it denotes
     */
    private AtomPredicate organicAtomPredicate(String rawSymbol)
    {
        if (rawSymbol.equals("*"))
        {
            return AtomPredicates.any();
        }
        String element = NotationSymbols.canonicalElement(rawSymbol);
        return NotationSymbols.isAromaticSymbol(rawSymbol) ? AtomPredicates.aromatic(element) : AtomPredicates.element(element);
    }

    /**
     * Parses a {@code [...]} bracket atom.
     *
     * @return the added atom position
     * @throws InvalidNotationException if the matching {@link TokenType#BRACKET_CLOSE} is never found
     */
    private QueryAtom parseBracketAtom()
    {
        next();
        AtomExpressionParser.Result result = new AtomExpressionParser(cursor, source, this::resolveSubPattern).parse();

        if (peek().type() != TokenType.BRACKET_CLOSE)
        {
            throw new InvalidNotationException("Expected ']' to close bracket atom", source, peek().position());
        }
        next();

        QueryAtom atom = new QueryAtom(result.predicate());
        builder.addAtom(atom);
        result.chirality().ifPresent(chirality -> builder.setChirality(atom, chirality));
        return atom;
    }

    /**
     * Resolves a recursive SMARTS primitive into the query it denotes.
     *
     * @param sharedCursor the cursor positioned at the recursive primitive's first token
     * @return the pattern it denotes
     */
    private Query resolveSubPattern(TokenCursor sharedCursor)
    {
        return new SmartsParser(sharedCursor, source).parseUntil(TokenType.EXPRESSION_END);
    }

    /**
     * Parses a bond expression written outside a bracket atom.
     *
     * @throws InvalidNotationException if a bond expression is already pending
     */
    private void parseBondExpression()
    {
        if (pendingBondResult != null)
        {
            throw new InvalidNotationException("Bond expression immediately follows another bond expression", source, peek().position());
        }

        // double bond stereo is only meaningful for a bond that must be a double bond, so a bare
        // '=' qualifies, while an expression such as '=,#' or '=&@' does not
        Token first = peek();
        boolean isDoubleCandidate = first.type() == TokenType.BOND
                && first.text().equals("=")
                && !BOND_EXPRESSION_TOKENS.contains(peek(1).type());

        pendingBondResult = new BondExpressionParser(cursor, source).parse();
        pendingIsDoubleBond = isDoubleCandidate;
        pendingBondPosition = first.position();
    }

    /**
     * Consumes a branch-opening token, pushing the current atom as the branch point.
     *
     * @param token the peeked {@link TokenType#BRANCH_OPEN} token
     * @throws InvalidNotationException if there is no preceding atom to branch from
     */
    private void parseBranchOpen(Token token)
    {
        if (previousAtom == null)
        {
            throw new InvalidNotationException("Branch opened with no preceding atom", source, token.position());
        }

        next();
        branchStack.push(previousAtom);
    }

    /**
     * Consumes a branch-closing token, restoring the atom the branch was opened from.
     *
     * @param token the peeked {@link TokenType#BRANCH_CLOSE} token
     * @throws InvalidNotationException if there is no open branch to close
     */
    private void parseBranchClose(Token token)
    {
        if (branchStack.isEmpty())
        {
            throw new InvalidNotationException("Unmatched ')'", source, token.position());
        }

        next();
        previousAtom = branchStack.pop();
    }

    /**
     * Consumes a disconnection dot.
     *
     * @throws InvalidNotationException if a bond expression is pending
     */
    private void parseDot()
    {
        next();
        if (pendingBondResult != null)
        {
            throw new InvalidNotationException("Bond expression not followed by an atom", source, pendingBondPosition);
        }

        previousAtom = null;
    }

    /**
     * Consumes a ring-closure digit (or {@code %nn} number):
     * either opens a new ring or closes an existing one.
     *
     * @param token the peeked {@link TokenType#RING_BOND} token
     * @throws InvalidNotationException if there is no preceding atom, or the closure would bond an atom to itself
     */
    private void parseRingBond(Token token)
    {
        next();
        if (previousAtom == null)
        {
            throw new InvalidNotationException("Ring closure not preceded by an atom", source, token.position());
        }

        // the pending expression belongs to this closure, not to whatever atom comes next
        BondExpressionParser.Result bondResultHere = pendingBondResult;
        boolean isDoubleHere = pendingIsDoubleBond;
        pendingBondResult = null;
        pendingIsDoubleBond = false;

        // a digit's first occurrence opens a ring, its second closes the one it opened
        PendingRingClosure opened = openRingBonds.remove(token.text());
        if (opened == null)
        {
            openRingBonds.put(token.text(), new PendingRingClosure(previousAtom, bondResultHere, isDoubleHere, token.position()));
            return;
        }

        if (opened.atom() == previousAtom)
        {
            throw new InvalidNotationException("Ring closure bonds an atom to itself", source, token.position());
        }

        BondPredicate predicate = resolveRingBondPredicate(opened.bondResult(), bondResultHere);
        builder.addBond(opened.atom(), previousAtom, predicate);
        stereo.recordBond(opened.atom(), previousAtom, opened.isDoubleBond() || isDoubleHere);

        registerRingClosureDirectionalEdges(opened.atom(), previousAtom, opened.bondResult(), bondResultHere, token);
    }

    /**
     * Resolves a ring-closure bond's predicate from the bond expressions seen at its opening and closing.
     *
     * @param opening the opening occurrence's bond expression result, or {@code null}
     * @param closing the closing occurrence's bond expression result, or {@code null}
     * @return the resolved predicate
     */
    private BondPredicate resolveRingBondPredicate(BondExpressionParser.Result opening, BondExpressionParser.Result closing)
    {
        if (opening == null && closing == null)
        {
            return defaultBondPredicate();
        }
        if (opening == null)
        {
            return closing.predicate();
        }
        if (closing == null)
        {
            return opening.predicate();
        }
        return opening.predicate().and(closing.predicate());
    }

    /**
     * Hands any direction declared at either occurrence of a just-closed ring-closure bond to {@link #stereo}.
     *
     * @param openingAtom the atom the ring closure was opened on
     * @param closingAtom the atom the ring closure was closed on
     * @param openingResult the opening occurrence's bond expression result, or {@code null}
     * @param closingResult the closing occurrence's bond expression result, or {@code null}
     * @param closingToken the closing digit's token, for error reporting
     * @throws InvalidNotationException if both occurrences declare the same direction
     */
    private void registerRingClosureDirectionalEdges(
        QueryAtom openingAtom,
        QueryAtom closingAtom,
        BondExpressionParser.Result openingResult,
        BondExpressionParser.Result closingResult,
        Token closingToken)
    {
        Optional<BondDirection> openingDirection = openingResult != null ? openingResult.direction() : Optional.empty();
        Optional<BondDirection> closingDirection = closingResult != null ? closingResult.direction() : Optional.empty();
        if (openingDirection.isEmpty() && closingDirection.isEmpty())
        {
            return;
        }
        // the two occurrences describe one bond from opposite ends, so declaring the same direction at both is a contradiction
        if (openingDirection.isPresent() && closingDirection.isPresent() && openingDirection.get() == closingDirection.get())
        {
            throw new InvalidNotationException(
                "Ring closure direction must be the opposite of its opening direction, not the same", source, closingToken.position());
        }

        openingDirection.ifPresent(direction -> stereo.recordRingClosureDirection(openingAtom, closingAtom, direction));
        closingDirection.ifPresent(direction -> stereo.recordRingClosureDirection(closingAtom, openingAtom, direction));
    }

    /**
     * An as-yet-unmatched ring-closure digit, recorded at the atom it was opened on.
     *
     * @param atom the atom the ring closure was opened on
     * @param bondResult the bond expression result written immediately before the digit when it was opened, or {@code null}
     * @param isDoubleBond {@code true} if {@code bondResult} denotes a literal double bond
     * @param position the opening digit's source position, for error reporting if it's never closed
     */
    private record PendingRingClosure(QueryAtom atom, BondExpressionParser.Result bondResult, boolean isDoubleBond, int position)
    {
    }

    /**
     * Bonds the given atom to the preceding atom, if any, consuming any pending bond expression.
     *
     * @param atom the atom position just parsed
     * @throws InvalidNotationException if a bond expression is pending but there is no preceding atom to bond it to
     */
    private void attachAtom(QueryAtom atom)
    {
        if (previousAtom == null)
        {
            if (pendingBondResult != null)
            {
                throw new InvalidNotationException("Bond expression not preceded by an atom", source, pendingBondPosition);
            }
        }
        else
        {
            BondPredicate predicate = pendingBondResult != null ? pendingBondResult.predicate() : defaultBondPredicate();
            builder.addBond(previousAtom, atom, predicate);
            stereo.recordBond(previousAtom, atom, pendingIsDoubleBond);

            // a direction is only written on a single bond flanking a double bond, never on the double bond itself
            if (!pendingIsDoubleBond && pendingBondResult != null && pendingBondResult.direction().isPresent())
            {
                stereo.recordDirection(previousAtom, atom, pendingBondResult.direction().get());
            }
        }

        pendingBondResult = null;
        pendingIsDoubleBond = false;
        previousAtom = atom;
    }

    /**
     * @return the predicate an unspecified bond denotes: single or aromatic
     */
    private static BondPredicate defaultBondPredicate()
    {
        return BondPredicates.order(BondOrder.SINGLE).or(BondPredicates.order(BondOrder.AROMATIC));
    }

    /**
     * @return the next token, without consuming it
     */
    private Token peek()
    {
        return cursor.peek();
    }

    /**
     * @param distance 0 for the next token, 1 for the one after it, and so on
     * @return the token {@code distance} positions ahead, without consuming anything
     */
    private Token peek(int distance)
    {
        return cursor.peek(distance);
    }

    /**
     * @return the next token, consuming it
     */
    private Token next()
    {
        return cursor.next();
    }
}
