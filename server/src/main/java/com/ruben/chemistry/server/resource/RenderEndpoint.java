package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.http.Context;
import com.ruben.chemistry.server.http.Endpoint;
import com.ruben.chemistry.server.http.EndpointRegistry;
import com.ruben.chemistry.server.http.HttpException;
import com.ruben.chemistry.server.http.HttpMethod;
import com.ruben.chemistry.server.http.Request;

import java.io.IOException;

public final class RenderEndpoint extends Endpoint
{
    /**
     * The request body.
     *
     * @param smiles the molecule to draw
     */
    public record RenderRequest(String smiles)
    {
    }

    private static final String SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 120" width="200" height="120">
              <text x="100" y="60" font-family="monospace" font-size="72" text-anchor="middle"
                    dominant-baseline="central" transform="rotate(90 100 60)">:3</text>
            </svg>
            """;

    @Override
    public void registerHandlers(EndpointRegistry registry)
    {
        registry.register(HttpMethod.POST, "/api/render", this::handleRender);
    }

    void handleRender(Request request, Context context) throws IOException
    {
        RenderRequest body = request.body(RenderRequest.class);
        if (body.smiles() == null || body.smiles().isBlank())
        {
            throw new HttpException(400, "INVALID_BODY", "smiles: is required");
        }

        context.respond(200, "image/svg+xml", SVG);
    }
}
