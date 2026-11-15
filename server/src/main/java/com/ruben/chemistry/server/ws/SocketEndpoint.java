package com.ruben.chemistry.server.ws;

/**
 * A group of related WebSocket routes.
 */
public abstract class SocketEndpoint
{
    /**
     * Registers this endpoint's routes.
     *
     * @param registry where to register them
     */
    public abstract void registerHandlers(SocketRegistry registry);
}
