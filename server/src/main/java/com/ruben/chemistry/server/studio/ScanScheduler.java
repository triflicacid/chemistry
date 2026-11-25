package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.quantum.scf.ScfListener;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Scans the interaction energy of two of a session's molecules along a hydrogen bond.
 */
public final class ScanScheduler implements AutoCloseable
{
    /** How long a session must be left alone before its scan is calculated. */
    public static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(300);

    private final JobScheduler<ScanReport> jobs;
    private final Function<ScfListener, PartialChargeModel> charges;

    /**
     * Assigns each molecule's charges with the studio's automatic Hartree-Fock model.
     *
     * @param debounce how long a session must be left alone before its scan is calculated
     */
    public ScanScheduler(Duration debounce)
    {
        this(new JobScheduler<>(debounce), listener -> ChargeModels.create(ChargeModels.MULLIKEN, listener));
    }

    /**
     * @param debounce how long a session must be left alone before its scan is calculated
     * @param charges makes the model that assigns each molecule's charges, given what to tell how it is getting on
     */
    public ScanScheduler(Duration debounce, Function<ScfListener, PartialChargeModel> charges)
    {
        this(new JobScheduler<>(debounce), Objects.requireNonNull(charges, "charges"));
    }

    private ScanScheduler(JobScheduler<ScanReport> jobs, Function<ScfListener, PartialChargeModel> charges)
    {
        this.jobs = jobs;
        this.charges = charges;
    }

    /**
     * @param session the session to scan
     * @param deliver told the result, from another thread, only if it is not out of date
     */
    public void schedule(StudioSession session, Consumer<ScanReport> deliver)
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
                    return ScanReport.of(session, described, model).elapsed(run.elapsedMs());
                },
                (described, elapsedMs, message) -> ScanReport.failed(session, described.version(), elapsedMs, message),
                deliver);
    }

    @Override
    public void close()
    {
        jobs.close();
    }
}
