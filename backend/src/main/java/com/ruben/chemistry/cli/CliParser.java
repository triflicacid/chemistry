
package com.ruben.chemistry.cli;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CliParser
{
    private CliParser()
    {
    }

    /**
     * Parse command-line arguments using the supplied option definitions.
     * <p>
     * Options use the form:
     *   --name value
     * Or
     *   --name=value
     * <p>
     * Boolean flags may be supplied without a value:
     *   --debug
     * <p>
     * Repeatable options may be supplied multiple times:
     *   --colour red --colour blue
     */

    public static CliOptions parse(List<String> arguments, Collection<? extends CliOption<?>> definitions)
    {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(definitions, "definitions");

        Map<String, CliOption<?>> options = indexOptions(definitions);
        Map<String, List<Object>> values = new LinkedHashMap<>();
        List<String> subjects = new ArrayList<>();

        for (int i = 0; i < arguments.size(); i++)
        {
            String argument = arguments.get(i);
            if (!argument.startsWith("--"))
            {
                subjects.add(argument);
                continue;
            }

            String body = argument.substring(2);
            int equals = body.indexOf('=');

            String name = equals < 0 ? body : body.substring(0, equals);
            if (name.isBlank())
            {
                throw new IllegalArgumentException("Invalid empty option name");
            }

            CliOption<?> option = options.get(name);

            if (option == null)
            {
                throw new IllegalArgumentException("Unknown option: --" + name);
            }

            boolean hasInlineValue = equals >= 0;
            String rawValue;

            if (hasInlineValue)
            {
                rawValue = body.substring(equals + 1);
            }
            else if (option.flag())
            {
                // A bare flag means true, but explicit true/false is also accepted.
                if (i + 1 < arguments.size() && isBoolean(arguments.get(i + 1)))
                {
                    rawValue = arguments.get(++i);
                }
                else
                {
                    rawValue = "true";
                }
            }
            else
            {
                if (i + 1 >= arguments.size()
                        || arguments.get(i + 1).startsWith("--"))
                {
                    throw new IllegalArgumentException(
                            "Missing value for option: --" + name);
                }

                rawValue = arguments.get(++i);
            }

            Object parsed;
            try
            {
                parsed = option.parse(rawValue);
            }
            catch (RuntimeException exception)
            {
                throw new IllegalArgumentException("Invalid value for --" + name + ": " + rawValue + " (" + exception.getMessage() + ")", exception);
            }

            List<Object> existing = values.computeIfAbsent(name, ignored -> new ArrayList<>());
            if (!option.repeatable() && !existing.isEmpty())
            {
                throw new IllegalArgumentException("Option may only be specified once: --" + name);
            }

            existing.add(parsed);
        }

        return new CliOptions(options, values, subjects);
    }

    private static Map<String, CliOption<?>> indexOptions(Collection<? extends CliOption<?>> definitions)
    {
        Map<String, CliOption<?>> options = new LinkedHashMap<>();

        for (CliOption<?> option : definitions)
        {
            String name = option.name();
            if (name == null || name.isBlank())
            {
                throw new IllegalArgumentException("Option name cannot be blank");
            }

            if (options.putIfAbsent(name, option) != null)
            {
                throw new IllegalArgumentException("Duplicate option definition: --" + name);
            }
        }

        return options;
    }

    private static boolean isBoolean(String value)
    {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    /**
     * Generate help text for a collection of options.
     */
    public static String usage(String synopsis, Collection<? extends CliOption<?>> options)
    {
        StringBuilder result = new StringBuilder(synopsis);

        if (!options.isEmpty())
        {
            result.append("\n\nOptions:\n");

            int width = options.stream()
                    .mapToInt(option -> option.name().length() + option.metavar().length() + 5)
                    .max()
                    .orElse(0);

            for (CliOption<?> option : options)
            {
                String label = "  --" + option.name() + (option.flag() ? "" : " " + option.metavar());

                result.append(String.format(
                        "  %-" + width + "s  %s%n",
                        label,
                        option.help()));
            }
        }

        return result.toString();
    }
}
