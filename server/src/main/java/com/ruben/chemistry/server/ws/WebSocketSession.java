
package com.ruben.chemistry.server.ws;

import com.ruben.chemistry.server.json.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * One live WebSocket connection, and the {@link WebSocketContext} its endpoint sends on.
 */
public final class WebSocketSession implements WebSocketContext
{
    /** How long to wait for the peer to stop sending before closing, in milliseconds. */
    private static final int DRAIN_TIMEOUT = 250;

    /** The most bytes discarded while waiting for the peer to stop sending. */
    private static final int DRAIN_LIMIT = 64 * 1024;

    private final String id;
    private final Socket socket;
    private final Handshake handshake;
    private final Map<String, String> pathParameters;
    private final WebSocketListener listener;
    private final long maxMessage;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    private final Object sendLock = new Object();
    private volatile boolean open = true;
    private boolean closeFrameSent;

    /** The close information retained for the final listener callback. */
    private volatile CloseCode closeCode = CloseCode.ABNORMAL;
    private volatile String closeReason = "";

    /** The queue used to wake the session when a frame arrives or the connection closes. */
    private volatile BlockingQueue<ReadResult> readQueue;

    /** Set when a keepalive ping is outstanding, so unrelated frames cannot satisfy it. */
    private boolean awaitingPong;
    private byte[] expectedPong;
    private long pingSequence;
    private long pongDeadline;

    WebSocketSession(
            String id,
            Socket socket,
            Handshake handshake,
            Map<String, String> pathParameters,
            WebSocketListener listener,
            long maxMessage)
    {
        this.id = Objects.requireNonNull(id, "id");
        this.socket = Objects.requireNonNull(socket, "socket");
        this.handshake = Objects.requireNonNull(handshake, "handshake");
        this.pathParameters = Map.copyOf(pathParameters);
        this.listener = Objects.requireNonNull(listener, "listener");

        if (maxMessage <= 0)
        {
            throw new IllegalArgumentException("The message limit must be positive");
        }
        this.maxMessage = maxMessage;
    }

    @Override
    public String id()
    {
        return id;
    }

    @Override
    public String path()
    {
        return handshake.path();
    }

    @Override
    public Map<String, String> pathParameters()
    {
        return pathParameters;
    }

    @Override
    public String pathParameter(String name)
    {
        String value = pathParameters.get(name);
        if (value == null)
        {
            throw new IllegalArgumentException("The route has no path parameter '" + name + "'");
        }
        return value;
    }

    @Override
    public Map<String, String> query()
    {
        return handshake.query();
    }

    @Override
    public Optional<String> queryParameter(String name)
    {
        return Optional.ofNullable(handshake.query().get(name));
    }

    @Override
    public Optional<String> header(String name)
    {
        return handshake.header(name);
    }

    @Override
    public Map<String, Object> attributes()
    {
        return attributes;
    }

    @Override
    public boolean isOpen()
    {
        return open;
    }

    @Override
    public void send(String text) throws IOException
    {
        write(Frame.text(text));
    }

    @Override
    public void sendJson(Object value) throws IOException
    {
        write(Frame.text(JsonWriter.write(value)));
    }

    @Override
    public void sendBinary(byte[] payload) throws IOException
    {
        write(Frame.binary(payload));
    }

    @Override
    public void error(String code, String message) throws IOException
    {
        error(null, null, code, message);
    }

    @Override
    public void error(String type, Long sequence, String code, String message) throws IOException
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
        sendJson(envelope);
    }

    @Override
    public void ping(byte[] payload) throws IOException
    {
        Objects.requireNonNull(payload, "payload");

        if (payload.length > Frame.MAX_CONTROL_PAYLOAD)
        {
            throw new IllegalArgumentException("A ping cannot carry more than " + Frame.MAX_CONTROL_PAYLOAD + " bytes");
        }

        write(new Frame(true, Opcode.PING, payload));
    }

    @Override
    public void close()
    {
        close(CloseCode.NORMAL, "");
    }

    @Override
    public void close(CloseCode closeCode, String reason)
    {
        Objects.requireNonNull(closeCode, "closeCode");

        synchronized (sendLock)
        {
            if (!open)
            {
                return;
            }

            this.closeCode = closeCode;
            this.closeReason = reason == null ? "" : reason;

            if (!closeFrameSent && closeCode.isSendable())
            {
                closeFrameSent = true;
                try
                {
                    Frame.close(closeCode, reason).write(socket.getOutputStream());
                }
                catch (IOException gone)
                {
                    // The peer is already unreachable, so there is nothing to tell it.
                }
            }

            open = false;
        }

        wakeReader();
        closeSocket();
    }

    @Override
    public String toString()
    {
        return "WebSocketSession[" + id + " " + path() + "]";
    }

    /**
     * Reads frames until the connection closes, delivering whole messages to the listener.
     * The listener is told the connection opened before the first read and closed after the
     * last, whatever ends it.
     *
     * @param idleTimeout how long without a frame before a keepalive ping is sent, in
     *     milliseconds; a second such period without the matching pong closes the connection
     */
    void run(int idleTimeout)
    {
        if (idleTimeout <= 0)
        {
            throw new IllegalArgumentException("The idle timeout must be positive");
        }

        try
        {
            listener.onOpen(this);

            if (!open)
            {
                return;
            }

            BlockingQueue<ReadResult> results = new ArrayBlockingQueue<>(1);
            readQueue = results;

            // Reading happens independently so an idle timeout cannot interrupt a frame
            // halfway through its header, masking key, or payload.
            if (open)
            {
                Thread.ofVirtual()
                        .name("websocket-reader-" + id)
                        .start(() -> readFrames(results));
            }

            ByteArrayOutputStream message = new ByteArrayOutputStream();
            Opcode messageType = null;
            long idleNanos = TimeUnit.MILLISECONDS.toNanos(idleTimeout);

            while (open)
            {
                long waitNanos = awaitingPong
                        ? Math.max(0, pongDeadline - System.nanoTime())
                        : idleNanos;

                ReadResult result;
                try
                {
                    result = results.poll(waitNanos, TimeUnit.NANOSECONDS);
                }
                catch (InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                    close(CloseCode.GOING_AWAY, "The session thread was interrupted");
                    break;
                }

                if (!open)
                {
                    break;
                }

                if (result == null)
                {
                    if (awaitingPong || !keepAlive(idleNanos))
                    {
                        close(CloseCode.GOING_AWAY, "No matching pong arrived within the idle timeout");
                        break;
                    }
                    continue;
                }

                if (result.closed())
                {
                    break;
                }

                if (result.failure() != null)
                {
                    rethrow(result.failure());
                }

                Frame frame = result.frame();
                byte[] payload = frame.payload();

                if (frame.opcode().isControl())
                {
                    if (frame.opcode() == Opcode.CLOSE)
                    {
                        // Validate and use the received close information, then echo the
                        // original frame so application-defined status codes are preserved.
                        CloseCode receivedCode = frame.closeCode();
                        String receivedReason = frame.closeReason();
                        closeFromPeer(frame, receivedCode, receivedReason);
                        break;
                    }

                    if (frame.opcode() == Opcode.PING)
                    {
                        write(new Frame(true, Opcode.PONG, payload));
                    }
                    else if (frame.opcode() == Opcode.PONG)
                    {
                        if (awaitingPong && Arrays.equals(payload, expectedPong))
                        {
                            awaitingPong = false;
                            expectedPong = null;
                        }
                    }

                    continue;
                }

                if (frame.opcode() == Opcode.CONTINUATION)
                {
                    if (messageType == null)
                    {
                        throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "A continuation frame arrived with no message to continue");
                    }
                }
                else
                {
                    if (messageType != null)
                    {
                        throw new WebSocketException(CloseCode.PROTOCOL_ERROR, "A new message started before the previous one finished");
                    }
                    messageType = frame.opcode();
                }

                long nextSize = (long) message.size() + payload.length;
                if (nextSize > maxMessage || nextSize > Integer.MAX_VALUE)
                {
                    throw new WebSocketException(CloseCode.TOO_BIG, "The message exceeds the " + maxMessage + " byte limit");
                }

                message.write(payload, 0, payload.length);

                if (!frame.fin())
                {
                    continue;
                }

                byte[] completePayload = message.toByteArray();
                Opcode type = messageType;
                message.reset();
                messageType = null;

                listener.onMessage(
                        type == Opcode.TEXT
                                ? WebSocketMessage.text(text(completePayload))
                                : WebSocketMessage.binary(completePayload),
                        this);
            }
        }
        catch (WebSocketException broken)
        {
            if (open)
            {
                close(broken.closeCode(), broken.getMessage());
            }
        }
        catch (EOFException lost)
        {
            if (open)
            {
                abort(CloseCode.ABNORMAL, "The connection was lost");
            }
        }
        catch (IOException failed)
        {
            if (open)
            {
                abort(CloseCode.ABNORMAL,
                        failed.getMessage() == null ? "The connection failed" : failed.getMessage());
            }
        }
        // Throwable, not Exception: a listener can fail with an Error too, and one that got
        // past here would leave the connection open with nothing reading it.
        catch (Throwable failure)
        {
            if (open)
            {
                safely(() -> listener.onError(this, failure));
                close(CloseCode.INTERNAL_ERROR, "The endpoint failed");
            }
        }
        finally
        {
            if (open)
            {
                abort(CloseCode.ABNORMAL, "The session ended");
            }

            readQueue = null;
            closeSocket();

            CloseCode closed = closeCode;
            String why = closeReason;
            safely(() -> listener.onClose(this, closed, why));
        }
    }

    /**
     * Reads frames on a separate virtual thread and publishes them in order.
     * The bounded queue applies backpressure if the session is busy processing a message.
     *
     * @param results the session's frame queue
     */
    private void readFrames(BlockingQueue<ReadResult> results)
    {
        try
        {
            InputStream in = socket.getInputStream();

            while (open)
            {
                Frame frame = Frame.read(in, maxMessage);
                if (!publish(results, new ReadResult(frame, null, false)))
                {
                    return;
                }
            }
        }
        catch (Throwable failure)
        {
            publish(results, new ReadResult(null, failure, false));
        }
    }

    /**
     * Publishes a frame or read failure unless the session has already closed.
     *
     * @param results the session's frame queue
     * @param result the result to publish
     * @return whether it was published
     */
    private boolean publish(BlockingQueue<ReadResult> results, ReadResult result)
    {
        while (open)
        {
            try
            {
                if (results.offer(result, 100, TimeUnit.MILLISECONDS))
                {
                    return true;
                }
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * Pings the peer after an idle period, or gives up if the previous ping went unanswered.
     *
     * @param timeoutNanos the allowed time for the pong
     * @return whether the connection should keep waiting for a frame
     */
    private boolean keepAlive(long timeoutNanos)
    {
        if (awaitingPong)
        {
            return false;
        }

        expectedPong = ByteBuffer.allocate(Long.BYTES)
                .putLong(++pingSequence)
                .array();

        awaitingPong = true;
        pongDeadline = System.nanoTime() + timeoutNanos;

        try
        {
            write(new Frame(true, Opcode.PING, expectedPong));
            return true;
        }
        catch (IOException gone)
        {
            return false;
        }
    }

    /**
     * Answers a peer's close frame and closes the connection.
     *
     * @param frame the close frame received
     * @param code its status code
     * @param reason its reason
     */
    private void closeFromPeer(Frame frame, CloseCode code, String reason)
    {
        synchronized (sendLock)
        {
            if (!open)
            {
                return;
            }

            closeCode = code;
            closeReason = reason;

            if (!closeFrameSent)
            {
                closeFrameSent = true;
                try
                {
                    frame.write(socket.getOutputStream());
                }
                catch (IOException gone)
                {
                    // The peer is already unreachable, so there is nothing to tell it.
                }
            }

            open = false;
        }

        wakeReader();
        closeSocket();
    }

    private void write(Frame frame) throws IOException
    {
        synchronized (sendLock)
        {
            if (!open)
            {
                throw new IOException("The connection is closed");
            }

            frame.write(socket.getOutputStream());
        }
    }

    /**
     * Closes without sending a close frame, because the connection is already unusable.
     *
     * @param code why the connection ended
     * @param reason a human-readable explanation
     */
    private void abort(CloseCode code, String reason)
    {
        synchronized (sendLock)
        {
            if (!open)
            {
                return;
            }

            closeCode = code;
            closeReason = reason;
            open = false;
        }

        wakeReader();
        closeSocket();
    }

    /**
     * Wakes the session thread if it is waiting for a frame.
     */
    private void wakeReader()
    {
        BlockingQueue<ReadResult> results = readQueue;
        if (results != null)
        {
            results.clear();
            results.offer(new ReadResult(null, null, true));
        }
    }

    /**
     * Ends the connection, first letting go of whatever the peer was still sending.
     * <p>
     * Closing a socket that still has data arriving resets it, and a reset discards the
     * close frame just written, so the peer would see the connection drop rather than the
     * reason it closed. Reading the rest out first, briefly and up to a bound, lets the
     * close frame land.
     */
    private void closeSocket()
    {
        try
        {
            if (!socket.isClosed() && !socket.isInputShutdown())
            {
                socket.setSoTimeout(DRAIN_TIMEOUT);
                InputStream in = socket.getInputStream();

                for (int drained = 0; drained < DRAIN_LIMIT && in.read() >= 0; drained++)
                {
                    // discarding what the peer had already sent
                }
            }
        }
        catch (IOException | RuntimeException gone)
        {
            // the peer has already stopped, which is what this was waiting for anyway
        }

        try
        {
            socket.close();
        }
        catch (IOException ignored)
        {
            // The connection is being discarded either way.
        }
    }

    /**
     * Rethrows a failure received from the reader thread.
     *
     * @param failure the read failure
     * @throws IOException if the stream could not be read
     */
    private static void rethrow(Throwable failure) throws IOException
    {
        if (failure instanceof WebSocketException broken)
        {
            throw broken;
        }
        if (failure instanceof IOException io)
        {
            throw io;
        }
        if (failure instanceof RuntimeException runtime)
        {
            throw runtime;
        }
        if (failure instanceof Error error)
        {
            throw error;
        }

        throw new IOException("The frame reader failed", failure);
    }

    /**
     * @param payload a text message's bytes
     * @return the text they spell
     * @throws WebSocketException if they are not valid UTF-8, which RFC 6455 requires
     */
    private static String text(byte[] payload)
    {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try
        {
            return decoder.decode(ByteBuffer.wrap(payload)).toString();
        }
        catch (CharacterCodingException invalid)
        {
            throw new WebSocketException(CloseCode.INVALID_PAYLOAD, "A text message must be valid UTF-8");
        }
    }

    /**
     * Runs a listener callback that must not stop the connection from being cleaned up.
     *
     * @param callback the call to make
     */
    private void safely(Runnable callback)
    {
        try
        {
            callback.run();
        }
        catch (Throwable ignored)
        {
            // Already closing, and a failure here must not hide why.
        }
    }

    private record ReadResult(Frame frame, Throwable failure, boolean closed)
    {
    }
}
