package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.http.ResponseContext;
import com.ruben.chemistry.server.http.Endpoint;
import com.ruben.chemistry.server.http.EndpointRegistry;
import com.ruben.chemistry.server.http.HttpMethod;
import com.ruben.chemistry.server.http.Request;
import com.ruben.chemistry.server.render.OptionSchema;

import java.io.IOException;
import java.util.Map;

public final class OptionsEndpoint extends Endpoint
{
    @Override
    public void registerHandlers(EndpointRegistry registry)
    {
        registry.register(HttpMethod.GET, "/api/options", this::options);
    }

    private void options(Request request, ResponseContext responseContext) throws IOException
    {
        responseContext.respond(200, Map.of("options", OptionSchema.options()));
    }
}
