package com.ruben.chemistry.server.render;

import com.ruben.chemistry.render.AtomShape;
import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.match.MatchStyle;
import com.ruben.chemistry.render.DepictionOptions;
import com.ruben.chemistry.render.FragmentArrangement;
import com.ruben.chemistry.render.HydrogenStyle;
import com.ruben.chemistry.render.RenderStyle;

import java.util.List;
import java.util.Locale;

/**
 * Turns {@link Render2dOptions} into what the library draws with.
 */
public final class Render2dOptionsMapper
{
    /**
     * @param depiction how the molecule is laid out
     * @param style how the layout is drawn
     */
    public record Settings(DepictionOptions depiction, RenderStyle style)
    {
    }

    /**
     * @param merged whether every match is highlighted on one drawing
     * @param allMatches whether matches covering the same atoms in another order count
     * @param maxMatches the most matches to draw
     * @param style how matches are highlighted
     */
    public record Matching(boolean merged, boolean allMatches, int maxMatches, MatchStyle style)
    {
    }

    private Render2dOptionsMapper()
    {
    }

    /**
     * @param options what the client asked for
     * @return how to find and highlight matches
     * @throws RenderException {@code INVALID_OPTIONS} if a value is not allowed
     */
    public static Matching map(MatchOptions options)
    {
        String view = choice("view", options.view(), OptionSchema.VIEWS);
        int maxMatches = options.maxMatches() == null ? OptionSchema.DEFAULT_MAX_MATCHES : options.maxMatches();
        if (maxMatches < 1 || maxMatches > OptionSchema.MAX_MATCHES)
        {
            throw invalid("max_matches", "must be between 1 and " + OptionSchema.MAX_MATCHES);
        }

        MatchStyle style = MatchStyle.defaults();
        if (options.dim() != null)
        {
            style = style.withDimRest(number("dim", options.dim(), 0, 1));
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
                throw invalid("match_colour", "must be a colour such as #ff8800");
            }
        }

        return new Matching(
                "merged".equals(view),
                Boolean.TRUE.equals(options.allMatches()),
                maxMatches,
                style);
    }

    /**
     * @param options what the client asked for
     * @return what to draw with; atoms and bonds always carry their metadata
     * @throws RenderException {@code INVALID_OPTIONS} if a value is not allowed
     */
    public static Settings map(Render2dOptions options)
    {
        String styleName = choice("style", options.style(), OptionSchema.STYLES);
        boolean skeletal = "skeletal".equals(styleName);

        DepictionOptions depiction = skeletal ? DepictionOptions.skeletal() : DepictionOptions.defaults();
        String hydrogens = choice("hydrogens", options.hydrogens(), OptionSchema.HYDROGENS);
        if (hydrogens != null)
        {
            depiction = depiction.withHydrogens(HydrogenStyle.valueOf(hydrogens.toUpperCase(Locale.ROOT)));
        }

        if (options.depthBonds() != null)
        {
            depiction = depiction.withDepthBonds(options.depthBonds());
        }

        RenderStyle.Builder style = RenderStyle.builder().metadata(true);
        if ("circles".equals(styleName))
        {
            style.atomShape(AtomShape.CIRCLE);
        }
        if (options.layout() != null)
        {
            style.fragmentArrangement(arrangement(options.layout()));
        }
        if (options.fragmentGap() != null)
        {
            style.fragmentGap(number("fragment_gap", options.fragmentGap(), 0, OptionSchema.FRAGMENT_GAP_MAX));
        }
        if (options.fontSize() != null)
        {
            style.fontSize(number("font_size", options.fontSize(), OptionSchema.FONT_SIZE_MIN, OptionSchema.FONT_SIZE_MAX));
        }
        if (options.isotopes() != null)
        {
            style.isotopes(options.isotopes());
        }
        if (options.charges() != null)
        {
            style.charges(options.charges());
        }
        if (options.bondGradient() != null)
        {
            style.bondGradient(options.bondGradient());
        }

        return new Settings(depiction, style.build());
    }

    private static String choice(String name, String value, List<OptionSchema.Choice> choices)
    {
        if (value == null)
        {
            return null;
        }

        List<String> allowed = choices.stream().map(OptionSchema.Choice::value).toList();
        if (!allowed.contains(value))
        {
            throw invalid(name, "must be one of " + allowed);
        }
        return value;
    }

    private static double number(String name, double value, double min, double max)
    {
        if (value < min || value > max)
        {
            throw invalid(name, "must be between " + min + " and " + max);
        }
        return value;
    }

    private static FragmentArrangement arrangement(String text)
    {
        try
        {
            return FragmentArrangement.parseLinear(text);
        }
        catch (IllegalArgumentException wrong)
        {
            throw invalid("layout", "must be row, column, row:<n> or column:<n>");
        }
    }

    private static RenderException invalid(String name, String problem)
    {
        return new RenderException("INVALID_OPTIONS", "options." + name + ": " + problem);
    }
}
