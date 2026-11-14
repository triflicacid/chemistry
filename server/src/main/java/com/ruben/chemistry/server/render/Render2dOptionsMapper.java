package com.ruben.chemistry.server.render;

import com.ruben.chemistry.render.AtomShape;
import com.ruben.chemistry.render.DepictionOptions;
import com.ruben.chemistry.render.FragmentArrangement;
import com.ruben.chemistry.render.HydrogenStyle;
import com.ruben.chemistry.render.RenderStyle;
import com.ruben.chemistry.server.molecule.MoleculeException;
import com.ruben.chemistry.server.options.OptionSchema;
import com.ruben.chemistry.server.options.OptionValues;

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
     * @throws MoleculeException {@code INVALID_OPTIONS} if a value is not allowed
     */
    public static Settings map(Render2dOptions options)
    {
        String styleName = OptionValues.choice("style", options.style(), OptionSchema.STYLES);
        boolean skeletal = "skeletal".equals(styleName);

        DepictionOptions depiction = skeletal ? DepictionOptions.skeletal() : DepictionOptions.defaults();
        String hydrogens = OptionValues.choice("hydrogens", options.hydrogens(), OptionSchema.HYDROGENS);
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
            style.fragmentGap(OptionValues.number("fragment_gap", options.fragmentGap(), 0, OptionSchema.FRAGMENT_GAP_MAX));
        }
        if (options.fontSize() != null)
        {
            style.fontSize(OptionValues.number("font_size", options.fontSize(), OptionSchema.FONT_SIZE_MIN, OptionSchema.FONT_SIZE_MAX));
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

    private static FragmentArrangement arrangement(String text)
    {
        try
        {
            return FragmentArrangement.parseLinear(text);
        }
        catch (IllegalArgumentException wrong)
        {
            throw OptionValues.invalid("layout", "must be row, column, row:<n> or column:<n>");
        }
    }
}
