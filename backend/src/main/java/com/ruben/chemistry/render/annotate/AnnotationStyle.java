package com.ruben.chemistry.render.annotate;

import com.ruben.chemistry.render.Colour;

import java.util.Objects;

/**
 * How the annotation layer draws halos and badges.
 *
 * @param haloOpacity how opaque halos are, from 0 to 1
 * @param badgeFontSize how large badge text is, in pixels; must be positive
 * @param badgeColour the colour of badge text that has none of its own
 * @param badgeOutline the colour drawn around badge text so that it stays readable on top of the structure
 * @param fontFamily the font badge text is written in
 */
public record AnnotationStyle(
    double haloOpacity,
    double badgeFontSize,
    Colour badgeColour,
    Colour badgeOutline,
    String fontFamily)
{
    public AnnotationStyle
    {
        if (!(haloOpacity >= 0 && haloOpacity <= 1))
        {
            throw new IllegalArgumentException("A halo's opacity must be between 0 and 1, but was " + haloOpacity);
        }
        if (!(badgeFontSize > 0))
        {
            throw new IllegalArgumentException("A badge's font size must be positive, but was " + badgeFontSize);
        }
    }

    /**
     * @return the default style: translucent halos, and small dark badges outlined in white
     */
    public static AnnotationStyle defaults()
    {
        return new AnnotationStyle(0.45, 12, Colour.BLACK, Colour.WHITE, "Helvetica, Arial, sans-serif");
    }

    /**
     * @param haloOpacity how opaque halos are, from 0 to 1
     * @return this style, with its halo opacity set
     */
    public AnnotationStyle withHaloOpacity(double haloOpacity)
    {
        return new AnnotationStyle(haloOpacity, badgeFontSize, badgeColour, badgeOutline, fontFamily);
    }

    /**
     * @param badgeFontSize how large badge text is, in pixels
     * @return this style, with its badge font size set
     */
    public AnnotationStyle withBadgeFontSize(double badgeFontSize)
    {
        return new AnnotationStyle(haloOpacity, badgeFontSize, badgeColour, badgeOutline, fontFamily);
    }

    /**
     * @param badgeColour the colour of badge text that has none of its own
     * @return this style, with its badge colour set
     */
    public AnnotationStyle withBadgeColour(Colour badgeColour)
    {
        return new AnnotationStyle(haloOpacity, badgeFontSize, badgeColour, badgeOutline, fontFamily);
    }

    /**
     * @param badgeOutline the colour drawn around badge text
     * @return this style, with its badge outline set
     */
    public AnnotationStyle withBadgeOutline(Colour badgeOutline)
    {
        return new AnnotationStyle(haloOpacity, badgeFontSize, badgeColour, badgeOutline, fontFamily);
    }

    /**
     * @param fontFamily the font badge text is written in
     * @return this style, with its font family set
     */
    public AnnotationStyle withFontFamily(String fontFamily)
    {
        return new AnnotationStyle(haloOpacity, badgeFontSize, badgeColour, badgeOutline, fontFamily);
    }
}
