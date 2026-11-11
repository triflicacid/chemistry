package com.ruben.chemistry.render.annotate;

import com.ruben.chemistry.render.Colour;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * How to restyle one atom.
 *
 * @param fill replacement fill colour
 * @param outline replacement outline colour
 * @param opacity how opaque the atom is, from 0 (invisible) to 1
 * @param saturation how much of the colour is kept, from 0 (grey) to 1 (unchanged)
 * @param halo a highlight behind the atom
 * @param badge text attached to the atom
 */
public record AtomLook(
    Optional<Colour> fill,
    Optional<Colour> outline,
    OptionalDouble opacity,
    OptionalDouble saturation,
    Optional<Halo> halo,
    Optional<Badge> badge)
{
    /** A look that changes nothing. */
    public static final AtomLook NONE = new AtomLook(Optional.empty(), Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(), Optional.empty(), Optional.empty());

    public AtomLook
    {
        Utils.requireFraction(opacity, "opacity");
        Utils.requireFraction(saturation, "saturation");
    }

    /**
     * @param fill the replacement fill colour
     * @return this look, with its fill colour set
     */
    public AtomLook withFill(Colour fill)
    {
        return new AtomLook(Optional.of(fill), outline, opacity, saturation, halo, badge);
    }

    /**
     * @param outline the replacement outline colour
     * @return this look, with its outline colour set
     */
    public AtomLook withOutline(Colour outline)
    {
        return new AtomLook(fill, Optional.of(outline), opacity, saturation, halo, badge);
    }

    /**
     * @param opacity how opaque the atom is, from 0 to 1
     * @return this look, with its opacity set
     */
    public AtomLook withOpacity(double opacity)
    {
        return new AtomLook(fill, outline, OptionalDouble.of(opacity), saturation, halo, badge);
    }

    /**
     * @param saturation how much of the colour is kept, from 0 to 1
     * @return this look, with its saturation set
     */
    public AtomLook withSaturation(double saturation)
    {
        return new AtomLook(fill, outline, opacity, OptionalDouble.of(saturation), halo, badge);
    }

    /**
     * @param halo the highlight behind the atom
     * @return this look, with its halo set
     */
    public AtomLook withHalo(Halo halo)
    {
        return new AtomLook(fill, outline, opacity, saturation, Optional.of(halo), badge);
    }

    /**
     * @param badge the text attached to the atom
     * @return this look, with its badge set
     */
    public AtomLook withBadge(Badge badge)
    {
        return new AtomLook(fill, outline, opacity, saturation, halo, Optional.of(badge));
    }

    /**
     * @param base the look to fall back on
     * @return a look taking each aspect from this look where set, and from {@code base} otherwise
     */
    public AtomLook over(AtomLook base)
    {
        return new AtomLook(
            fill.or(() -> base.fill),
            outline.or(() -> base.outline),
            Utils.firstPresent(opacity, base.opacity),
            Utils.firstPresent(saturation, base.saturation),
            halo.or(() -> base.halo),
            badge.or(() -> base.badge));
    }
}
