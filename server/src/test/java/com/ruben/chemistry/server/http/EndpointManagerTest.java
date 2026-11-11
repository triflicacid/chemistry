package com.ruben.chemistry.server.http;

import com.ruben.chemistry.server.json.Json;
import com.ruben.chemistry.server.json.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndpointManagerTest
{
    private static HttpServer server;
    private static String base;
    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws IOException
    {
        EndpointManager endpoint = new EndpointManager(100);
        endpoint.register(HttpMethod.GET, "/ping", (request, context) -> context.respond(204));
        endpoint.register(HttpMethod.GET, "/text", (request, context) -> context.respond("hi"));
        endpoint.register(HttpMethod.GET, "/items/:id/", (request, context) ->
        {
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("id", request.pathParameter("id"));
            reply.put("path", request.path());
            reply.put("query", request.query());
            reply.put("agent", request.header("X-Custom").orElse(null));
            context.respond(reply);
        });
        endpoint.register(HttpMethod.POST, "/echo", (request, context) -> context.respond(201, request.requireBody()));
        endpoint.register(HttpMethod.GET, "/fail", (request, context) ->
        {
            throw new HttpException(418, "TEAPOT", "short and stout");
        });
        endpoint.register(HttpMethod.GET, "/crash", (request, context) ->
        {
            throw new IllegalStateException("boom");
        });
        endpoint.register(HttpMethod.GET, "/silent", (request, context) ->
        {
        });

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        endpoint.attach(server);
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop()
    {
        server.stop(0);
    }

    private static HttpResponse<String> send(String method, String path, String body, String... headers) throws Exception
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (headers.length > 0)
        {
            builder.headers(headers);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void should_answer_with_a_status_alone() throws Exception
    {
        assertThat(send("GET", "/ping", null).statusCode()).isEqualTo(204);
    }

    @Test
    void should_answer_with_text() throws Exception
    {
        HttpResponse<String> response = send("GET", "/text", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("hi");
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("text/plain"));
    }

    @Test
    void should_pass_path_parameters_query_and_headers() throws Exception
    {
        HttpResponse<String> response = send("GET", "/items/a%20b/?x=y&z=1&x=ignored&flag", null, "x-custom", "yes");
        Map<String, Object> body = JsonParser.parse(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) Json.extractProperty(body, "id")).isEqualTo("a b");
        assertThat((String) Json.extractProperty(body, "path")).isEqualTo("/items/a b");
        assertThat((String) Json.extractProperty(body, "query", "x")).isEqualTo("y");
        assertThat((String) Json.extractProperty(body, "query", "z")).isEqualTo("1");
        assertThat((String) Json.extractProperty(body, "query", "flag")).isEmpty();
        assertThat((String) Json.extractProperty(body, "agent")).isEqualTo("yes");
    }

    @Test
    void should_parse_a_json_body() throws Exception
    {
        HttpResponse<String> response = send("POST", "/echo", "{\"a\":[1,2]}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).isEqualTo("{\"a\":[1,2]}");
    }

    @Test
    void should_reject_bad_bodies() throws Exception
    {
        assertThat(send("POST", "/echo", "{oops").statusCode()).isEqualTo(400);
        assertThat(send("POST", "/echo", "[1]").statusCode()).isEqualTo(400);
        assertThat(send("POST", "/echo", null).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/echo", "{\"a\":\"" + "x".repeat(200) + "\"}").statusCode()).isEqualTo(413);
    }

    @Test
    void should_answer_404_for_an_unknown_path() throws Exception
    {
        assertThat(send("GET", "/nope", null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/items", null).statusCode()).isEqualTo(404);
    }

    @Test
    void should_answer_405_with_allow_for_the_wrong_method() throws Exception
    {
        HttpResponse<String> response = send("DELETE", "/echo", null);

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow")).hasValue("POST");
    }

    @Test
    void should_answer_an_http_exception_with_its_own_response() throws Exception
    {
        HttpResponse<String> response = send("GET", "/fail", null);

        assertThat(response.statusCode()).isEqualTo(418);
        assertThat((String) Json.extractProperty(JsonParser.parse(response.body()), "error", "code")).isEqualTo("TEAPOT");
    }

    @Test
    void should_answer_500_for_other_failures() throws Exception
    {
        assertThat(send("GET", "/crash", null).statusCode()).isEqualTo(500);
        assertThat(send("GET", "/silent", null).statusCode()).isEqualTo(500);
    }

    @Test
    void should_reject_bad_registrations()
    {
        EndpointManager endpoint = new EndpointManager();
        endpoint.register(HttpMethod.GET, "/a/:x", (request, context) -> { });

        assertThatThrownBy(() -> endpoint.register(HttpMethod.GET, "/a/:y/", (request, context) -> { }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> endpoint.register(HttpMethod.GET, "a", (request, context) -> { }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> endpoint.register(HttpMethod.GET, "/:", (request, context) -> { }))
                .isInstanceOf(IllegalArgumentException.class);
        endpoint.register(HttpMethod.POST, "/a/:x", (request, context) -> { });
    }
}
