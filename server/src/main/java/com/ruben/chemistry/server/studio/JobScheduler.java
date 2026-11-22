package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.server.util.Debouncer;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Runs a slow calculation on a session when asked, without doing so for every request of a run of them.
 *
 * @param <R> what the calculation produces
 */
public final class JobScheduler<R> implements AutoCloseable
{
    @FunctionalInterface
    public interface Work<R>
    {
        /**
         * @param described the geometry to calculate from, at one version
         * @param run lets the work see how long it has been going and whether it has been abandoned
         * @return the result
         * @throws RuntimeException if the calculation cannot be made
         */
        R run(StudioSession.Described described, Run run);
    }

    @FunctionalInterface
    public interface Failure<R>
    {
        /**
         * @param described the geometry that was being calculated from
         * @param elapsedMs how long it was tried for, in milliseconds
         * @param message why it failed
         * @return the result to deliver in place of one
         */
        R failed(StudioSession.Described described, long elapsedMs, String message);
    }

    /**
     * One calculation under way.
     */
    public static final class Run
    {
        private final Runnable check;
        private final long started = System.nanoTime();

        private Run(Runnable check)
        {
            this.check = check;
        }

        /**
         * Stops the calculation if it is no longer wanted.
         *
         * @throws CancellationException if the session has moved on from the geometry or a newer request has been made
         */
        public void check()
        {
            check.run();
        }

        /**
         * @return how long the calculation has been running, in milliseconds
         */
        public long elapsedMs()
        {
            return (System.nanoTime() - started) / 1_000_000;
        }
    }

    private final Debouncer<String> debouncer;
    private final Map<String, AtomicLong> latest = new ConcurrentHashMap<>();

    /**
     * @param debounce how long a session must be left alone before its calculation is started
     */
    public JobScheduler(Duration debounce)
    {
        this(new Debouncer<>(debounce));
    }

    private JobScheduler(Debouncer<String> debouncer)
    {
        this.debouncer = debouncer;
    }

    /**
     * @param <R> what the calculations produce
     * @return a scheduler that never calculates anything, for tests that must not be sent results they did not ask for
     */
    public static <R> JobScheduler<R> disabled()
    {
        return new JobScheduler<>(Debouncer.disabled());
    }

    /**
     * Asks for a calculation once the session has been left alone, replacing any request for it that is waiting and
     * abandoning any calculation of it that is under way.
     *
     * @param session the session to calculate for
     * @param work the calculation
     * @param failure makes what is delivered if the calculation throws
     * @param deliver told the result, from another thread, only if it is not out of date
     */
    public void schedule(StudioSession session, Work<R> work, Failure<R> failure, Consumer<R> deliver)
    {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(deliver, "deliver");
        latest.computeIfAbsent(session.sessionId(), id -> new AtomicLong()).incrementAndGet();
        debouncer.submit(session.sessionId(), () -> run(session, work, failure, deliver));
    }

    /** Stops calculating anything, and abandons what is waiting. */
    @Override
    public void close()
    {
        debouncer.close();
    }

    private void run(StudioSession session, Work<R> work, Failure<R> failure, Consumer<R> deliver)
    {
        AtomicLong generation = latest.computeIfAbsent(session.sessionId(), id -> new AtomicLong());
        long mine = generation.get();
        StudioSession.Described described = session.describe();
        Run run = new Run(() ->
        {
            if (generation.get() != mine || session.version() != described.version())
            {
                throw new CancellationException("The result is out of date");
            }
        });

        R result;
        try
        {
            run.check();
            result = work.run(described, run);
        }
        catch (CancellationException outOfDate)
        {
            return;
        }
        catch (RuntimeException broken)
        {
            result = failure.failed(described, run.elapsedMs(), String.valueOf(broken.getMessage()));
        }

        if (generation.get() == mine && session.version() == described.version())
        {
            deliver.accept(result);
        }
    }
}
