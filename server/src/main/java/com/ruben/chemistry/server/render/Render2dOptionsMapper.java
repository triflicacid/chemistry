package com.ruben.chemistry.server.render;

import com.ruben.chemistry.render.AtomShape;
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

    private Render2dOptionsMapper()
    {
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
