package com.ruben.chemistry.server.http;

import com.ruben.chemistry.server.json.JsonException;
import com.ruben.chemistry.server.json.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Routes requests to {@link Handler}s by method and path.
 * {@link Endpoint}s register their routes here.
 * <p>
 * A route's path is a series of segments; a segment written {@code :name} matches any
 * one segment and exposes it as {@link Request#pathParameter(String)}, e.g.
 * <pre>{@code
 * manager.register(HttpMethod.GET, "/api/molecules/:id/", handler);
 * }</pre>
 * <p>
 * A request body, if any, is parsed as JSON before the handler runs; invalid JSON
 * answers 400 and a body over the limit 413.
 */
final class EndpointManager implements EndpointRegistry
{
    /** The largest request body accepted, in bytes. */
    public static final int DEFAULT_MAX_BODY = 1 << 20;

    private final List<Route> routes = new ArrayList<>();
    private final int maxBody;

    /**
     * A manager accepting bodies up to {@link #DEFAULT_MAX_BODY} bytes.
     */
    public EndpointManager()
    {
        this(DEFAULT_MAX_BODY);
    }

    /**
     * @param maxBody the largest request body accepted, in bytes
     */
    public EndpointManager(int maxBody)
    {
        if (maxBody < 0)
        {
            throw new IllegalArgumentException("The body limit cannot be negative, but was " + maxBody);
        }
        this.maxBody = maxBody;
    }

    /**
     * Lets each endpoint register its routes. An endpoint is given only an
     * {@link EndpointRegistry} view, so it cannot reach the rest of this manager.
     *
     * @param endpoints the endpoints to register
     * @return this
     */
    public EndpointManager registerAll(Iterable<? extends Endpoint> endpoints)
    {
        for (Endpoint endpoint : endpoints)
        {
            endpoint.registerHandlers(this);
        }
        return this;
    }

    @Override
    public void register(HttpMethod method, String path, Handler handler)
    {
        if (!path.startsWith("/"))
        {
            throw new IllegalArgumentException("A route must start with '/', but was '" + path + "'");
        }

        List<String> segments = segments(path);
        for (String segment : segments)
        {
            if (segment.equals(":"))
            {
                throw new IllegalArgumentException("A path parameter needs a name, in '" + path + "'");
            }
        }

        for (Route existing : routes)
        {
            if (existing.method == method && existing.sameShape(segments))
            {
                throw new IllegalArgumentException(method + " " + path + " is already registered");
            }
        }

        routes.add(new Route(method, segments, handler));
    }

    /**
     * Sends every request the server receives through this manager.
     *
     * @param server the server to serve from
     */
    public void attach(HttpServer server)
    {
        server.createContext("/", this::dispatch);
    }

    private void dispatch(HttpExchange exchange) throws IOException
    {
        ExchangeContext context = new ExchangeContext(exchange);
        String line = exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath();
        System.out.println("--> " + line);

        try (exchange)
        {
            try
            {
                handle(exchange, context);
            }
            catch (HttpException failure)
            {
                if (!context.responded())
                {
                    context.error(failure.status(), failure.code(), failure.getMessage());
                }
            }
            catch (Exception failure)
            {
                System.err.println("Unhandled error for " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
                failure.printStackTrace();

                if (!context.responded())
                {
                    context.error(500, "INTERNAL", "Internal server error");
                }
            }

            if (!context.responded())
            {
                context.error(500, "INTERNAL", "The handler sent no response");
            }

            System.out.println("<-- " + line + " " + context.summary());
        }
    }

    private void handle(HttpExchange exchange, ExchangeContext context) throws Exception
    {
        List<String> segments = segments(decodeRawPath(exchange.getRequestURI().getRawPath()));
        Optional<HttpMethod> method = HttpMethod.of(exchange.getRequestMethod());

        Route matched = null;
        Map<String, String> parameters = null;
        Set<HttpMethod> allowed = EnumSet.noneOf(HttpMethod.class);

        for (Route route : routes)
        {
            Map<String, String> found = route.match(segments);
            if (found == null)
            {
                continue;
            }

            allowed.add(route.method);
            if (matched == null && method.isPresent() && route.method == method.get())
            {
                matched = route;
                parameters = found;
            }
        }

        if (matched == null)
        {
            if (allowed.isEmpty())
            {
                context.error(404, "NOT_FOUND", "No such path");
                return;
            }

            context.header("Allow", allowed.stream().map(Enum::name).sorted().collect(Collectors.joining(", ")));
            context.error(405, "METHOD_NOT_ALLOWED", "This path does not accept " + exchange.getRequestMethod());
            return;
        }

        byte[] raw = exchange.getRequestBody().readNBytes(maxBody + 1);
        if (raw.length > maxBody)
        {
            throw new HttpException(413, "BODY_TOO_LARGE", "The request body exceeds " + maxBody + " bytes");
        }

        String text = new String(raw, StandardCharsets.UTF_8);
        Map<String, Object> body = null;
        if (!text.isBlank())
        {
            try
            {
                body = JsonParser.parse(text);
            }
            catch (JsonException invalid)
            {
                throw new HttpException(400, "INVALID_JSON", invalid.getMessage());
            }
        }

        Request request = new Request(
                matched.method,
                "/" + String.join("/", segments),
                parameters,
                parseQuery(exchange.getRequestURI().getRawQuery()),
                exchange.getRequestHeaders(),
                body);

        matched.handler.handle(request, context);
    }

    /**
     * Splits a path into segments, ignoring empty ones (so a trailing slash is ignored).
     */
    private static List<String> segments(String path)
    {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/"))
        {
            if (!segment.isEmpty())
            {
                segments.add(segment);
            }
        }
        return segments;
    }

    /**
     * Decodes each segment of a raw path separately, so an encoded slash stays inside its segment.
     */
    private static String decodeRawPath(String rawPath)
    {
        if (rawPath == null)
        {
            return "/";
        }

        StringBuilder decoded = new StringBuilder();
        for (String segment : rawPath.split("/", -1))
        {
            if (segment.isEmpty())
            {
                decoded.append('/');
                continue;
            }
            decoded.append(decode(segment.replace("+", "%2B")).replace("/", "%2F")).append('/');
        }
        return decoded.toString();
    }

    private static Map<String, String> parseQuery(String rawQuery)
    {
        Map<String, String> query = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty())
        {
            return query;
        }

        for (String pair : rawQuery.split("&"))
        {
            if (pair.isEmpty())
            {
                continue;
            }

            int equals = pair.indexOf('=');
            String name = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
            query.putIfAbsent(name, value);
        }
        return query;
    }

    private static String decode(String encoded)
    {
        try
        {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException malformed)
        {
            throw new HttpException(400, "BAD_REQUEST", "Malformed percent-encoding in the request URL");
        }
    }

    private record Route(HttpMethod method, List<String> segments, Handler handler)
    {
        Map<String, String> match(List<String> given)
        {
            if (given.size() != segments.size())
            {
                return null;
            }

            Map<String, String> parameters = new LinkedHashMap<>();
            for (int i = 0; i < segments.size(); i++)
            {
                String expected = segments.get(i);
                String actual = given.get(i);

                if (expected.startsWith(":"))
                {
                    parameters.put(expected.substring(1), actual);
                }
                else if (!expected.equals(actual))
                {
                    return null;
                }
            }
            return parameters;
        }

        boolean sameShape(List<String> other)
        {
            if (other.size() != segments.size())
            {
                return false;
            }

            for (int i = 0; i < segments.size(); i++)
            {
                String mine = segments.get(i);
                String theirs = other.get(i);
                boolean mineVariable = mine.startsWith(":");

                if (mineVariable != theirs.startsWith(":") || (!mineVariable && !mine.equals(theirs)))
                {
                    return false;
                }
            }
            return true;
        }
    }
}
