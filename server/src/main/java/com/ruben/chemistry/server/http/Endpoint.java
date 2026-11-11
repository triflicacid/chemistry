package com.ruben.chemistry.server.http;

/**
 * A group of related routes. Needs a public no-argument constructor; it is added to the
 * {@link Server} by class, e.g. {@code server.add(HealthEndpoint.class)}.
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
