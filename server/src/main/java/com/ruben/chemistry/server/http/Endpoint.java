package com.ruben.chemistry.server.http;

/**
 * A group of related routes.
 */
public abstract class Endpoint
{
    /**
     * Registers this endpoint's routes.
     *
     * @param registry where to register them; it only adds routes
     */
    public abstract void registerHandlers(EndpointRegistry registry);
}
