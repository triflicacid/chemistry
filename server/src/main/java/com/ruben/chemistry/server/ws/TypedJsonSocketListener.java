package com.ruben.chemistry.server.ws;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A WebSocket listener for JSON messages carrying a {@code type}.
 * <p>
 * Every incoming message must be a JSON object with a non-blank {@code type}.
 * Registered handlers receive the parsed message and its optional {@code sequence}.
 * Messages without a registered handler receive an error response.
 */
public abstract class TypedJsonSocketListener implements WebSocketListener
{
    private final Map<String, MessageHandler> handlers;

    /**
     * @param handlers the handlers registered for this socket's message types
     */
    protected TypedJsonSocketListener(Map<String, MessageHandler> handlers)
    {
        Objects.requireNonNull(handlers, "handlers");

        Map<String, MessageHandler> copy = new LinkedHashMap<>();
        handlers.forEach((type, handler) ->
        {
            if (type == null || type.isBlank())
            {
                throw new IllegalArgumentException("A message type cannot be blank");
            }

            copy.put(type, Objects.requireNonNull(handler, "handler"));
        });

        this.handlers = Map.copyOf(copy);
    }

    @Override
    public final void onMessage(WebSocketMessage message, WebSocketContext context) throws IOException
    {
        // a message this listener cannot read is answered, not fatal: one bad message from
        // a client should not cost the user their editing session
        Map<String, Object> body = message.asJson().orElse(null);
        if (body == null)
        {
            error(context, null, null, "INVALID_JSON", "Every message must be a JSON object");
            return;
        }

        Long sequence = sequence(body);

        String type = message.string("type").orElse("");
        if (type.isBlank())
        {
            error(context, null, sequence, "MISSING_TYPE", "Every message needs a 'type'");
            return;
        }

        // Handle special case here
        if ("ping".equals(type))
        {
            context.sendJson(reply("pong", sequence));
            return;
        }

        MessageHandler handler = handlers.get(type);
        if (handler == null)
        {
            error(context, type, sequence, "UNKNOWN_TYPE", "This server does not handle '" + type + "'");
            return;
        }

        handler.handle(context, body, sequence);
    }

    /**
     * Tells the client a message could not be handled, without closing the connection.
     *
     * @param context the connection to answer
     * @param type the type that failed, if it was readable
     * @param sequence the sequence number of the message that failed, if it had one
     * @param code a short machine-readable reason
     * @param message a human-readable explanation
     * @throws IOException if the connection cannot be written
     */
    public static void error(WebSocketContext context, String type, Long sequence, String code, String message)
            throws IOException
    {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", code);
        details.put("message", message);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "error");

        if (type != null)
        {
            envelope.put("answering", type);
        }
        if (sequence != null)
        {
            envelope.put("sequence", sequence);
        }

        envelope.put("error", details);
        context.sendJson(envelope);
    }

    /**
     * @param type the type being answered
     * @param sequence the sequence number of the message being answered, if it had one
     * @return a reply envelope
     */
    protected static Map<String, Object> reply(String type, Long sequence)
    {
        return sequence == null
                ? Map.of("type", type)
                : Map.of("type", type, "sequence", sequence);
    }

    /**
     * @param body a parsed message
     * @return its sequence number, or {@code null} if it carried none that is a whole number
     */
    private static Long sequence(Map<String, Object> body)
    {
        Object value = body.get("sequence");

        if (!(value instanceof Number number))
        {
            return null;
        }

        try
        {
            return new BigDecimal(number.toString()).longValueExact();
        }
        catch (NumberFormatException | ArithmeticException invalid)
        {
            return null;
        }
    }

    /**
     * Handles one registered message type.
     */
    @FunctionalInterface
    protected interface MessageHandler
    {
        /**
         * @param context the connection that sent the message
         * @param body the parsed message
         * @param sequence the message's optional sequence number
         * @throws IOException if the connection cannot be written
         */
        void handle(WebSocketContext context, Map<String, Object> body, Long sequence) throws IOException;
    }
}
