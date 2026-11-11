package com.ruben.chemistry.render;

/**
 * Settings for a {@link MoleculeLayout}.
 *
 * @param style how much of the molecule is spelled out
 * @param chainGeometry how a chain of bonded atoms carries on through an atom
 * @param hydrogens how an atom's hydrogens are drawn; a {@link DepictionStyle#SKELETAL} ignores this
 */
public record DepictionOptions(DepictionStyle style, ChainGeometry chainGeometry, HydrogenStyle hydrogens)
{
    /**
     * @return a straight-chain layout drawing every hydrogen, i.e. an ordinary structural diagram
     */
    public static DepictionOptions defaults()
    {
        return new DepictionOptions(DepictionStyle.EXPLICIT, ChainGeometry.STRAIGHT, HydrogenStyle.SEPARATE);
    }

    /**
     * @return a skeletal layout where carbons are vertices
     */
    public static DepictionOptions skeletal()
    {
        return new DepictionOptions(DepictionStyle.SKELETAL, ChainGeometry.ZIGZAG, HydrogenStyle.CONDENSED);
    }

    /**
     * @return how hydrogens are drawn once the style has had its say
     */
    public HydrogenStyle effectiveHydrogens()
    {
        return style == DepictionStyle.SKELETAL ? HydrogenStyle.CONDENSED : hydrogens;
    }

    /**
     * @param style how much of the molecule is spelled out
     * @return these options, in that style
     */
    public DepictionOptions withStyle(DepictionStyle style)
    {
        return new DepictionOptions(style, chainGeometry, hydrogens);
    }

    /**
     * @param chainGeometry how a chain of bonded atoms carries on through an atom
     * @return these options, with that geometry
     */
    public DepictionOptions withChainGeometry(ChainGeometry chainGeometry)
    {
        return new DepictionOptions(style, chainGeometry, hydrogens);
    }

    /**
     * @param hydrogens how an atom's hydrogens are drawn
     * @return these options, with that style
     */
    public DepictionOptions withHydrogens(HydrogenStyle hydrogens)
    {
        return new DepictionOptions(style, chainGeometry, hydrogens);
    }
}
