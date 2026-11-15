package com.ruben.chemistry.server.ws;

@FunctionalInterface
public interface SocketRegistry
{
    /**
     * Adds a route. A connection opened on a matching path gets a {@link WebSocketListener}
     * of its own from {@code listeners}.
     *
     * @param path the path, where a {@code :name} segment matches any one segment
     * @param listeners makes a listener per connection
     * @throws IllegalArgumentException if the path is malformed or already registered
     */
    void register(String path, SocketListenerFactory listeners);
}
