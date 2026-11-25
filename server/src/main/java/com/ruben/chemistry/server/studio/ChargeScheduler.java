package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.quantum.scf.ScfListener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Works out the partial charges of a session's atoms when asked, and reports how the calculation is getting on.
 * <p>
 * It is a {@link JobScheduler}: requests are debounced, the calculation runs on its own thread, and it is abandoned as soon
 * as the session moves on or a newer request is made for it.
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

    private final JobScheduler<ChargeReport> jobs;
    private final Models models;

    /**
     * Assigns charges with the models of {@link ChargeModels}.
     *
     * @param debounce how long a session must be left alone before its charges are calculated
     */
    public ChargeScheduler(Duration debounce)
    {
        this(new JobScheduler<>(debounce), ChargeModels::create);
    }

    /**
     * @param debounce how long a session must be left alone before its charges are calculated
     * @param models makes the model a request asks for
     */
    public ChargeScheduler(Duration debounce, Models models)
    {
        this(new JobScheduler<>(debounce), Objects.requireNonNull(models, "models"));
    }

    /**
     * @param debounce how long a session must be left alone before its charges are calculated
     * @param model the model to assign every request's charges with, whatever it asks for
     */
    public ChargeScheduler(Duration debounce, PartialChargeModel model)
    {
        this(debounce, only(model));
    }

    private ChargeScheduler(JobScheduler<ChargeReport> jobs, Models models)
    {
        this.jobs = jobs;
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
        return new ChargeScheduler(JobScheduler.disabled(), ChargeModels::create);
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
        jobs.schedule(session,
                (described, run) ->
                {
                    Pace pace = new Pace(session, described, requested, progress, run);
                    // every Fock build is kept, where progress reports only as many as can be read
                    List<ChargeReport.ScfStep> steps = new ArrayList<>();
                    pace.stage("starting");
                    PartialChargeModel model = models.create(requested, new ScfListener()
                    {
                        @Override
                        public void stage(String stage)
                        {
                            run.check();
                            pace.stage(stage);
                        }

                        @Override
                        public void iteration(int iteration, double energy, double error)
                        {
                            run.check();
                            if (iteration == 1)
                            {
                                // the count starts again from 1 when a second calculation begins, which replaces the first
                                steps.clear();
                            }
                            steps.add(new ChargeReport.ScfStep(iteration, energy, error));
                            pace.iteration(iteration, energy, error);
                        }
                    });
                    return ChargeReport.of(session, described, requested, model).converged(steps).elapsed(run.elapsedMs());
                },
                (described, elapsedMs, message) -> ChargeReport.failed(session, described.version(), requested, elapsedMs, message),
                deliver);
    }

    /** Stops calculating anything, and abandons what is waiting. */
    @Override
    public void close()
    {
        jobs.close();
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
        private final JobScheduler.Run run;
        private String stage = "starting";
        private long last;

        private Pace(StudioSession session, StudioSession.Described described, String requested, Consumer<ChargeProgress> progress, JobScheduler.Run run)
        {
            this.session = session;
            this.described = described;
            this.requested = requested;
            this.progress = progress;
            this.run = run;
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
                    iteration, energy, error, run.elapsedMs()));
        }
    }
}
