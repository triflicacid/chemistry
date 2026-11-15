package com.ruben.chemistry.server;

import com.ruben.chemistry.server.http.EndpointFactory;
import com.ruben.chemistry.server.http.Server;
import com.ruben.chemistry.server.resource.HealthEndpoint;
import com.ruben.chemistry.server.resource.MoleculeEndpoint;
import com.ruben.chemistry.server.resource.MoleculeLibraryEndpoint;
import com.ruben.chemistry.server.resource.OptionsEndpoint;
import com.ruben.chemistry.server.resource.QueryLibraryEndpoint;
import com.ruben.chemistry.server.resource.StudioSocketEndpoint;
import com.ruben.chemistry.server.ws.SocketEndpointFactory;
import com.ruben.chemistry.server.ws.WebSocketServer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts the {@link Server} that serves the chemistry front-end, and the
 * {@link WebSocketServer} that serves its interactive editing sessions.
 * <p>
 * Run with {@code ./gradlew :server:run}.
 * The HTTP port is the first argument, else the {@code PORT} environment variable, else
 * 8080; the WebSocket port is the second argument, else {@code WS_PORT}, else the HTTP
 * port plus one.
 */
public final class Main
{
    private static final int DEFAULT_PORT = 8080;
    private static final int THREADS = 8;

    private static final List<EndpointFactory> httpEndpoints = List.of(
            HealthEndpoint::new,
            MoleculeEndpoint::new,
            MoleculeLibraryEndpoint::new,
            OptionsEndpoint::new,
            QueryLibraryEndpoint::new
    );

    private static final List<SocketEndpointFactory> socketEndpoints = List.of(
            StudioSocketEndpoint::new
    );

    private Main()
    {
    }

    public static void main(String[] args) throws IOException
    {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        int httpPort = port(args);

        Server server = new Server(httpPort, executor);
        httpEndpoints.forEach(server::add);

        WebSocketServer sockets = new WebSocketServer(socketPort(args, httpPort));
        socketEndpoints.forEach(sockets::add);

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            sockets.stop();
            server.stop();
            executor.shutdown();
        }));

        server.start();
        sockets.start();

        System.out.println("==== SERVER STARTED ====");
        System.out.println("HTTP listening on http://localhost:" + server.port());
        System.out.println("WS listening on http://localhost:" + sockets.port());
    }

    private static int port(String[] args)
    {
        String given = args.length > 0 ? args[0] : System.getenv("PORT");
        return given == null || given.isBlank() ? DEFAULT_PORT : Integer.parseInt(given.trim());
    }

    /**
     * @param args the command line, whose second argument is the WebSocket port
     * @param httpPort the port the HTTP server listens on
     * @return the port to serve WebSocket connections on
     */
    private static int socketPort(String[] args, int httpPort)
    {
        String given = args.length > 1 ? args[1] : System.getenv("WS_PORT");

        // a port of 0 means "any free port", which would leave the front-end unable to guess it
        return given == null || given.isBlank() ? httpPort + 1 : Integer.parseInt(given.trim());
    }
}
