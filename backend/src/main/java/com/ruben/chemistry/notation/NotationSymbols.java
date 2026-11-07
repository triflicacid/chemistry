package com.ruben.chemistry.notation;

import com.ruben.chemistry.core.BondDirection;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Elements;

import java.util.Set;

/**
 * Utilities for atom and bond symbols.
 */
public final class NotationSymbols
{
    /** Organic-subset element symbols written as two characters. */
    private static final Set<String> TWO_CHAR_ORGANIC_ATOMS = Set.of("Cl", "Br");

    /** Organic-subset element symbols written as a single character. */
    private static final String SINGLE_CHAR_ORGANIC_ATOMS = "BCNOPSFI";

    /** The same elements written lower-case, denoting an aromatic atom. */
    private static final String AROMATIC_ORGANIC_ATOMS = "bcnops";

    /** Aromatic element symbols beyond the organic subset, recognised only inside brackets. */
    private static final Set<String> AROMATIC_TWO_CHAR_ELEMENTS = Set.of("se", "as", "te");

    private NotationSymbols()
    {
    }

    /**
     * @param source the source string
     * @param position 0-based offset into {@code source}
     * @return {@code true} if a two-character organic-subset atom symbol (e.g. {@code Cl}) starts at {@code position}
     */
    public static boolean startsWithTwoCharOrganicAtom(String source, int position)
    {
        if (position + 2 > source.length())
        {
            return false;
        }
        return TWO_CHAR_ORGANIC_ATOMS.contains(source.substring(position, position + 2));
    }

    /**
     * @param ch a candidate character
     * @return {@code true} if {@code ch} alone is an organic-subset atom symbol, aliphatic or aromatic
     */
    public static boolean isSingleCharOrganicAtom(char ch)
    {
        return SINGLE_CHAR_ORGANIC_ATOMS.indexOf(ch) >= 0 || AROMATIC_ORGANIC_ATOMS.indexOf(ch) >= 0;
    }

    /**
     * Matches an element symbol at a position against the periodic table and the aromatic
     * extension set, preferring a two-character match.
     *
     * @param source the source string
     * @param position 0-based offset into {@code source}
     * @return the matched length (1 or 2), or 0 if no element symbol starts there
     */
    public static int elementSymbolLengthAt(String source, int position)
    {
        // two characters first, so Cl is never read as C followed by a stray l
        if (position + 2 <= source.length())
        {
            String twoChars = source.substring(position, position + 2);
            if (Elements.isSymbol(twoChars) || AROMATIC_TWO_CHAR_ELEMENTS.contains(twoChars))
            {
                return 2;
            }
        }

        char ch = source.charAt(position);
        if (Elements.isSymbol(String.valueOf(ch)) || AROMATIC_ORGANIC_ATOMS.indexOf(ch) >= 0)
        {
            return 1;
        }

        return 0;
    }

    /**
     * @param ch a candidate character
     * @return {@code true} if {@code ch} is a bond symbol
     */
    public static boolean isBondSymbol(char ch)
    {
        return BondOrder.isSymbol(ch) || BondDirection.isSymbol(ch);
    }
}
