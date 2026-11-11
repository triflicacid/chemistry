package com.ruben.chemistry.render;

/**
 * How an atom's hydrogens are drawn.
 */
public enum HydrogenStyle
{
    /** Each hydrogen is an atom of its own, bonded to the atom it belongs to. */
    SEPARATE,

    /** Hydrogens are gathered into the label of the atom they belong to, e.g., methyl becomes {@code CH3}. */
    CONDENSED,

    /** Hydrogens are omitted. */
    NONE;

    /**
     * @return whether a hydrogen drawn this way is an atom in its own right
     */
    public boolean isSeparate()
    {
        return this == SEPARATE;
    }

    /**
     * @return whether a hydrogen drawn this way shows up at all
     */
    public boolean isShown()
    {
        return this != NONE;
    }
}
