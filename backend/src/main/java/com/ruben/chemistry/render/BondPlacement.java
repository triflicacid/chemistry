package com.ruben.chemistry.render;

import com.ruben.chemistry.core.BondOrder;

import java.util.Objects;

/**
 * One bond of a {@link Depiction}, drawn between two {@link AtomPlacement}s.
 *
 * @param first the index of the atom the bond is drawn from, which is the stereocentre of a {@link BondStyle#isStereo() stereo} bond
 * @param second the index of the atom the bond is drawn to
 * @param order the bond order
 * @param style how the bond leaves the plane of the page
 */
public record BondPlacement(int first, int second, BondOrder order, BondStyle style)
{
    /**
     * @throws NullPointerException if {@code order} or {@code style} is {@code null}
     * @throws IllegalArgumentException if either index is negative, or the two are the same
     */
    public BondPlacement
    {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(style, "style");
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
     * @param style how the bond leaves the plane of the page
     * @return this placement, with that style
     */
    public BondPlacement styled(BondStyle style)
    {
        return new BondPlacement(first, second, order, style);
    }

    /**
     * @return this placement with its endpoints the other way around
     */
    public BondPlacement reversed()
    {
        return new BondPlacement(second, first, order, style);
    }
}
