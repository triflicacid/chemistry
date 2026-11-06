package com.ruben.chemistry.core;

/**
 * The as-written direction of a single bond flanking a double bond. Must be compared to
 * a double bond's two flanking directions to resolve a {@link DoubleBondStereo}.
 */
public enum BondDirection
{
    /** {@code /}. */
    SLASH('/'),

    /** {@code \}. */
    BACKSLASH('\\');

    private final char symbol;

    BondDirection(char symbol)
    {
        this.symbol = symbol;
    }

    /**
     * @return the opposite direction
     */
    public BondDirection flip()
    {
        return this == SLASH ? BACKSLASH : SLASH;
    }

    /**
     * @return the symbol denoting this direction: {@code /} or {@code \}
     */
    public char symbol()
    {
        return symbol;
    }

    /**
     * @param symbol a candidate direction symbol
     * @return {@code true} if {@code symbol} is {@code /} or {@code \}
     */
    public static boolean isSymbol(char symbol)
    {
        return symbol == '/' || symbol == '\\';
    }

    /**
     * @param symbol a direction symbol: {@code /} or {@code \}
     * @return the direction denoted by {@code symbol}
     * @throws IllegalArgumentException if {@code symbol} is not a recognised direction symbol
     */
    public static BondDirection fromSymbol(char symbol)
    {
        return switch (symbol)
        {
            case '/' -> SLASH;
            case '\\' -> BACKSLASH;
            default -> throw new IllegalArgumentException("Unrecognised bond direction symbol '" + symbol + "'");
        };
    }
}
