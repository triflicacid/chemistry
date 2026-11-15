package com.ruben.chemistry.server.ws;

import com.ruben.chemistry.server.json.JsonWriter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Send on a WebSocket connection, and read what it was opened with.
 */
public interface WebSocketContext
{
    /**
     * @return this connection's identifier, unique among the connections this server has served
     */
    String id();

    /**
     * @return the path the connection was opened on
     */
    String path();

    /**
     * @return the values of the {@code :name} segments of the route that matched
     */
    Map<String, String> pathParameters();

    /**
     * @param name a {@code :name} segment of the route
     * @return its value
     * @throws IllegalArgumentException if the route has no such segment
     */
    String pathParameter(String name);

    /**
     * @return the query parameters the connection was opened with
     */
    Map<String, String> query();

    /**
     * @param name a query parameter
     * @return its value, if given
     */
    Optional<String> queryParameter(String name);

    /**
     * @param name a header name, in any case
     * @return its first value on the upgrade request, if sent
     */
    Optional<String> header(String name);

    /**
     * State kept for this connection, for an endpoint to use as it needs, such as which
     * molecule is being edited and the version last acknowledged. Safe to read and write
     * from any thread.
     *
     * @return the connection's mutable attributes
     */
    Map<String, Object> attributes();

    /**
     * @return whether the connection is still able to send
     */
    boolean isOpen();

    /**
     * Sends a text message.
     *
     * @param text the message text
     * @throws IOException if the connection is closed or cannot be written
     */
    void send(String text) throws IOException;

    /**
     * Sends a value as a JSON text message.
     *
     * @param value what to send; a {@link Map} or {@link Record} becomes a JSON object, see {@link JsonWriter#write}
     * @throws IOException if the connection is closed or cannot be written
     */
    void sendJson(Object value) throws IOException;

    /**
     * Sends a binary message.
     *
     * @param payload the message bytes
     * @throws IOException if the connection is closed or cannot be written
     */
    void sendBinary(byte[] payload) throws IOException;

    /**
     * Sends {@code {"type": "error", "error": {"code": ..., "message": ...}}}.
     * The connection stays open.
     *
     * @param code a short machine-readable reason
     * @param message a human-readable explanation
     * @throws IOException if the connection is closed or cannot be written
     */
    void error(String code, String message) throws IOException;

    /**
     * Like {@link #error(String, String)}, but names the message being answered so a client
     * can match the error to what it sent.
     *
     * @param type the type of the message the error answers, echoed as {@code answering}; {@code null} if it was unreadable
     * @param sequence the sequence number of the message the error answers, echoed as {@code sequence}; {@code null} if it had none
     * @param code a short machine-readable reason
     * @param message a human-readable explanation
     * @throws IOException if the connection is closed or cannot be written
     */
    void error(String type, Long sequence, String code, String message) throws IOException;

    /**
     * Sends a ping, which a well-behaved peer answers with a pong. The connection pings on
     * its own when it falls idle, so an endpoint rarely needs this.
     *
     * @param payload up to {@link Frame#MAX_CONTROL_PAYLOAD} bytes, echoed back in the pong
     * @throws IOException if the connection is closed or cannot be written
     * @throws IllegalArgumentException if the payload is too large for a control frame
     */
    void ping(byte[] payload) throws IOException;

    /**
     * Closes the connection normally.
     */
    void close();

    /**
     * Starts the closing handshake, then stops the connection. Calling this more than once,
     * or on an already closed connection, does nothing.
     *
     * @param closeCode why the connection is closing
     * @param reason a human-readable explanation for the peer
     */
    void close(CloseCode closeCode, String reason);
}
