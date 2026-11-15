package com.ruben.chemistry.server.ws;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The WebSocket server.
 * <p>
 * Use {@link #add} to add endpoints, then {@link #start} to start listening.
 * <p>
 * This listens on its own port rather than sharing the HTTP server's.
 */
public final class WebSocketServer
{
    /** The largest message accepted, in bytes. */
    public static final int DEFAULT_MAX_MESSAGE = 1 << 20;

    /** How long a connection may be silent before it is pinged, in milliseconds. */
    public static final int DEFAULT_IDLE_TIMEOUT = 30_000;

    private final int requestedPort;
    private final long maxMessage;
    private final int idleTimeout;
    private final List<SocketEndpointFactory> endpointFactories = new ArrayList<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    private ServerSocket serverSocket;
    private Thread acceptor;

    /**
     * A server with the default message limit and idle timeout.
     *
     * @param port the port to listen on, or 0 for any free port
     */
    public WebSocketServer(int port)
    {
        this(port, DEFAULT_MAX_MESSAGE, DEFAULT_IDLE_TIMEOUT);
    }

    /**
     * @param port the port to listen on, or 0 for any free port
     * @param maxMessage the largest message accepted, in bytes
     * @param idleTimeout how long a connection may be silent before it is pinged (ms; a second such period closes it)
     * @throws IllegalArgumentException if the limit or timeout is not positive
     */
    public WebSocketServer(int port, long maxMessage, int idleTimeout)
    {
        if (maxMessage <= 0)
        {
            throw new IllegalArgumentException("The message limit must be positive, but was " + maxMessage);
        }
        if (idleTimeout <= 0)
        {
            throw new IllegalArgumentException("The idle timeout must be positive, but was " + idleTimeout);
        }

        this.requestedPort = port;
        this.maxMessage = maxMessage;
        this.idleTimeout = idleTimeout;
    }

    /**
     * Adds a new {@link SocketEndpointFactory}, whose {@link SocketEndpoint} will be setup
     * when the server starts.
     *
     * @param endpointFactory the factory
     * @return this
     * @throws IllegalStateException if already started
     * @throws IllegalArgumentException if this factory was already added
     */
    public synchronized WebSocketServer add(SocketEndpointFactory endpointFactory)
    {
        if (serverSocket != null)
        {
            throw new IllegalStateException("Endpoints cannot be added once the server is started");
        }

        if (endpointFactories.contains(endpointFactory))
        {
            throw new IllegalArgumentException("Endpoint was already added");
        }

        endpointFactories.add(endpointFactory);
        return this;
    }

    /**
     * Instantiates every added endpoint, has each register its routes, and starts listening.
     *
     * @throws IOException if the port cannot be bound
     * @throws IllegalArgumentException if two endpoints register the same path
     * @throws IllegalStateException if already started, or an endpoint cannot be instantiated
     */
    public synchronized void start() throws IOException
    {
        if (serverSocket != null)
        {
            throw new IllegalStateException("The server is already started");
        }

        List<SocketEndpoint> instances = endpointFactories.stream()
                .map(SocketEndpointFactory::create)
                .toList();

        SocketManager manager = new SocketManager();
        manager.registerAll(instances);

        ServerSocket created = new ServerSocket();
        created.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), requestedPort));

        serverSocket = created;
        acceptor = Thread.ofVirtual()
                .name("websocket-acceptor")
                .start(() -> accept(created, manager));
    }

    /**
     * Stops listening and closes every open connection, telling each peer the server is going away.
     */
    public synchronized void stop()
    {
        if (serverSocket == null)
        {
            return;
        }

        try
        {
            serverSocket.close();
        }
        catch (IOException ignored)
        {
            // the socket is being discarded either way
        }

        serverSocket = null;
        acceptor = null;

        // copy first: closing a session removes it from the map
        for (WebSocketSession session : List.copyOf(sessions.values()))
        {
            session.close(CloseCode.GOING_AWAY, "The server is shutting down");
        }
        sessions.clear();
    }

    /**
     * @return the port actually listened on
     * @throws IllegalStateException if not started
     */
    public synchronized int port()
    {
        if (serverSocket == null)
        {
            throw new IllegalStateException("The server is not started");
        }
        return serverSocket.getLocalPort();
    }

    /**
     * Every connection currently open, for an endpoint that needs to reach sessions other
     * than its own, such as to tell every viewer of a molecule that it changed.
     *
     * @return the open sessions, as a snapshot that does not change as connections come and go
     */
    public Collection<WebSocketSession> sessions()
    {
        return Collections.unmodifiableCollection(List.copyOf(sessions.values()));
    }

    private void accept(ServerSocket listening, SocketManager manager)
    {
        System.out.println("WebSocket listening on ws://localhost:" + listening.getLocalPort() + " for " + manager.paths());

        while (!listening.isClosed())
        {
            Socket client;
            try
            {
                client = listening.accept();
            }
            catch (SocketException closed)
            {
                // stop() closed the listening socket, which is how this loop ends
                return;
            }
            catch (IOException failed)
            {
                System.err.println("Could not accept a WebSocket connection: " + failed);
                continue;
            }

            Thread.ofVirtual()
                    .name("websocket-" + client.getPort())
                    .start(() -> serve(client, manager));
        }
    }

    private void serve(Socket client, SocketManager manager)
    {
        String id = "ws-" + nextId.getAndIncrement();
        WebSocketSession session = null;

        try
        {
            client.setTcpNoDelay(true);

            // the handshake must not be allowed to hang: a peer that connects and says
            // nothing would otherwise hold the connection open indefinitely
            client.setSoTimeout(idleTimeout);
            Handshake handshake = Handshake.read(client.getInputStream());

            SocketManager.Match matched = manager.match(handshake.path());
            if (matched == null)
            {
                refuse(client, 404, "No WebSocket endpoint at " + handshake.path());
                return;
            }

            handshake.accept(client.getOutputStream());
            System.out.println("ws+ " + id + " " + handshake.path());

            session = new WebSocketSession(id, client, handshake, matched.pathParameters(), matched.listener(), maxMessage);
            sessions.put(id, session);
            session.run(idleTimeout);
        }
        catch (Handshake.HandshakeException refused)
        {
            refuse(client, refused.status(), refused.getMessage());
        }
        catch (IOException failed)
        {
            closeQuietly(client);
        }
        finally
        {
            if (session != null)
            {
                sessions.remove(id);
                System.out.println("ws- " + id);
            }
            closeQuietly(client);
        }
    }

    /**
     * Answers a connection that never became a WebSocket as ordinary HTTP, so that a
     * browser or a person with a terminal sees why it was turned away.
     *
     * @param client the connection to refuse
     * @param status the HTTP status to refuse it with
     * @param message a human-readable explanation
     */
    private static void refuse(Socket client, int status, String message)
    {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + " " + reason(status) + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + (status == 426 ? "Sec-WebSocket-Version: 13\r\n" : "")
                + "\r\n";

        try
        {
            OutputStream out = client.getOutputStream();
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
        }
        catch (IOException gone)
        {
            // the peer is already unreachable, so there is nothing to tell it
        }
        finally
        {
            closeQuietly(client);
        }
    }

    private static String reason(int status)
    {
        return switch (status)
        {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 426 -> "Upgrade Required";
            case 431 -> "Request Header Fields Too Large";
            default -> "Error";
        };
    }

    private static void closeQuietly(Socket client)
    {
        try
        {
            client.close();
        }
        catch (IOException ignored)
        {
            // the connection is being discarded either way
        }
    }
}
