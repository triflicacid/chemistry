package com.ruben.chemistry.server.util;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs a task once the thing it is for has been left alone, however many times it was asked for in the meantime.
 *
 * @param <K> what a task is for
 */
public final class Debouncer<K> implements AutoCloseable
{
    private final Duration delay;
    private final ScheduledExecutorService executor;
    private final Map<K, ScheduledFuture<?>> waiting = new HashMap<>();

    /**
     * @param delay how long a key must be left alone before its task runs
     * @throws IllegalArgumentException if {@code delay} is negative
     */
    public Debouncer(Duration delay)
    {
        this(delay, true);
    }

    private Debouncer(Duration delay, boolean enabled)
    {
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative())
        {
            throw new IllegalArgumentException("delay cannot be negative, but was " + delay);
        }

        this.delay = delay;
        this.executor = enabled
                ? Executors.newSingleThreadScheduledExecutor(task ->
                {
                    Thread thread = new Thread(task, "debouncer");
                    thread.setDaemon(true);
                    return thread;
                })
                : null;
    }

    /**
     * @param <K> what a task is for
     * @return a debouncer that never runs anything, for when what it would do must not happen
     */
    public static <K> Debouncer<K> disabled()
    {
        return new Debouncer<>(Duration.ZERO, false);
    }

    /**
     * Asks for a task to run once its key has been left alone for the delay, replacing any task still waiting under it.
     * An exception the task throws is not reported anywhere, so a task that can fail must deal with that itself.
     *
     * @param key what the task is for
     * @param task what to run
     */
    public void submit(K key, Runnable task)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(task, "task");
        if (executor == null)
        {
            return;
        }

        synchronized (waiting)
        {
            AtomicReference<ScheduledFuture<?>> own = new AtomicReference<>();
            ScheduledFuture<?> next = executor.schedule(() ->
            {
                synchronized (waiting)
                {
                    // a task that has begun is no longer waiting, unless a newer one has taken its place
                    if (waiting.get(key) == own.get())
                    {
                        waiting.remove(key);
                    }
                }
                task.run();
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
            own.set(next);

            ScheduledFuture<?> replaced = waiting.put(key, next);
            if (replaced != null)
            {
                replaced.cancel(false);
            }
        }
    }

    /**
     * @return how many keys have a task waiting
     */
    public int waiting()
    {
        synchronized (waiting)
        {
            return waiting.size();
        }
    }

    /** Stops running anything, and abandons what is waiting. */
    @Override
    public void close()
    {
        if (executor != null)
        {
            executor.shutdownNow();
        }
        synchronized (waiting)
        {
            waiting.clear();
        }
    }
}
