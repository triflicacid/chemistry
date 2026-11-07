package com.ruben.chemistry.core;

/**
 * The order of a {@link Bond}.
 */
public enum BondOrder
{
    /** {@code -} */
    SINGLE('-'),

    /** {@code =} */
    DOUBLE('='),

    /** {@code #} */
    TRIPLE('#'),

    /** {@code $} */
    QUADRUPLE('$'),

    /** {@code :} a bond within an aromatic ring, with no one order of its own */
    AROMATIC(':');

    private final char symbol;

    BondOrder(char symbol)
    {
        this.symbol = symbol;
    }

    /**
     * @return the SMILES bond symbol denoting this order
     */
    public char symbol()
    {
        return symbol;
    }

    /**
     * @param symbol a SMILES bond symbol: one of {@code - = # $ :}
     * @return the bond order denoted by {@code symbol}
     * @throws IllegalArgumentException if {@code symbol} is not a recognised bond symbol
     */
    public static BondOrder fromSymbol(char symbol)
    {
        for (BondOrder order : values())
        {
            if (order.symbol == symbol)
            {
                return order;
            }
        }
        throw new IllegalArgumentException("Unrecognised bond symbol '" + symbol + "'");
    }

    /**
     * @param symbol a candidate bond symbol
     * @return {@code true} if {@code symbol} denotes one of {@link #values()}
     */
    public static boolean isSymbol(char symbol)
    {
        for (BondOrder order : values())
        {
            if (order.symbol == symbol)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @param first one endpoint atom
     * @param second the other endpoint atom
     * @return {@link #AROMATIC} if both atoms are aromatic, otherwise {@link #SINGLE}
     */
    public static BondOrder inferredBetween(Atom first, Atom second)
    {
        return first.aromatic() && second.aromatic() ? AROMATIC : SINGLE;
    }
}
