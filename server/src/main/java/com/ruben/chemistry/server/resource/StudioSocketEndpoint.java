package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.ws.SocketEndpoint;
import com.ruben.chemistry.server.ws.SocketRegistry;
import com.ruben.chemistry.server.ws.TypedJsonSocketListener;
import com.ruben.chemistry.server.ws.WebSocketContext;

import java.io.IOException;
import java.util.Map;

import static com.ruben.chemistry.server.ws.WebSocketServer.DEFAULT_MAX_MESSAGE;

/**
 * The Studio editing channel: one WebSocket session per open editing workspace.
 * <p>
 * Message types are registered as they are implemented. Until then, unrecognised
 * messages receive a standard error response.
 */
public final class StudioSocketEndpoint extends SocketEndpoint
{
    /** Bumped when a message type changes shape, so a stale client can be told to reload. */
    private static final int PROTOCOL_VERSION = 1;

    @Override
    public void registerHandlers(SocketRegistry registry)
    {
        registry.register("/ws/studio", StudioSocket::new);
    }

    /**
     * One Studio editing session.
     */
    private static final class StudioSocket extends TypedJsonSocketListener
    {
        private StudioSocket()
        {
            super(Map.of());
        }

        @Override
        public void onOpen(WebSocketContext context) throws IOException
        {
            context.sendJson(Map.of(
                    "type", "session.ready",
                    "session_id", context.id(),
                    "protocol_version", PROTOCOL_VERSION,
                    "max_message_bytes", DEFAULT_MAX_MESSAGE));
        }
    }
}
