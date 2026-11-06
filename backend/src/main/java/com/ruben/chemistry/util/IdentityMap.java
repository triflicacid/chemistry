package com.ruben.chemistry.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Factories for a {@link Map} that compares keys by reference identity.
 */
public final class IdentityMap
{
    private IdentityMap()
    {
    }

    /**
     * @param map the map to copy
     * @return an unmodifiable copy of {@code map}, keyed by reference identity
     */
    public static <K, V> Map<K, V> copyOf(Map<? extends K, ? extends V> map)
    {
        return Collections.unmodifiableMap(new IdentityHashMap<>(map));
    }
}
