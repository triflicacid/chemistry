package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.StructureFixer;
import com.ruben.chemistry.space.Vec3;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Relaxes the geometry of a session off the thread that asked, reporting progress as it goes.
 * <p>
 * A session is optimised once at a time. An optimisation is made from the session at one version, and is dropped
 * without touching the session if the session has moved on by the time it finishes, or when it is asked to stop.
 */
public final class OptimisationRunner implements AutoCloseable
{
    /** The status of an optimisation that moved atoms, which has been applied to the session as one edit. */
    public static final String COMPLETE = "complete";

    /** The status of an optimisation that found nothing worth moving. */
    public static final String UNCHANGED = "unchanged";

    /** The status of an optimisation that could not reach a relaxed structure, and so changed nothing. */
    public static final String FAILED = "failed";

    /** The status of an optimisation that was stopped or overtaken by an edit, and so changed nothing. */
    public static final String CANCELLED = "cancelled";

    /** The least time between two reports of progress. */
    public static final Duration PROGRESS_INTERVAL = Duration.ofMillis(100);

    /**
     * What does the relaxing, which tests replace with something they can hold up.
     */
    @FunctionalInterface
    interface Fixer
    {
        /**
         * @param structure the structure to relax
         * @param constraints what to hold
         * @param options how hard to try
         * @param progress told how it is getting on, and able to stop it
         * @return the relaxed structure
         */
        StructureFixer.Fixed fix(Structure3D structure, StructureFixer.Constraints constraints, StructureFixer.Options options, StructureFixer.Progress progress);
    }

    /**
     * One optimisation under way.
     */
    public static final class Operation
    {
        private final String id;
        private final String sessionId;
        private final long version;
        private volatile String cancelledBecause;

        private Operation(String id, String sessionId, long version)
        {
            this.id = id;
            this.sessionId = sessionId;
            this.version = version;
        }

        /**
         * @return what the client calls this optimisation by
         */
        public String id()
        {
            return id;
        }

        /**
         * @return the session being optimised
         */
        public String sessionId()
        {
            return sessionId;
        }

        /**
         * @return the version of the session it was made from
         */
        public long version()
        {
            return version;
        }

        /**
         * Asks for it to stop, which it does at its next step. Only the first reason is kept.
         *
         * @param reason why, in words the user can be shown
         */
        public synchronized void cancel(String reason)
        {
            if (cancelledBecause == null)
            {
                cancelledBecause = reason;
            }
        }

        /**
         * @return whether it has been asked to stop
         */
        public boolean cancelled()
        {
            return cancelledBecause != null;
        }
    }

    /**
     * How an optimisation ended.
     *
     * @param status {@link #COMPLETE}, {@link #UNCHANGED}, {@link #FAILED} or {@link #CANCELLED}
     * @param version the version of the session after it, which is the one it was made from unless atoms moved
     * @param moved where each atom that moved now is, which is empty unless the status is {@link #COMPLETE}
     * @param strainBefore the {@link GeometryReport} strain of the geometry it started from, which is the figure the client
     *     shows, or {@code null} if it never got that far or the geometry could not be examined
     * @param strainAfter the same of the geometry the session ends with, which is the one it started from unless atoms moved,
     *     or {@code null} under the same conditions
     * @param iterations how many steps it took
     * @param warnings things the optimisation could not do as asked, such as stereochemistry it could not read, and so left free
     * @param message why it failed or was cancelled, or {@code null} if it did neither
     */
    public record Result(
            String status,
            long version,
            Map<String, Vec3> moved,
            Double strainBefore,
            Double strainAfter,
            int iterations,
            List<String> warnings,
            String message)
    {
    }

    /**
     * Told what becomes of an optimisation, from the thread running it.
     */
    public interface Listener
    {
        /**
         * @param operation the optimisation
         * @param iteration how many steps it has taken
         * @param energy the value it is minimising, which has no unit
         */
        void progress(Operation operation, int iteration, double energy);

        /**
         * Told once, last, after the optimisation is no longer registered as running and any change has been made to
         * the session.
         *
         * @param operation the optimisation
         * @param result how it ended
         */
        void finished(Operation operation, Result result);
    }

    private final Map<String, Operation> running = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final Fixer fixer;
    private final long progressNanos;

    /**
     * Relaxes with {@link StructureFixer}.
     */
    public OptimisationRunner()
    {
        this(StructureFixer::fix, PROGRESS_INTERVAL);
    }

    /**
     * @param fixer what does the relaxing
     * @param progressInterval the least time between two reports of progress
     */
    OptimisationRunner(Fixer fixer, Duration progressInterval)
    {
        this.fixer = fixer;
        this.progressNanos = progressInterval.toNanos();
        this.executor = Executors.newCachedThreadPool(task ->
        {
            Thread thread = new Thread(task, "studio-optimise");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts optimising a session's geometry as it is now.
     *
     * @param session the session to optimise
     * @param listener told how it goes, from another thread
     * @return the optimisation, which is under way
     * @throws StudioEditException with {@code BUSY} if the session is already being optimised or has an edit in progress
     */
    public Operation start(StudioSession session, Listener listener)
    {
        if (running.containsKey(session.sessionId()))
        {
            throw new StudioEditException("BUSY", "This structure is already being optimised");
        }

        StudioSession.OptimisationInput input = session.beginOptimisation();
        Operation operation = new Operation(UUID.randomUUID().toString(), session.sessionId(), input.version());
        if (running.putIfAbsent(session.sessionId(), operation) != null)
        {
            throw new StudioEditException("BUSY", "This structure is already being optimised");
        }

        executor.execute(() -> run(session, operation, input, listener));
        return operation;
    }

    /**
     * Asks for a session's optimisation to stop, if it has one.
     *
     * @param sessionId the session
     * @param reason why, in words the user can be shown
     * @return whether the session had an optimisation to stop
     */
    public boolean cancel(String sessionId, String reason)
    {
        Operation operation = running.get(sessionId);
        if (operation == null)
        {
            return false;
        }

        operation.cancel(reason);
        return true;
    }

    /**
     * Drops the session's optimisation if the session has changed since it was made from it, so that its result is
     * not worked out for a structure that is gone.
     *
     * @param session the session that may have changed
     */
    public void supersede(StudioSession session)
    {
        Operation operation = running.get(session.sessionId());
        if (operation != null && operation.version() != session.version())
        {
            operation.cancel("The structure was edited, so the optimisation was dropped");
        }
    }

    /**
     * @param sessionId the session
     * @return its optimisation under way, if it has one
     */
    public Optional<Operation> runningFor(String sessionId)
    {
        return Optional.ofNullable(running.get(sessionId));
    }

    /** Stops every optimisation. */
    @Override
    public void close()
    {
        running.values().forEach(operation -> operation.cancel("The server is shutting down"));
        executor.shutdownNow();
    }

    private void run(StudioSession session, Operation operation, StudioSession.OptimisationInput input, Listener listener)
    {
        int[] reached = {0};
        long[] told = {0};
        StructureFixer.Progress progress = (iteration, energy) ->
        {
            reached[0] = iteration;
            if (operation.cancelled())
            {
                return false;
            }

            long now = System.nanoTime();
            if (iteration == 0 || now - told[0] >= progressNanos)
            {
                told[0] = now;
                listener.progress(operation, iteration, energy);
            }
            return true;
        };

        Result result;
        try
        {
            result = conclude(session, operation, input, progress, reached);
        }
        catch (RuntimeException failure)
        {
            result = operation.cancelled()
                    ? cancelled(session, operation, reached[0])
                    : new Result(FAILED, session.version(), Map.of(), null, null, reached[0], List.of(), String.valueOf(failure.getMessage()));
        }

        running.remove(session.sessionId(), operation);
        listener.finished(operation, result);
    }

    private Result conclude(StudioSession session, Operation operation, StudioSession.OptimisationInput input, StructureFixer.Progress progress, int[] reached)
    {
        StructureFixer.Constraints constraints = StructureFixer.Constraints.NONE;
        for (int site : input.lockedSites())
        {
            constraints = constraints.pinningSite(site);
        }

        StructureFixer.Fixed fixed = fixer.fix(input.structure(), constraints, StructureFixer.Options.DEFAULT, progress);
        if (operation.cancelled())
        {
            return cancelled(session, operation, reached[0]);
        }

        StructureFixer.Report report = fixed.reports().getFirst();
        List<String> warnings = new ArrayList<>(report.conflicts());
        report.unread().forEach(issue -> warnings.add(issue.message()));

        return switch (report.outcome())
        {
            case FAILED -> new Result(FAILED, session.version(), Map.of(), null, null, reached[0], warnings,
                    "The structure could not be relaxed without changing its stereochemistry");
            case UNCHANGED -> unchanged(session, input, warnings, reached[0]);
            case FIXED -> apply(session, operation, input, fixed, warnings, reached[0]);
        };
    }

    private Result apply(StudioSession session, Operation operation, StudioSession.OptimisationInput input, StructureFixer.Fixed fixed,
            List<String> warnings, int iterations)
    {
        List<Vec3> relaxed = fixed.structure().conformers().getFirst().positions();
        Map<String, Vec3> positions = new LinkedHashMap<>();
        for (int site = 0; site < input.sites().size(); site++)
        {
            positions.put(input.sites().get(site), relaxed.get(site));
        }

        Optional<StudioSession.Applied> applied = session.optimised(input.version(), positions);
        if (applied.isEmpty())
        {
            operation.cancel("The structure was edited, so the optimisation was dropped");
            return cancelled(session, operation, iterations);
        }

        if (applied.get().moved().isEmpty())
        {
            return unchanged(session, input, warnings, iterations);
        }

        Map<String, Vec3> before = before(input);
        Map<String, Vec3> after = new LinkedHashMap<>(before);
        after.putAll(applied.get().moved());
        return new Result(COMPLETE, applied.get().version(), applied.get().moved(), strain(session, before), strain(session, after), iterations, warnings, null);
    }

    private Result unchanged(StudioSession session, StudioSession.OptimisationInput input, List<String> warnings, int iterations)
    {
        Double strain = strain(session, before(input));
        return new Result(UNCHANGED, session.version(), Map.of(), strain, strain, iterations, warnings, null);
    }

    /**
     * @param input what the optimisation was made from
     * @return where each atom was when it began
     */
    private static Map<String, Vec3> before(StudioSession.OptimisationInput input)
    {
        Map<String, Vec3> positions = new LinkedHashMap<>();
        List<Vec3> started = input.structure().conformers().getFirst().positions();
        for (int site = 0; site < input.sites().size(); site++)
        {
            positions.put(input.sites().get(site), started.get(site));
        }
        return positions;
    }

    /**
     * Measures a geometry as the client's strain figure does, so that a result's numbers can be set beside it.
     * Failing to measure must not undo an optimisation that has already been applied, so it is not an error.
     *
     * @param session the session the geometry is of
     * @param positions where each atom is
     * @return its strain, or {@code null} if it could not be examined
     */
    private static Double strain(StudioSession session, Map<String, Vec3> positions)
    {
        try
        {
            List<Vec3> ordered = session.molecule().atoms().stream().map(atom -> positions.get(session.atomId(atom))).toList();
            return GeometryReport.of(session, new StudioSession.Snapshot(session.version(), ordered)).strain();
        }
        catch (RuntimeException unexaminable)
        {
            return null;
        }
    }

    private Result cancelled(StudioSession session, Operation operation, int iterations)
    {
        return new Result(CANCELLED, session.version(), Map.of(), null, null, iterations, List.of(), operation.cancelledBecause);
    }
}
