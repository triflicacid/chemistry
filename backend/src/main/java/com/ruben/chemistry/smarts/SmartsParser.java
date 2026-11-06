package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.BondDirection;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.query.AtomPredicate;
import com.ruben.chemistry.query.AtomPredicates;
import com.ruben.chemistry.query.BondPredicate;
import com.ruben.chemistry.query.BondPredicates;
import com.ruben.chemistry.query.Query;
import com.ruben.chemistry.query.QueryAtom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses a SMARTS string into a {@link Query}.
 */
public final class SmartsParser
{
    /** Token types that can appear in a bond expression written outside a bracket atom. */
    private static final Set<TokenType> BOND_EXPRESSION_TOKENS =
        Set.of(TokenType.BOND, TokenType.NOT, TokenType.SEMI, TokenType.AND, TokenType.OR);

    private final String source;
    private final TokenCursor cursor;
    private final Query.Builder builder = new Query.Builder();
    private final Deque<QueryAtom> branchStack = new ArrayDeque<>();
    private final Map<QueryAtom, List<QueryAtom>> substituentOrderOf = new IdentityHashMap<>();
    private final Map<QueryAtom, QueryAtom> doubleBondPartnerOf = new IdentityHashMap<>();
    private final Map<QueryAtom, DirectionalEdge> directionalEdgeOf = new IdentityHashMap<>();
    private final Map<String, PendingRingClosure> openRingBonds = new LinkedHashMap<>();

    private QueryAtom previousAtom;
    private BondExpressionParser.Result pendingBondResult;
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
     * Parses tokens up to a terminator token type, consuming it too unless it's the
     * end-of-source marker (which is never physically present as a token to consume).
     * Used both for a whole top-level SMARTS string (terminated by {@link TokenType#EOF}) and
     * for a {@code $(...)} recursive primitive's span (terminated by its own
     * {@link TokenType#EXPRESSION_END}) - either way, nothing needs to be pre-collected: a
     * nested recursive primitive's own matching {@link TokenType#EXPRESSION_END} is consumed
     * by its own, deeper call to this same method before control ever returns here, so this
     * loop only ever sees the terminator that actually belongs to it.
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
     * Parses a single grammar element at the current token position: an atom, bond
     * expression, branch delimiter, ring closure, or disconnection dot.
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
     * Parses an organic-subset atom token outside brackets into an atom position whose
     * predicate matches that element (or aromatic element), directly - no expression parsing
     * is needed since a bare atom token can't carry logical operators.
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
     * @param rawSymbol an organic-subset atom symbol exactly as lexed, e.g. {@code "c"}, {@code "Cl"}, {@code "*"}
     * @return the predicate it denotes
     */
    private AtomPredicate organicAtomPredicate(String rawSymbol)
    {
        if (rawSymbol.equals("*"))
        {
            return AtomPredicates.any();
        }
        return isAromatic(rawSymbol) ? AtomPredicates.aromatic(canonicalElement(rawSymbol)) : AtomPredicates.element(canonicalElement(rawSymbol));
    }

    /**
     * Parses a {@code [...]} bracket atom into an atom position, delegating the primitive
     * expression between the brackets to {@link AtomExpressionParser}, which consumes
     * directly from this parser's own cursor and stops exactly at the matching
     * {@link TokenType#BRACKET_CLOSE} - no separate token collection pass is needed.
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
     * Resolves a recursive SMARTS primitive into the query it denotes, by parsing directly
     * off the same shared cursor the primitive was found on, up to and including its own
     * matching {@link TokenType#EXPRESSION_END} - no separate span needs to be collected
     * first, and no re-lexing is needed either.
     *
     * @param sharedCursor the cursor positioned at the recursive primitive's first token
     * @return the pattern it denotes
     */
    private Query resolveSubPattern(TokenCursor sharedCursor)
    {
        return new SmartsParser(sharedCursor, source).parseUntil(TokenType.EXPRESSION_END);
    }

    /**
     * Parses a bond expression written outside a bracket atom (bond symbols and the logical
     * operators combining them) directly off this parser's cursor, recording its result as
     * pending until the atom or ring closure it applies to is parsed. {@link BondExpressionParser}
     * stops consuming on its own, exactly where the expression ends, so no separate token
     * collection pass is needed here either.
     */
    private void parseBondExpression()
    {
        Token first = peek();
        boolean isDoubleCandidate = first.type() == TokenType.BOND && first.text().equals("=")
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

        BondExpressionParser.Result bondResultHere = pendingBondResult;
        boolean isDoubleHere = pendingIsDoubleBond;
        pendingBondResult = null;
        pendingIsDoubleBond = false;

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
        recordSubstituent(opened.atom(), previousAtom);
        recordSubstituent(previousAtom, opened.atom());

        if (opened.isDoubleBond() || isDoubleHere)
        {
            doubleBondPartnerOf.put(opened.atom(), previousAtom);
            doubleBondPartnerOf.put(previousAtom, opened.atom());
        }

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
     * Registers directional edges for a just-closed ring-closure bond, if either of its two
     * occurrences declared a direction, then attempts to reconcile any double bond either
     * atom belongs to.
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
        if (openingDirection.isPresent() && closingDirection.isPresent() && openingDirection.get() == closingDirection.get())
        {
            throw new InvalidNotationException(
                "Ring closure direction must be the opposite of its opening direction, not the same", source, closingToken.position());
        }

        openingDirection.ifPresent(direction -> directionalEdgeOf.put(openingAtom, new DirectionalEdge(closingAtom, direction, true)));
        closingDirection.ifPresent(direction -> directionalEdgeOf.put(closingAtom, new DirectionalEdge(openingAtom, direction, true)));

        reconcileIfDoubleBondAtom(openingAtom);
        reconcileIfDoubleBondAtom(closingAtom);
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
            recordSubstituent(previousAtom, atom);
            recordSubstituent(atom, previousAtom);

            if (pendingIsDoubleBond)
            {
                doubleBondPartnerOf.put(previousAtom, atom);
                doubleBondPartnerOf.put(atom, previousAtom);
            }
            else if (pendingBondResult != null && pendingBondResult.direction().isPresent())
            {
                recordDirectionalEdge(previousAtom, atom, pendingBondResult.direction().get());
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
     * Records {@code other} as one of {@code atom}'s substituents, in the order its bonds are parsed.
     */
    private void recordSubstituent(QueryAtom atom, QueryAtom other)
    {
        substituentOrderOf.computeIfAbsent(atom, ignored -> new ArrayList<>()).add(other);
    }

    /**
     * Records one directional single bond between two just-bonded atoms, then attempts to complete
     * the stereo declaration of either atom's double bond, if it has one and its other reference is already known too.
     *
     * @param writtenFirst the earlier-written of the bond's two atoms
     * @param writtenSecond the later-written of the bond's two atoms
     * @param direction the direction as written
     */
    private void recordDirectionalEdge(QueryAtom writtenFirst, QueryAtom writtenSecond, BondDirection direction)
    {
        directionalEdgeOf.put(writtenFirst, new DirectionalEdge(writtenSecond, direction, true));
        directionalEdgeOf.put(writtenSecond, new DirectionalEdge(writtenFirst, direction, false));

        reconcileIfDoubleBondAtom(writtenFirst);
        reconcileIfDoubleBondAtom(writtenSecond);
    }

    /**
     * If {@code atom} is one endpoint of a double bond, attempts to complete that double
     * bond's stereo declaration now that one more of its two reference substituents is known.
     *
     * @param atom the atom to check
     */
    private void reconcileIfDoubleBondAtom(QueryAtom atom)
    {
        QueryAtom partner = doubleBondPartnerOf.get(atom);
        if (partner != null)
        {
            tryReconcileDoubleBondStereo(atom, partner);
        }
    }

    /**
     * Reconciles the two directional edges flanking the double bond between {@code first}
     * and {@code second} into its declared stereo configuration, if both of its endpoints
     * now have one recorded; does nothing otherwise. The declared value is reprojected onto
     * the default reference substituents.
     *
     * @param first one endpoint of the double bond
     * @param second the other endpoint
     */
    private void tryReconcileDoubleBondStereo(QueryAtom first, QueryAtom second)
    {
        DirectionalEdge firstEdge = directionalEdgeOf.get(first);
        DirectionalEdge secondEdge = directionalEdgeOf.get(second);
        if (firstEdge == null || secondEdge == null)
        {
            return;
        }

        BondDirection firstOutward = firstEdge.thisAtomWrittenFirst() ? firstEdge.direction() : firstEdge.direction().flip();
        BondDirection secondOutward = secondEdge.thisAtomWrittenFirst() ? secondEdge.direction() : secondEdge.direction().flip();
        DoubleBondStereo relativeToEdges = firstOutward == secondOutward ? DoubleBondStereo.SAME_SIDE : DoubleBondStereo.OPPOSITE_SIDE;

        QueryAtom firstDefault = defaultSubstituentOf(first, second);
        QueryAtom secondDefault = defaultSubstituentOf(second, first);
        boolean flipped = (firstEdge.other() != firstDefault) ^ (secondEdge.other() != secondDefault);

        builder.setDoubleBondStereo(first, second, flipped ? relativeToEdges.flip() : relativeToEdges);
    }

    /**
     * @param atom an atom of a double bond
     * @param excluding the double bond's other atom, excluded as a candidate
     * @return the first of {@code atom}'s substituents, other than {@code excluding}, in parsed order, or {@code null} if it has none
     */
    private QueryAtom defaultSubstituentOf(QueryAtom atom, QueryAtom excluding)
    {
        return substituentOrderOf.getOrDefault(atom, List.of()).stream()
            .filter(candidate -> candidate != excluding)
            .findFirst()
            .orElse(null);
    }

    /**
     * One atom's end of a directional single bond.
     *
     * @param other the atom at the other end of this directional bond
     * @param direction the direction as written
     * @param thisAtomWrittenFirst whether the atom this edge is recorded for was written before {@code other}
     */
    private record DirectionalEdge(QueryAtom other, BondDirection direction, boolean thisAtomWrittenFirst)
    {
    }

    /**
     * Determines whether an atom symbol as written denotes an aromatic atom.
     *
     * @param rawSymbol the atom symbol exactly as lexed, e.g. {@code "c"}, {@code "Cl"}, {@code "*"}
     * @return {@code true} if {@code rawSymbol} is lower case
     */
    private boolean isAromatic(String rawSymbol)
    {
        return !rawSymbol.equals("*") && Character.isLowerCase(rawSymbol.charAt(0));
    }

    /**
     * Canonicalises an atom symbol as written into its standard-case element symbol.
     *
     * @param rawSymbol the atom symbol exactly as lexed, e.g. {@code "c"}, {@code "Cl"}, {@code "*"}
     * @return {@code "*"} unchanged, or {@code rawSymbol} with its first letter upper-cased and any remaining letters lower-cased
     */
    private String canonicalElement(String rawSymbol)
    {
        if (rawSymbol.equals("*"))
        {
            return rawSymbol;
        }
        return Character.toUpperCase(rawSymbol.charAt(0)) + rawSymbol.substring(1).toLowerCase();
    }

    private Token peek()
    {
        return cursor.peek();
    }

    private Token peek(int distance)
    {
        return cursor.peek(distance);
    }

    private Token next()
    {
        return cursor.next();
    }
}
