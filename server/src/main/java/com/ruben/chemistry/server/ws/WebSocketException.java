package com.ruben.chemistry.server.ws;

/**
 * Ends a WebSocket connection with a close code.
 */
public final class WebSocketException extends RuntimeException
{
    private final CloseCode closeCode;

    /**
     * @param closeCode the code to close the connection with
     * @param message a human-readable explanation, sent as the close reason
     */
    public WebSocketException(CloseCode closeCode, String message)
    {
        super(message);
        this.closeCode = closeCode;
    }

    /**
     * @return the code to close the connection with
     */
    public CloseCode closeCode()
    {
        return closeCode;
    }
}
