package com.ruben.chemistry.util;

import java.util.Comparator;
import java.util.List;

/**
 * Factories for {@link Comparator}s.
 */
public final class Comparators
{
    private Comparators()
    {
    }

    /**
     * @param order a total order over the list's elements
     * @param <T> the element type
     * @return a total order over lists of {@code T}, lexicographic under {@code order},
     *     with a shorter list that is otherwise equal to a longer one sorting first
     */
    public static <T> Comparator<List<T>> lexicographically(Comparator<T> order)
    {
        return (a, b) ->
        {
            int limit = Math.min(a.size(), b.size());
            for (int i = 0; i < limit; i++)
            {
                int comparison = order.compare(a.get(i), b.get(i));
                if (comparison != 0)
                {
                    return comparison;
                }
            }
            return Integer.compare(a.size(), b.size());
        };
    }
}
