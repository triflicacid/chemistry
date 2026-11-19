package com.ruben.chemistry.server.ws;

import com.ruben.chemistry.server.json.JsonParser;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public final class SocketHarness implements AfterEachCallback
{
    /** How long to wait for a server to answer before failing the test. */
    public static final int TIMEOUT_SECONDS = 10;

    private final List<WebSocketServer> started = new ArrayList<>();

    /**
     * @param endpoints the endpoints to serve
     * @return a server listening on a free port
     * @throws IOException if it cannot listen
     */
    public WebSocketServer start(SocketEndpointFactory... endpoints) throws IOException
    {
        return start(WebSocketServer.DEFAULT_MAX_MESSAGE, endpoints);
    }

    /**
     * @param maxMessage the largest message to accept, in bytes
     * @param endpoints the endpoints to serve
     * @return a server listening on a free port
     * @throws IOException if it cannot listen
     */
    public WebSocketServer start(long maxMessage, SocketEndpointFactory... endpoints) throws IOException
    {
        WebSocketServer server = new WebSocketServer(0, maxMessage, WebSocketServer.DEFAULT_IDLE_TIMEOUT);
        for (SocketEndpointFactory endpoint : endpoints)
        {
            server.add(endpoint);
        }

        server.start();
        started.add(server);
        return server;
    }

    @Override
    public void afterEach(ExtensionContext context)
    {
        started.forEach(WebSocketServer::stop);
        started.clear();
    }

    /**
     * @param routes the routes the endpoint registers
     * @return a factory for an endpoint registering them
     */
    public static SocketEndpointFactory endpoint(Consumer<SocketRegistry> routes)
    {
        return () -> new SocketEndpoint()
        {
            @Override
            public void registerHandlers(SocketRegistry registry)
            {
                routes.accept(registry);
            }
        };
    }

    /**
     * @param server the server to connect to
     * @param path the path to connect on
     * @param listener what to tell about the connection
     * @return the open connection
     */
    public static WebSocket connect(WebSocketServer server, String path, WebSocket.Listener listener)
    {
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + server.port() + path), listener)
                .join();
    }

    /** Collects whole messages a server sends, joining the fragments the client is given. */
    public static final class Collector implements WebSocket.Listener
    {
        public final BlockingQueue<String> messages = new ArrayBlockingQueue<>(64);
        public final CountDownLatch closed = new CountDownLatch(1);
        public final AtomicReference<String> closeStatus = new AtomicReference<>();
        private final StringBuilder partial = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket)
        {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last)
        {
            partial.append(data);
            if (last)
            {
                messages.add(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last)
        {
            messages.add("binary:" + StandardCharsets.UTF_8.decode(data));
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason)
        {
            closeStatus.set(statusCode + ":" + reason);
            closed.countDown();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error)
        {
            closed.countDown();
        }

        /**
         * @return the next message the server sent
         * @throws AssertionError if none arrives in time
         */
        public String next() throws InterruptedException
        {
            String message = messages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(message).as("a message from the server").isNotNull();
            return message;
        }

        /**
         * @return the next message the server sent, parsed as JSON
         * @throws AssertionError if none arrives in time
         */
        public Map<String, Object> nextJson() throws InterruptedException
        {
            return JsonParser.parse(next());
        }
    }
}
