package com.ruben.chemistry.server.ws;

import java.io.IOException;

/**
 * Answers what happens on one WebSocket connection:
 * {@link #onMessage} reads a {@link WebSocketMessage} and replies through a {@link WebSocketContext}.
 * <p>
 * A listener is created per connection, and every method is on its thread.
 */
public interface WebSocketListener
{
    /**
     * Called once, after the handshake completes and before any message is read.
     *
     * @param context the connection
     * @throws Exception to close the connection
     */
    default void onOpen(WebSocketContext context) throws Exception
    {
    }

    /**
     * Called for each complete message, text or binary.
     *
     * @param message what arrived
     * @param context the connection it arrived on, and how to answer
     * @throws Exception to close the connection
     */
    default void onMessage(WebSocketMessage message, WebSocketContext context) throws Exception
    {
    }

    /**
     * Called once the connection has closed, however it closed, including when it was lost
     * without a closing handshake. Nothing can be sent on the context any more.
     *
     * @param context the connection
     * @param closeCode why it closed; {@link CloseCode#ABNORMAL} if the connection was lost
     * @param reason the reason the peer gave, or an empty string if it gave none
     */
    default void onClose(WebSocketContext context, CloseCode closeCode, String reason)
    {
    }

    /**
     * Called when this listener, or the connection itself, failed. The connection is closed
     * afterwards, and {@link #onClose} still follows.
     *
     * @param context the connection
     * @param failure what went wrong
     */
    default void onError(WebSocketContext context, Throwable failure)
    {
    }
}
