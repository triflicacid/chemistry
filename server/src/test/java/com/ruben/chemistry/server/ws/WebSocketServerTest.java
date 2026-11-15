package com.ruben.chemistry.server.ws;

import com.ruben.chemistry.server.json.JsonParser;
import com.ruben.chemistry.server.resource.StudioSocketEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.assertj.core.api.InstanceOfAssertFactories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketServerTest
{
    private static final int TIMEOUT_SECONDS = 10;

    /** Answers whatever it is sent, and records the connection's lifecycle. */
    private static final class EchoSocket implements WebSocketListener
    {
        static final BlockingQueue<String> opened = new ArrayBlockingQueue<>(16);
        static final BlockingQueue<String> closed = new ArrayBlockingQueue<>(16);
        static final List<Throwable> failures = new CopyOnWriteArrayList<>();
        static final AtomicReference<WebSocketContext> last = new AtomicReference<>();

        static void reset()
        {
            opened.clear();
            closed.clear();
            failures.clear();
            last.set(null);
        }

        @Override
        public void onOpen(WebSocketContext context)
        {
            last.set(context);
            opened.add(context.id());
        }

        @Override
        public void onMessage(WebSocketMessage message, WebSocketContext context) throws IOException
        {
            if (message.isBinary())
            {
                context.sendBinary(message.bytes());
                return;
            }
            context.send(message.text());
        }

        @Override
        public void onClose(WebSocketContext context, CloseCode closeCode, String reason)
        {
            closed.add(closeCode + ":" + reason);
        }

        @Override
        public void onError(WebSocketContext context, Throwable failure)
        {
            failures.add(failure);
        }
    }

    /** Collects whole messages a server sends, joining the fragments the client is given. */
    private static final class Collector implements WebSocket.Listener
    {
        final BlockingQueue<String> messages = new ArrayBlockingQueue<>(64);
        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicReference<String> closeStatus = new AtomicReference<>();
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
        String next() throws InterruptedException
        {
            String message = messages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(message).as("a message from the server").isNotNull();
            return message;
        }

        /**
         * @return the next message the server sent, parsed as JSON
         * @throws AssertionError if none arrives in time
         */
        Map<String, Object> nextJson() throws InterruptedException
        {
            return JsonParser.parse(next());
        }
    }

    private final List<WebSocketServer> started = new ArrayList<>();

    @AfterEach
    void shut_down()
    {
        started.forEach(WebSocketServer::stop);
        started.clear();
        EchoSocket.reset();
    }

    /**
     * @param routes the routes the endpoint registers
     * @return a factory for an endpoint registering them
     */
    private static SocketEndpointFactory endpoint(Consumer<SocketRegistry> routes)
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

    private WebSocketServer start(SocketEndpointFactory... endpoints) throws IOException
    {
        return start(WebSocketServer.DEFAULT_MAX_MESSAGE, endpoints);
    }

    private WebSocketServer start(long maxMessage, SocketEndpointFactory... endpoints) throws IOException
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

    /**
     * @return a server serving an {@link EchoSocket} at {@code /ws/echo} and {@code /ws/echo/:name}
     */
    private WebSocketServer startEcho() throws IOException
    {
        return start(endpoint(registry ->
        {
            registry.register("/ws/echo", EchoSocket::new);
            registry.register("/ws/echo/:name", EchoSocket::new);
        }));
    }

    private static WebSocket connect(WebSocketServer server, String path, WebSocket.Listener listener)
    {
        return HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + server.port() + path), listener)
                .join();
    }

    @Test
    void should_complete_the_handshake_and_carry_text_both_ways() throws Exception
    {
        WebSocketServer server = startEcho();
        Collector collector = new Collector();
        WebSocket socket = connect(server, "/ws/echo", collector);

        assertThat(EchoSocket.opened.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).startsWith("ws-");

        socket.sendText("hello", true).join();
        assertThat(collector.next()).isEqualTo("hello");

        socket.sendText("again", true).join();
        assertThat(collector.next()).isEqualTo("again");

        assertThat(server.sessions()).hasSize(1);
    }

    @Test
    void should_carry_binary_messages() throws Exception
    {
        WebSocketServer server = startEcho();
        Collector collector = new Collector();
        WebSocket socket = connect(server, "/ws/echo", collector);

        socket.sendBinary(ByteBuffer.wrap("bytes".getBytes(StandardCharsets.UTF_8)), true).join();
        assertThat(collector.next()).isEqualTo("binary:bytes");
    }

    @Test
    void should_join_a_fragmented_message() throws Exception
    {
        WebSocketServer server = startEcho();
        Collector collector = new Collector();
        WebSocket socket = connect(server, "/ws/echo", collector);

        socket.sendText("one ", false).join();
        socket.sendText("two ", false).join();
        socket.sendText("three", true).join();

        assertThat(collector.next()).isEqualTo("one two three");
    }

    @Test
    void should_expose_the_path_query_and_headers_of_the_connection() throws Exception
    {
        WebSocketServer server = startEcho();
        Collector collector = new Collector();
        connect(server, "/ws/echo/ethanol?style=ball", collector);

        assertThat(EchoSocket.opened.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        WebSocketContext context = EchoSocket.last.get();

        assertThat(context.path()).isEqualTo("/ws/echo/ethanol");
        assertThat(context.pathParameter("name")).isEqualTo("ethanol");
        assertThat(context.queryParameter("style")).contains("ball");
        assertThat(context.queryParameter("missing")).isEmpty();
        assertThat(context.header("Upgrade")).contains("websocket");
        assertThat(context.isOpen()).isTrue();
    }

    @Test
    void should_keep_per_connection_state_in_attributes() throws Exception
    {
        WebSocketServer server = startEcho();
        connect(server, "/ws/echo", new Collector());
        assertThat(EchoSocket.opened.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();

        WebSocketContext context = EchoSocket.last.get();
        context.attributes().put("molecule_id", "molecule-123");

        assertThat(context.attributes()).containsEntry("molecule_id", "molecule-123");
    }

    @Test
    void should_answer_a_ping_from_the_client() throws Exception
    {
        WebSocketServer server = startEcho();
        Collector collector = new Collector();
        WebSocket socket = connect(server, "/ws/echo", collector);

        // the JDK client answers the server's pong itself, so this only has to not fail
        socket.sendPing(ByteBuffer.wrap("beat".getBytes(StandardCharsets.UTF_8))).join();
        socket.sendText("still here", true).join();

        assertThat(collector.next()).isEqualTo("still here");
    }

    @Test
    void should_report_the_close_the_client_asked_for() throws Exception
    {
        WebSocketServer server = startEcho();
        WebSocket socket = connect(server, "/ws/echo", new Collector());
        assertThat(EchoSocket.opened.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();

        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();

        assertThat(EchoSocket.closed.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("NORMAL:done");
        assertThat(EchoSocket.failures).isEmpty();
    }

    @Test
    void should_close_a_connection_the_server_ends() throws Exception
    {
        WebSocketServer server = startEcho();
        Collector collector = new Collector();
        connect(server, "/ws/echo", collector);
        assertThat(EchoSocket.opened.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();

        EchoSocket.last.get().close(CloseCode.POLICY_VIOLATION, "not allowed");

        assertThat(collector.closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(collector.closeStatus.get()).isEqualTo("1008:not allowed");
        assertThat(EchoSocket.closed.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void should_forget_a_session_once_it_closes() throws Exception
    {
        WebSocketServer server = startEcho();
        WebSocket socket = connect(server, "/ws/echo", new Collector());
        assertThat(EchoSocket.opened.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        assertThat(server.sessions()).hasSize(1);

        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        assertThat(EchoSocket.closed.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();

        assertThat(server.sessions()).isEmpty();
    }

    @Test
    void should_close_a_message_over_the_limit() throws Exception
    {
        WebSocketServer server = start(64, endpoint(registry -> registry.register("/ws/echo", EchoSocket::new)));
        Collector collector = new Collector();
        WebSocket socket = connect(server, "/ws/echo", collector);

        socket.sendText("x".repeat(256), true);

        assertThat(collector.closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(collector.closeStatus.get()).startsWith("1009:");
    }

    @Test
    void should_refuse_a_path_with_no_endpoint() throws Exception
    {
        WebSocketServer server = startEcho();

        assertThatThrownBy(() -> connect(server, "/ws/nothing", new Collector()))
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(WebSocketHandshakeException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(WebSocketHandshakeException.class))
                .extracting(failure -> failure.getResponse().statusCode())
                .isEqualTo(404);
    }

    @Test
    void should_refuse_a_request_that_is_not_an_upgrade() throws Exception
    {
        WebSocketServer server = startEcho();
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + server.port() + "/ws/echo")
                .toURL()
                .openConnection();

        try
        {
            assertThat(connection.getResponseCode()).isEqualTo(426);
            assertThat(connection.getHeaderField("Sec-WebSocket-Version")).isEqualTo("13");
        }
        finally
        {
            connection.disconnect();
        }
    }

    @Test
    void should_refuse_an_unmasked_frame() throws Exception
    {
        WebSocketServer server = startEcho();

        try (Socket client = new Socket("localhost", server.port()))
        {
            OutputStream out = client.getOutputStream();
            out.write(("GET /ws/echo HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Sec-WebSocket-Key: AAAAAAAAAAAAAAAAAAAAAA==\r\n"
                    + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = client.getInputStream();
            assertThat(readHead(in)).contains("101 Switching Protocols");

            // a text frame a client must mask, sent unmasked
            out.write(new byte[] {(byte) 0x81, 0x02, 'h', 'i'});
            out.flush();

            Frame answer = readServerFrame(in);
            assertThat(answer.opcode()).isEqualTo(Opcode.CLOSE);
            assertThat(answer.closeCode()).isEqualTo(CloseCode.PROTOCOL_ERROR);
        }
    }

    @Test
    void should_complete_the_handshake_with_the_accept_key_rfc_6455_gives() throws Exception
    {
        // the example from RFC 6455 section 1.3
        Handshake handshake = new Handshake("/ws/echo", "", "dGhlIHNhbXBsZSBub25jZQ==", Map.of());
        assertThat(handshake.acceptKey()).isEqualTo("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
    }

    @Test
    void should_tell_every_peer_the_server_is_going_away() throws Exception
    {
        WebSocketServer server = startEcho();
        Collector collector = new Collector();
        connect(server, "/ws/echo", collector);
        assertThat(EchoSocket.opened.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();

        server.stop();

        assertThat(collector.closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(collector.closeStatus.get()).startsWith("1001:");
    }

    @Test
    void should_reject_duplicate_and_late_endpoints() throws Exception
    {
        SocketEndpointFactory factory = endpoint(registry -> registry.register("/ws/echo", EchoSocket::new));
        WebSocketServer server = new WebSocketServer(0).add(factory);

        assertThatThrownBy(() -> server.add(factory)).isInstanceOf(IllegalArgumentException.class);

        server.start();
        started.add(server);
        assertThatThrownBy(() -> server.add(factory)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_reject_two_endpoints_on_the_same_path()
    {
        WebSocketServer server = new WebSocketServer(0)
                .add(endpoint(registry ->
                {
                    registry.register("/ws/echo/:a", EchoSocket::new);
                    registry.register("/ws/echo/:b", EchoSocket::new);
                }));

        assertThatThrownBy(server::start).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_not_report_a_port_before_starting()
    {
        assertThatThrownBy(() -> new WebSocketServer(0).port()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_greet_a_studio_session_and_answer_its_pings() throws Exception
    {
        WebSocketServer server = start(StudioSocketEndpoint::new);
        Collector collector = new Collector();
        WebSocket socket = connect(server, "/ws/studio", collector);

        Map<String, Object> ready = collector.nextJson();
        assertThat(ready).containsEntry("type", "session.ready");
        assertThat(ready).containsEntry("protocol_version", 1L);
        assertThat(ready.get("session_id")).asString().startsWith("ws-");

        socket.sendText("{\"type\": \"ping\", \"sequence\": 108}", true).join();

        assertThat(collector.nextJson())
                .containsEntry("type", "pong")
                .containsEntry("sequence", 108L);
    }

    @Test
    void should_answer_a_studio_message_it_cannot_handle_without_closing() throws Exception
    {
        WebSocketServer server = start(StudioSocketEndpoint::new);
        Collector collector = new Collector();
        WebSocket socket = connect(server, "/ws/studio", collector);
        collector.next();

        socket.sendText("{\"type\": \"atom.patch\", \"sequence\": 7}", true).join();
        Map<String, Object> unknown = collector.nextJson();
        assertThat(unknown).containsEntry("type", "error").containsEntry("answering", "atom.patch");
        assertThat(unknown).containsEntry("sequence", 7L);
        assertThat(error(unknown)).containsEntry("code", "UNKNOWN_TYPE");

        socket.sendText("not json at all", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_JSON");

        socket.sendText("{\"sequence\": 9}", true).join();
        Map<String, Object> missing = collector.nextJson();
        assertThat(error(missing)).containsEntry("code", "MISSING_TYPE");
        assertThat(missing).containsEntry("sequence", 9L);

        // the session survived all three
        socket.sendText("{\"type\": \"ping\"}", true).join();
        assertThat(collector.nextJson()).containsEntry("type", "pong");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> error(Map<String, Object> envelope)
    {
        return (Map<String, Object>) envelope.get("error");
    }

    /**
     * @param in a connection the server has answered on
     * @return the HTTP response head, up to the blank line that ends it
     */
    private static String readHead(InputStream in) throws IOException
    {
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n"))
        {
            int read = in.read();
            if (read < 0)
            {
                break;
            }
            head.append((char) read);
        }
        return head.toString();
    }

    /**
     * Reads one frame the server sent, which unlike a client's is not masked.
     *
     * @param in the connection to read
     * @return the frame read
     */
    private static Frame readServerFrame(InputStream in) throws IOException
    {
        int first = in.read();
        int length = in.read();
        if (first < 0 || length < 0)
        {
            throw new IOException("The server closed without sending a frame");
        }

        assertThat(length & 0x80).as("a frame from a server is not masked").isZero();

        byte[] payload = in.readNBytes(length & 0x7F);
        Opcode opcode = Opcode.of(first & 0x0F).orElseThrow();
        return new Frame((first & 0x80) != 0, opcode, payload);
    }
}
