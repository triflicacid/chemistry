package com.ruben.chemistry.server.options;

import com.ruben.chemistry.server.molecule.MoleculeException;

import java.util.List;

/**
 * Checks the values a client gave for options.
 */
public final class OptionValues
{
    private OptionValues()
    {
    }

    /**
     * @param name the option's name
     * @param value what the client asked for, or null for the default
     * @param choices what is allowed
     * @return {@code value}
     * @throws MoleculeException {@code INVALID_OPTIONS} if {@code value} is not allowed
     */
    public static String choice(String name, String value, List<OptionSchema.Choice> choices)
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

    /**
     * @param name the option's name
     * @param value what the client asked for
     * @param min the least allowed
     * @param max the most allowed
     * @return {@code value}
     * @throws MoleculeException {@code INVALID_OPTIONS} if {@code value} is out of range
     */
    public static double number(String name, double value, double min, double max)
    {
        if (value < min || value > max)
        {
            throw invalid(name, "must be between " + min + " and " + max);
        }
        return value;
    }

    /**
     * @param name the option's name
     * @param problem what is wrong with it
     * @return the exception to throw
     */
    public static MoleculeException invalid(String name, String problem)
    {
        return new MoleculeException("INVALID_OPTIONS", "options." + name + ": " + problem);
    }
}
