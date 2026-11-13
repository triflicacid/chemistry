package com.ruben.chemistry.render.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A named property of an {@link Element}.
 *
 * @param name the attribute's name, which follows SVG's
 * @param value a {@link String}, a {@link Double} or {@code List<Double>}
 */
public record Attribute(String name, Object value)
{
    public Attribute
    {
        if (value instanceof List<?> list)
        {
            List<Double> numbers = new ArrayList<>(list.size());
            for (Object item : list)
            {
                if (!(item instanceof Double number))
                {
                    throw new IllegalArgumentException("Attribute " + name + " holds a list with " + item);
                }
                numbers.add(number);
            }
            value = List.copyOf(numbers);
        }
        else if (!(value instanceof String) && !(value instanceof Double))
        {
            throw new IllegalArgumentException("Attribute " + name + " can not hold " + value);
        }
    }

    /**
     * @param name the attribute's name
     * @param value its value
     * @return the attribute
     */
    public static Attribute of(String name, String value)
    {
        return new Attribute(name, value);
    }

    /**
     * @param name the attribute's name
     * @param value its value
     * @return the attribute
     */
    public static Attribute of(String name, double value)
    {
        return new Attribute(name, value);
    }

    /**
     * @param name the attribute's name
     * @param values its values, in order
     * @return the attribute
     */
    public static Attribute of(String name, double... values)
    {
        List<Double> numbers = new ArrayList<>(values.length);
        for (double value : values)
        {
            numbers.add(value);
        }
        return new Attribute(name, numbers);
    }
}
