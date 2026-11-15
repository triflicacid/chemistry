package com.ruben.chemistry.server.ws;

import java.util.Optional;

/**
 * A WebSocket close status code (RFC 6455 section 7.4).
 */
public enum CloseCode
{
    /** The purpose of the connection has been fulfilled. */
    NORMAL(1000),
    /** The peer is going away, e.g. the server is shutting down. */
    GOING_AWAY(1001),
    /** The peer broke the protocol. */
    PROTOCOL_ERROR(1002),
    /** The peer sent a kind of data this endpoint does not accept. */
    UNSUPPORTED_DATA(1003),
    /** No status code was present in the close frame; never sent on the wire. */
    NO_STATUS(1005),
    /** The connection was lost without a closing handshake; never sent on the wire. */
    ABNORMAL(1006),
    /** A text frame's payload was not valid UTF-8. */
    INVALID_PAYLOAD(1007),
    /** The message broke a rule of this endpoint. */
    POLICY_VIOLATION(1008),
    /** The message was larger than this endpoint accepts. */
    TOO_BIG(1009),
    /** The endpoint hit an unexpected condition. */
    INTERNAL_ERROR(1011);

    private final int code;

    CloseCode(int code)
    {
        this.code = code;
    }

    /**
     * @param code a close status code as it appears on the wire
     * @return the matching code, or empty if it is one this server does not name
     */
    public static Optional<CloseCode> of(int code)
    {
        for (CloseCode close : values())
        {
            if (close.code == code)
            {
                return Optional.of(close);
            }
        }
        return Optional.empty();
    }

    /**
     * @return the status code as it appears on the wire
     */
    public int code()
    {
        return code;
    }

    /**
     * {@link #NO_STATUS} and {@link #ABNORMAL} describe how a connection ended rather
     * than something a peer can report, so RFC 6455 forbids putting them in a frame.
     *
     * @return whether this code may be sent in a close frame
     */
    public boolean isSendable()
    {
        return this != NO_STATUS && this != ABNORMAL;
    }
}
