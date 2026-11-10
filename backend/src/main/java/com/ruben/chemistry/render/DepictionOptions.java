package com.ruben.chemistry.render;

import java.util.Objects;

/**
 * Settings for a {@link MoleculeLayout}.
 *
 * @param chainGeometry how a chain of bonded atoms carries on through an atom
 * @param hydrogens how an atom's hydrogens are drawn
 */
public record DepictionOptions(ChainGeometry chainGeometry, HydrogenStyle hydrogens)
{
    /**
     * @throws NullPointerException if either setting is {@code null}
     */
    public DepictionOptions
    {
        Objects.requireNonNull(chainGeometry, "chainGeometry");
        Objects.requireNonNull(hydrogens, "hydrogens");
    }

    /**
     * @return a straight-chain layout drawing every hydrogen, i.e. an ordinary structural diagram
     */
    public static DepictionOptions defaults()
    {
        return new DepictionOptions(ChainGeometry.STRAIGHT, HydrogenStyle.SEPARATE);
    }

    /**
     * @param chainGeometry how a chain of bonded atoms carries on through an atom
     * @return these options, with that geometry
     */
    public DepictionOptions withChainGeometry(ChainGeometry chainGeometry)
    {
        return new DepictionOptions(chainGeometry, hydrogens);
    }

    /**
     * @param hydrogens how an atom's hydrogens are drawn
     * @return these options, with that style
     */
    public DepictionOptions withHydrogens(HydrogenStyle hydrogens)
    {
        return new DepictionOptions(chainGeometry, hydrogens);
    }
}
