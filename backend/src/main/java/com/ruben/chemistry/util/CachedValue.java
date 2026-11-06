package com.ruben.chemistry.util;

import java.util.function.Supplier;

/**
 * Lazily computes and caches a value from a {@link Supplier}, invoking the supplier at most
 * once, the first time {@link #get()} is called.
 *
 * @param <T> the type of value held
 */
public final class CachedValue<T> implements Supplier<T>
{
    private final Supplier<T> producer;
    private T value;
    private boolean computed;

    public CachedValue(Supplier<T> producer)
    {
        this.producer = producer;
    }

    /**
     * @return the cached value. If no value is cached, gets it from the supplier.
     */
    @Override
    public T get()
    {
        if (!computed)
        {
            value = producer.get();
            computed = true;
        }
        return value;
    }
}
