package com.ruben.chemistry.render;

import com.ruben.chemistry.render.rings.RingRefinement;

/**
 * Settings for a {@link MoleculeLayout}.
 *
 * @param style how much of the molecule is spelled out
 * @param chainGeometry how a chain of bonded atoms carries on through an atom
 * @param hydrogens how an atom's hydrogens are drawn; a {@link DepictionStyle#SKELETAL} ignores this
 * @param projectCages whether a ring system that cannot be drawn flat is drawn as a view of its 3D structure
 * @param ringRefinement how hard ring systems, and branches that hang off them, are worked on to avoid collisions
 * @param ringRanking which of a ring system's distinct drawings to use, counting from 0 for the best; a system with
 *     fewer drawings uses its last
 */
public record DepictionOptions(DepictionStyle style, ChainGeometry chainGeometry, HydrogenStyle hydrogens, boolean projectCages, RingRefinement ringRefinement, int ringRanking)
{
    /**
     * @throws IllegalArgumentException if {@code ringRanking} is negative
     */
    public DepictionOptions
    {
        if (ringRanking < 0)
        {
            throw new IllegalArgumentException("Ring ranking cannot be negative, but was " + ringRanking);
        }
    }

    /**
     * @param style how much of the molecule is spelled out
     * @param chainGeometry how a chain of bonded atoms carries on through an atom
     * @param hydrogens how an atom's hydrogens are drawn
     */
    public DepictionOptions(DepictionStyle style, ChainGeometry chainGeometry, HydrogenStyle hydrogens)
    {
        this(style, chainGeometry, hydrogens, true, RingRefinement.STANDARD, 0);
    }

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
        return new DepictionOptions(style, chainGeometry, hydrogens, projectCages, ringRefinement, ringRanking);
    }

    /**
     * @param chainGeometry how a chain of bonded atoms carries on through an atom
     * @return these options, with that geometry
     */
    public DepictionOptions withChainGeometry(ChainGeometry chainGeometry)
    {
        return new DepictionOptions(style, chainGeometry, hydrogens, projectCages, ringRefinement, ringRanking);
    }

    /**
     * @param hydrogens how an atom's hydrogens are drawn
     * @return these options, with that style
     */
    public DepictionOptions withHydrogens(HydrogenStyle hydrogens)
    {
        return new DepictionOptions(style, chainGeometry, hydrogens, projectCages, ringRefinement, ringRanking);
    }

    /**
     * @param projectCages whether ring systems that cannot be drawn flat are drawn as views of their 3D structure
     * @return these options, with that choice
     */
    public DepictionOptions withProjectCages(boolean projectCages)
    {
        return new DepictionOptions(style, chainGeometry, hydrogens, projectCages, ringRefinement, ringRanking);
    }

    /**
     * @param ringRefinement how hard ring systems, and branches that hang off them, are worked on to avoid collisions
     * @return these options, with that effort
     */
    public DepictionOptions withRingRefinement(RingRefinement ringRefinement)
    {
        return new DepictionOptions(style, chainGeometry, hydrogens, projectCages, ringRefinement, ringRanking);
    }

    /**
     * @param ringRanking which of a ring system's distinct drawings to use, counting from 0 for the best
     * @return these options, with that ranking
     * @throws IllegalArgumentException if {@code ringRanking} is negative
     */
    public DepictionOptions withRingRanking(int ringRanking)
    {
        return new DepictionOptions(style, chainGeometry, hydrogens, projectCages, ringRefinement, ringRanking);
    }
}
