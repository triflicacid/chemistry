package com.ruben.chemistry.forcefield;

/**
 * Checks shared by the force field's value types.
 */
final class Validation
{
    private Validation()
    {
    }

    /**
     * @param value a number
     * @param name what the number is, for the error message
     * @throws IllegalArgumentException if {@code value} is not finite
     */
    static void finite(double value, String name)
    {
        if (!Double.isFinite(value))
        {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
