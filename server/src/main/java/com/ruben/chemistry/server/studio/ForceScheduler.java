package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.quantum.scf.ScfListener;
import com.ruben.chemistry.server.util.Debouncer;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Works out the forces on a session's atoms when asked.
 */
public final class ForceScheduler implements AutoCloseable
{
    /** How long a session must be left alone before its forces are calculated. */
    public static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(300);

    private final Debouncer<String> debouncer;
    private final Function<ScfListener, PartialChargeModel> charges;
    private final Map<String, AtomicLong> latest = new ConcurrentHashMap<>();

    /**
     * Assigns each molecule's charges with the studio's automatic Hartree-Fock model.
     *
     * @param debounce how long a session must be left alone before its forces are calculated
     */
    public ForceScheduler(Duration debounce)
    {
        this(new Debouncer<>(debounce), listener -> ChargeModels.create(ChargeModels.MULLIKEN, listener));
    }

    /**
     * @param debounce how long a session must be left alone before its forces are calculated
     * @param charges makes the model that assigns each molecule's charges, given what to tell how it is getting on
     */
    public ForceScheduler(Duration debounce, Function<ScfListener, PartialChargeModel> charges)
    {
        this(new Debouncer<>(debounce), Objects.requireNonNull(charges, "charges"));
    }

    private ForceScheduler(Debouncer<String> debouncer, Function<ScfListener, PartialChargeModel> charges)
    {
        this.debouncer = debouncer;
        this.charges = charges;
    }

    /**
     * @return a scheduler that never calculates anything, for tests that must not be sent results they did not ask for
     */
    public static ForceScheduler disabled()
    {
        return new ForceScheduler(Debouncer.disabled(), listener -> ChargeModels.create(ChargeModels.MULLIKEN, listener));
    }

    /**
     * Asks for a session's forces to be calculated once it has been left alone, replacing any request for it that is
     * waiting and abandoning any calculation of it that is under way.
     *
     * @param session the session to calculate forces for
     * @param deliver told the result, from another thread, only if it is not out of date
     */
    public void schedule(StudioSession session, Consumer<ForceReport> deliver)
    {
        latest.computeIfAbsent(session.sessionId(), id -> new AtomicLong()).incrementAndGet();
        debouncer.submit(session.sessionId(), () -> run(session, deliver));
    }

    /** Stops calculating anything, and abandons what is waiting. */
    @Override
    public void close()
    {
        debouncer.close();
    }

    private void run(StudioSession session, Consumer<ForceReport> deliver)
    {
        AtomicLong generation = latest.computeIfAbsent(session.sessionId(), id -> new AtomicLong());
        long mine = generation.get();
        long started = System.nanoTime();
        StudioSession.Described described = session.describe();
        Runnable check = () ->
        {
            if (generation.get() != mine || session.version() != described.version())
            {
                throw new CancellationException("The forces are out of date");
            }
        };

        ForceReport report;
        try
        {
            check.run();
            PartialChargeModel model = charges.apply(new ScfListener()
            {
                @Override
                public void stage(String stage)
                {
                    check.run();
                }

                @Override
                public void iteration(int iteration, double energy, double error)
                {
                    check.run();
                }
            });
            report = ForceReport.of(session, described, model).elapsed(millisSince(started));
        }
        catch (CancellationException outOfDate)
        {
            return;
        }
        catch (RuntimeException failure)
        {
            report = ForceReport.failed(session, described.version(), millisSince(started), String.valueOf(failure.getMessage()));
        }

        if (generation.get() == mine && session.version() == described.version())
        {
            deliver.accept(report);
        }
    }

    private static long millisSince(long nanos)
    {
        return (System.nanoTime() - nanos) / 1_000_000;
    }
}
