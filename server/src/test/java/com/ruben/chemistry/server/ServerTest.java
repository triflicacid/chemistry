package com.ruben.chemistry.server;

import com.ruben.chemistry.server.http.Endpoint;
import com.ruben.chemistry.server.http.EndpointFactory;
import com.ruben.chemistry.server.http.EndpointRegistry;
import com.ruben.chemistry.server.http.HttpMethod;
import com.ruben.chemistry.server.http.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerTest
{
    /** Records how it was used. */
    private static final class MockEndpoint extends Endpoint
    {
        final AtomicInteger registrations = new AtomicInteger();
        final AtomicInteger hits = new AtomicInteger();

        @Override
        public void registerHandlers(EndpointRegistry registry)
        {
            registrations.incrementAndGet();
            registry.register(HttpMethod.GET, "/api/mock", (request, context) ->
            {
                hits.incrementAndGet();
                context.respond(200);
            });
        }
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final MockEndpoint endpoint = new MockEndpoint();
    private final AtomicInteger creations = new AtomicInteger();
    private final EndpointFactory factory = () ->
    {
        creations.incrementAndGet();
        return endpoint;
    };

    @AfterEach
    void shut_down()
    {
        executor.shutdownNow();
    }

    private HttpResponse<String> get(Server server, String path) throws Exception
    {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void should_serve_the_added_endpoints() throws Exception
    {
        Server server = new Server(0, executor).add(factory);
        assertThat(creations).hasValue(0);
        assertThat(endpoint.registrations).hasValue(0);

        server.start();
        try
        {
            assertThat(creations).hasValue(1);
            assertThat(endpoint.registrations).hasValue(1);
            assertThat(endpoint.hits).hasValue(0);

            assertThat(get(server, "/api/mock").statusCode()).isEqualTo(200);
            assertThat(endpoint.hits).hasValue(1);

            assertThat(get(server, "/api/nothing").statusCode()).isEqualTo(404);
            assertThat(endpoint.hits).hasValue(1);
        }
        finally
        {
            server.stop();
        }
    }

    @Test
    void should_reject_duplicate_and_late_endpoints() throws Exception
    {
        Server server = new Server(0, executor).add(factory);

        assertThatThrownBy(() -> server.add(factory)).isInstanceOf(IllegalArgumentException.class);

        server.start();
        try
        {
            assertThatThrownBy(() -> server.add(MockEndpoint::new)).isInstanceOf(IllegalStateException.class);
        }
        finally
        {
            server.stop();
        }
    }
}
