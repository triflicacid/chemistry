package com.ruben.chemistry.server.http;

@FunctionalInterface
public interface EndpointRegistry
{
    /**
     * Adds a route.
     *
     * @param method the method it answers
     * @param path the path
     * @param handler what answers it
     * @throws IllegalArgumentException if the path is malformed or that method and path are already registered
     */
    void register(HttpMethod method, String path, Handler handler);
}
