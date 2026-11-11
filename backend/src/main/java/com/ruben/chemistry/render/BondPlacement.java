package com.ruben.chemistry.render;

import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * One bond of a {@link Depiction}, drawn between two {@link AtomPlacement}s.
 *
 * @param first the index of the atom the bond is drawn from, which is the stereocentre of a {@link BondStyle#isStereo() stereo} bond
 * @param second the index of the atom the bond is drawn to
 * @param order the bond order
 * @param style how the bond leaves the plane of the page
 * @param sourceBond the index of the {@link Molecule#bonds() molecule bond} this came from, or empty if it
 *     is the bond to a hydrogen the depiction made up
 */
public record BondPlacement(int first, int second, BondOrder order, BondStyle style, OptionalInt sourceBond)
{
    public BondPlacement
    {
        if (first < 0 || second < 0)
        {
            throw new IllegalArgumentException("Bond placement indices cannot be negative, but were " + first + " and " + second);
        }
        if (first == second)
        {
            throw new IllegalArgumentException("A bond cannot join atom " + first + " to itself");
        }
    }

    /**
     * @param first the index of the atom the bond is drawn from
     * @param second the index of the atom the bond is drawn to
     * @param order the bond order
     * @param style how the bond leaves the plane of the page
     * @see #BondPlacement(int, int, BondOrder, BondStyle, OptionalInt)
     */
    public BondPlacement(int first, int second, BondOrder order, BondStyle style)
    {
        this(first, second, order, style, OptionalInt.empty());
    }

    /**
     * @param style how the bond leaves the plane of the page
     * @return this placement, with that style
     */
    public BondPlacement styled(BondStyle style)
    {
        return new BondPlacement(first, second, order, style, sourceBond);
    }

    /**
     * @return this placement with its endpoints the other way around
     */
    public BondPlacement reversed()
    {
        return new BondPlacement(second, first, order, style, sourceBond);
    }
}
