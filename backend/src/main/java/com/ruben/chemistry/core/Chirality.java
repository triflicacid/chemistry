package com.ruben.chemistry.core;

/**
 * The tetrahedral parity of a stereocentre: the order in which its neighbours appear
 * going around it, viewed from the first neighbour.
 * <p>
 * Chirality only makes sense when the atom's neighbours are ordered, so it is only meaningful in the context of a {@link Molecule}.
 */
public enum Chirality
{
    /** {@code @}: the remaining neighbours appear anticlockwise, viewed from the first. */
    ANTICLOCKWISE,

    /** {@code @@}: the remaining neighbours appear clockwise, viewed from the first. */
    CLOCKWISE;

    /**
     * @return the opposite parity, as results from swapping any two neighbours in the order this value is interpreted relative to
     */
    public Chirality flip()
    {
        return this == ANTICLOCKWISE ? CLOCKWISE : ANTICLOCKWISE;
    }

    /**
     * @return the SMILES chirality symbol denoting this parity: {@code @} or {@code @@}
     */
    public String symbol()
    {
        return this == ANTICLOCKWISE ? "@" : "@@";
    }

    /**
     * @param symbol a SMILES chirality symbol: {@code @} or {@code @@}
     * @return the parity denoted by {@code symbol}
     * @throws IllegalArgumentException if {@code symbol} is not a recognised chirality symbol
     */
    public static Chirality fromSymbol(String symbol)
    {
        return switch (symbol)
        {
            case "@" -> ANTICLOCKWISE;
            case "@@" -> CLOCKWISE;
            default -> throw new IllegalArgumentException("Unrecognised chirality symbol '" + symbol + "'");
        };
    }
}
