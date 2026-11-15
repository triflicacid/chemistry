package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.json.JsonException;
import com.ruben.chemistry.server.json.JsonNames;
import com.ruben.chemistry.server.json.RecordConverter;
import com.ruben.chemistry.server.molecule.MoleculeException;
import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.server.molecule.ParsedMolecule;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.server.studio.GeometryScheduler;
import com.ruben.chemistry.server.studio.OptimisationRunner;
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
import java.util.function.Function;

import static com.ruben.chemistry.server.ws.WebSocketServer.DEFAULT_MAX_MESSAGE;

/**
 * The Studio editing channel: one WebSocket connection per open editing workspace.
 */
public final class StudioSocketEndpoint extends SocketEndpoint
{
    /** Bumped when a message type changes shape, so a stale client can be told to reload. */
    private static final int PROTOCOL_VERSION = 8;

    /** Which session a connection is holding, if any. */
    private static final String HELD = "studio.session";

    private final MoleculeStore molecules;
    private final StudioSessionStore sessions;
    private final GeometryScheduler geometry;
    private final OptimisationRunner optimiser;
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

        StudioSession session = current(context, type, sequence, request.sessionId(), request.baseVersion());
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

        stored.setLatestStructure3d(session.structure());
        stored.setLockedSites(session.lockedSites());
        stored.setPose(session.pose());
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
            super(Map.of(
                    "session.open", endpoint::open,
                    "session.resync", endpoint::resync,
                    "smiles.submit", endpoint::submitSmiles,
                    "atom.patch", endpoint::patch,
                    "molecule.transform", endpoint::transform,
                    "atom.lock", endpoint::lock,
                    "optimise.start", endpoint::optimise,
                    "optimise.cancel", endpoint::cancelOptimise,
                    "edit.undo", endpoint::undo,
                    "edit.redo", endpoint::redo));
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
