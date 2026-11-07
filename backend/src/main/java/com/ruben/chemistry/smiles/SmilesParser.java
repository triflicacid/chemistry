package com.ruben.chemistry.smiles;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondDirection;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.notation.NotationSymbols;
import com.ruben.chemistry.notation.StereoTracker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Parses a SMILES string into a {@link Molecule}.
 * <p>
 * A parser consumes its source as it goes, so it cannot be reused: {@link #parse()} throws if called a second time.
 */
public final class SmilesParser
{
    private final String source;
    private final Lexer lexer;
    private final Molecule.Builder builder = new Molecule.Builder();

    /** The atoms branches were opened from, innermost last, to return to as each one closes. */
    private final Deque<Atom> branchStack = new ArrayDeque<>();

    /** Collects the double bonds and directional bonds parsed so far, and declares their stereo. */
    private final StereoTracker<Atom> stereo = new StereoTracker<>(builder::setDoubleBondStereo);

    /** Ring-closure digits still waiting for their second occurrence, in the order they were opened. */
    private final Map<String, PendingRingClosure> openRingBonds = new LinkedHashMap<>();

    /** The atom the next one bonds to, or {@code null} at the start of a disconnected fragment. */
    private Atom previousAtom;

    /** A bond symbol read but not yet applied, since the atom it bonds to comes after it. */
    private String pendingBondSymbol;
    private int pendingBondPosition;

    /**
     * Creates a parser for the given source string.
     *
     * @param source the SMILES string to parse
     */
    public SmilesParser(String source)
    {
        this.source = source;
        this.lexer = new Lexer(source);
    }

    /**
     * Parses the source string into a molecule.
     *
     * @return the parsed molecule
     * @throws InvalidNotationException if the source is not valid SMILES
     */
    public Molecule parse()
    {
        if (lexer.isAtEnd())
        {
            throw new InvalidNotationException("SMILES string must not be empty", source, 0);
        }

        while (!lexer.isAtEnd())
        {
            parseNext();
        }

        if (!branchStack.isEmpty())
        {
            throw new InvalidNotationException("Unmatched '('", source, source.length());
        }
        if (pendingBondSymbol != null)
        {
            throw new InvalidNotationException("Bond symbol not followed by an atom", source, pendingBondPosition);
        }
        if (!openRingBonds.isEmpty())
        {
            PendingRingClosure firstUnpaired = openRingBonds.values().iterator().next();
            throw new InvalidNotationException("Unpaired ring closure digit", source, firstUnpaired.position());
        }

        return builder.build();
    }

    /**
     * Parses a single grammar element at the current token position: an atom, bond,
     * branch delimiter, ring closure, or disconnection dot.
     *
     * @throws InvalidNotationException if the current token cannot start a valid element here
     */
    private void parseNext()
    {
        Token token = lexer.peek();

        switch (token.type())
        {
            case ATOM -> attachAtom(parseOrganicAtom());
            case BRACKET_OPEN -> attachAtom(parseBracketAtom());
            case BOND -> parseBond(token);
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
     * @return the added atom
     */
    private Atom parseOrganicAtom()
    {
        Token token = lexer.next();
        Atom atom = Atom.organic(NotationSymbols.canonicalElement(token.text()), NotationSymbols.isAromaticSymbol(token.text()));
        builder.addAtom(atom);
        return atom;
    }

    /**
     * Parses a {@code [...]} bracket atom: isotope, element symbol, chirality, hydrogen
     * count, charge, and atom class, in that order.
     *
     * @return the added atom
     * @throws InvalidNotationException if the contents are not a valid bracket atom
     */
    private Atom parseBracketAtom()
    {
        lexer.next();

        // every component is optional but their order is fixed, so each parse step either
        // consumes its component or leaves the position untouched for the next one
        OptionalInt isotope = parseOptionalIsotope();
        Token symbol = parseElementSymbol();

        Chirality asWritten = null;
        if (lexer.peek().type() == TokenType.CHIRALITY)
        {
            asWritten = Chirality.fromSymbol(lexer.next().text());
        }

        OptionalInt hydrogenCount = parseOptionalHydrogenCount();
        int charge = parseOptionalCharge();
        OptionalInt atomClass = parseOptionalAtomClass();

        if (lexer.peek().type() != TokenType.BRACKET_CLOSE)
        {
            Token unexpected = lexer.peek();
            throw new InvalidNotationException("Expected ']' to close bracket atom", source, unexpected.position());
        }
        lexer.next();

        Atom atom = Atom.builder(NotationSymbols.canonicalElement(symbol.text()))
            .aromatic(NotationSymbols.isAromaticSymbol(symbol.text()))
            .charge(charge)
            .isotope(isotope)
            .hydrogenCount(hydrogenCount)
            .atomClass(atomClass)
            .build();
        builder.addAtom(atom);

        if (asWritten != null)
        {
            builder.setChirality(atom, normaliseForStorage(asWritten, hydrogenCount));
        }

        return atom;
    }

    /**
     * Converts a chirality marker, interpreted relative to the order this atom's
     * neighbours are written in, into the fixed order {@link Molecule} stores chirality relative to.
     *
     * @param asWritten the chirality as denoted by the {@code @}/{@code @@} marker
     * @param hydrogenCount this atom's parsed hydrogen count
     * @return the chirality to store on this atom
     */
    private Chirality normaliseForStorage(Chirality asWritten, OptionalInt hydrogenCount)
    {
        boolean hasPrecedingAtom = previousAtom != null;
        boolean hasImplicitHydrogen = hydrogenCount.orElse(0) > 0;
        return hasPrecedingAtom && hasImplicitHydrogen ? asWritten.flip() : asWritten;
    }

    /**
     * Parses an optional leading isotope number inside a bracket atom.
     *
     * @return the isotope mass number, or empty if none is present
     */
    private OptionalInt parseOptionalIsotope()
    {
        if (lexer.peek().type() == TokenType.NUMBER)
        {
            return OptionalInt.of(Integer.parseInt(lexer.next().text()));
        }
        return OptionalInt.empty();
    }

    /**
     * Parses the required element symbol of a bracket atom.
     *
     * @return the consumed {@link TokenType#ELEMENT} token
     * @throws InvalidNotationException if the current token is not an element symbol
     */
    private Token parseElementSymbol()
    {
        if (lexer.peek().type() != TokenType.ELEMENT)
        {
            Token unexpected = lexer.peek();
            throw new InvalidNotationException("Expected element symbol in bracket atom", source, unexpected.position());
        }
        return lexer.next();
    }

    /**
     * Parses an optional hydrogen-count marker inside a bracket atom: an {@code H} element
     * token, optionally followed by a digit run giving the count.
     *
     * @return the explicit hydrogen count: 0 if no {@code H} marker is present, 1 if one
     *     is present with no following number, or the following number otherwise
     */
    private OptionalInt parseOptionalHydrogenCount()
    {
        if (lexer.peek().type() == TokenType.ELEMENT && lexer.peek().text().equals("H"))
        {
            lexer.next();
            if (lexer.peek().type() == TokenType.NUMBER)
            {
                return OptionalInt.of(Integer.parseInt(lexer.next().text()));
            }
            return OptionalInt.of(1);
        }
        return OptionalInt.of(0);
    }

    /**
     * Parses an optional charge inside a bracket atom: a single sign followed by a digit
     * run giving its magnitude, or one or more repeated signs each worth one unit.
     *
     * @return the signed charge, {@code 0} if no sign is present
     */
    private int parseOptionalCharge()
    {
        if (lexer.peek().type() != TokenType.SIGN)
        {
            return 0;
        }

        Token firstSign = lexer.next();
        int sign = firstSign.text().equals("+") ? 1 : -1;

        if (lexer.peek().type() == TokenType.NUMBER)
        {
            return sign * Integer.parseInt(lexer.next().text());
        }

        // no number, so this is the repeated-sign notation: [Ca++] means the same as [Ca+2]
        int magnitude = 1;
        while (lexer.peek().type() == TokenType.SIGN && lexer.peek().text().equals(firstSign.text()))
        {
            lexer.next();
            magnitude++;
        }
        return sign * magnitude;
    }

    /**
     * Parses an optional atom class inside a bracket atom: a colon followed by a digit run.
     *
     * @return the atom class number, or empty if no colon is present
     * @throws InvalidNotationException if a colon is not followed by a digit run
     */
    private OptionalInt parseOptionalAtomClass()
    {
        if (lexer.peek().type() != TokenType.COLON)
        {
            return OptionalInt.empty();
        }

        lexer.next();
        if (lexer.peek().type() != TokenType.NUMBER)
        {
            Token unexpected = lexer.peek();
            throw new InvalidNotationException("Expected a number after ':' in atom class", source, unexpected.position());
        }
        return OptionalInt.of(Integer.parseInt(lexer.next().text()));
    }

    /**
     * Consumes a bond token, recording its order for the atom that follows.
     *
     * @param token the peeked {@link TokenType#BOND} token
     * @throws InvalidNotationException if a bond symbol is already pending
     */
    private void parseBond(Token token)
    {
        if (pendingBondSymbol != null)
        {
            throw new InvalidNotationException("Bond symbol immediately follows another bond symbol", source, token.position());
        }

        lexer.next();
        pendingBondSymbol = token.text();
        pendingBondPosition = token.position();
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

        lexer.next();
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

        lexer.next();
        previousAtom = branchStack.pop();
    }

    /**
     * Consumes a disconnection dot, clearing the preceding atom so the next atom begins a
     * new, unbonded fragment.
     *
     * @throws InvalidNotationException if a bond symbol is pending
     */
    private void parseDot()
    {
        lexer.next();
        if (pendingBondSymbol != null)
        {
            throw new InvalidNotationException("Bond symbol not followed by an atom", source, pendingBondPosition);
        }

        previousAtom = null;
    }

    /**
     * Consumes a ring-closure digit (or {@code %nn} number): either opens a new ring or
     * closes an existing one.
     *
     * @param token the peeked {@link TokenType#RING_BOND} token
     * @throws InvalidNotationException if there is no preceding atom, the closure would bond
     *     an atom to itself, or its bond symbol doesn't match the one it was opened with
     */
    private void parseRingBond(Token token)
    {
        lexer.next();
        if (previousAtom == null)
        {
            throw new InvalidNotationException("Ring closure not preceded by an atom", source, token.position());
        }

        // the pending symbol belongs to this closure, not to whatever atom comes next
        String symbolHere = pendingBondSymbol;
        pendingBondSymbol = null;

        // a digit's first occurrence opens a ring, its second closes the one it opened
        PendingRingClosure opened = openRingBonds.remove(token.text());
        if (opened == null)
        {
            openRingBonds.put(token.text(), new PendingRingClosure(previousAtom, symbolHere, token.position()));
            return;
        }

        if (opened.atom() == previousAtom)
        {
            throw new InvalidNotationException("Ring closure bonds an atom to itself", source, token.position());
        }

        BondOrder order = resolveRingBondOrder(opened.bondSymbol(), symbolHere, opened.atom(), previousAtom, token);
        builder.addBond(opened.atom(), previousAtom, order);
        stereo.recordBond(opened.atom(), previousAtom, order == BondOrder.DOUBLE);

        registerRingClosureDirectionalEdges(opened.atom(), previousAtom, opened.bondSymbol(), symbolHere, token);
    }

    /**
     * Resolves a ring-closure bond's order from the bond symbols seen at its opening and closing.
     *
     * @param openingSymbol the bond symbol written at the opening occurrence, or {@code null}
     * @param closingSymbol the bond symbol written at the closing occurrence, or {@code null}
     * @param first one endpoint, used to infer a default order if neither symbol is present
     * @param second the other endpoint
     * @param closingToken the closing digit's token, for error reporting
     * @return the resolved bond order
     * @throws InvalidNotationException if both symbols are present and resolve to different orders
     */
    private BondOrder resolveRingBondOrder(String openingSymbol, String closingSymbol, Atom first, Atom second, Token closingToken)
    {
        BondOrder openingOrder = openingSymbol != null ? bondOrderOf(openingSymbol) : null;
        BondOrder closingOrder = closingSymbol != null ? bondOrderOf(closingSymbol) : null;

        if (openingOrder != null && closingOrder != null && openingOrder != closingOrder)
        {
            throw new InvalidNotationException("Ring closure bond symbol '" + closingSymbol
                + "' does not match its opening symbol '" + openingSymbol + "'", source, closingToken.position());
        }

        if (openingOrder != null)
        {
            return openingOrder;
        }
        return closingOrder != null ? closingOrder : BondOrder.inferredBetween(first, second);
    }

    /**
     * Hands any direction written at either occurrence of a just-closed ring-closure bond to {@link #stereo}.
     *
     * @param openingAtom the atom the ring closure was opened on
     * @param closingAtom the atom the ring closure was closed on
     * @param openingSymbol the bond symbol written at the opening occurrence, or {@code null}
     * @param closingSymbol the bond symbol written at the closing occurrence, or {@code null}
     * @param closingToken the closing digit's token, for error reporting
     * @throws InvalidNotationException if both occurrences are directional with the same character
     */
    private void registerRingClosureDirectionalEdges(Atom openingAtom, Atom closingAtom, String openingSymbol, String closingSymbol, Token closingToken)
    {
        boolean openingIsDirectional = isDirectional(openingSymbol);
        boolean closingIsDirectional = isDirectional(closingSymbol);
        if (!openingIsDirectional && !closingIsDirectional)
        {
            return;
        }
        // the two occurrences describe one bond from opposite ends, so writing the same
        // character at both is a contradiction
        if (openingIsDirectional && closingIsDirectional && openingSymbol.charAt(0) == closingSymbol.charAt(0))
        {
            throw new InvalidNotationException("Ring closure direction symbol '" + closingSymbol
                + "' must be the opposite of its opening symbol '" + openingSymbol + "', not the same", source, closingToken.position());
        }

        if (openingIsDirectional)
        {
            stereo.recordRingClosureDirection(openingAtom, closingAtom, BondDirection.fromSymbol(openingSymbol.charAt(0)));
        }
        if (closingIsDirectional)
        {
            stereo.recordRingClosureDirection(closingAtom, openingAtom, BondDirection.fromSymbol(closingSymbol.charAt(0)));
        }
    }

    /**
     * An as-yet-unmatched ring-closure digit, recorded at the atom it was opened on.
     *
     * @param atom the atom the ring closure was opened on
     * @param bondSymbol the bond symbol written immediately before the digit when it was opened, or {@code null}
     * @param position the opening digit's source position, for error reporting if it's never closed
     */
    private record PendingRingClosure(Atom atom, String bondSymbol, int position)
    {
    }

    /**
     * Bonds the given atom to the preceding atom, if any, consuming any pending bond symbol.
     *
     * @param atom the atom just parsed
     * @throws InvalidNotationException if a bond symbol is pending but there is no preceding atom to bond it to
     */
    private void attachAtom(Atom atom)
    {
        if (previousAtom == null)
        {
            if (pendingBondSymbol != null)
            {
                throw new InvalidNotationException("Bond symbol not preceded by an atom", source, pendingBondPosition);
            }
        }
        else
        {
            BondOrder order = pendingBondSymbol != null
                ? bondOrderOf(pendingBondSymbol)
                : BondOrder.inferredBetween(previousAtom, atom);
            builder.addBond(previousAtom, atom, order);
            stereo.recordBond(previousAtom, atom, order == BondOrder.DOUBLE);

            // a direction is only ever written on a single bond flanking a double bond, never on the double bond itself
            if (order != BondOrder.DOUBLE && isDirectional(pendingBondSymbol))
            {
                stereo.recordDirection(previousAtom, atom, BondDirection.fromSymbol(pendingBondSymbol.charAt(0)));
            }
        }

        pendingBondSymbol = null;
        previousAtom = atom;
    }

    /**
     * @param bondSymbol a bond symbol, or {@code null}
     * @return {@code true} if {@code bondSymbol} is a directional bond, {@code /} or {@code \}
     */
    private static boolean isDirectional(String bondSymbol)
    {
        return bondSymbol != null && BondDirection.isSymbol(bondSymbol.charAt(0));
    }

    /**
     * Maps a bond symbol to its {@link BondOrder}.
     *
     * @param symbol the bond symbol: one of {@code - = # $ : / \}
     * @return the corresponding bond order
     */
    private BondOrder bondOrderOf(String symbol)
    {
        return isDirectional(symbol) ? BondOrder.SINGLE : BondOrder.fromSymbol(symbol.charAt(0));
    }
}
