package com.ruben.chemistry.server;

import com.ruben.chemistry.server.http.EndpointFactory;
import com.ruben.chemistry.server.http.Server;
import com.ruben.chemistry.server.resource.HealthEndpoint;
import com.ruben.chemistry.server.resource.LibraryEndpoint;
import com.ruben.chemistry.server.resource.MoleculeEndpoint;
import com.ruben.chemistry.server.resource.OptionsEndpoint;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts the {@link Server} that serves the chemistry front-end.
 * <p>
 * Run with {@code ./gradlew :server:run}.
 * The port is the first argument, else the {@code PORT} environment variable, else 8080.
 */
public final class Main
{
    private static final int DEFAULT_PORT = 8080;
    private static final int THREADS = 8;

    private static final List<EndpointFactory> endpoints = List.of(
            HealthEndpoint::new,
            LibraryEndpoint::new,
            MoleculeEndpoint::new,
            OptionsEndpoint::new
    );

    private Main()
    {
    }

    public static void main(String[] args) throws IOException
    {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        Server server = new Server(port(args), executor);
        endpoints.forEach(server::add);

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        {
            server.stop();
            executor.shutdown();
        }));

        server.start();

        System.out.println("Listening on http://localhost:" + server.port());
    }

    private static int port(String[] args)
    {
        String given = args.length > 0 ? args[0] : System.getenv("PORT");
        return given == null || given.isBlank() ? DEFAULT_PORT : Integer.parseInt(given.trim());
    }
}
