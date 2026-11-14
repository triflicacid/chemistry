package com.ruben.chemistry.server.render;

import com.ruben.chemistry.server.options.OptionSchema;

/**
 * The options a client may set on a render. A {@code null} component means "use the default".
 * The matching schema is {@link OptionSchema}.
 *
 * @param style {@code text}, {@code circles} or {@code skeletal}
 * @param layout how disconnected parts are arranged: {@code row}, {@code column}, {@code row:<n>} or {@code column:<n>}
 * @param hydrogens {@code separate}, {@code condensed} or {@code none}; ignored by {@code skeletal}
 * @param fragmentGap pixels of space between disconnected parts
 * @param isotopes whether a mass number is written beside a symbol
 * @param charges whether a charge is written beside a symbol
 * @param fontSize how many pixels tall a symbol is written
 * @param bondGradient whether a bond runs between the colours of the atoms at its ends
 * @param depthBonds whether a projection of a 3D structure draws bonds towards or away from the viewer as wedges and hashes
 */
public record Render2dOptions(
        String style,
        String layout,
        String hydrogens,
        Double fragmentGap,
        Boolean isotopes,
        Boolean charges,
        Double fontSize,
        Boolean bondGradient,
        Boolean depthBonds)
{
    /**
     * @return options that change nothing
     */
    public static Render2dOptions defaults()
    {
        return new Render2dOptions(null, null, null, null, null, null, null, null, null);
    }
}
