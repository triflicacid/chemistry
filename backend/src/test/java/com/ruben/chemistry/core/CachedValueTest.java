package com.ruben.chemistry.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachedValueTest
{
    @Test
    void returns_the_producers_value()
    {
        CachedValue<String> cached = new CachedValue<>(() -> "value");

        assertThat(cached.get()).isEqualTo("value");
    }

    @Test
    void invokes_the_producer_at_most_once()
    {
        AtomicInteger invocations = new AtomicInteger();
        CachedValue<Integer> cached = new CachedValue<>(invocations::incrementAndGet);

        cached.get();
        cached.get();
        cached.get();

        assertThat(invocations.get()).isEqualTo(1);
    }
}
