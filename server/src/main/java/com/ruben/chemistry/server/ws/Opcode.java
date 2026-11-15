package com.ruben.chemistry.server.ws;

import java.util.Optional;

/**
 * A WebSocket frame's opcode (RFC 6455 section 5.2).
 */
public enum Opcode
{
    /** Carries the rest of a message whose first frame set the type. */
    CONTINUATION(0x0, false),
    /** A UTF-8 text message. */
    TEXT(0x1, false),
    /** A binary message. */
    BINARY(0x2, false),
    /** Starts or completes the closing handshake. */
    CLOSE(0x8, true),
    /** Asks the peer for a {@link #PONG}. */
    PING(0x9, true),
    /** Answers a {@link #PING}, or is sent unsolicited as a heartbeat. */
    PONG(0xA, true);

    private final int code;
    private final boolean control;

    Opcode(int code, boolean control)
    {
        this.code = code;
        this.control = control;
    }

    /**
     * @param code the four-bit opcode as it appears on the wire
     * @return the matching opcode, or empty if the code is reserved
     */
    public static Optional<Opcode> of(int code)
    {
        for (Opcode opcode : values())
        {
            if (opcode.code == code)
            {
                return Optional.of(opcode);
            }
        }
        return Optional.empty();
    }

    /**
     * @return the four-bit opcode as it appears on the wire
     */
    public int code()
    {
        return code;
    }

    /**
     * Control frames may be delivered in the middle of a fragmented message, cannot
     * themselves be fragmented, and cannot carry more than 125 bytes.
     *
     * @return whether this is a control opcode
     */
    public boolean isControl()
    {
        return control;
    }
}
