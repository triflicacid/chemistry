package com.ruben.chemistry.server.match;

import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.match.MatchStyle;
import com.ruben.chemistry.server.options.OptionSchema;
import com.ruben.chemistry.server.options.OptionValues;

/**
 * Turns {@link MatchOptions} into how matches are found and shown.
 */
public final class MatchOptionsMapper
{
    /**
     * @param merged whether every match is highlighted at once
     * @param allMatches whether matches covering the same atoms in another order count
     * @param maxMatches the most matches to show
     * @param style how matches are highlighted
     */
    public record Matching(boolean merged, boolean allMatches, int maxMatches, MatchStyle style)
    {
    }

    private MatchOptionsMapper()
    {
    }

    /**
     * @param options what the client asked for
     * @return how to find and highlight matches
     * @throws com.ruben.chemistry.server.molecule.MoleculeException {@code INVALID_OPTIONS} if a value is not allowed
     */
    public static Matching map(MatchOptions options)
    {
        String view = OptionValues.choice("view", options.view(), OptionSchema.VIEWS);
        int maxMatches = options.maxMatches() == null ? OptionSchema.DEFAULT_MAX_MATCHES : options.maxMatches();
        if (maxMatches < 1 || maxMatches > OptionSchema.MAX_MATCHES)
        {
            throw OptionValues.invalid("max_matches", "must be between 1 and " + OptionSchema.MAX_MATCHES);
        }

        MatchStyle style = MatchStyle.defaults();
        if (options.dim() != null)
        {
            style = style.withDimRest(OptionValues.number("dim", options.dim(), 0, 1));
        }
        if (options.labels() != null)
        {
            style = style.withLabels(options.labels());
        }
        if (options.recolour() != null)
        {
            style = style.withRecolour(options.recolour());
        }
        if (options.matchColour() != null && !options.matchColour().isBlank())
        {
            try
            {
                style = style.withColour(Colour.of(options.matchColour().trim()));
            }
            catch (IllegalArgumentException wrong)
            {
                throw OptionValues.invalid("match_colour", "must be a colour such as #ff8800");
            }
        }

        return new Matching(
                "merged".equals(view),
                Boolean.TRUE.equals(options.allMatches()),
                maxMatches,
                style);
    }
}
