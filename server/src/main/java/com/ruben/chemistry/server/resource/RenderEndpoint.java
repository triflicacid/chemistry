package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.http.Context;
import com.ruben.chemistry.server.http.Endpoint;
import com.ruben.chemistry.server.http.EndpointRegistry;
import com.ruben.chemistry.server.http.HttpException;
import com.ruben.chemistry.server.http.HttpMethod;
import com.ruben.chemistry.server.http.Request;
import com.ruben.chemistry.server.render.RenderException;
import com.ruben.chemistry.server.render.SmilesRenderer;

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

    private final SmilesRenderer renderer = new SmilesRenderer();

    @Override
    public void registerHandlers(EndpointRegistry registry)
    {
        registry.register(HttpMethod.POST, "/api/render", this::render);
    }

    private void render(Request request, Context context) throws IOException
    {
        RenderRequest body = request.body(RenderRequest.class);
        if (body.smiles() == null || body.smiles().isBlank())
        {
            throw new HttpException(400, "INVALID_BODY", "smiles: is required");
        }

        try
        {
            context.respond(200, "image/svg+xml", renderer.render(body.smiles().trim()));
        }
        catch (RenderException failure)
        {
            throw new HttpException(400, failure.code(), failure.getMessage());
        }
    }
}
