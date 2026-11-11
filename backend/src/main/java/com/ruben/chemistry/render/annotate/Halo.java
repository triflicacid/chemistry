package com.ruben.chemistry.render.annotate;

import com.ruben.chemistry.render.Colour;

/**
 * A soft highlight drawn behind an atom or bond.
 *
 * @param colour the colour of the highlight
 * @param radiusScale how large the highlight is relative to the default size
 */
public record Halo(Colour colour, double radiusScale)
{
    /**
     * @param colour the colour of the highlight
     * @return a halo of the default size
     */
    public static Halo of(Colour colour)
    {
        return new Halo(colour, 1);
    }
}
