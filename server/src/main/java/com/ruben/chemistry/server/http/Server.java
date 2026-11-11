package com.ruben.chemistry.server.http;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The HTTP server.
 * <p>
 * Use {@link #add} to add endpoints, then {@link #start} to start listening.
 */
public final class Server
{
    private final int requestedPort;
    private final ExecutorService executor;
    private final List<EndpointFactory> endpointFactories = new ArrayList<>();
    private HttpServer server;

    /**
     * @param port the port to listen on, or 0 for any free port
     * @param executor what runs requests
     */
    public Server(int port, ExecutorService executor)
    {
        this.requestedPort = port;
        this.executor = executor;
    }

    /**
     * Adds a new {@link EndpointFactory}, whose {@link Endpoint} will be setup when the server starts.
     *
     * @param endpointFactory the factory
     * @return this
     * @throws IllegalStateException if already started
     * @throws IllegalArgumentException if this factory wasalready added
     */
    public synchronized Server add(EndpointFactory endpointFactory)
    {
        if (server != null)
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
     * @throws IllegalArgumentException if two endpoints register the same method and path
     * @throws IllegalStateException if already started, or an endpoint cannot be instantiated
     */
    public synchronized void start() throws IOException
    {
        if (server != null)
        {
            throw new IllegalStateException("The server is already started");
        }

        List<Endpoint> instances = endpointFactories.stream()
                .map(EndpointFactory::create)
                .toList();

        EndpointManager manager = new EndpointManager();
        manager.registerAll(instances);

        HttpServer created = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), requestedPort), 0);
        manager.attach(created);

        created.setExecutor(executor);
        created.start();
        server = created;
    }

    /**
     * Stops listening, letting running requests finish for up to a second.
     */
    public synchronized void stop()
    {
        if (server == null)
        {
            return;
        }

        server.stop(1);
        server = null;
    }

    /**
     * @return the port actually listened on
     * @throws IllegalStateException if not started
     */
    public synchronized int port()
    {
        if (server == null)
        {
            throw new IllegalStateException("The server is not started");
        }
        return server.getAddress().getPort();
    }
}
