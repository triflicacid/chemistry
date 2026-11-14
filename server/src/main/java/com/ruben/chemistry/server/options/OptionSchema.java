package com.ruben.chemistry.server.options;

import com.ruben.chemistry.render.DepictionOptions;
import com.ruben.chemistry.render.RenderStyle;
import com.ruben.chemistry.server.render.Render2dOptions;

import java.util.List;
import java.util.Locale;

/**
 * Describes the 2D drawing options a client may set.
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
     * @param name the key in {@link Render2dOptions}, in snake_case
     * @param label a short name to show
     * @param description what it does
     * @param type {@code boolean}, {@code number}, {@code text}, {@code choice}, or {@code arrangement} (a {@code choice} of
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

    public static final List<Choice> STYLES = List.of(
            new Choice("text", "Symbols"),
            new Choice("circles", "Circles"),
            new Choice("skeletal", "Skeletal"));

    static final List<Choice> LAYOUTS = List.of(
            new Choice("row", "Row"),
            new Choice("column", "Column"));

    public static final List<Choice> HYDROGENS = List.of(
            new Choice("separate", "Separate"),
            new Choice("condensed", "Condensed"),
            new Choice("none", "None"));

    public static final double FRAGMENT_GAP_MAX = 500;
    public static final double FONT_SIZE_MIN = 4;
    public static final double FONT_SIZE_MAX = 96;

    private OptionSchema()
    {
    }

    /**
     * @return the options a client may set for a 2D drawing, in the order to show them
     */
    public static List<Option> options2d()
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
                        "boolean", style.bondGradient(), null, null, null, null, null),
                new Option("depth_bonds", "Depth bonds",
                        "Whether a 2D projection of a 3D structure draws bonds that point towards the viewer as wedges and away as hashes",
                        "boolean", false, null, null, null, null, null));
    }

    public static final List<Choice> VIEWS = List.of(
            new Choice("each", "One per match"),
            new Choice("merged", "Merged"));

    public static final int DEFAULT_MAX_MATCHES = 10;
    public static final int MAX_MATCHES = 100;

    /**
     * @return the options a client may set for how a SMARTS pattern's matches are shown, in the order to show them.
     *     A {@code text} option is free text; here {@code match_colour} is a hex colour, or blank for a colour per match.
     */
    public static List<Option> optionsMatch()
    {
        return List.of(
                new Option("view", "View",
                        "Whether each match gets its own drawing of the molecule, or all are highlighted on one",
                        "choice", "each", null, null, null, VIEWS, null),
                new Option("all_matches", "All matches",
                        "Whether matches covering the same atoms in another order count as matches of their own",
                        "boolean", false, null, null, null, null, null),
                new Option("max_matches", "Most matches",
                        "The most matches to draw",
                        "number", DEFAULT_MAX_MATCHES, "matches", 1.0, (double) MAX_MATCHES, null, null),
                new Option("dim", "Dim the rest",
                        "How much everything outside a match is faded, from 0 (not at all) to 1 (invisible)",
                        "number", 0, null, 0.0, 1.0, null, null),
                new Option("labels", "Labels",
                        "Whether matched atoms and the bonds between them are numbered",
                        "boolean", false, null, null, null, null, null),
                new Option("recolour", "Recolour",
                        "Whether matched atoms are filled with the colour of their match",
                        "boolean", false, null, null, null, null, null),
                new Option("match_colour", "Match colour",
                        "A hex colour to highlight every match in, such as #ff8800; blank gives each match a colour of its own",
                        "text", "", null, null, null, null, null));
    }

    static final List<Choice> STYLES_3D = List.of(
            new Choice("ball_and_stick", "Ball and stick"),
            new Choice("sticks", "Sticks"),
            new Choice("wireframe", "Wireframe"),
            new Choice("spacefill", "Spacefill"));

    static final double MAX_CONFORMERS = 20;
    static final double MAX_DEBUG_THRESHOLD = 10;

    /**
     * @return the options a client may set for a 3D view, in the order to show them. Only {@code conformers} changes
     *     what the server sends; the rest are applied by the client, so changing them needs no request.
     */
    public static List<Option> options3d()
    {
        return List.of(
                new Option("style", "Style",
                        "How atoms and bonds are drawn",
                        "choice", "ball_and_stick", null, null, null, STYLES_3D, null),
                new Option("hydrogens", "Hydrogens",
                        "Whether hydrogens are drawn",
                        "boolean", true, null, null, null, null, null),
                new Option("charges", "Charges",
                        "Whether a formal charge is labelled beside its atom",
                        "boolean", true, null, null, null, null, null),
                new Option("conformers", "Shapes",
                        "How many different shapes to search for; the least strained is shown",
                        "number", 1, "shapes", 1.0, MAX_CONFORMERS, null, null),
                new Option("axes", "Axes",
                        "Whether to draw the x (red), y (green) and z (blue) axes from the origin",
                        "boolean", false, null, null, null, null, null),
                new Option("debug_bonds", "Bond lengths",
                        "Mark bonds whose length strays from ideal",
                        "boolean", false, null, null, null, null, null),
                new Option("debug_angles", "Angles",
                        "Mark angles that stray from ideal",
                        "boolean", false, null, null, null, null, null),
                new Option("debug_torsions", "Torsions",
                        "Mark torsions that stray from ideal",
                        "boolean", false, null, null, null, null, null),
                new Option("debug_clashes", "Clashes",
                        "Join atoms that are too close",
                        "boolean", false, null, null, null, null, null),
                new Option("debug_strain", "Strain",
                        "Tint atoms red by how strained they are",
                        "boolean", false, null, null, null, null, null),
                new Option("debug_lone_pairs", "Lone pairs",
                        "Show where lone pairs point",
                        "boolean", false, null, null, null, null, null),
                new Option("debug_threshold", "Debug threshold",
                        "How many times the usual tolerances a term must stray before it is shown; 0 shows every term",
                        "number", 1, "x", 0.0, MAX_DEBUG_THRESHOLD, null, null));
    }
}