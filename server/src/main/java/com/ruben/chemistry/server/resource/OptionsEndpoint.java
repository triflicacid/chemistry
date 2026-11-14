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
        registry.register(HttpMethod.GET, "/api/options/2d", this::options2d);
        registry.register(HttpMethod.GET, "/api/options/3d", this::options3d);
        registry.register(HttpMethod.GET, "/api/options/match", this::optionsMatch);
    }

    private void optionsMatch(Request request, ResponseContext responseContext) throws IOException
    {
        responseContext.respond(200, Map.of("options", OptionSchema.optionsMatch()));
    }

    private void options2d(Request request, ResponseContext responseContext) throws IOException
    {
        responseContext.respond(200, Map.of("options", OptionSchema.options2d()));
    }

    private void options3d(Request request, ResponseContext responseContext) throws IOException
    {
        responseContext.respond(200, Map.of("options", OptionSchema.options3d()));
    }
}
