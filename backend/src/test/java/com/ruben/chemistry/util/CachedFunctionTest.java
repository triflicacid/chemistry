package com.ruben.chemistry.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachedFunctionTest
{
    @Nested
    class ByIdentity
    {
        @Test
        void returns_the_producers_value()
        {
            CachedFunction<String, Integer> cached = CachedFunction.byIdentity(String::length);

            assertThat(cached.apply("four")).isEqualTo(4);
        }

        @Test
        void invokes_the_producer_at_most_once_per_argument()
        {
            AtomicInteger invocations = new AtomicInteger();
            CachedFunction<String, Integer> cached = CachedFunction.byIdentity(key -> invocations.incrementAndGet());

            String first = "first";
            String second = "second";
            cached.apply(first);
            cached.apply(first);
            cached.apply(second);
            cached.apply(first);

            assertThat(invocations.get()).isEqualTo(2);
        }

        @Test
        void tells_equal_but_distinct_arguments_apart()
        {
            List<String> one = List.of("same");
            List<String> other = List.of("same");
            assertThat(one).isEqualTo(other);

            AtomicInteger invocations = new AtomicInteger();
            CachedFunction<List<String>, Integer> cached = CachedFunction.byIdentity(key -> invocations.incrementAndGet());

            assertThat(cached.apply(one)).isEqualTo(1);
            assertThat(cached.apply(other)).isEqualTo(2);
            assertThat(cached.apply(one)).isEqualTo(1);
        }

        @Test
        void rejects_a_producer_that_returns_no_value()
        {
            CachedFunction<String, String> cached = CachedFunction.byIdentity(key -> null);

            assertThatThrownBy(() -> cached.apply("key"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("key");
        }

        @Test
        void retries_an_argument_whose_producer_threw()
        {
            AtomicInteger invocations = new AtomicInteger();
            CachedFunction<String, String> cached = CachedFunction.byIdentity(key ->
            {
                if (invocations.incrementAndGet() == 1)
                {
                    throw new IllegalStateException("first attempt fails");
                }
                return "value";
            });

            assertThatThrownBy(() -> cached.apply("key")).isInstanceOf(IllegalStateException.class);

            assertThat(cached.apply("key")).isEqualTo("value");
            assertThat(invocations.get()).isEqualTo(2);
        }
    }

    @Nested
    class ByValue
    {
        @Test
        void returns_the_producers_value()
        {
            CachedFunction<String, Integer> cached = CachedFunction.byValue(String::length);

            assertThat(cached.apply("four")).isEqualTo(4);
        }

        @Test
        void invokes_the_producer_at_most_once_per_argument()
        {
            AtomicInteger invocations = new AtomicInteger();
            CachedFunction<String, Integer> cached = CachedFunction.byValue(key -> invocations.incrementAndGet());

            cached.apply("first");
            cached.apply("first");
            cached.apply("second");
            cached.apply("first");

            assertThat(invocations.get()).isEqualTo(2);
        }

        @Test
        void shares_one_value_between_equal_but_distinct_arguments()
        {
            List<String> one = List.of("same");
            List<String> other = List.of("same");
            assertThat(one).isEqualTo(other).isNotSameAs(other);

            AtomicInteger invocations = new AtomicInteger();
            CachedFunction<List<String>, Integer> cached = CachedFunction.byValue(key -> invocations.incrementAndGet());

            assertThat(cached.apply(one)).isEqualTo(1);
            assertThat(cached.apply(other)).isEqualTo(1);
            assertThat(invocations.get()).isEqualTo(1);
        }

        @Test
        void keeps_unequal_arguments_apart()
        {
            AtomicInteger invocations = new AtomicInteger();
            CachedFunction<List<String>, Integer> cached = CachedFunction.byValue(key -> invocations.incrementAndGet());

            assertThat(cached.apply(List.of("one"))).isEqualTo(1);
            assertThat(cached.apply(List.of("two"))).isEqualTo(2);
            assertThat(cached.apply(List.of("one"))).isEqualTo(1);
            assertThat(invocations.get()).isEqualTo(2);
        }

        @Test
        void rejects_a_producer_that_returns_no_value()
        {
            CachedFunction<String, String> cached = CachedFunction.byValue(key -> null);

            assertThatThrownBy(() -> cached.apply("key"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("key");
        }

        @Test
        void retries_an_argument_whose_producer_threw()
        {
            AtomicInteger invocations = new AtomicInteger();
            CachedFunction<String, String> cached = CachedFunction.byValue(key ->
            {
                if (invocations.incrementAndGet() == 1)
                {
                    throw new IllegalStateException("first attempt fails");
                }
                return "value";
            });

            assertThatThrownBy(() -> cached.apply("key")).isInstanceOf(IllegalStateException.class);

            assertThat(cached.apply("key")).isEqualTo("value");
            assertThat(invocations.get()).isEqualTo(2);
        }
    }
}
