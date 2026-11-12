package com.ruben.chemistry.server.http;

import com.ruben.chemistry.server.json.JsonWriter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class ExchangeContext implements ResponseContext
{
    private static final String JSON = "application/json; charset=utf-8";
    private static final String TEXT = "text/plain; charset=utf-8";

    private final HttpExchange exchange;
    private boolean responded;
    private int status;
    private int size;

    ExchangeContext(HttpExchange exchange)
    {
        this.exchange = exchange;
    }

    @Override
    public ResponseContext header(String name, String value)
    {
        exchange.getResponseHeaders().set(name, value);
        return this;
    }

    @Override
    public void respond(int code) throws IOException
    {
        begin();
        status = code;
        exchange.sendResponseHeaders(code, -1);
    }

    @Override
    public void respond(String body) throws IOException
    {
        respond(200, TEXT, body);
    }

    @Override
    public void respond(Map<?, ?> body) throws IOException
    {
        respond(200, body);
    }

    @Override
    public void respond(int code, Map<?, ?> body) throws IOException
    {
        respond(code, JSON, JsonWriter.write(body));
    }

    @Override
    public void respond(int code, String contentType, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        begin();
        status = code;
        size = bytes.length;
        exchange.getResponseHeaders().set("Content-Type", contentType);

        if (bytes.length == 0)
        {
            exchange.sendResponseHeaders(code, -1);
            return;
        }

        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }

    @Override
    public void error(int status, String code, String message) throws IOException
    {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);

        respond(status, Map.of("error", error));
    }

    /**
     * @return the response line to log: {@code <code> <content type> <size>}
     */
    String summary()
    {
        String type = exchange.getResponseHeaders().getFirst("Content-Type");
        return status + " " + (size == 0 || type == null ? "-" : type.split(";")[0]) + " " + size + "B";
    }

    /**
     * @return whether a response has been started
     */
    boolean responded()
    {
        return responded;
    }

    private void begin()
    {
        if (responded)
        {
            throw new IllegalStateException("A response has already been sent");
        }
        responded = true;
    }
}
