package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.quantum.scf.ScfListener;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Works out the forces on a session's atoms when asked.
 * <p>
 * Each molecule's charges come from a quantum calculation, so this is a {@link JobScheduler}: requests are debounced, the
 * calculation runs on its own thread, and it is abandoned as soon as the session moves on or a newer request is made for it.
 */
public final class ForceScheduler implements AutoCloseable
{
    /** How long a session must be left alone before its forces are calculated. */
    public static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(300);

    private final JobScheduler<ForceReport> jobs;
    private final Function<ScfListener, PartialChargeModel> charges;

    /**
     * Assigns each molecule's charges with the studio's automatic Hartree-Fock model.
     *
     * @param debounce how long a session must be left alone before its forces are calculated
     */
    public ForceScheduler(Duration debounce)
    {
        this(new JobScheduler<>(debounce), listener -> ChargeModels.create(ChargeModels.MULLIKEN, listener));
    }

    /**
     * @param debounce how long a session must be left alone before its forces are calculated
     * @param charges makes the model that assigns each molecule's charges, given what to tell how it is getting on
     */
    public ForceScheduler(Duration debounce, Function<ScfListener, PartialChargeModel> charges)
    {
        this(new JobScheduler<>(debounce), Objects.requireNonNull(charges, "charges"));
    }

    private ForceScheduler(JobScheduler<ForceReport> jobs, Function<ScfListener, PartialChargeModel> charges)
    {
        this.jobs = jobs;
        this.charges = charges;
    }

    /**
     * @return a scheduler that never calculates anything, for tests that must not be sent results they did not ask for
     */
    public static ForceScheduler disabled()
    {
        return new ForceScheduler(JobScheduler.disabled(), listener -> ChargeModels.create(ChargeModels.MULLIKEN, listener));
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
        jobs.schedule(session,
                (described, run) ->
                {
                    PartialChargeModel model = charges.apply(new ScfListener()
                    {
                        @Override
                        public void stage(String stage)
                        {
                            run.check();
                        }

                        @Override
                        public void iteration(int iteration, double energy, double error)
                        {
                            run.check();
                        }
                    });
                    return ForceReport.of(session, described, model).elapsed(run.elapsedMs());
                },
                (described, elapsedMs, message) -> ForceReport.failed(session, described.version(), elapsedMs, message),
                deliver);
    }

    /** Stops calculating anything, and abandons what is waiting. */
    @Override
    public void close()
    {
        jobs.close();
    }
}
