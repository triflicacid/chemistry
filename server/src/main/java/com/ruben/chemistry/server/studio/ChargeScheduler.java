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

/**
 * Works out the partial charges of a session's atoms when asked, without doing so for every request of a run of them.
 * <p>
 * The calculation is a quantum one and takes a while, so it runs on its own thread, apart from the one that examines
 * the geometry, and reports how it is getting on. Requests are debounced. A calculation is abandoned, and nothing is
 * delivered for it, as soon as the session moves on from the geometry it was started from or a newer request is made
 * for the session.
 */
public final class ChargeScheduler implements AutoCloseable
{
    /** How long a session must be left alone before its charges are calculated. */
    public static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(300);

    /** The least time between two reports of progress through the iterations. */
    public static final Duration PROGRESS_INTERVAL = Duration.ofMillis(100);

    /**
     * Makes the model a client asked for.
     */
    @FunctionalInterface
    public interface Models
    {
        /**
         * @param id the name the client asked for the model by
         * @param listener told how a quantum calculation is getting on, and able to stop it
         * @return the model
         * @throws IllegalArgumentException if there is no model of that name
         */
        PartialChargeModel create(String id, ScfListener listener);
    }

    private final Debouncer<String> debouncer;
    private final Models models;
    private final Map<String, AtomicLong> latest = new ConcurrentHashMap<>();

    /**
     * Assigns charges with the models of {@link ChargeModels}.
     *
     * @param debounce how long a session must be left alone before its charges are calculated
     */
    public ChargeScheduler(Duration debounce)
    {
        this(new Debouncer<>(debounce), ChargeModels::create);
    }

    /**
     * @param debounce how long a session must be left alone before its charges are calculated
     * @param models makes the model a request asks for
     */
    public ChargeScheduler(Duration debounce, Models models)
    {
        this(new Debouncer<>(debounce), Objects.requireNonNull(models, "models"));
    }

    /**
     * @param debounce how long a session must be left alone before its charges are calculated
     * @param model the model to assign every request's charges with, whatever it asks for
     */
    public ChargeScheduler(Duration debounce, PartialChargeModel model)
    {
        this(debounce, only(model));
    }

    private ChargeScheduler(Debouncer<String> debouncer, Models models)
    {
        this.debouncer = debouncer;
        this.models = models;
    }

    private static Models only(PartialChargeModel model)
    {
        Objects.requireNonNull(model, "model");
        return (id, listener) -> model;
    }

    /**
     * @return a scheduler that never calculates anything, for tests that must not be sent results they did not ask for
     */
    public static ChargeScheduler disabled()
    {
        return new ChargeScheduler(Debouncer.disabled(), ChargeModels::create);
    }

    /**
     * Asks for a session's charges to be calculated once it has been left alone, replacing any request for it that is
     * waiting and abandoning any calculation of it that is under way.
     *
     * @param session the session to calculate charges for
     * @param requested the name of the model to assign them with
     * @param progress told how the calculation is getting on, from another thread, while it is not out of date
     * @param deliver told the result, from another thread, only if it is not out of date
     */
    public void schedule(StudioSession session, String requested, Consumer<ChargeProgress> progress, Consumer<ChargeReport> deliver)
    {
        latest.computeIfAbsent(session.sessionId(), id -> new AtomicLong()).incrementAndGet();
        debouncer.submit(session.sessionId(), () -> run(session, requested, progress, deliver));
    }

    /** Stops calculating anything, and abandons what is waiting. */
    @Override
    public void close()
    {
        debouncer.close();
    }

    private void run(StudioSession session, String requested, Consumer<ChargeProgress> progress, Consumer<ChargeReport> deliver)
    {
        AtomicLong generation = latest.computeIfAbsent(session.sessionId(), id -> new AtomicLong());
        long mine = generation.get();
        long started = System.nanoTime();
        StudioSession.Described described = session.describe();
        Pace pace = new Pace(session, described, requested, progress, started);
        Runnable check = () ->
        {
            if (generation.get() != mine || session.version() != described.version())
            {
                throw new CancellationException("The charges are out of date");
            }
        };

        ChargeReport report;
        try
        {
            check.run();
            pace.stage("starting");
            PartialChargeModel model = models.create(requested, new ScfListener()
            {
                @Override
                public void stage(String stage)
                {
                    check.run();
                    pace.stage(stage);
                }

                @Override
                public void iteration(int iteration, double energy, double error)
                {
                    check.run();
                    pace.iteration(iteration, energy, error);
                }
            });
            report = ChargeReport.of(session, described, requested, model).elapsed(Pace.millisSince(started));
        }
        catch (CancellationException outOfDate)
        {
            return;
        }
        catch (RuntimeException failure)
        {
            report = ChargeReport.failed(session, described.version(), requested, Pace.millisSince(started), String.valueOf(failure.getMessage()));
        }

        if (generation.get() == mine && session.version() == described.version())
        {
            deliver.accept(report);
        }
    }

    /**
     * Reports progress, leaving out the iterations that come faster than anyone could read them.
     */
    private static final class Pace
    {
        private final StudioSession session;
        private final StudioSession.Described described;
        private final String requested;
        private final Consumer<ChargeProgress> progress;
        private final long started;
        private String stage = "starting";
        private long last;

        private Pace(StudioSession session, StudioSession.Described described, String requested, Consumer<ChargeProgress> progress, long started)
        {
            this.session = session;
            this.described = described;
            this.requested = requested;
            this.progress = progress;
            this.started = started;
        }

        static long millisSince(long nanos)
        {
            return (System.nanoTime() - nanos) / 1_000_000;
        }

        /** A change of stage is always reported. */
        void stage(String next)
        {
            stage = next;
            send(0, null, null);
        }

        /** An iteration is reported if enough time has passed since the last report. */
        void iteration(int iteration, double energy, double error)
        {
            if (System.nanoTime() - last >= PROGRESS_INTERVAL.toNanos())
            {
                send(iteration, energy, error);
            }
        }

        private void send(int iteration, Double energy, Double error)
        {
            last = System.nanoTime();
            progress.accept(new ChargeProgress(ChargeProgress.TYPE, session.sessionId(), described.version(), requested, stage,
                    iteration, energy, error, millisSince(started)));
        }
    }
}
