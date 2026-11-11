package com.ruben.chemistry.render.annotate;

import java.util.OptionalDouble;

/**
 * Helpers shared by {@link AtomLook} and {@link BondLook}.
 */
final class Looks
{
    private Looks()
    {
    }

    /**
     * @param value an optional fraction
     * @param name what the fraction is (for the error message)
     * @throws IllegalArgumentException if {@code value} is present and falls outside 0 to 1
     */
    static void requireFraction(OptionalDouble value, String name)
    {
        if (value.isPresent() && !(value.getAsDouble() >= 0 && value.getAsDouble() <= 1))
        {
            throw new IllegalArgumentException("A look's " + name + " must be between 0 and 1, but was " + value.getAsDouble());
        }
    }

    /**
     * @param preferred the value to use if present
     * @param fallback the value to use otherwise
     * @return {@code preferred} if present, else {@code fallback}
     */
    static OptionalDouble firstPresent(OptionalDouble preferred, OptionalDouble fallback)
    {
        return preferred.isPresent() ? preferred : fallback;
    }
}
