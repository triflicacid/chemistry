package com.ruben.chemistry.formula;

/**
 * Nuclide symbols as written in a formula: an element symbol optionally prefixed by a mass
 * number, e.g. {@code "C"}, {@code "2H"}, or {@code "13C"}.
 */
final class Nuclide
{
    /** The element symbol standing for any element. */
    private static final String WILDCARD = "*";

    /** Conventional single-letter names for the heavy hydrogen isotopes, indexed by mass number. */
    private static final String[] HYDROGEN_ALIASES = {null, null, "D", "T"};

    private Nuclide()
    {
    }

    /**
     * @param element the element symbol
     * @param massNumber the mass number, or zero if the nuclide is unspecified
     * @return the nuclide's symbol
     */
    static String symbolOf(String element, int massNumber)
    {
        if (massNumber <= 0 || (element.equals("H") && massNumber == 1))
        {
            return element;
        }
        return massNumber + element;
    }

    /**
     * @param symbol a nuclide symbol
     * @return the element symbol it names
     */
    static String elementOf(String symbol)
    {
        int digits = leadingDigits(symbol);
        return symbol.substring(digits);
    }

    /**
     * @param symbol a nuclide symbol
     * @return the mass number it declares, or zero if it declares none
     */
    static int massNumberOf(String symbol)
    {
        int digits = leadingDigits(symbol);
        return digits == 0 ? 0 : Integer.parseInt(symbol.substring(0, digits));
    }

    /**
     * Writes a nuclide symbol for display.
     *
     * @param symbol the nuclide symbol to write
     * @param unicode whether to render a mass number as superscript digits rather than bracketed plain ones
     * @param style how to name a labelled nuclide
     * @return the symbol as it should appear in a formula, e.g. {@code [2H]}, {@code ²H}, or {@code D}
     */
    static String render(String symbol, boolean unicode, MolecularFormula.IsotopeStyle style)
    {
        int massNumber = massNumberOf(symbol);
        if (massNumber == 0)
        {
            return symbol;
        }

        String element = elementOf(symbol);
        if (style == MolecularFormula.IsotopeStyle.HYDROGEN_ALIASES
            && element.equals("H") && massNumber < HYDROGEN_ALIASES.length
            && HYDROGEN_ALIASES[massNumber] != null)
        {
            return HYDROGEN_ALIASES[massNumber];
        }
        return (unicode ? Unicode.superscript(massNumber) + element : "[" + massNumber + element + "]");
    }

    /**
     * @param symbol a nuclide symbol
     * @return whether it stands for any element rather than a named one
     */
    static boolean isWildcard(String symbol)
    {
        return symbol.equals(WILDCARD);
    }

    /**
     * @param symbol a nuclide symbol
     * @return how many characters of {@code symbol} are its leading mass number
     */
    private static int leadingDigits(String symbol)
    {
        int index = 0;
        while (index < symbol.length() && Character.isDigit(symbol.charAt(index)))
        {
            index++;
        }
        return index;
    }
}
