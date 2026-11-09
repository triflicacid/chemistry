package com.ruben.chemistry.render;

import java.util.Objects;

/**
 * Settings for a {@link MoleculeLayout}.
 *
 * @param chainGeometry how a chain of bonded atoms carries on through an atom
 * @param explicitHydrogens whether an atom's hydrogens are drawn as atoms of their own, rather
 *     than left to be written into the atom's own label
 */
public record DepictionOptions(ChainGeometry chainGeometry, boolean explicitHydrogens)
{
    /**
     * @throws NullPointerException if {@code chainGeometry} is {@code null}
     */
    public DepictionOptions
    {
        Objects.requireNonNull(chainGeometry, "chainGeometry");
    }

    /**
     * @return a straight-chain layout drawing every hydrogen, i.e. an ordinary structural diagram
     */
    public static DepictionOptions defaults()
    {
        return new DepictionOptions(ChainGeometry.STRAIGHT, true);
    }

    /**
     * @param chainGeometry how a chain of bonded atoms carries on through an atom
     * @return these options, with that geometry
     */
    public DepictionOptions withChainGeometry(ChainGeometry chainGeometry)
    {
        return new DepictionOptions(chainGeometry, explicitHydrogens);
    }

    /**
     * @param explicitHydrogens whether hydrogens are drawn as atoms of their own
     * @return these options, with that choice
     */
    public DepictionOptions withExplicitHydrogens(boolean explicitHydrogens)
    {
        return new DepictionOptions(chainGeometry, explicitHydrogens);
    }
}
