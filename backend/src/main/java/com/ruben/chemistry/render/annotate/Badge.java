package com.ruben.chemistry.render.annotate;

import com.ruben.chemistry.render.Colour;

import java.util.Objects;
import java.util.Optional;

/**
 * A short piece of text attached to an atom or bond.
 *
 * @param text the text to show
 * @param colour the colour of the text, or empty for the annotation style's default
 */
public record Badge(String text, Optional<Colour> colour)
{
    /**
     * @param text the text to show
     * @return a badge in the default colour
     */
    public static Badge of(String text)
    {
        return new Badge(text, Optional.empty());
    }

    /**
     * @param text the text to show
     * @param colour the colour of the text
     * @return a badge in {@code colour}
     */
    public static Badge of(String text, Colour colour)
    {
        return new Badge(text, Optional.of(colour));
    }
}
