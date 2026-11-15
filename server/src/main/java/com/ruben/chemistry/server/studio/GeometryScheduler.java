package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.server.util.Debouncer;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Works out how strained a session's geometry is without doing so for every patch of a drag.
 * Such requests are debounced.
 */
public final class GeometryScheduler implements AutoCloseable
{
    /** How long a session must be left alone before its geometry is examined. */
    public static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(250);

    private final Debouncer<String> debouncer;
    private final BiFunction<StudioSession, StudioSession.Snapshot, GeometryReport> calculator;

    /**
     * @param debounce how long a session must be left alone before its geometry is examined
     */
    public GeometryScheduler(Duration debounce)
    {
        this(new Debouncer<>(debounce), GeometryReport::of);
    }

    /**
     * @param debounce how long a session must be left alone before its geometry is examined
     * @param calculator examines a snapshot of a session's geometry
     */
    GeometryScheduler(Duration debounce, BiFunction<StudioSession, StudioSession.Snapshot, GeometryReport> calculator)
    {
        this(new Debouncer<>(debounce), calculator);
    }

    private GeometryScheduler(Debouncer<String> debouncer, BiFunction<StudioSession, StudioSession.Snapshot, GeometryReport> calculator)
    {
        this.debouncer = debouncer;
        this.calculator = calculator;
    }

    /**
     * @return a scheduler that never examines anything, for tests that must not be sent results they did not ask for
     */
    public static GeometryScheduler disabled()
    {
        return new GeometryScheduler(Debouncer.disabled(), GeometryReport::of);
    }

    /**
     * Asks for a session's geometry to be examined.
     *
     * @param session the session that has changed
     * @param deliver told the result, from another thread, only if the session is still at the version it was made from
     */
    public void schedule(StudioSession session, Consumer<GeometryReport> deliver)
    {
        debouncer.submit(session.sessionId(), () -> run(session, deliver));
    }

    /** Stops examining anything, and abandons what is waiting. */
    @Override
    public void close()
    {
        debouncer.close();
    }

    private void run(StudioSession session, Consumer<GeometryReport> deliver)
    {
        StudioSession.Snapshot snapshot = session.snapshot();

        GeometryReport report;
        try
        {
            report = calculator.apply(session, snapshot);
        }
        catch (RuntimeException failure)
        {
            report = GeometryReport.failed(session, snapshot.version(), String.valueOf(failure.getMessage()));
        }

        if (session.version() == snapshot.version())
        {
            deliver.accept(report);
        }
    }
}
