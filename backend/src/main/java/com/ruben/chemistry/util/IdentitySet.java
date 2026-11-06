package com.ruben.chemistry.util;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Factories for a {@link Set} that compare elements by reference identity.
 */
public final class IdentitySet
{
    private IdentitySet()
    {
    }

    /**
     * @param <T> the element type
     * @return a new, empty, mutable identity set
     */
    public static <T> Set<T> of()
    {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * @param elements the elements to copy in
     * @return an unmodifiable identity set containing {@code elements}
     */
    public static <T> Set<T> copyOf(Collection<? extends T> elements)
    {
        Set<T> set = of();
        set.addAll(elements);
        return Collections.unmodifiableSet(set);
    }
}
