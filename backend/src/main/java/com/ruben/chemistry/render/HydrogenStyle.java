package com.ruben.chemistry.render;

/**
 * How an atom's hydrogens are drawn.
 */
public enum HydrogenStyle
{
    /**
     * Each hydrogen is an atom of its own, bonded to the atom it belongs to. This is the ordinary
     * structural diagram, and the only style that can show a parity about an implicit hydrogen.
     */
    SEPARATE,

    /**
     * Hydrogens are gathered into the label of the atom they belong to, so that a methyl group is
     * written {@code CH3} rather than drawn as four atoms. This keeps a crowded molecule readable.
     */
    CONDENSED,

    /**
     * Hydrogens are left out altogether.
     */
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
