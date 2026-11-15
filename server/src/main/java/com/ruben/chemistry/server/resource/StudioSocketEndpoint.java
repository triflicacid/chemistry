package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.json.JsonException;
import com.ruben.chemistry.server.json.JsonNames;
import com.ruben.chemistry.server.json.RecordConverter;
import com.ruben.chemistry.server.molecule.MoleculeException;
import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.ParsedMolecule;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.server.molecule.StoredMolecule;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.ruben.chemistry.server.ws.WebSocketServer.DEFAULT_MAX_MESSAGE;

/**
 * The Studio editing channel: one WebSocket connection per open editing workspace.
 */
public final class StudioSocketEndpoint extends SocketEndpoint
{
    /**
     * @param moleculeId the stored molecule to open a session on
     * @param conformers how many conformers to embed if it has no shape yet (optional, default one)
     */
    public record OpenRequest(String moleculeId, Integer conformers)
    {
    }

    /**
     * @param sessionId the session to send state for
     * @param sinceVersion the version the client holds, which it reports so a gap is visible in the logs
     */
    public record ResyncRequest(String sessionId, Long sinceVersion)
    {
    }

    /**
     * @param sessionId the session to rebuild
     * @param baseVersion the version the client was looking at when it wrote the SMILES, so that a rebuild over a changed session is refused
     * @param smiles the molecule to rebuild the session on, read as written
     */
    public record SubmitRequest(String sessionId, Long baseVersion, String smiles)
    {
    }

    /** Bumped when a message type changes shape, so a stale client can be told to reload. */
    private static final int PROTOCOL_VERSION = 2;

    /** Which session a connection is holding, if any. */
    private static final String HELD = "studio.session";

    private final MoleculeStore molecules;
    private final StudioSessionStore sessions;
    private final MoleculeParser parser = new MoleculeParser();

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
        this.molecules = Objects.requireNonNull(molecules, "molecules");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
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
        OpenRequest request;
        try
        {
            request = payload(body, OpenRequest.class);
        }
        catch (JsonException malformed)
        {
            TypedJsonSocketListener.error(context, "session.open", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.moleculeId() == null)
        {
            TypedJsonSocketListener.error(context, "session.open", sequence, "INVALID_MESSAGE", "molecule_id is required");
            return;
        }

        StoredMolecule stored = molecules.find(request.moleculeId()).orElse(null);
        if (stored == null)
        {
            TypedJsonSocketListener.error(context, "session.open", sequence, "UNKNOWN_MOLECULE",
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
            TypedJsonSocketListener.error(context, "session.open", sequence, "INVALID_MESSAGE", outOfRange.getMessage());
            return;
        }
        catch (IllegalStateException unembeddable)
        {
            TypedJsonSocketListener.error(context, "session.open", sequence, "UNSUPPORTED", unembeddable.getMessage());
            return;
        }

        sessions.add(session, context.id());
        hold(context, session);
        context.sendJson(StudioState.of(session, sequence));
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
        ResyncRequest request;
        try
        {
            request = payload(body, ResyncRequest.class);
        }
        catch (JsonException malformed)
        {
            TypedJsonSocketListener.error(context, "session.resync", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        StudioSession session = request.sessionId() == null
                ? null
                : sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            TypedJsonSocketListener.error(context, "session.resync", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        // a reconnecting client resyncs on a new connection, which takes over the session the
        // dropped one was holding and so stops its grace window
        hold(context, session);
        context.sendJson(StudioState.of(session, sequence));
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
        SubmitRequest request;
        try
        {
            request = payload(body, SubmitRequest.class);
        }
        catch (JsonException malformed)
        {
            TypedJsonSocketListener.error(context, "smiles.submit", sequence, "INVALID_MESSAGE", malformed.getMessage());
            return;
        }

        if (request.sessionId() == null || request.baseVersion() == null || request.smiles() == null)
        {
            TypedJsonSocketListener.error(context, "smiles.submit", sequence, "INVALID_MESSAGE",
                    "session_id, base_version and smiles are required");
            return;
        }

        StudioSession session = sessions.find(request.sessionId()).orElse(null);
        if (session == null)
        {
            TypedJsonSocketListener.error(context, "smiles.submit", sequence, "NO_SESSION",
                    "No session is open as '" + request.sessionId() + "'; open one on a molecule");
            return;
        }

        if (request.baseVersion() != session.version())
        {
            Map<String, Object> conflict = new LinkedHashMap<>();
            conflict.put("type", "conflict");
            conflict.put("sequence", sequence);
            conflict.put("expected_version", session.version());
            conflict.put("base_version", request.baseVersion());
            conflict.put("state", StudioState.of(session, null));
            return;
        }

        if (request.smiles().isBlank())
        {
            TypedJsonSocketListener.error(context, "smiles.submit", sequence, "INVALID_SMILES", "The SMILES is empty");
            return;
        }

        ParsedMolecule parsed;
        try
        {
            parsed = parser.parse(request.smiles());
        }
        catch (MoleculeException unreadable)
        {
            TypedJsonSocketListener.error(context, "smiles.submit", sequence, "INVALID_SMILES", unreadable.getMessage());
            return;
        }

        StudioSession rebuilt;
        try
        {
            rebuilt = session.rebuilt(parsed.molecule(), StudioSession.DEFAULT_CONFORMERS);
        }
        catch (IllegalStateException | IllegalArgumentException unembeddable)
        {
            TypedJsonSocketListener.error(context, "smiles.submit", sequence, "UNSUPPORTED", unembeddable.getMessage());
            return;
        }

        sessions.replace(rebuilt);
        context.sendJson(StudioState.of(rebuilt, sequence));
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
                    "smiles.submit", endpoint::submitSmiles));
            this.endpoint = endpoint;
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
