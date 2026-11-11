package com.ruben.chemistry.render.annotate;

import com.ruben.chemistry.render.Colour;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * How to restyle one bond.
 *
 * @param colour replacement colour
 * @param opacity how opaque the bond is, from 0 (invisible) to 1
 * @param widthScale how thick the bond is relative to normal; must be positive
 * @param halo a highlight behind the bond
 * @param badge text attached to the middle of the bond
 */
public record BondLook(
    Optional<Colour> colour,
    OptionalDouble opacity,
    OptionalDouble widthScale,
    Optional<Halo> halo,
    Optional<Badge> badge)
{
    /** A look that changes nothing. */
    public static final BondLook NONE = new BondLook(Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(), Optional.empty(), Optional.empty());

    public BondLook
    {
        Utils.requireFraction(opacity, "opacity");
        if (widthScale.isPresent() && !(widthScale.getAsDouble() > 0))
        {
            throw new IllegalArgumentException("A bond's width scale must be positive, but was " + widthScale.getAsDouble());
        }
    }

    /**
     * @param colour the replacement colour
     * @return this look, with its colour set
     */
    public BondLook withColour(Colour colour)
    {
        return new BondLook(Optional.of(colour), opacity, widthScale, halo, badge);
    }

    /**
     * @param opacity how opaque the bond is, from 0 to 1
     * @return this look, with its opacity set
     */
    public BondLook withOpacity(double opacity)
    {
        return new BondLook(colour, OptionalDouble.of(opacity), widthScale, halo, badge);
    }

    /**
     * @param widthScale how thick the bond is relative to normal
     * @return this look, with its width scale set
     */
    public BondLook withWidthScale(double widthScale)
    {
        return new BondLook(colour, opacity, OptionalDouble.of(widthScale), halo, badge);
    }

    /**
     * @param halo the highlight behind the bond
     * @return this look, with its halo set
     */
    public BondLook withHalo(Halo halo)
    {
        return new BondLook(colour, opacity, widthScale, Optional.of(halo), badge);
    }

    /**
     * @param badge the text attached to the bond
     * @return this look, with its badge set
     */
    public BondLook withBadge(Badge badge)
    {
        return new BondLook(colour, opacity, widthScale, halo, Optional.of(badge));
    }

    /**
     * @param base the look to fall back on
     * @return a look taking each aspect from this look where set, and from {@code base} otherwise
     */
    public BondLook over(BondLook base)
    {
        return new BondLook(
            colour.or(() -> base.colour),
            Utils.firstPresent(opacity, base.opacity),
            Utils.firstPresent(widthScale, base.widthScale),
            halo.or(() -> base.halo),
            badge.or(() -> base.badge));
    }
}
