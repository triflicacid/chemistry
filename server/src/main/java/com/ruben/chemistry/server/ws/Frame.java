
package com.ruben.chemistry.server.ws;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * One WebSocket frame (RFC 6455 section 5.2).
 * <p>
 * {@link #read} decodes a frame sent by a client, unmasking its payload; {@link #write}
 * encodes one to send to a client, which is never masked.
 *
 * @param fin whether this frame completes its message
 * @param opcode what the frame carries
 * @param payload the unmasked payload, owned by this frame
 */
public record Frame(boolean fin, Opcode opcode, byte[] payload)
{
    /** The largest payload a control frame may carry, per RFC 6455 section 5.5. */
    public static final int MAX_CONTROL_PAYLOAD = 125;

    public Frame
    {
        Objects.requireNonNull(opcode, "opcode");
        Objects.requireNonNull(payload, "payload");

        payload = payload.clone();
    }

    /**
     * @return a defensive copy of this frame's payload
     */
    @Override
    public byte[] payload()
    {
        return payload.clone();
    }

    /**
     * @param text the message text
     * @return a complete text frame holding it as UTF-8
     */
    public static Frame text(String text)
    {
        return new Frame(true, Opcode.TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param payload the message bytes
     * @return a complete binary frame holding them
     */
    public static Frame binary(byte[] payload)
    {
        return new Frame(true, Opcode.BINARY, payload);
    }

    /**
     * @param closeCode why the connection is closing
     * @param reason a human-readable explanation, truncated to fit a control frame
     * @return a close frame holding the code and reason
     */
    public static Frame close(CloseCode closeCode, String reason)
    {
        Objects.requireNonNull(closeCode, "closeCode");

        byte[] text = reason == null ? new byte[0] : reason.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(text.length, MAX_CONTROL_PAYLOAD - 2);

        // Do not split a multi-byte UTF-8 character.
        while (length > 0 && length < text.length && (text[length] & 0xC0) == 0x80)
        {
            length--;
        }

        byte[] payload = new byte[2 + length];
        payload[0] = (byte) (closeCode.code() >>> 8);
        payload[1] = (byte) closeCode.code();
        System.arraycopy(text, 0, payload, 2, length);

        return new Frame(true, Opcode.CLOSE, payload);
    }

    /**
     * Reads the next frame, blocking until it has arrived in full.
     *
     * @param in the client's stream
     * @param maxPayload the largest single-frame payload accepted, in bytes
     * @return the frame read
     * @throws EOFException if the stream ends before the frame is complete
     * @throws IOException if the stream cannot be read
     * @throws WebSocketException if the frame breaks the protocol
     */
    public static Frame read(InputStream in, long maxPayload) throws IOException
    {
        Objects.requireNonNull(in, "in");

        if (maxPayload < 0)
        {
            throw new IllegalArgumentException("maxPayload cannot be negative");
        }

        int first = in.read();
        if (first < 0)
        {
            throw new EOFException("The connection ended between frames");
        }

        int second = require(in);

        boolean fin = (first & 0x80) != 0;
        if ((first & 0x70) != 0)
        {
            throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "No extension was negotiated, so the RSV bits must be zero");
        }

        Opcode opcode = Opcode.of(first & 0x0F)
                .orElseThrow(() -> new WebSocketException(CloseCode.PROTOCOL_ERROR, "Reserved opcode " + (first & 0x0F)));

        // The client half of a connection must mask, so that a hostile page cannot make a proxy read a crafted payload as a second request.
        boolean masked = (second & 0x80) != 0;
        if (!masked)
        {
            throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "A frame from a client must be masked");
        }

        long length = second & 0x7F;
        if (length == 126)
        {
            length = ((long) require(in) << 8) | require(in);

            if (length < 126)
            {
                throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "A payload length below 126 must use the short form");
            }
        }
        else if (length == 127)
        {
            length = 0;
            for (int i = 0; i < 8; i++)
            {
                length = (length << 8) | require(in);
            }

            if (length < 0)
            {
                throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "The payload length has its top bit set");
            }

            if (length <= 0xFFFF)
            {
                throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "A payload length below 65536 must use the 16-bit form");
            }
        }

        if (opcode.isControl())
        {
            if (!fin)
            {
                throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "A control frame cannot be fragmented");
            }
            if (length > MAX_CONTROL_PAYLOAD)
            {
                throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "A control frame cannot carry more than " + MAX_CONTROL_PAYLOAD + " bytes");
            }
        }

        if (length > maxPayload)
        {
            throw new WebSocketException(CloseCode.TOO_BIG, "A frame of " + length + " bytes exceeds the " + maxPayload + " byte limit");
        }

        if (length > Integer.MAX_VALUE)
        {
            throw new WebSocketException(CloseCode.TOO_BIG, "The frame payload is too large for a Java byte array");
        }

        byte[] mask = in.readNBytes(4);
        if (mask.length < 4)
        {
            throw new EOFException("The connection ended inside a frame's masking key");
        }

        byte[] payload = in.readNBytes((int) length);
        if (payload.length < length)
        {
            throw new EOFException("The connection ended inside a frame's payload");
        }

        for (int i = 0; i < payload.length; i++)
        {
            payload[i] ^= mask[i & 3];
        }

        Frame frame = new Frame(fin, opcode, payload);

        // Validate this close frame.
        if (opcode == Opcode.CLOSE)
        {
            frame.closeCode();
            frame.closeReason();
        }

        return frame;
    }

    /**
     * Writes this frame unmasked, as a server must, and flushes it.
     *
     * @param out the client's stream
     * @throws IOException if the stream cannot be written
     * @throws WebSocketException if this frame is invalid
     */
    public void write(OutputStream out) throws IOException
    {
        Objects.requireNonNull(out, "out");

        if (opcode.isControl())
        {
            if (!fin)
            {
                throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "A control frame cannot be fragmented");
            }
            if (payload.length > MAX_CONTROL_PAYLOAD)
            {
                throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "A control frame cannot carry more than " + MAX_CONTROL_PAYLOAD + " bytes");
            }
        }

        if (opcode == Opcode.CLOSE)
        {
            closeCode();
            closeReason();
        }

        out.write((fin ? 0x80 : 0) | opcode.code());

        if (payload.length < 126)
        {
            out.write(payload.length);
        }
        else if (payload.length <= 0xFFFF)
        {
            out.write(126);
            out.write(payload.length >>> 8);
            out.write(payload.length);
        }
        else
        {
            out.write(127);
            for (int shift = 56; shift >= 0; shift -= 8)
            {
                out.write((int) ((long) payload.length >>> shift));
            }
        }

        out.write(payload);
        out.flush();
    }

    /**
     * @return the close code this close frame carries, or {@link CloseCode#NO_STATUS} if
     *     it carries none. Application-defined codes are represented as
     *     {@link CloseCode#NORMAL}; use {@link #closeStatusCode()} to retrieve the exact code.
     * @throws WebSocketException if the payload is not a valid close frame body
     */
    public CloseCode closeCode()
    {
        int code = closeStatusCode();

        if (code == -1)
        {
            return CloseCode.NO_STATUS;
        }

        // orElseGet, not orElse: orElse evaluates its argument even when the code is a
        // known one, so invalidCloseCode would throw for every valid close frame
        return CloseCode.of(code)
                .filter(CloseCode::isSendable)
                .orElseGet(() -> code >= 3000 && code <= 4999
                        ? CloseCode.NORMAL
                        : invalidCloseCode(code));
    }

    /**
     * @return the raw close status code, or -1 if the frame carries no status code
     * @throws WebSocketException if the payload contains an invalid status code
     */
    public int closeStatusCode()
    {
        if (opcode != Opcode.CLOSE)
        {
            throw new IllegalStateException("This is not a close frame");
        }

        if (payload.length == 0)
        {
            return -1;
        }

        if (payload.length == 1)
        {
            throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "A close frame's status code needs two bytes");
        }

        int code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);

        if (CloseCode.of(code).filter(CloseCode::isSendable).isEmpty()
                && (code < 3000 || code > 4999))
        {
            throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "Invalid close status code " + code);
        }

        return code;
    }

    /**
     * @return the reason this close frame carries, or an empty string if it carries none
     * @throws WebSocketException if the reason is not valid UTF-8
     */
    public String closeReason()
    {
        if (opcode != Opcode.CLOSE)
        {
            throw new IllegalStateException("This is not a close frame");
        }

        if (payload.length <= 2)
        {
            return "";
        }

        try
        {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload, 2, payload.length - 2))
                    .toString();
        }
        catch (CharacterCodingException e)
        {
            throw new WebSocketException(CloseCode.INVALID_PAYLOAD, "A close frame's reason must be valid UTF-8");
        }
    }

    private static CloseCode invalidCloseCode(int code)
    {
        throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "Invalid close status code " + code);
    }

    private static int require(InputStream in) throws IOException
    {
        int read = in.read();
        if (read < 0)
        {
            throw new EOFException("The connection ended inside a frame header");
        }
        return read;
    }
}
