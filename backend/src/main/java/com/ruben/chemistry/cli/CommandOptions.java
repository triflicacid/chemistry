package com.ruben.chemistry.cli;

import com.ruben.chemistry.render.AtomShape;
import com.ruben.chemistry.render.ChainGeometry;
import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.DepictionOptions;
import com.ruben.chemistry.render.HydrogenStyle;
import com.ruben.chemistry.render.RenderStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The arguments a drawing command was given: whatever it is being asked to draw, and the settings
 * to draw it by.
 * <p>
 * An argument beginning {@code --} is a setting, written {@code --name=value}; anything else is
 * something to draw. A setting given more than once keeps every value, behind commas, so that
 * {@code --colour} can be given once per element.
 */
final class CommandOptions
{
    /** How the settings are written, shown when a command's arguments make no sense. */
    static final String USAGE = """
          --style=text|circles|skeletal
                                       whether an atom is written as its symbol, drawn as a ball,
                                       or, for carbon, left as a bare vertex of a line-angle
                                       drawing (default text)
          --geometry=straight|zigzag   how a chain of single bonds runs (default straight, zigzag
                                       under --style=skeletal)
          --hydrogens=separate|condensed|none
                                       whether hydrogens are drawn as atoms of their own, gathered
                                       into the symbol they hang off, or left out (default separate)
          --isotopes=true|false        whether a mass number is written beside a symbol (default true)
          --charges=true|false         whether a charge is written beside a symbol (default true)
          --bond-length=<px>           how many pixels long one bond is (default 50)
          --margin=<px>                how many pixels of empty space surround the drawing (default 12)
          --font-size=<px>             how many pixels tall a symbol is written (default 18)
          --background=<colour>        the colour behind the drawing, or none (default #ffffff)
          --bond-colour=<colour>       the colour a bond is drawn in
          --bond-gradient=true|false   whether a bond runs between the colours of the atoms at its
                                       ends instead (default false)
          --colour=<element>:<colour>  the colour one element is drawn in, repeatable
        """;

    /** The settings this class itself understands; a command adds whatever else it takes. */
    private static final Set<String> KNOWN = Set.of(
        "style", "geometry", "hydrogens", "isotopes", "charges", "bond-length", "margin",
        "font-size", "background", "bond-colour", "bond-gradient", "colour");

    /** The arguments that name something to draw. */
    private final List<String> subjects;

    /** Each setting against its value. */
    private final Map<String, String> values;

    /**
     * @param subjects the arguments that name something to draw
     * @param values each setting against its value
     */
    private CommandOptions(List<String> subjects, Map<String, String> values)
    {
        this.subjects = List.copyOf(subjects);
        this.values = Map.copyOf(values);
    }

    /**
     * @param arguments the arguments given
     * @return what they ask for
     * @throws IllegalArgumentException if a setting has no value
     */
    static CommandOptions of(List<String> arguments)
    {
        List<String> subjects = new ArrayList<>();
        Map<String, String> values = new LinkedHashMap<>();

        for (String argument : arguments)
        {
            if (!argument.startsWith("--"))
            {
                subjects.add(argument);
                continue;
            }

            String body = argument.substring(2);
            int split = body.indexOf('=');
            if (split < 1)
            {
                throw new IllegalArgumentException("Option '" + argument + "' needs a value, as in --name=value");
            }

            // Every element keeps its own colour, so a repeated setting piles up rather than replacing.
            values.merge(body.substring(0, split), body.substring(split + 1), (held, added) -> held + "," + added);
        }
        return new CommandOptions(subjects, values);
    }

    /**
     * @return the arguments that name something to draw
     */
    List<String> subjects()
    {
        return subjects;
    }

    /**
     * Checks that every setting given is one something understands, so that a misspelt one is
     * complained about rather than quietly ignored.
     *
     * @param alsoAllowed the settings the command itself takes, besides the drawing ones
     * @throws IllegalArgumentException if a setting is not understood
     */
    void validate(String... alsoAllowed)
    {
        for (String name : values.keySet())
        {
            if (!KNOWN.contains(name) && !List.of(alsoAllowed).contains(name))
            {
                throw new IllegalArgumentException("There is no --" + name + " setting");
            }
        }
    }

    /**
     * @param expected what one subject means, for the error message
     * @return the only subject given
     * @throws IllegalArgumentException if there is not exactly one
     */
    String onlySubject(String expected)
    {
        if (subjects.size() != 1)
        {
            throw new IllegalArgumentException("Expected exactly one " + expected + ", but was given " + subjects.size());
        }
        return subjects.getFirst();
    }

    /**
     * @param name the setting to read
     * @param fallback what to use where it was not given
     * @return its value
     */
    String get(String name, String fallback)
    {
        return values.getOrDefault(name, fallback);
    }

    /**
     * @return what the settings say about how to lay a molecule out
     */
    DepictionOptions depictionOptions()
    {
        DepictionOptions base = drawing() == Drawing.SKELETAL
            ? DepictionOptions.skeletal()
            : DepictionOptions.defaults();

        return new DepictionOptions(
            base.style(),
            choice("geometry", ChainGeometry.class, base.chainGeometry()),
            values.containsKey("hydrogens") ? hydrogenStyle() : base.hydrogens());
    }

    /**
     * Reads the one setting that says how a molecule is drawn, allowing {@code circle} as another
     * way of saying {@code circles}.
     *
     * @return the choice made
     * @throws IllegalArgumentException if the value names no choice
     */
    private Drawing drawing()
    {
        String value = values.get("style");
        if (value == null)
        {
            return Drawing.TEXT;
        }
        if (value.equalsIgnoreCase("circle"))
        {
            return Drawing.CIRCLES;
        }
        return choice("style", Drawing.class, Drawing.TEXT);
    }

    /**
     * How a molecule is drawn, which decides both how much of it is laid out and how an atom that
     * was laid out is marked on the page.
     */
    private enum Drawing
    {
        /** Every atom written out as its own symbol. */
        TEXT(AtomShape.TEXT),

        /** Every atom drawn as a ball, as a molecular model has them. */
        CIRCLES(AtomShape.CIRCLE),

        /** Carbon left as a bare vertex, everything else written out. */
        SKELETAL(AtomShape.TEXT);

        /** How an atom that is drawn at all is marked. */
        private final AtomShape shape;

        /**
         * @param shape how an atom that is drawn at all is marked
         */
        Drawing(AtomShape shape)
        {
            this.shape = shape;
        }
    }

    /**
     * Reads how hydrogens should be drawn, allowing the {@code true} and {@code false} this setting
     * once took as other ways of saying separate and none.
     *
     * @return the choice made
     * @throws IllegalArgumentException if the value names no choice
     */
    private HydrogenStyle hydrogenStyle()
    {
        String value = values.get("hydrogens");
        if (value == null)
        {
            return HydrogenStyle.SEPARATE;
        }
        if (value.equalsIgnoreCase("true"))
        {
            return HydrogenStyle.SEPARATE;
        }
        if (value.equalsIgnoreCase("false"))
        {
            return HydrogenStyle.NONE;
        }
        return choice("hydrogens", HydrogenStyle.class, HydrogenStyle.SEPARATE);
    }

    /**
     * @return what the settings say about how to draw a depiction
     */
    RenderStyle style()
    {
        RenderStyle.Builder style = RenderStyle.builder()
            .atomShape(drawing().shape)
            .isotopes(flag("isotopes", true))
            .charges(flag("charges", true))
            .bondGradient(flag("bond-gradient", false));

        if (values.containsKey("bond-length"))
        {
            style.bondLength(number("bond-length"));
        }
        if (values.containsKey("margin"))
        {
            style.margin(number("margin"));
        }
        if (values.containsKey("font-size"))
        {
            style.fontSize(number("font-size"));
        }
        if (values.containsKey("background"))
        {
            String background = values.get("background");
            style.background(background.equalsIgnoreCase("none") ? null : Colour.of(background));
        }
        if (values.containsKey("bond-colour"))
        {
            style.bondColour(Colour.of(values.get("bond-colour")));
        }
        if (values.containsKey("colour"))
        {
            for (String chosen : values.get("colour").split(","))
            {
                int split = chosen.indexOf(':');
                if (split < 1)
                {
                    throw new IllegalArgumentException("Expected --colour=<element>:<colour>, but was given '" + chosen + "'");
                }
                style.colour(chosen.substring(0, split), Colour.of(chosen.substring(split + 1)));
            }
        }
        return style.build();
    }

    /**
     * @param name the setting to read
     * @param type the choices it allows
     * @param fallback what to use where it was not given
     * @param <T> the choices it allows
     * @return the choice made
     * @throws IllegalArgumentException if the value names no choice
     */
    private <T extends Enum<T>> T choice(String name, Class<T> type, T fallback)
    {
        String value = values.get(name);
        if (value == null)
        {
            return fallback;
        }

        try
        {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException unknown)
        {
            throw new IllegalArgumentException("'" + value + "' is not one of the choices --" + name + " allows");
        }
    }

    /**
     * @param name the setting to read
     * @param fallback what to use where it was not given
     * @return whether it is on
     * @throws IllegalArgumentException if the value is neither true nor false
     */
    private boolean flag(String name, boolean fallback)
    {
        String value = values.get(name);
        if (value == null)
        {
            return fallback;
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))
        {
            return Boolean.parseBoolean(value);
        }
        throw new IllegalArgumentException("--" + name + " must be true or false, but was '" + value + "'");
    }

    /**
     * @param name the setting to read
     * @return the measurement it gives
     * @throws IllegalArgumentException if the value is not a number
     */
    private double number(String name)
    {
        String value = values.get(name);
        try
        {
            return Double.parseDouble(value);
        }
        catch (NumberFormatException notANumber)
        {
            throw new IllegalArgumentException("--" + name + " must be a number, but was '" + value + "'");
        }
    }
}
