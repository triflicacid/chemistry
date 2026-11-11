package com.ruben.chemistry.cli;

import com.ruben.chemistry.render.AtomShape;
import com.ruben.chemistry.render.CellAlignment;
import com.ruben.chemistry.render.ChainGeometry;
import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.DepictionOptions;
import com.ruben.chemistry.render.FragmentArrangement;
import com.ruben.chemistry.render.HydrogenStyle;
import com.ruben.chemistry.render.RenderStyle;
import com.ruben.chemistry.render.rings.RingRefinement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The arguments a drawing command was given.
 */
final class CommandOptions
{
    /**
     * How a molecule is drawn.
     */
    private enum Drawing
    {
        /** Every atom written out as its own symbol. */
        TEXT(AtomShape.TEXT),

        /** Every atom drawn as a ball. */
        CIRCLES(AtomShape.CIRCLE),

        /** Carbon left as a bare vertex, everything else written out. */
        SKELETAL(AtomShape.TEXT);

        private final AtomShape shape;

        Drawing(AtomShape shape)
        {
            this.shape = shape;
        }
    }

    static final CliOption<Boolean> HELP = CliOption.flag(
            "help",
            "show usage information for this command",
            false
    );

    static final CliOption<Drawing> STYLE = CliOption.choice(
            "style",
            "whether an atom is written as its symbol, drawn as a ball, or, "
                    + "for carbon, left as a bare vertex of a line-angle drawing",
            Map.of(
                    "text", Drawing.TEXT,
                    "circles", Drawing.CIRCLES,
                    "skeletal", Drawing.SKELETAL
            ),
            () -> Drawing.TEXT
    );

    static final CliOption<ChainGeometry> GEOMETRY = CliOption.choice(
            "geometry",
            "how a chain of single bonds runs",
            Map.of(
                    "straight", ChainGeometry.STRAIGHT,
                    "zigzag", ChainGeometry.ZIGZAG
            ),
            () -> ChainGeometry.STRAIGHT
    );

    static final CliOption<FragmentArrangement> LAYOUT = CliOption.parsed(
            "layout",
            "row|column|row:<n>|column:<n>|grid:<template>",
            "how the parts of a disconnected molecule are arranged",
            FragmentArrangement::parse,
            null
    );

    static final CliOption<CellAlignment> GRID_ALIGN = CliOption.choice(
            "grid-align",
            "where a part sits sideways in its cell",
            Map.of(
                    "left", CellAlignment.LEFT,
                    "centre", CellAlignment.CENTRE,
                    "center", CellAlignment.CENTRE,
                    "right", CellAlignment.RIGHT
            ),
            () -> CellAlignment.CENTRE
    ).withDefaultText("centre");

    static final CliOption<HydrogenStyle> HYDROGENS = CliOption.choice(
            "hydrogens",
            "whether hydrogens are drawn as atoms of their own, gathered into "
                    + "the symbol they hang off, or left out",
            Map.of(
                    "separate", HydrogenStyle.SEPARATE,
                    "condensed", HydrogenStyle.CONDENSED,
                    "none", HydrogenStyle.NONE
            ),
            () -> HydrogenStyle.SEPARATE
    ).withRequirement(
            options -> options.get(STYLE) != Drawing.SKELETAL || options.get(CommandOptions.HYDROGENS) == HydrogenStyle.NONE,
            "--hydrogens must be none with --style=skeletal, which leaves hydrogens out"
    );

    static final CliOption<Double> FRAGMENT_GAP = CliOption.number(
            "fragment-gap",
            "px",
            "how many pixels of empty space lie between parts",
            30
    );

    static final CliOption<Boolean> PROJECT_CAGES = CliOption.flag(
            "project-cages",
            "whether a ring system that cannot be drawn flat is drawn as a view of its 3D structure",
            true
    ).withRequirement(
            options -> options.get(CommandOptions.RING_REFINEMENT).isEnabled(),
            "--project-cages has no effect with --ring-refinement=none, which does not work on ring systems"
    );

    static final CliOption<RingRefinement> RING_REFINEMENT = CliOption.parsed(
            "ring-refinement",
            "none|standard|thorough|<n>",
            "how hard ring systems, and branches that hang off them, are worked on to avoid collisions; "
                    + "a number is how many times the usual effort, and is raised to --ring-alternatives if that is higher",
            RingRefinement::parse,
            () -> RingRefinement.STANDARD
    ).withDefaultText("standard");

    static final CliOption<Integer> ALTERNATIVES = CliOption.parsed(
            "ring-alternatives",
            "n",
            "how many different drawings of the ring systems to show side by side; 1 shows only the best",
            input -> {
                try
                {
                    int count = Integer.parseInt(input);
                    if (count < 1)
                    {
                        throw new IllegalArgumentException("--ring-alternatives must be at least 1, but was " + count);
                    }
                    return count;
                }
                catch (NumberFormatException invalid)
                {
                    throw new IllegalArgumentException("--ring-alternatives must be a whole number, but was '" + input + "'");
                }
            },
            () -> 1
    ).withRequirement(
            options -> options.get(CommandOptions.ALTERNATIVES) <= 1 || options.get(PROJECT_CAGES),
            "--ring-alternatives above 1 needs --project-cages, since other drawings are other views of the 3D structure"
    ).withRequirement(
            options -> options.get(CommandOptions.ALTERNATIVES) <= 1 || options.get(RING_REFINEMENT).isEnabled(),
            "--ring-alternatives above 1 cannot be had with --ring-refinement=none"
    );

    static final CliOption<Boolean> DEBUG_LAYOUT = CliOption.flag(
            "debug-layout",
            "outline each part's box and the grid cell it sits in for debugging layouts",
            false
    );

    static final CliOption<Boolean> ISOTOPES = CliOption.flag(
            "isotopes",
            "whether a mass number is written beside a symbol",
            true
    );

    static final CliOption<Boolean> CHARGES = CliOption.flag(
            "charges",
            "whether a charge is written beside a symbol",
            true
    );

    static final CliOption<Double> BOND_LENGTH = CliOption.number(
            "bond-length",
            "px",
            "how many pixels long one bond is",
            50
    );

    static final CliOption<Double> MARGIN = CliOption.number(
            "margin",
            "px",
            "how many pixels of empty space surround the drawing",
            12
    );

    static final CliOption<Double> FONT_SIZE = CliOption.number(
            "font-size",
            "px",
            "how many pixels tall a symbol is written",
            18
    );

    static final CliOption<Colour> BACKGROUND = CliOption.parsed(
            "background",
            "colour",
            "the colour behind the drawing, or none",
            value -> value.equalsIgnoreCase("none") ? null : Colour.of(value),
            null
    );

    static final CliOption<String> BOND_COLOUR = CliOption.string(
            "bond-colour",
            "colour",
            "the colour a bond is drawn in",
            null
    );

    static final CliOption<Boolean> BOND_GRADIENT = CliOption.flag(
            "bond-gradient",
            "whether a bond runs between the colours of the atoms at its ends instead",
            false
    );

    static final CliOption<Boolean> METADATA = CliOption.flag(
            "metadata",
            "whether each atom and bond carries data-* attributes tracing it back to the molecule",
            false
    );

    static final CliOption<String> COLOUR = CliOption.repeatedString(
            "colour",
            "<element>:<colour>",
            "the colour one element is drawn in; repeatable"
    );

    static final List<CliOption<?>> DRAWING_OPTIONS = List.of(
            HELP,
            STYLE,
            GEOMETRY,
            LAYOUT,
            GRID_ALIGN,
            HYDROGENS,
            FRAGMENT_GAP,
            DEBUG_LAYOUT,
            RING_REFINEMENT,
            ALTERNATIVES,
            PROJECT_CAGES,
            ISOTOPES,
            CHARGES,
            BOND_LENGTH,
            MARGIN,
            FONT_SIZE,
            BACKGROUND,
            BOND_COLOUR,
            BOND_GRADIENT,
            METADATA,
            COLOUR
    );

    static final String USAGE = CliParser.usage("Drawing options:", DRAWING_OPTIONS);

    private CommandOptions()
    {
    }

    /**
     * Returns the shared drawing option definitions.
     */
    static List<CliOption<?>> definitions()
    {
        return DRAWING_OPTIONS;
    }

    /**
     * Combines the shared drawing options with command-specific options.
     *
     * @param commandOptions the additional options
     * @return all option definitions
     */
    static List<CliOption<?>> definitions(CliOption<?>... commandOptions)
    {
        return Stream.concat(DRAWING_OPTIONS.stream(), Stream.of(commandOptions)).collect(
                Collectors.collectingAndThen(
                        Collectors.toMap(
                                CliOption::name,
                                option -> option,
                                (first, second) -> {
                                    throw new IllegalArgumentException("Duplicate option definition: --" + second.name());
                                },
                                LinkedHashMap::new),
                        map -> List.copyOf(map.values())));
    }

    /**
     * Parses arguments using the shared and command-specific options.
     *
     * @param arguments the arguments given
     * @param commandOptions the additional options understood by this command
     * @return the parsed options
     */
    static CliOptions of(List<String> arguments, CliOption<?>... commandOptions)
    {
        return CliParser.parse(arguments, definitions(commandOptions));
    }

    /**
     * Returns these options, with the supplied layout replacing any existing one.
     */
    static CliOptions withLayout(CliOptions options, String layout)
    {
        Map<String, List<Object>> changed = new LinkedHashMap<>(options.values());
        changed.put(
                LAYOUT.name(),
                List.of(LAYOUT.parse(layout))
        );

        return new CliOptions(
                options.definitions(),
                changed,
                options.subjects()
        );
    }

    /**
     * Returns how the settings say a molecule should be drawn.
     */
    static DepictionOptions depictionOptions(CliOptions options)
    {
        Drawing drawing = options.get(STYLE);

        DepictionOptions base = drawing == Drawing.SKELETAL
                ? DepictionOptions.skeletal()
                : DepictionOptions.defaults();

        ChainGeometry geometry = options.has(GEOMETRY.name())
                ? options.get(GEOMETRY)
                : base.chainGeometry();

        HydrogenStyle hydrogens = options.has(HYDROGENS.name())
                ? options.get(HYDROGENS)
                : base.hydrogens();

        return new DepictionOptions(
                base.style(),
                geometry,
                hydrogens,
                options.get(PROJECT_CAGES),
                options.get(RING_REFINEMENT).atLeast(options.get(ALTERNATIVES) > 1 ? options.get(ALTERNATIVES) : 0),
                0
        );
    }

    /**
     * Returns what the settings say about how to draw a depiction.
     */
    static RenderStyle style(CliOptions options)
    {
        Drawing drawing = options.get(STYLE);

        RenderStyle.Builder style = RenderStyle.builder()
                .atomShape(drawing.shape)
                .isotopes(options.get(ISOTOPES))
                .charges(options.get(CHARGES))
                .bondGradient(options.get(BOND_GRADIENT))
                .metadata(options.get(METADATA))
                .debugLayout(options.get(DEBUG_LAYOUT));

        if (options.has(BOND_LENGTH.name()))
        {
            style.bondLength(options.get(BOND_LENGTH));
        }

        if (options.has(LAYOUT.name()))
        {
            style.fragmentArrangement(options.get(LAYOUT));
        }

        if (options.has(FRAGMENT_GAP.name()))
        {
            style.fragmentGap(options.get(FRAGMENT_GAP));
        }

        if (options.has(GRID_ALIGN.name()))
        {
            style.gridAlignment(options.get(GRID_ALIGN));
        }

        if (options.has(MARGIN.name()))
        {
            style.margin(options.get(MARGIN));
        }

        if (options.has(FONT_SIZE.name()))
        {
            style.fontSize(options.get(FONT_SIZE));
        }

        if (options.has(BACKGROUND.name()))
        {
            style.background(options.get(BACKGROUND));
        }

        if (options.has(BOND_COLOUR.name()))
        {
            style.bondColour(Colour.of(options.get(BOND_COLOUR)));
        }

        for (String chosen : options.getAll(COLOUR))
        {
            int split = chosen.indexOf(':');

            if (split < 1)
            {
                throw new IllegalArgumentException("Expected --colour=<element>:<colour>, but was given '" + chosen + "'");
            }

            style.colour(
                    chosen.substring(0, split),
                    Colour.of(chosen.substring(split + 1))
            );
        }

        return style.build();
    }
}
