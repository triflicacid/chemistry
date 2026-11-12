package com.ruben.chemistry.server.render;

import com.ruben.chemistry.render.DepictionOptions;
import com.ruben.chemistry.render.RenderStyle;

import java.util.List;
import java.util.Locale;

/**
 * Describes the rendering options a client may set.
 */
public final class OptionSchema
{
    /**
     * One of a fixed set of values.
     *
     * @param value what is sent to the server
     * @param label what is shown to the user
     */
    public record Choice(String value, String label)
    {
    }

    /**
     * The option is ignored while another option has a given value.
     *
     * @param option the other option
     * @param value the value it has
     */
    public record Condition(String option, String value)
    {
    }

    /**
     * @param name the key in {@link RenderOptions}, in snake_case
     * @param label a short name to show
     * @param description what it does
     * @param type {@code boolean}, {@code number}, {@code choice}, or {@code arrangement} (a {@code choice} of
     *     lines, optionally followed by {@code :<n>} fragments per line)
     * @param defaultValue what is used if the client does not set it
     * @param unit what a number counts, or {@code null}
     * @param min the least a number may be, or {@code null}
     * @param max the most a number may be, or {@code null}
     * @param choices the values a {@code choice} or {@code arrangement} may take, or {@code null}
     * @param ignoredWhen when the option has no effect, or {@code null}
     */
    public record Option(
            String name,
            String label,
            String description,
            String type,
            Object defaultValue,
            String unit,
            Double min,
            Double max,
            List<Choice> choices,
            Condition ignoredWhen)
    {
    }

    static final List<Choice> STYLES = List.of(
            new Choice("text", "Symbols"),
            new Choice("circles", "Circles"),
            new Choice("skeletal", "Skeletal"));

    static final List<Choice> LAYOUTS = List.of(
            new Choice("row", "Row"),
            new Choice("column", "Column"));

    static final List<Choice> HYDROGENS = List.of(
            new Choice("separate", "Separate"),
            new Choice("condensed", "Condensed"),
            new Choice("none", "None"));

    static final double FRAGMENT_GAP_MAX = 500;
    static final double FONT_SIZE_MIN = 4;
    static final double FONT_SIZE_MAX = 96;

    private OptionSchema()
    {
    }

    /**
     * @return the options a client may set, in the order to show them
     */
    public static List<Option> options()
    {
        RenderStyle style = RenderStyle.defaults();
        String hydrogens = DepictionOptions.defaults().hydrogens().name().toLowerCase(Locale.ROOT);

        return List.of(
                new Option("style", "Style",
                        "Whether an atom is written as its symbol, drawn as a ball, or, for carbon, left as a bare vertex",
                        "choice", "text", null, null, null, STYLES, null),
                new Option("layout", "Layout",
                        "How the parts of a disconnected molecule are arranged",
                        "arrangement", "row", "per line", 1.0, null, LAYOUTS, null),
                new Option("hydrogens", "Hydrogens",
                        "Whether hydrogens are drawn as atoms of their own, gathered into the symbol they hang off, or left out",
                        "choice", hydrogens, null, null, null, HYDROGENS, new Condition("style", "skeletal")),
                new Option("fragment_gap", "Fragment gap",
                        "Empty space between the parts of a disconnected molecule",
                        "number", style.fragmentGap(), "px", 0.0, FRAGMENT_GAP_MAX, null, null),
                new Option("isotopes", "Isotopes",
                        "Whether a mass number is written beside a symbol",
                        "boolean", style.isotopes(), null, null, null, null, null),
                new Option("charges", "Charges",
                        "Whether a charge is written beside a symbol",
                        "boolean", style.charges(), null, null, null, null, null),
                new Option("font_size", "Font size",
                        "How tall a symbol is written",
                        "number", style.fontSize(), "px", FONT_SIZE_MIN, FONT_SIZE_MAX, null, null),
                new Option("bond_gradient", "Bond gradient",
                        "Whether a bond runs between the colours of the atoms at its ends",
                        "boolean", style.bondGradient(), null, null, null, null, null));
    }
}
