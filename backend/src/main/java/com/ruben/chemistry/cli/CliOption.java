
package com.ruben.chemistry.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Describes how a command-line option accepts and interprets its value.
 *
 * @param <T> the type produced by this option
 */
public sealed interface CliOption<T>
{
    String name();

    String metavar();

    String help();

    T defaultValue();

    boolean flag();

    boolean repeatable();

    T parse(String input);

    /**
     * @return how the default is written in usage text, or {@code null} if there is no default to show
     */
    String defaultText();

    /**
     * @return what must hold of all the arguments for this option to be given; checked only if it was given
     */
    List<Requirement> requirements();

    /**
     * @param text how to write the default in usage text, in place of what is worked out from it
     * @return this option, showing that default
     */
    CliOption<T> withDefaultText(String text);

    /**
     * @param holds whether this option may be given alongside the other arguments, which are the arguments as parsed;
     *     options that were not given answer with their defaults
     * @param message what to say when it does not hold
     * @return this option, also requiring that
     */
    CliOption<T> withRequirement(Predicate<CliOptions> holds, String message);

    /**
     * A condition on the whole command line that must hold if an option is given.
     *
     * @param holds whether it holds for the arguments
     * @param message what to say when it does not
     */
    record Requirement(Predicate<CliOptions> holds, String message)
    {
        public Requirement
        {
            Objects.requireNonNull(holds);
            Objects.requireNonNull(message);
        }
    }

    /**
     * An option whose accepted values are explicitly enumerated.
     */
    record Choice<T>(
            String name,
            String help,
            Map<String, T> values,
            Supplier<T> defaultValueSupplier,
            String shownDefault,
            List<Requirement> requirements
    ) implements CliOption<T>
    {
        public Choice
        {
            Objects.requireNonNull(name);
            Objects.requireNonNull(help);
            Objects.requireNonNull(values);

            Map<String, T> normalised = new LinkedHashMap<>();
            values.forEach((key, value) -> normalised.put(key.toLowerCase(Locale.ROOT), value));

            values = Map.copyOf(normalised);
            requirements = List.copyOf(requirements);
        }

        @Override
        public String defaultText()
        {
            if (shownDefault != null)
            {
                return shownDefault;
            }
            T value = defaultValue();
            return values.entrySet().stream()
                    .filter(entry -> Objects.equals(entry.getValue(), value))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public CliOption<T> withDefaultText(String text)
        {
            return new Choice<>(name, help, values, defaultValueSupplier, text, requirements);
        }

        @Override
        public CliOption<T> withRequirement(Predicate<CliOptions> holds, String message)
        {
            List<Requirement> more = new java.util.ArrayList<>(requirements);
            more.add(new Requirement(holds, message));
            return new Choice<>(name, help, values, defaultValueSupplier, shownDefault, more);
        }

        @Override
        public String metavar()
        {
            return String.join("|", values.keySet());
        }

        @Override
        public boolean flag()
        {
            return false;
        }

        @Override
        public boolean repeatable()
        {
            return false;
        }

        @Override
        public T defaultValue()
        {
            return defaultValueSupplier == null ? null : defaultValueSupplier.get();
        }

        @Override
        public T parse(String input)
        {
            T value = values.get(input.toLowerCase(Locale.ROOT));

            if (value == null)
            {
                throw new IllegalArgumentException("'" + input + "' is not one of the choices --" + name + " allows: " + metavar());
            }

            return value;
        }
    }

    /**
     * An option whose value is interpreted by a supplied parser.
     */
    record Parsed<T>(
            String name,
            String metavar,
            String help,
            Function<String, T> parser,
            Supplier<T> defaultValueSupplier,
            boolean flag,
            boolean repeatable,
            String shownDefault,
            List<Requirement> requirements
    ) implements CliOption<T>
    {
        public Parsed
        {
            Objects.requireNonNull(name);
            Objects.requireNonNull(metavar);
            Objects.requireNonNull(help);
            Objects.requireNonNull(parser);
            requirements = List.copyOf(requirements);
        }

        @Override
        public T defaultValue()
        {
            return defaultValueSupplier == null ? null : defaultValueSupplier.get();
        }

        @Override
        public String defaultText()
        {
            if (shownDefault != null)
            {
                return shownDefault;
            }
            return Objects.toString(defaultValue(), null);
        }

        @Override
        public CliOption<T> withDefaultText(String text)
        {
            return new Parsed<>(name, metavar, help, parser, defaultValueSupplier, flag, repeatable, text, requirements);
        }

        @Override
        public CliOption<T> withRequirement(Predicate<CliOptions> holds, String message)
        {
            List<Requirement> more = new java.util.ArrayList<>(requirements);
            more.add(new Requirement(holds, message));
            return new Parsed<>(name, metavar, help, parser, defaultValueSupplier, flag, repeatable, shownDefault, more);
        }

        @Override
        public T parse(String input)
        {
            return parser.apply(input);
        }
    }

    /**
     * Creates an option with a fixed set of accepted values.
     */
    static <T> CliOption<T> choice(
            String name,
            String help,
            Map<String, T> values,
            Supplier<T> defaultValueSupplier)
    {
        return new Choice<>(name, help, values, defaultValueSupplier, null, List.of());
    }

    /**
     * Creates an option accepting an arbitrary string.
     */
    static CliOption<String> string(
            String name,
            String metavar,
            String help,
            String defaultValue)
    {
        return parsed(name, metavar, help, Function.identity(), () -> defaultValue, false, false);
    }

    /**
     * Creates a numeric option.
     */
    static CliOption<Double> number(
            String name,
            String metavar,
            String help,
            double defaultValue)
    {
        return parsed(
                name,
                metavar,
                help,
                input -> {
                    try
                    {
                        return Double.parseDouble(input);
                    }
                    catch (NumberFormatException invalid)
                    {
                        throw new IllegalArgumentException("--" + name + " must be a number, but was '" + input + "'");
                    }
                },
                () -> defaultValue)
                .withDefaultText(defaultValue == Math.rint(defaultValue) ? Long.toString((long) defaultValue) : Double.toString(defaultValue));
    }

    /**
     * Creates an option interpreted by a custom parser.
     */
    static <T> CliOption<T> parsed(
            String name,
            String metavar,
            String help,
            Function<String, T> parser,
            Supplier<T> defaultValueSupplier)
    {
        return parsed(name, metavar, help, parser, defaultValueSupplier, false, false);
    }

    /**
     * Creates a Boolean flag. Giving the option without a value means true.
     */
    static CliOption<Boolean> flag(String name, String help, boolean defaultValue)
    {
        return parsed(
                name,
                "true|false",
                help,
                input -> {
                    if (input.equalsIgnoreCase("true")) return true;
                    if (input.equalsIgnoreCase("false")) return false;

                    throw new IllegalArgumentException("--" + name + " must be true or false, but was '" + input + "'");
                },
                () -> defaultValue,
                true,
                false);
    }

    /**
     * Creates a repeatable string option.
     */
    static CliOption<String> repeatedString(String name, String metavar, String help)
    {
        return parsed(name, metavar, help, Function.identity(), null, false, true);
    }

    /**
     * Creates a parsed option with explicit flag and repeatability behaviour.
     */
    static <T> CliOption<T> parsed(
            String name,
            String metavar,
            String help,
            Function<String, T> parser,
            Supplier<T> defaultValueSupplier,
            boolean flag,
            boolean repeatable)
    {
        return new Parsed<>(name, metavar, help, parser, defaultValueSupplier, flag, repeatable, null, List.of());
    }
}
