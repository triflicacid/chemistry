package com.ruben.chemistry.smiles;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.Molecule;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalInt;

/**
 * Parses a SMILES string into a {@link Molecule}, driven token-by-token by a {@link Lexer}.
 *
 * <p>Ring closures are recognised by the lexer but not yet supported by this parser;
 * encountering one raises an {@link InvalidSmilesException}.
 */
public final class SmilesParser
{
    private final String source;
    private final Lexer lexer;
    private final Molecule.Builder builder = new Molecule.Builder();
    private final Deque<Atom> branchStack = new ArrayDeque<>();

    private Atom previousAtom;
    private String pendingBondSymbol;
    private int pendingBondPosition;

    /**
     * Creates a parser for the given source string. Parsing itself happens lazily in
     * {@link #parse()}.
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
     * @throws InvalidSmilesException if the source is not valid SMILES, or uses a ring closure
     */
    public Molecule parse()
    {
        if (lexer.isAtEnd())
        {
            throw new InvalidSmilesException("SMILES string must not be empty", source, 0);
        }

        while (!lexer.isAtEnd())
        {
            parseNext();
        }

        if (!branchStack.isEmpty())
        {
            throw new InvalidSmilesException("Unmatched '('", source, source.length());
        }
        if (pendingBondSymbol != null)
        {
            throw new InvalidSmilesException("Bond symbol not followed by an atom", source, pendingBondPosition);
        }

        return builder.build();
    }

    /**
     * Parses a single grammar element at the current token position: an atom, bond,
     * branch delimiter, ring closure, or disconnection dot.
     *
     * @throws InvalidSmilesException if the current token cannot start a valid element here
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
            case RING_BOND -> throw new InvalidSmilesException("Ring closures are not yet supported", source, token.position());
            default -> throw new InvalidSmilesException("Unexpected token '" + token.text() + "'", source, token.position());
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
        Atom atom = Atom.organic(canonicalElement(token.text()), isAromatic(token.text()));
        builder.addAtom(atom);
        return atom;
    }

    /**
     * Parses a {@code [...]} bracket atom: isotope, element symbol, chirality, hydrogen
     * count, charge, and atom class, in that order.
     *
     * @return the added atom
     * @throws InvalidSmilesException if the contents are not a valid bracket atom
     */
    private Atom parseBracketAtom()
    {
        lexer.next();

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
            throw new InvalidSmilesException("Expected ']' to close bracket atom", source, unexpected.position());
        }
        lexer.next();

        Atom atom = Atom.builder(canonicalElement(symbol.text()))
            .aromatic(isAromatic(symbol.text()))
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
     * neighbours are written in, into the fixed order {@link Molecule} stores chirality
     * relative to (any implicit hydrogen first, then the atom's bonds in {@link Molecule#bondsOf} order).
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
     * @throws InvalidSmilesException if the current token is not an element symbol
     */
    private Token parseElementSymbol()
    {
        if (lexer.peek().type() != TokenType.ELEMENT)
        {
            Token unexpected = lexer.peek();
            throw new InvalidSmilesException("Expected element symbol in bracket atom", source, unexpected.position());
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
     * @throws InvalidSmilesException if a colon is not followed by a digit run
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
            throw new InvalidSmilesException("Expected a number after ':' in atom class", source, unexpected.position());
        }
        return OptionalInt.of(Integer.parseInt(lexer.next().text()));
    }

    /**
     * Consumes a bond token, recording its order for the atom that follows.
     *
     * @param token the peeked {@link TokenType#BOND} token
     * @throws InvalidSmilesException if the bond symbol denotes an unsupported directional bond
     */
    private void parseBond(Token token)
    {
        lexer.next();
        if (token.text().equals("/") || token.text().equals("\\"))
        {
            throw new InvalidSmilesException("Directional bonds are not yet supported", source, token.position());
        }

        pendingBondSymbol = token.text();
        pendingBondPosition = token.position();
    }

    /**
     * Consumes a branch-opening token, pushing the current atom as the branch point.
     *
     * @param token the peeked {@link TokenType#BRANCH_OPEN} token
     * @throws InvalidSmilesException if there is no preceding atom to branch from
     */
    private void parseBranchOpen(Token token)
    {
        if (previousAtom == null)
        {
            throw new InvalidSmilesException("Branch opened with no preceding atom", source, token.position());
        }

        lexer.next();
        branchStack.push(previousAtom);
    }

    /**
     * Consumes a branch-closing token, restoring the atom the branch was opened from.
     *
     * @param token the peeked {@link TokenType#BRANCH_CLOSE} token
     * @throws InvalidSmilesException if there is no open branch to close
     */
    private void parseBranchClose(Token token)
    {
        if (branchStack.isEmpty())
        {
            throw new InvalidSmilesException("Unmatched ')'", source, token.position());
        }

        lexer.next();
        previousAtom = branchStack.pop();
    }

    /**
     * Consumes a disconnection dot, clearing the preceding atom so the next atom begins a
     * new, unbonded fragment.
     *
     * @throws InvalidSmilesException if a bond symbol is pending
     */
    private void parseDot()
    {
        lexer.next();
        if (pendingBondSymbol != null)
        {
            throw new InvalidSmilesException("Bond symbol not followed by an atom", source, pendingBondPosition);
        }

        previousAtom = null;
    }

    /**
     * Bonds the given atom to the preceding atom, if any, consuming any pending bond symbol.
     *
     * @param atom the atom just parsed
     * @throws InvalidSmilesException if a bond symbol is pending but there is no preceding atom to bond it to
     */
    private void attachAtom(Atom atom)
    {
        if (previousAtom == null)
        {
            if (pendingBondSymbol != null)
            {
                throw new InvalidSmilesException("Bond symbol not preceded by an atom", source, pendingBondPosition);
            }
        }
        else
        {
            BondOrder order = pendingBondSymbol != null
                ? bondOrderOf(pendingBondSymbol)
                : BondOrder.inferredBetween(previousAtom, atom);
            builder.addBond(previousAtom, atom, order);
        }

        pendingBondSymbol = null;
        previousAtom = atom;
    }

    /**
     * Maps a bond symbol to its {@link BondOrder}.
     *
     * @param symbol the bond symbol: one of {@code - = # $ :}
     * @return the corresponding bond order
     */
    private BondOrder bondOrderOf(String symbol)
    {
        return BondOrder.fromSymbol(symbol.charAt(0));
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
     * @param rawSymbol the atom symbol exactly as lexed, e.g. {@code "c"}, {@code "Cl"}, {@code "se"}, {@code "*"}
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
}
