package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.http.Endpoint;
import com.ruben.chemistry.server.http.EndpointRegistry;
import com.ruben.chemistry.server.http.HttpMethod;

public final class HealthEndpoint extends Endpoint
{
    @Override
    public void registerHandlers(EndpointRegistry registry)
    {
        registry.register(HttpMethod.GET, "/api/health", (request, context) -> context.respond(200));
    }
}
