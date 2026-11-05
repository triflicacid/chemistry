package com.ruben.chemistry.core;

/**
 * The order of a {@link Bond}.
 */
public enum BondOrder
{
    SINGLE('-'),
    DOUBLE('='),
    TRIPLE('#'),
    QUADRUPLE('$'),
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
}
