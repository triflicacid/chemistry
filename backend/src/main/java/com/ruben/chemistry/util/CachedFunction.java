package com.ruben.chemistry.util;

import java.util.HashMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Lazily computes and caches one value per argument, invoking the underlying function at most
 * once for each distinct argument.
 *
 * @param <K> the type of argument
 * @param <V> the type of value held
 */
public final class CachedFunction<K, V> implements Function<K, V>
{
    private final Function<K, V> producer;
    private final Map<K, V> values;

    private CachedFunction(Function<K, V> producer, Map<K, V> values)
    {
        this.producer = producer;
        this.values = values;
    }

    /**
     * Creates a cache that differentiates its arguments by equality.
     *
     * @param producer computes an argument's value
     * @return a new, empty cache
     */
    public static <K, V> CachedFunction<K, V> byValue(Function<K, V> producer)
    {
        return new CachedFunction<>(producer, new HashMap<>());
    }

    /**
     * Creates a cache that differentiates its arguments by reference identity.
     *
     * @param producer computes an argument's value
     * @return a new, empty cache
     */
    public static <K, V> CachedFunction<K, V> byIdentity(Function<K, V> producer)
    {
        return new CachedFunction<>(producer, new IdentityHashMap<>());
    }

    /**
     * @param key the argument to look up
     * @return the cached value for {@code key}. If none is cached, computes it with this cache's producer and stores it.
     * @throws NullPointerException if the producer returns {@code null}
     */
    @Override
    public V apply(K key)
    {
        V value = values.get(key);
        if (value != null)
        {
            return value;
        }

        // a producer that throws caches nothing
        value = producer.apply(key);
        if (value == null)
        {
            throw new NullPointerException("Producer returned no value for key: " + key);
        }
        values.put(key, value);
        return value;
    }
}
