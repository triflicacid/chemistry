package com.ruben.chemistry.server.http;

import java.util.Locale;
import java.util.Optional;

/**
 * An HTTP request method.
 */
public enum HttpMethod
{
    GET,
    POST,
    PUT,
    PATCH,
    DELETE;

    /**
     * @param name a method name as sent over HTTP
     * @return the matching method, or empty if it is not one this server routes
     */
    public static Optional<HttpMethod> of(String name)
    {
        try
        {
            return Optional.of(valueOf(name.toUpperCase(Locale.ROOT)));
        }
        catch (IllegalArgumentException unknown)
        {
            return Optional.empty();
        }
    }
}
