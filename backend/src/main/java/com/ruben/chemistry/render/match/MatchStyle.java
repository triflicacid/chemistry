package com.ruben.chemistry.render.match;

import com.ruben.chemistry.render.Colour;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How {@link MatchAnnotator} highlights a match.
 *
 * @param halo whether matched atoms and the bonds between them get a halo
 * @param recolour whether matched atoms are also filled with their match's colour
 * @param dimRest how much everything outside the matches is faded, from 0 (not at all) to 1 (invisible)
 * @param labels whether matched atoms and bonds are labelled with {@link com.ruben.chemistry.query.MatchLabels}
 * @param palette the colours given to successive matches, cycling if there are more matches than colours
 * @param colourOverride a colour to give every match instead of the palette
 */
public record MatchStyle(
    boolean halo,
    boolean recolour,
    double dimRest,
    boolean labels,
    List<Colour> palette,
    Optional<Colour> colourOverride)
{
    public MatchStyle
    {
        if (!(dimRest >= 0 && dimRest <= 1))
        {
            throw new IllegalArgumentException("The dimming must be between 0 and 1, but was " + dimRest);
        }
        if (palette.isEmpty())
        {
            throw new IllegalArgumentException("A match style needs at least one palette colour");
        }
    }

    /**
     * @return a halo on matches, nothing dimmed, no automatic labels
     */
    public static MatchStyle defaults()
    {
        return new MatchStyle(
            true,
            false,
            0,
            false,
            List.of(Colour.of("#ffd43b"), Colour.of("#74c0fc"), Colour.of("#8ce99a"), Colour.of("#ffa8a8"), Colour.of("#d0bfff")),
            Optional.empty());
    }

    /**
     * @param halo whether matches get a halo
     * @return this style, with that set
     */
    public MatchStyle withHalo(boolean halo)
    {
        return new MatchStyle(halo, recolour, dimRest, labels, palette, colourOverride);
    }

    /**
     * @param recolour whether matched atoms are filled with their match's colour
     * @return this style, with that set
     */
    public MatchStyle withRecolour(boolean recolour)
    {
        return new MatchStyle(halo, recolour, dimRest, labels, palette, colourOverride);
    }

    /**
     * @param dimRest how much everything outside the matches is faded, from 0 to 1
     * @return this style, with that set
     */
    public MatchStyle withDimRest(double dimRest)
    {
        return new MatchStyle(halo, recolour, dimRest, labels, palette, colourOverride);
    }

    /**
     * @param labels whether matches are labelled automatically
     * @return this style, with that set
     */
    public MatchStyle withLabels(boolean labels)
    {
        return new MatchStyle(halo, recolour, dimRest, labels, palette, colourOverride);
    }

    /**
     * @param palette the colours given to successive matches
     * @return this style, with that set
     */
    public MatchStyle withPalette(List<Colour> palette)
    {
        return new MatchStyle(halo, recolour, dimRest, labels, palette, colourOverride);
    }

    /**
     * @param colour a colour to give every match instead of the palette
     * @return this style, with that set
     */
    public MatchStyle withColour(Colour colour)
    {
        return new MatchStyle(halo, recolour, dimRest, labels, palette, Optional.of(colour));
    }

    /**
     * @param index which match, counting from 0
     * @return the colour of that match
     */
    Colour colourOfMatch(int index)
    {
        return colourOverride.orElseGet(() -> palette.get(index % palette.size()));
    }
}
