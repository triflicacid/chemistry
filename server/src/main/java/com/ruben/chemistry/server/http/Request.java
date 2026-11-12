package com.ruben.chemistry.server.http;

import com.ruben.chemistry.server.json.JsonException;
import com.ruben.chemistry.server.json.JsonNames;
import com.ruben.chemistry.server.json.RecordConverter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

public final class Request
{
    private final HttpMethod method;
    private final String path;
    private final Map<String, String> pathParameters;
    private final Map<String, String> query;
    private final Map<String, List<String>> headers;
    private final Map<String, Object> body;
    private final boolean hasBody;

    Request(
            HttpMethod method,
            String path,
            Map<String, String> pathParameters,
            Map<String, String> query,
            Map<String, List<String>> headers,
            Map<String, Object> body)
    {
        this.method = method;
        this.path = path;
        this.pathParameters = Collections.unmodifiableMap(pathParameters);
        this.query = Collections.unmodifiableMap(query);
        this.headers = caseInsensitive(headers);
        this.hasBody = body != null;
        this.body = body;
    }

    private static Map<String, List<String>> caseInsensitive(Map<String, List<String>> headers)
    {
        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * @return the request method
     */
    public HttpMethod method()
    {
        return method;
    }

    /**
     * @return the path asked for
     */
    public String path()
    {
        return path;
    }

    /**
     * @return the values of the {@code :name} segments of the route that matched
     */
    public Map<String, String> pathParameters()
    {
        return pathParameters;
    }

    /**
     * @param name a {@code :name} segment of the route
     * @return its value
     * @throws IllegalArgumentException if the route has no such segment
     */
    public String pathParameter(String name)
    {
        String value = pathParameters.get(name);
        if (value == null)
        {
            throw new IllegalArgumentException("The route has no path parameter '" + name + "'");
        }
        return value;
    }

    /**
     * @return the query parameters ({@code ?x=y&z=1})
     */
    public Map<String, String> query()
    {
        return query;
    }

    /**
     * @param name a query parameter
     * @return its value, if given
     */
    public Optional<String> queryParameter(String name)
    {
        return Optional.ofNullable(query.get(name));
    }

    /**
     * @return every header sent, looked up without regard to case
     */
    public Map<String, List<String>> headers()
    {
        return headers;
    }

    /**
     * @param name a header name, in any case
     * @return its first value, if sent
     */
    public Optional<String> header(String name)
    {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    /**
     * @return whether the request carried a body
     */
    public boolean hasBody()
    {
        return hasBody;
    }

    /**
     * @return the body parsed as a JSON object, or empty if there was no body
     */
    public Optional<Map<String, Object>> body()
    {
        return Optional.ofNullable(body);
    }

    /**
     * @param type the record the body should fit
     * @param <T> the record type
     * @return the body bound to {@code type}
     * @throws HttpException 400 if there is no body or it does not fit {@code type}
     * @see RecordConverter#bind
     */
    public <T extends Record> T body(Class<T> type)
    {
        try
        {
            return RecordConverter.bind(requireBody(), type, JsonNames::of);
        }
        catch (JsonException invalid)
        {
            throw new HttpException(400, "INVALID_BODY", invalid.getMessage());
        }
    }

    /**
     * @return the body, for a handler that requires one
     * @throws HttpException 400 if there is no body
     */
    public Map<String, Object> requireBody()
    {
        if (body == null)
        {
            throw new HttpException(400, "MISSING_BODY", "Expected a JSON object in the request body");
        }
        return body;
    }
}
