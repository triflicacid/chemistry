package com.ruben.chemistry.server.json;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Helpers for reading values out of what {@link JsonParser} produces.
 */
public final class Json
{
    private Json()
    {
    }

    /**
     * Drills down into a parsed JSON value. Each step of the path is a key into a
     * {@code Map} (any string) or, when the value there is a {@code List}, a whole-number
     * index written as a string, e.g. {@code extractProperty(body, "colours", "0", "element")}.
     *
     * @param object the value to start from, usually the parsed document
     * @param path the keys and indexes to follow; empty returns {@code object} itself
     * @param <T> the type the caller expects the value to have
     * @return the value found, or {@code null} if the last key is not there (or is JSON null)
     * @throws NoSuchElementException if the path does not exist
     */
    @SuppressWarnings("unchecked")
    public static <T> T extractProperty(Object object, String... path)
    {
        Object current = object;

        for (int i = 0; i < path.length; i++)
        {
            String step = path[i];
            boolean last = i == path.length - 1;

            if (current instanceof Map<?, ?> map)
            {
                if (!last && !map.containsKey(step))
                {
                    throw new NoSuchElementException("No key '" + step + "' at " + describe(path, i));
                }
                current = map.get(step);
            }
            else if (current instanceof List<?> list)
            {
                int index = indexOf(step, path, i);

                if (index >= list.size())
                {
                    if (last)
                    {
                        return null;
                    }
                    throw new NoSuchElementException("Index " + index + " is out of range (size " + list.size() + ") at " + describe(path, i));
                }
                current = list.get(index);
            }
            else
            {
                throw new NoSuchElementException(
                        "Cannot take step '" + step + "' into "
                                + (current == null ? "null" : current.getClass().getSimpleName())
                                + " at " + describe(path, i));
            }
        }

        return (T) current;
    }

    private static int indexOf(String step, String[] path, int at)
    {
        try
        {
            int index = Integer.parseInt(step);
            if (index >= 0)
            {
                return index;
            }
        }
        catch (NumberFormatException notANumber)
        {
            // reported below
        }

        throw new NoSuchElementException("'" + step + "' is not a valid list index at " + describe(path, at));
    }

    private static String describe(String[] path, int upTo)
    {
        return upTo == 0 ? "the root" : String.join(".", Arrays.copyOf(path, upTo));
    }
}
