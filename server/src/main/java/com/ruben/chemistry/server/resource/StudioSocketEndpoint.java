package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.json.JsonException;
import com.ruben.chemistry.server.json.JsonNames;
import com.ruben.chemistry.server.json.RecordConverter;
import com.ruben.chemistry.server.molecule.Camera;
import com.ruben.chemistry.server.molecule.MoleculeException;
import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.server.molecule.ParsedMolecule;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.server.molecule.StudioSnapshot;
import com.ruben.chemistry.server.studio.ChargeModels;
import com.ruben.chemistry.server.studio.ChargeScheduler;
import com.ruben.chemistry.server.studio.FieldReport;
import com.ruben.chemistry.server.studio.ForceScheduler;
import com.ruben.chemistry.server.studio.GeometryScheduler;
import com.ruben.chemistry.server.studio.OptimisationRunner;
import com.ruben.chemistry.server.studio.OrbitalScheduler;
import com.ruben.chemistry.server.studio.ScanScheduler;
import com.ruben.chemistry.server.studio.StudioEditException;
import com.ruben.chemistry.server.studio.StudioRequests;
import com.ruben.chemistry.server.studio.StudioSession;
import com.ruben.chemistry.server.studio.StudioSessionStore;
import com.ruben.chemistry.server.studio.StudioState;
import com.ruben.chemistry.server.ws.CloseCode;
import com.ruben.chemistry.server.ws.SocketEndpoint;
import com.ruben.chemistry.server.ws.SocketRegistry;
import com.ruben.chemistry.server.ws.TypedJsonSocketListener;
import com.ruben.chemistry.server.ws.WebSocketContext;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.ruben.chemistry.server.ws.WebSocketServer.DEFAULT_MAX_MESSAGE;

/**
 * The Studio editing channel: one WebSocket connection per open editing workspace.
 */
public final class StudioSocketEndpoint extends SocketEndpoint
{
    /** Bumped when a message type changes shape, so a stale client can be told to reload. */
    private static final int PROTOCOL_VERSION = 32;

    /** Which session a connection is holding, if any. */
    private static final String HELD = "studio.session";

    private final MoleculeStore molecules;
    private final StudioSessionStore sessions;
    private final GeometryScheduler geometry;
    private final OptimisationRunner optimiser;
    private final ChargeScheduler charges;
    private final ForceScheduler forces;
    private final ScanScheduler scans;
    private final OrbitalScheduler orbitals;
    private final MoleculeParser parser = new MoleculeParser();

    /** The connection each session's results are sent to, which is the one holding it. */
    private final Map<String, WebSocketContext> holders = new ConcurrentHashMap<>();

    /**
     * @param molecules the molecules a session can be opened on
     */
    public StudioSocketEndpoint(MoleculeStore molecules)
    {
        this(molecules, new StudioSessionStore());
    }

    /**
     * @param molecules the molecules a session can be opened on
     * @param sessions where to hold the open sessions
     */
    public StudioSocketEndpoint(MoleculeStore molecules, StudioSessionStore sessions)
    {
        this(molecules, sessions, new GeometryScheduler(GeometryScheduler.DEFAULT_DEBOUNCE));
    }

    /**
     * @param molecules the molecules a session can be opened on
     * @param sessions where to hold the open sessions
     * @param geometry what examines a session's geometry once it has been left alone
     */
    public StudioSocketEndpoint(MoleculeStore molecules, StudioSessionStore sessions, GeometryScheduler geometry)
    {
        this(molecules, sessions, geometry, new OptimisationRunner());
    }

    /**
     * @param molecules the molecules a session can be opened on
     * @param sessions where to hold the open sessions
     * @param geometry what examines a session's geometry once it has been left alone
     * @param optimiser what relaxes a session's geometry when asked to
     */
    public StudioSocketEndpoint(MoleculeStore molecules, StudioSessionStore sessions, GeometryScheduler geometry, OptimisationRunner optimiser)
    {
        this(molecules, sessions, geometry, optimiser, new ChargeScheduler(ChargeScheduler.DEFAULT_DEBOUNCE));
    }

    /**
     * @param molecules the molecules a session can be opened on
     * @param sessions where to hold the open sessions
     * @param geometry what examines a session's geometry once it has been left alone
     * @param optimiser what relaxes a session's geometry when asked to
     * @param charges what works out the partial charges of a session's atoms when asked to
     */
    public StudioSocketEndpoint(MoleculeStore molecules, StudioSessionStore sessions, GeometryScheduler geometry, OptimisationRunner optimiser, ChargeScheduler charges)
    {
        this(molecules, sessions, geometry, optimiser, charges, new ForceScheduler(ForceScheduler.DEFAULT_DEBOUNCE));
    }

    /**
     * @param molecules the molecules a session can be opened on
     * @param sessions where to hold the open sessions
     * @param geometry what examines a session's geometry once it has been left alone
     * @param optimiser what relaxes a session's geometry when asked to
     * @param charges what works out the partial charges of a session's atoms when asked to
     * @param forces what works out the forces on a session's atoms when asked to
     */
    public StudioSocketEndpoint(MoleculeStore molecules, StudioSessionStore sessions, GeometryScheduler geometry, OptimisationRunner optimiser, ChargeScheduler charges, ForceScheduler forces)
    {
        this(molecules, sessions, geometry, optimiser, charges, forces, new ScanScheduler(ScanScheduler.DEFAULT_DEBOUNCE));
    }

    /**
     * @param molecules the molecules a session can be opened on
     * @param sessions where to hold the open sessions
     * @param geometry what examines a session's geometry once it has been left alone
     * @param optimiser what relaxes a session's geometry when asked to
     * @param charges what works out the partial charges of a session's atoms when asked to
     * @param forces what works out the forces on a session's atoms when asked to
     * @param scans what scans two of a session's molecules along a hydrogen bond when asked to
     */
    public StudioSocketEndpoint(MoleculeStore molecules, StudioSessionStore sessions, GeometryScheduler geometry, OptimisationRunner optimiser, ChargeScheduler charges, ForceScheduler forces, ScanScheduler scans)
    {
        this(molecules, sessions, geometry, optimiser, charges, forces, scans, new OrbitalScheduler(OrbitalScheduler.DEFAULT_DEBOUNCE));
    }

    /**
     * @param molecules the molecules a session can be opened on
     * @param sessions where to hold the open sessions
     * @param geometry what examines a session's geometry once it has been left alone
     * @param optimiser what relaxes a session's geometry when asked to
     * @param charges what works out the partial charges of a session's atoms when asked to
     * @param forces what works out the forces on a session's atoms when asked to
     * @param scans what scans two of a session's molecules along a hydrogen bond when asked to
     * @param orbitals what works out a session's molecular orbitals when asked to
     */
    public StudioSocketEndpoint(MoleculeStore molecules, StudioSessionStore sessions, GeometryScheduler geometry, OptimisationRunner optimiser, ChargeScheduler charges, ForceScheduler forces, ScanScheduler scans, OrbitalScheduler orbitals)
    {
        this.orbitals = Objects.requireNonNull(orbitals, "orbitals");
        this.scans = Objects.requireNonNull(scans, "scans");
        this.charges = Objects.requireNonNull(charges, "charges");
        this.forces = Objects.requireNonNull(forces, "forces");
        this.molecules = Objects.requireNonNull(molecules, "molecules");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.optimiser = Objects.requireNonNull(optimiser, "optimiser");
    }

    @Override
    public void registerHandlers(SocketRegistry registry)
    {
        registry.register("/ws/studio", () -> new StudioSocket(this));
    }

    /**
     * Opens a session on a stored molecule and answers with its full state.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void open(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.OpenRequest request;
        try
        {
            request = payload(body, StudioRequests.OpenRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("session.open", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.moleculeId() == null)
        {
            context.error("session.open", sequence, "INVALID_MESSAGE", "molecule_id is required");
            return;
        }

        StoredMolecule stored = molecules.find(request.moleculeId()).orElse(null);
        if (stored == null)
        {
            context.error("session.open", sequence, "UNKNOWN_MOLECULE",
                    "No molecule is stored as '" + request.moleculeId() + "'");
            return;
        }

        int conformers = request.conformers() == null ? StudioSession.DEFAULT_CONFORMERS : request.conformers();
        StudioSession session;
        try
        {
            session = StudioSession.open(UUID.randomUUID().toString(), stored, conformers);
        }
        catch (IllegalArgumentException outOfRange)
        {
            context.error("session.open", sequence, "INVALID_MESSAGE", outOfRange.getMessage());
            return;
        }
        catch (IllegalStateException unembeddable)
        {
            context.error("session.open", sequence, "UNSUPPORTED", unembeddable.getMessage());
            return;
        }

        sessions.add(session, context.id());
        hold(context, session);
        context.sendJson(StudioState.of(session, sequence));
        recalculate(session);
    }

    /**
     * Answers with the full state of a session the client has lost track of.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void resync(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.ResyncRequest request;
        try
        {
            request = payload(body, StudioRequests.ResyncRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("session.resync", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("session.resync", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        // a reconnecting client resyncs on a new connection, which takes over the session the
        // dropped one was holding and so stops its grace window
        hold(context, session);
        context.sendJson(StudioState.of(session, sequence));
        recalculate(session);
    }

    /**
     * Rebuilds a session on a SMILES the user wrote.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void submitSmiles(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.SubmitRequest request;
        try
        {
            request = payload(body, StudioRequests.SubmitRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("smiles.submit", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.smiles() == null)
        {
            context.error("smiles.submit", sequence, "INVALID_MESSAGE",
                    "session_id, base_version and smiles are required");
            return;
        }

        StudioSession session = current(context, "smiles.submit", sequence, request.sessionId(), request.baseVersion());
        if (session == null)
        {
            return;
        }

        if (request.smiles().isBlank())
        {
            context.error("smiles.submit", sequence, "INVALID_SMILES", "The SMILES is empty");
            return;
        }

        ParsedMolecule parsed;
        try
        {
            parsed = parser.parse(request.smiles());
        }
        catch (MoleculeException unreadable)
        {
            context.error("smiles.submit", sequence, "INVALID_SMILES", unreadable.getMessage());
            return;
        }

        StudioSession rebuilt;
        try
        {
            rebuilt = session.rebuilt(parsed.molecule(), StudioSession.DEFAULT_CONFORMERS);
        }
        catch (IllegalStateException | IllegalArgumentException unembeddable)
        {
            context.error("smiles.submit", sequence, "UNSUPPORTED", unembeddable.getMessage());
            return;
        }

        sessions.replace(rebuilt);
        context.sendJson(StudioState.of(rebuilt, sequence));
        recalculate(rebuilt);
    }

    /**
     * Moves atoms.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void patch(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.PatchRequest request;
        try
        {
            request = payload(body, StudioRequests.PatchRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("atom.patch", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.atoms() == null || request.atoms().isEmpty()
                || request.atoms().stream().anyMatch(atom -> atom == null || atom.id() == null || atom.position() == null))
        {
            context.error("atom.patch", sequence, "INVALID_MESSAGE",
                    "atoms are required, and each needs an id and a position");
            return;
        }

        StudioSession session = current(context, "atom.patch", sequence, request.sessionId(), request.baseVersion());
        if (session == null)
        {
            return;
        }

        List<StudioSession.Move> moves = request.atoms().stream()
                .map(atom -> new StudioSession.Move(atom.id(), atom.position()))
                .toList();

        boolean commit = request.commit() == null || request.commit();
        synchronized (session)
        {
            long before = session.version();
            long version;
            try
            {
                version = session.move(moves, commit);
            }
            catch (StudioEditException refused)
            {
                context.error("atom.patch", sequence, refused.code(), refused.getMessage());
                return;
            }

            // an edit in progress is not yet something worth keeping
            if (commit && session.canUndo())
            {
                save(session);
            }

            Map<String, Object> ack = new LinkedHashMap<>();
            if (version != before)
            {
                recalculate(session);
            }

            ack.put("type", "atom.patch.ack");
            ack.put("sequence", sequence);
            ack.put("version", version);
            ack.put("status", "applied");
            ack.put("saved_molecule_id", session.savedMoleculeId());
            ack.put("can_undo", session.canUndo());
            ack.put("can_redo", session.canRedo());
            context.sendJson(ack);
        }
    }

    /**
     * Moves and turns the molecule as a whole.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void transform(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.TransformRequest request;
        try
        {
            request = payload(body, StudioRequests.TransformRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("molecule.transform", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.position() == null || request.rotation() == null)
        {
            context.error("molecule.transform", sequence, "INVALID_MESSAGE", "position and rotation are required");
            return;
        }

        StudioSession session = current(context, "molecule.transform", sequence, request.sessionId(), request.baseVersion());
        if (session == null)
        {
            return;
        }

        boolean commit = request.commit() == null || request.commit();
        synchronized (session)
        {
            long before = session.version();
            long version;
            try
            {
                version = session.transform(request.pose(), commit);
            }
            catch (StudioEditException refused)
            {
                context.error("molecule.transform", sequence, refused.code(), refused.getMessage());
                return;
            }

            // an edit in progress is not yet something worth keeping
            if (commit && session.canUndo())
            {
                save(session);
            }

            Map<String, Object> ack = new LinkedHashMap<>();
            if (version != before)
            {
                recalculate(session);
            }

            ack.put("type", "molecule.transform.ack");
            ack.put("sequence", sequence);
            ack.put("version", version);
            ack.put("status", "applied");
            ack.put("saved_molecule_id", session.savedMoleculeId());
            ack.put("can_undo", session.canUndo());
            ack.put("can_redo", session.canRedo());
            context.sendJson(ack);
        }
    }

    /**
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void camera(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.CameraRequest request;
        try
        {
            request = payload(body, StudioRequests.CameraRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("camera.set", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("camera.set", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        Camera camera;
        try
        {
            camera = request.camera();
        }
        catch (IllegalArgumentException unusable)
        {
            context.error("camera.set", sequence, "INVALID_MESSAGE", unusable.getMessage());
            return;
        }

        synchronized (session)
        {
            session.setCamera(camera);
            save(session);

            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("type", "camera.set.ack");
            ack.put("sequence", sequence);
            ack.put("saved_molecule_id", session.savedMoleculeId());
            context.sendJson(ack);
        }
    }

    /**
     * Locks or unlocks atoms.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void lock(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.LockRequest request;
        try
        {
            request = payload(body, StudioRequests.LockRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("atom.lock", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.atoms() == null || request.atoms().isEmpty() || request.atoms().contains(null) || request.locked() == null)
        {
            context.error("atom.lock", sequence, "INVALID_MESSAGE",
                    "atoms are required, and locked says whether to lock or unlock them");
            return;
        }

        StudioSession session = current(context, "atom.lock", sequence, request.sessionId(), request.baseVersion());
        if (session == null)
        {
            return;
        }

        synchronized (session)
        {
            long before = session.version();
            long version;
            try
            {
                version = session.lock(request.atoms(), request.locked());
            }
            catch (StudioEditException refused)
            {
                context.error("atom.lock", sequence, refused.code(), refused.getMessage());
                return;
            }

            // what is locked is kept with the molecule, so a change to it is worth saving as an edit is
            if (version != before)
            {
                save(session);
                recalculate(session);
            }

            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("type", "atom.lock.ack");
            ack.put("sequence", sequence);
            ack.put("version", version);
            ack.put("locked", List.copyOf(session.locked()));
            ack.put("saved_molecule_id", session.savedMoleculeId());
            context.sendJson(ack);
        }
    }

    /**
     * Starts relaxing the geometry. The answer says only that it has started; progress and the result follow, from
     * another thread, as {@code optimise.progress} and {@code optimise.result}.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void optimise(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.OptimiseRequest request;
        try
        {
            request = payload(body, StudioRequests.OptimiseRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("optimise.start", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = current(context, "optimise.start", sequence, request.sessionId(), request.baseVersion());
        if (session == null)
        {
            return;
        }

        synchronized (session)
        {
            // an edit may have landed since the check above, and the optimisation must be of the version the client saw
            if (session.version() != request.baseVersion())
            {
                conflict(context, sequence, session, request.baseVersion());
                return;
            }

            OptimisationRunner.Operation operation;
            try
            {
                operation = optimiser.start(session, new OptimisationRunner.Listener()
                {
                    @Override
                    public void progress(OptimisationRunner.Operation operation, int iteration, double energy)
                    {
                        Map<String, Object> progress = new LinkedHashMap<>();
                        progress.put("type", "optimise.progress");
                        progress.put("session_id", session.sessionId());
                        progress.put("operation_id", operation.id());
                        progress.put("iteration", iteration);
                        progress.put("energy", energy);
                        sendToHolder(session, progress);
                    }

                    @Override
                    public void finished(OptimisationRunner.Operation operation, OptimisationRunner.Result result)
                    {
                        optimised(session, operation, result);
                    }
                });
            }
            catch (StudioEditException refused)
            {
                context.error("optimise.start", sequence, refused.code(), refused.getMessage());
                return;
            }

            Map<String, Object> started = new LinkedHashMap<>();
            started.put("type", "optimise.started");
            started.put("sequence", sequence);
            started.put("operation_id", operation.id());
            started.put("version", operation.version());
            context.sendJson(started);
        }
    }

    /**
     * Asks for the partial charges of a session's atoms, as the geometry is when they have been calculated. The answer
     * says only that the request was understood; {@code charges.progress} follows while they are worked out, and the
     * charges as {@code charges.result}, from another thread. Both are dropped if the session has moved on by then.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestCharges(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.ChargesRequest request;
        try
        {
            request = payload(body, StudioRequests.ChargesRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("charges.request", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("charges.request", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        String model = request.model() == null ? ChargeModels.DEFAULT : request.model();
        if (!ChargeModels.exists(model))
        {
            context.error("charges.request", sequence, "UNKNOWN_MODEL",
                    "No charge model is called '" + model + "'; use one of " + ChargeModels.IDS);
            return;
        }

        charges.schedule(session, model, progress -> sendToHolder(session, progress), report -> sendToHolder(session, report));

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "charges.request.ack");
        ack.put("sequence", sequence);
        context.sendJson(ack);
    }

    /**
     * Asks for the forces on a session's atoms, as the geometry is when they have been calculated. The answer says only
     * that the request was understood; the forces follow, from another thread, as {@code forces.result}, which is dropped
     * if the session has moved on by then.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestForces(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.ForcesRequest request;
        try
        {
            request = payload(body, StudioRequests.ForcesRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("forces.request", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("forces.request", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        forces.schedule(session, report -> sendToHolder(session, report));

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "forces.request.ack");
        ack.put("sequence", sequence);
        context.sendJson(ack);
    }

    /**
     * Asks for a scan of two of a session's molecules along a hydrogen bond, as the geometry is when it has been calculated.
     * The answer says only that the request was understood; the scan follows, from another thread, as {@code scan.result},
     * which is dropped if the session has moved on by then.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestScan(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.ScanRequest request;
        try
        {
            request = payload(body, StudioRequests.ScanRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("scan.request", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("scan.request", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        scans.schedule(session, report -> sendToHolder(session, report));

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "scan.request.ack");
        ack.put("sequence", sequence);
        context.sendJson(ack);
    }

    /**
     * Asks for the energy levels of a session's molecular orbitals, as the geometry is when they have been calculated. The
     * answer says only that the request was understood; the levels follow, from another thread, as {@code orbitals.result},
     * which is dropped if the session has moved on by then.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestOrbitals(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.OrbitalsRequest request;
        try
        {
            request = payload(body, StudioRequests.OrbitalsRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("orbitals.request", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("orbitals.request", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        orbitals.levels(session, request.model(), request.fragments(), report -> sendToHolder(session, report));

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "orbitals.request.ack");
        ack.put("sequence", sequence);
        context.sendJson(ack);
    }

    /**
     * Asks for a field over a session's space, such as the values of one orbital, as the geometry is when it has been
     * calculated. The answer says only that the request was understood; the field follows, from another thread, as
     * {@code field.result}, which is dropped if the session has moved on by then.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestField(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.FieldRequest request;
        try
        {
            request = payload(body, StudioRequests.FieldRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("field.request", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("field.request", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }
        List<StudioRequests.FieldTarget> targets = request.targets() == null ? List.of() : request.targets();
        if (!FieldReport.ORBITAL.equals(request.kind()) || targets.isEmpty() || targets.stream().anyMatch(target -> target.orbital() == null || target.spin() == null))
        {
            context.error("field.request", sequence, "UNKNOWN_FIELD",
                    "Only fields of kind '" + FieldReport.ORBITAL + "' can be calculated, and each target needs an orbital index and a spin");
            return;
        }

        orbitals.orbitals(session, request.model(),
                targets.stream().map(target -> new OrbitalScheduler.Target(target.fragment(), target.spin(), target.orbital())).toList(),
                reports -> reports.forEach(report -> sendToHolder(session, report)));

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "field.request.ack");
        ack.put("sequence", sequence);
        context.sendJson(ack);
    }

    /**
     * Asks for how the orbitals of everything in a session together are made of those of its molecules on their own. The answer
     * says only that the request was understood; the diagram follows, from another thread, as {@code interaction.result}, which is
     * dropped if the session has moved on by then.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestInteraction(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.InteractionRequest request;
        try
        {
            request = payload(body, StudioRequests.InteractionRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("interaction.request", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("interaction.request", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        orbitals.interaction(session, request.model(), report -> sendToHolder(session, report));

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "interaction.request.ack");
        ack.put("sequence", sequence);
        context.sendJson(ack);
    }

    /**
     * Asks for the electron density of a session, with its electrostatic potential, on a grid round its atoms. The answer says only that the
     * request was understood; the field follows, from another thread, as {@code esp.result}, which is dropped if the
     * session has moved on by then.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestEsp(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.EspRequest request;
        try
        {
            request = payload(body, StudioRequests.EspRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("esp.request", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("esp.request", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        orbitals.potential(session, request.model(), request.fragment(), request.multiplicity(), report -> sendToHolder(session, report));

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "esp.request.ack");
        ack.put("sequence", sequence);
        context.sendJson(ack);
    }

    /**
     * Asks for the reduced density gradient of a session, with the density signed by its curvature, on a grid round its atoms. The answer says
     * only that the request was understood; the fields follow, from another thread, as {@code nci.result}, which is dropped if the session has
     * moved on by then.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestNci(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.DensityRequest request;
        try
        {
            request = payload(body, StudioRequests.DensityRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("nci.request", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("nci.request", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        orbitals.contacts(session, request.model(), request.fragment(), request.multiplicity(), report -> sendToHolder(session, report));

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "nci.request.ack");
        ack.put("sequence", sequence);
        context.sendJson(ack);
    }

    /**
     * Asks for the electron density of a session, on a grid round its atoms. The answer says only that the request was understood; the
     * field follows, from another thread, as {@code density.result}, which is dropped if the session has moved on by then.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestDensity(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        densityRequest(context, body, sequence, "density.request",
                (session, request) -> orbitals.density(session, request.model(), request.fragment(), request.multiplicity(), report -> sendToHolder(session, report)));
    }

    /**
     * Asks for the spin density of a session, on a grid round its atoms.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestSpin(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        densityRequest(context, body, sequence, "spin.request",
                (session, request) -> orbitals.spin(session, request.model(), request.fragment(), request.multiplicity(), report -> sendToHolder(session, report)));
    }

    /**
     * Asks for minus the Laplacian of the electron density of a session, on a grid round its atoms.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestLaplacian(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        densityRequest(context, body, sequence, "laplacian.request",
                (session, request) -> orbitals.laplacian(session, request.model(), request.fragment(), request.multiplicity(), report -> sendToHolder(session, report)));
    }

    /**
     * Asks for the electron localisation function of a session, on a grid round its atoms.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestElf(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        densityRequest(context, body, sequence, "elf.request",
                (session, request) -> orbitals.localisation(session, request.model(), request.fragment(), request.multiplicity(), report -> sendToHolder(session, report)));
    }

    /**
     * Asks for the difference density of a session, on a grid round its atoms.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestDifference(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        densityRequest(context, body, sequence, "difference.request",
                (session, request) -> orbitals.difference(session, request.model(), request.multiplicity(), report -> sendToHolder(session, report)));
    }

    /**
     * Asks for the electrostatic potential of a session on a flat grid across its molecules. The answer says only that the request was
     * understood; the field follows, from another thread, as {@code plane.result}, which is dropped if the session has moved on by then or
     * a newer request for a plane has been made.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void requestPlane(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.PlaneRequest request;
        try
        {
            request = payload(body, StudioRequests.PlaneRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("plane.request", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error("plane.request", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        orbitals.plane(session, request.model(), request.fragment(), request.multiplicity(), request.axis(),
                request.position() == null ? 0.5 : request.position(), report -> sendToHolder(session, report));

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "plane.request.ack");
        ack.put("sequence", sequence);
        context.sendJson(ack);
    }

    /**
     * Reads a request for some density.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @param requestType the type of the message, whose acknowledgement is the same with {@code .ack} after it
     * @param schedule asks the scheduler for the density
     * @throws IOException if the connection cannot be written
     */
    private void densityRequest(WebSocketContext context, Map<String, Object> body, Long sequence, String requestType,
                                BiConsumer<StudioSession, StudioRequests.DensityRequest> schedule) throws IOException
    {
        StudioRequests.DensityRequest request;
        try
        {
            request = payload(body, StudioRequests.DensityRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error(requestType, sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            context.error(requestType, sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        schedule.accept(session, request);

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", requestType + ".ack");
        ack.put("sequence", sequence);
        context.sendJson(ack);
    }

    /**
     * Stops the session's optimisation, which then ends with a {@code cancelled} result.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void cancelOptimise(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.CancelOptimiseRequest request;
        try
        {
            request = payload(body, StudioRequests.CancelOptimiseRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("optimise.cancel", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.sessionId() == null)
        {
            context.error("optimise.cancel", sequence, "INVALID_MESSAGE", "session_id is required");
            return;
        }

        boolean stopping = optimiser.cancel(request.sessionId(), "Cancelled");

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "optimise.cancel.ack");
        ack.put("sequence", sequence);
        ack.put("cancelling", stopping);
        context.sendJson(ack);
    }

    /**
     * Keeps and reports what an optimisation did.
     *
     * @param session the session that was optimised
     * @param operation the optimisation
     * @param result how it ended, with any change already made to the session
     */
    private void optimised(StudioSession session, OptimisationRunner.Operation operation, OptimisationRunner.Result result)
    {
        synchronized (session)
        {
            // only a result that moved atoms is an edit, and so worth keeping and examining
            if (result.status().equals(OptimisationRunner.COMPLETE))
            {
                save(session);
                recalculate(session);
            }

            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("type", "optimise.result");
            reply.put("session_id", session.sessionId());
            reply.put("operation_id", operation.id());
            reply.put("base_version", operation.version());
            reply.put("status", result.status());
            reply.put("version", result.version());
            reply.put("can_undo", session.canUndo());
            reply.put("can_redo", session.canRedo());
            reply.put("saved_molecule_id", session.savedMoleculeId());
            reply.put("atoms", result.moved().entrySet().stream()
                    .map(moved -> Map.of("id", moved.getKey(), "position", moved.getValue()))
                    .toList());
            reply.put("strain_before", result.strainBefore());
            reply.put("strain_after", result.strainAfter());
            reply.put("iterations", result.iterations());
            reply.put("energy_history", result.history().stream()
                    .map(step -> Map.of("iteration", step.iteration(), "energy", step.energy()))
                    .toList());
            reply.put("warnings", result.warnings());
            reply.put("message", result.message());
            sendToHolder(session, reply);
        }
    }

    /**
     * Takes back the latest edit.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void undo(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        walk(context, body, sequence, "edit.undo", StudioSession::undo);
    }

    /**
     * Makes the latest undone edit again.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void redo(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        walk(context, body, sequence, "edit.redo", StudioSession::redo);
    }

    /**
     * Forgets every edit in a session's history.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void clearHistory(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        walk(context, body, sequence, "history.clear", StudioSession::clearHistory);
    }

    /**
     * Gives a bond a length.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void bondLength(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.BondLengthRequest request;
        try
        {
            request = payload(body, StudioRequests.BondLengthRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("bond.length", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.bond() == null || request.length() == null)
        {
            context.error("bond.length", sequence, "INVALID_MESSAGE", "bond and length are required");
            return;
        }

        reshape(context, "bond.length", sequence, request.sessionId(), request.baseVersion(),
                request.commit() == null || request.commit(),
                (session, commit) -> session.setBondLength(request.bond(), request.length(), request.hold(), commit));
    }

    /**
     * Gives a bond a torsion.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void bondTorsion(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.BondTorsionRequest request;
        try
        {
            request = payload(body, StudioRequests.BondTorsionRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("bond.torsion", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.bond() == null || request.from() == null || request.to() == null || request.angle() == null)
        {
            context.error("bond.torsion", sequence, "INVALID_MESSAGE", "bond, from, to and angle are required");
            return;
        }

        reshape(context, "bond.torsion", sequence, request.sessionId(), request.baseVersion(),
                request.commit() == null || request.commit(),
                (session, commit) -> session.setBondTorsion(request.bond(), request.from(), request.to(), request.angle(), request.hold(), commit));
    }

    /**
     * Gives an atom's two bonds an angle.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void angle(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.AngleRequest request;
        try
        {
            request = payload(body, StudioRequests.AngleRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("atom.angle", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.centre() == null || request.from() == null || request.to() == null || request.angle() == null)
        {
            context.error("atom.angle", sequence, "INVALID_MESSAGE", "centre, from, to and angle are required");
            return;
        }

        reshape(context, "atom.angle", sequence, request.sessionId(), request.baseVersion(),
                request.commit() == null || request.commit(),
                (session, commit) -> session.setAngle(request.centre(), request.from(), request.to(), request.angle(), request.hold(), commit));
    }

    /**
     * Changes an atom's non-geometric peoperties.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void setAtom(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.AtomSetRequest request;
        try
        {
            request = payload(body, StudioRequests.AtomSetRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("atom.set", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.atom() == null)
        {
            context.error("atom.set", sequence, "INVALID_MESSAGE", "atom is required");
            return;
        }

        graphEdit(context, "atom.set", sequence, request.sessionId(), request.baseVersion(),
                session -> noneCreated(session.setAtom(request.atom(), request.element(), request.charge(), request.isotope())));
    }

    /**
     * Adds an atom, bonded to an existing one or free.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void addAtom(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.AtomAddRequest request;
        try
        {
            request = payload(body, StudioRequests.AtomAddRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("atom.add", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.element() == null || (request.attach() == null && request.position() == null))
        {
            context.error("atom.add", sequence, "INVALID_MESSAGE", "element is required, and attach, position or both");
            return;
        }

        graphEdit(context, "atom.add", sequence, request.sessionId(), request.baseVersion(),
                session -> session.addAtom(request.element(), request.attach(), request.order(), request.position()));
    }

    /**
     * Deletes atoms, and the bonds they are in.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void deleteAtoms(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.AtomDeleteRequest request;
        try
        {
            request = payload(body, StudioRequests.AtomDeleteRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("atom.delete", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.atoms() == null || request.atoms().isEmpty() || request.atoms().contains(null))
        {
            context.error("atom.delete", sequence, "INVALID_MESSAGE", "atoms are required");
            return;
        }

        graphEdit(context, "atom.delete", sequence, request.sessionId(), request.baseVersion(),
                session -> noneCreated(session.deleteAtoms(request.atoms(), request.hydrogens() != null && request.hydrogens())));
    }

    /**
     * Gives atoms the hydrogens that fill their valence.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void fillValences(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.AtomFillRequest request;
        try
        {
            request = payload(body, StudioRequests.AtomFillRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("atom.fill", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.atoms() == null || request.atoms().isEmpty() || request.atoms().contains(null))
        {
            context.error("atom.fill", sequence, "INVALID_MESSAGE", "atoms are required");
            return;
        }

        graphEdit(context, "atom.fill", sequence, request.sessionId(), request.baseVersion(), session -> session.fillValences(request.atoms()));
    }

    /**
     * Bonds two atoms.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void addBond(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.BondAddRequest request;
        try
        {
            request = payload(body, StudioRequests.BondAddRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("bond.add", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.first() == null || request.second() == null)
        {
            context.error("bond.add", sequence, "INVALID_MESSAGE", "first and second are required");
            return;
        }

        graphEdit(context, "bond.add", sequence, request.sessionId(), request.baseVersion(),
                session -> session.addBond(request.first(), request.second(), request.order()));
    }

    /**
     * Deletes bonds.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void deleteBonds(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.BondDeleteRequest request;
        try
        {
            request = payload(body, StudioRequests.BondDeleteRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("bond.delete", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.bonds() == null || request.bonds().isEmpty() || request.bonds().contains(null))
        {
            context.error("bond.delete", sequence, "INVALID_MESSAGE", "bonds are required");
            return;
        }

        graphEdit(context, "bond.delete", sequence, request.sessionId(), request.baseVersion(),
                session -> noneCreated(session.deleteBonds(request.bonds())));
    }

    /**
     * Gives a bond an order.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void bondOrder(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.BondOrderRequest request;
        try
        {
            request = payload(body, StudioRequests.BondOrderRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("bond.order", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.named().isEmpty() || request.named().contains(null) || request.order() == null)
        {
            context.error("bond.order", sequence, "INVALID_MESSAGE", "bond or bonds, and order, are required");
            return;
        }

        graphEdit(context, "bond.order", sequence, request.sessionId(), request.baseVersion(),
                session -> noneCreated(session.setBondOrders(request.named(), request.order())));
    }

    /**
     * @param applied an edit that made no atom or bond
     * @return it, as an edit of the graph that created nothing
     */
    private static StudioSession.Created noneCreated(StudioSession.Applied applied)
    {
        return new StudioSession.Created(applied, List.of(), List.of());
    }

    /**
     * Makes an edit of the graph.
     *
     * @param context the connection that asked
     * @param type the type of the message, for the reply, which is this followed by {@code .ack}
     * @param sequence the message's sequence number, if it had one
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at
     * @param edit what to do to the session
     * @throws IOException if the connection cannot be written
     */
    private void graphEdit(WebSocketContext context, String type, Long sequence, String sessionId, Long baseVersion, Function<StudioSession, StudioSession.Created> edit) throws IOException
    {
        StudioSession session = current(context, type, sequence, sessionId, baseVersion);
        if (session == null)
        {
            return;
        }

        synchronized (session)
        {
            long before = session.version();
            StudioSession.Created created;
            try
            {
                created = edit.apply(session);
            }
            catch (StudioEditException refused)
            {
                context.error(type, sequence, refused.code(), refused.getMessage());
                return;
            }

            if (created.applied().version() != before)
            {
                save(session);
                recalculate(session);
            }

            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("type", type + ".ack");
            reply.put("sequence", sequence);
            reply.put("version", created.applied().version());
            reply.put("status", "applied");
            reply.put("added_atoms", created.atoms());
            reply.put("added_bonds", created.bonds());
            reply.put("state", StudioState.of(session, null));
            context.sendJson(reply);
        }
    }

    /**
     * Makes an edit the session works out the atoms' new positions for.
     *
     * @param context the connection that asked
     * @param type the type of the message, for the reply, which is this followed by {@code .ack}
     * @param sequence the message's sequence number, if it had one
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at
     * @param commit whether this ends the edit it belongs to
     * @param edit what to do to the session, given whether it commits
     * @throws IOException if the connection cannot be written
     */
    private void reshape(WebSocketContext context, String type, Long sequence, String sessionId, Long baseVersion, boolean commit, BiFunction<StudioSession, Boolean, StudioSession.Applied> edit) throws IOException
    {
        StudioSession session = current(context, type, sequence, sessionId, baseVersion);
        if (session == null)
        {
            return;
        }

        synchronized (session)
        {
            long before = session.version();
            StudioSession.Applied applied;
            try
            {
                applied = edit.apply(session, commit);
            }
            catch (StudioEditException refused)
            {
                context.error(type, sequence, refused.code(), refused.getMessage());
                return;
            }

            // an edit in progress is not yet something worth keeping
            if (commit && session.canUndo())
            {
                save(session);
            }
            if (applied.version() != before)
            {
                recalculate(session);
            }

            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("type", type + ".ack");
            reply.put("sequence", sequence);
            reply.put("version", applied.version());
            reply.put("status", "applied");
            reply.put("can_undo", session.canUndo());
            reply.put("can_redo", session.canRedo());
            reply.put("saved_molecule_id", session.savedMoleculeId());
            reply.put("atoms", applied.moved().entrySet().stream()
                    .map(moved -> Map.of("id", moved.getKey(), "position", moved.getValue()))
                    .toList());
            context.sendJson(reply);
        }
    }

    /**
     * Steps a session back or forward through its history and answers with the atoms that moved.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @param type the type of the message, for the reply
     * @param step what to do to the session
     * @throws IOException if the connection cannot be written
     */
    private void walk(WebSocketContext context, Map<String, Object> body, Long sequence, String type, Function<StudioSession, StudioSession.Applied> step) throws IOException
    {
        StudioRequests.HistoryRequest request;
        try
        {
            request = payload(body, StudioRequests.HistoryRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error(type, sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        walk(context, sequence, type, request.sessionId(), request.baseVersion(), step);
    }

    /**
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void history(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.HistoryRequest request;
        try
        {
            request = payload(body, StudioRequests.HistoryRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("history.list", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = current(context, "history.list", sequence, request.sessionId(), request.baseVersion());
        if (session == null)
        {
            return;
        }

        StudioSession.History history = session.history();
        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("type", "history.list");
        reply.put("sequence", sequence);
        reply.put("version", session.version());
        reply.put("position", history.position());
        reply.put("entries", history.entries().stream()
                .map(entry -> Map.of("label", entry.label(), "at", entry.at()))
                .toList());
        context.sendJson(reply);
    }

    /**
     * Moves a session to a point in its history.
     *
     * @param context the connection that asked
     * @param body the message
     * @param sequence the message's sequence number, if it had one
     * @throws IOException if the connection cannot be written
     */
    private void rewind(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException
    {
        StudioRequests.RewindRequest request;
        try
        {
            request = payload(body, StudioRequests.RewindRequest.class);
        }
        catch (JsonException malformed)
        {
            context.error("history.rewind", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.position() == null)
        {
            context.error("history.rewind", sequence, "INVALID_MESSAGE", "position is required");
            return;
        }

        boolean discard = request.discard() != null && request.discard();
        walk(context, sequence, "history.rewind", request.sessionId(), request.baseVersion(), session -> session.rewind(request.position(), discard));
    }

    private void walk(WebSocketContext context, Long sequence, String type, String sessionId, Long baseVersion, Function<StudioSession, StudioSession.Applied> step) throws IOException
    {
        StudioSession session = current(context, type, sequence, sessionId, baseVersion);
        if (session == null)
        {
            return;
        }

        synchronized (session)
        {
            StudioSession.Applied applied;
            try
            {
                applied = step.apply(session);
            }
            catch (StudioEditException refused)
            {
                context.error(type, sequence, refused.code(), refused.getMessage());
                return;
            }

            save(session);
            recalculate(session);

            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("type", "edit.applied");
            reply.put("sequence", sequence);
            reply.put("version", applied.version());
            reply.put("can_undo", session.canUndo());
            reply.put("can_redo", session.canRedo());
            reply.put("saved_molecule_id", session.savedMoleculeId());
            reply.put("transform", applied.pose());
            reply.put("atoms", applied.moved().entrySet().stream()
                    .map(moved -> Map.of("id", moved.getKey(), "position", moved.getValue()))
                    .toList());
            // a step that changed the graph leaves the client's attributes and SMILES out of date, so it gets it all
            reply.put("state", applied.graph() ? StudioState.of(session, null) : null);
            context.sendJson(reply);
        }
    }

    /**
     * Asks for a session's geometry to be examined once it has been left alone, and the result sent to the connection
     * holding it. A session that has been replaced or has no holder is not sent one.
     *
     * @param session the session that has changed
     */
    private void recalculate(StudioSession session)
    {
        // a change to the structure leaves an optimisation made from the one before it with nothing to apply to
        optimiser.supersede(session);
        geometry.schedule(session, report -> sendToHolder(session, report));
    }

    /**
     * Sends a message to the connection holding a session, unless the session has been replaced or has no holder.
     *
     * @param session the session the message is about
     * @param message the message
     */
    private void sendToHolder(StudioSession session, Object message)
    {
        WebSocketContext holder = holders.get(session.sessionId());
        if (holder == null || sessions.find(session.sessionId()).orElse(null) != session)
        {
            return;
        }

        try
        {
            holder.sendJson(message);
        }
        catch (IOException closed)
        {
            // the connection is gone, and the client will resync and be sent the state again
        }
    }

    /**
     * @param session the session to save, which the caller holds the lock of
     */
    private void save(StudioSession session)
    {
        StoredMolecule stored = session.savedMoleculeId() == null
                ? null
                : molecules.find(session.savedMoleculeId()).orElse(null);
        if (stored == null)
        {
            stored = molecules.add(ParsedMolecule.written(session.collapsed()));
            session.saved(stored.id());
        }
        else if (stored.molecule() != session.collapsed())
        {
            // the session's graph has changed since this was stored, so what is stored must change with it
            stored = molecules.replace(stored.id(), ParsedMolecule.written(session.collapsed())).orElse(stored);
        }

        stored.setStudio(new StudioSnapshot(
                session.structure(),
                session.lockedSites(),
                session.pose(),
                session.camera().orElse(stored.studio().camera())));
    }

    /**
     * Finds the session a mutation is for and checks the mutation was made on its current version.
     * When it cannot be used, replies with the error or conflict that says why.
     *
     * @param context the connection that asked
     * @param type the type of the message, for the reply
     * @param sequence the message's sequence number, if it had one
     * @param sessionId the session the message names, if it did
     * @param baseVersion the version the message was based on, if it said
     * @return the session, or {@code null} if a reply has already been sent
     * @throws IOException if the connection cannot be written
     */
    private StudioSession current(WebSocketContext context, String type, Long sequence, String sessionId, Long baseVersion) throws IOException
    {
        if (sessionId == null || baseVersion == null)
        {
            context.error(type, sequence, "INVALID_MESSAGE", "session_id and base_version are required");
            return null;
        }

        StudioSession session = sessions.find(sessionId).orElse(null);
        if (session == null)
        {
            context.error(type, sequence, "NO_SESSION",
                    "No session is open as '" + sessionId + "'; open one on a molecule");
            return null;
        }

        synchronized (session)
        {
            if (baseVersion != session.version())
            {
                conflict(context, sequence, session, baseVersion);
                return null;
            }
        }

        return session;
    }

    /**
     * Tells a client its message was made on a version the session has moved on from, with the state to adopt instead.
     *
     * @param context the connection that asked
     * @param sequence the message's sequence number, if it had one
     * @param session the session, which the caller holds the lock of
     * @param baseVersion the version the message was based on
     * @throws IOException if the connection cannot be written
     */
    private static void conflict(WebSocketContext context, Long sequence, StudioSession session, long baseVersion) throws IOException
    {
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("type", "conflict");
        conflict.put("sequence", sequence);
        conflict.put("expected_version", session.version());
        conflict.put("base_version", baseVersion);
        conflict.put("state", StudioState.of(session, null));
        context.sendJson(conflict);
    }

    /**
     * @param body the message
     * @param type the record its payload should fit
     * @param <T> the record type
     * @return the payload
     * @throws JsonException if the payload does not fit {@code type}
     */
    private static <T extends Record> T payload(Map<String, Object> body, Class<T> type)
    {
        Map<String, Object> fields = new LinkedHashMap<>(body);
        fields.remove("type");
        fields.remove("sequence");
        fields.remove("protocol_version");
        return RecordConverter.bind(fields, type, JsonNames::of);
    }

    /**
     * Notes that this connection is holding a session, letting go of whichever it held before.
     *
     * @param context the connection
     * @param session the session it is now holding
     */
    private void hold(WebSocketContext context, StudioSession session)
    {
        Object previous = context.attributes().put(HELD, session.sessionId());
        if (previous != null && !previous.equals(session.sessionId()))
        {
            sessions.detach((String) previous, context.id());
        }

        sessions.attach(session.sessionId(), context.id());
        holders.put(session.sessionId(), context);
    }

    /**
     * Starts the grace window of whichever session this connection was holding.
     *
     * @param context the connection
     */
    private void release(WebSocketContext context)
    {
        Object held = context.attributes().remove(HELD);
        if (held != null)
        {
            sessions.detach((String) held, context.id());
            holders.remove((String) held, context);
        }
    }

    /**
     * One Studio connection.
     */
    private static final class StudioSocket extends TypedJsonSocketListener
    {
        private final StudioSocketEndpoint endpoint;

        private StudioSocket(StudioSocketEndpoint endpoint)
        {
            super(Map.<String, MessageHandler>ofEntries(
                    Map.entry("session.open", endpoint::open),
                    Map.entry("session.resync", endpoint::resync),
                    Map.entry("smiles.submit", endpoint::submitSmiles),
                    Map.entry("atom.patch", endpoint::patch),
                    Map.entry("molecule.transform", endpoint::transform),
                    Map.entry("camera.set", endpoint::camera),
                    Map.entry("atom.lock", endpoint::lock),
                    Map.entry("bond.length", endpoint::bondLength),
                    Map.entry("bond.torsion", endpoint::bondTorsion),
                    Map.entry("atom.angle", endpoint::angle),
                    Map.entry("atom.set", endpoint::setAtom),
                    Map.entry("atom.add", endpoint::addAtom),
                    Map.entry("atom.delete", endpoint::deleteAtoms),
                    Map.entry("atom.fill", endpoint::fillValences),
                    Map.entry("bond.add", endpoint::addBond),
                    Map.entry("bond.delete", endpoint::deleteBonds),
                    Map.entry("bond.order", endpoint::bondOrder),
                    Map.entry("optimise.start", endpoint::optimise),
                    Map.entry("optimise.cancel", endpoint::cancelOptimise),
                    Map.entry("charges.request", endpoint::requestCharges),
                    Map.entry("forces.request", endpoint::requestForces),
                    Map.entry("scan.request", endpoint::requestScan),
                    Map.entry("orbitals.request", endpoint::requestOrbitals),
                    Map.entry("field.request", endpoint::requestField),
                    Map.entry("interaction.request", endpoint::requestInteraction),
                    Map.entry("esp.request", endpoint::requestEsp),
                    Map.entry("density.request", endpoint::requestDensity),
                    Map.entry("nci.request", endpoint::requestNci),
                    Map.entry("laplacian.request", endpoint::requestLaplacian),
                    Map.entry("elf.request", endpoint::requestElf),
                    Map.entry("spin.request", endpoint::requestSpin),
                    Map.entry("difference.request", endpoint::requestDifference),
                    Map.entry("plane.request", endpoint::requestPlane),
                    Map.entry("edit.undo", endpoint::undo),
                    Map.entry("edit.redo", endpoint::redo),
                    Map.entry("history.list", endpoint::history),
                    Map.entry("history.rewind", endpoint::rewind),
                    Map.entry("history.clear", endpoint::clearHistory)));
            this.endpoint = endpoint;
        }

        /**
         * Refuses a message that was not written for this server's protocol.
         */
        @Override
        protected boolean accepts(WebSocketContext context, Map<String, Object> body, String type, Long sequence) throws IOException
        {
            Object sent = body.get("protocol_version");
            if (sent instanceof Number number && number.doubleValue() == PROTOCOL_VERSION)
            {
                return true;
            }

            String what = sent instanceof Number number
                    ? (number.doubleValue() < PROTOCOL_VERSION
                            ? "is out of date: reload the page"
                            : "is newer than this server: restart the server")
                    : "did not name its protocol_version: reload the page";
            context.error(type, sequence, "PROTOCOL_MISMATCH",
                    "This server speaks protocol " + PROTOCOL_VERSION + ", and the client " + what);
            return false;
        }

        @Override
        public void onOpen(WebSocketContext context) throws IOException
        {
            context.sendJson(Map.of(
                    "type", "session.ready",
                    "connection_id", context.id(),
                    "protocol_version", PROTOCOL_VERSION,
                    "max_message_bytes", DEFAULT_MAX_MESSAGE));
        }

        @Override
        public void onClose(WebSocketContext context, CloseCode closeCode, String reason)
        {
            endpoint.release(context);
        }
    }
}
