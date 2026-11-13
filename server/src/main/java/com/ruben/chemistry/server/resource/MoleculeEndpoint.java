package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.server.http.Endpoint;
import com.ruben.chemistry.server.http.EndpointRegistry;
import com.ruben.chemistry.server.http.HttpException;
import com.ruben.chemistry.server.http.HttpMethod;
import com.ruben.chemistry.server.http.Request;
import com.ruben.chemistry.server.http.ResponseContext;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.server.render.MoleculeRenderer;
import com.ruben.chemistry.server.render.RenderException;
import com.ruben.chemistry.server.render.RenderOptions;
import com.ruben.chemistry.server.render.Rendering;
import com.ruben.chemistry.server.render.SceneJson;

import java.io.IOException;
import java.util.Map;

public final class MoleculeEndpoint extends Endpoint
{
    /**
     * @param smiles the molecule
     */
    public record CreateRequest(String smiles)
    {
    }

    /**
     * @param options how to draw it (optional)
     */
    public record RenderRequest(RenderOptions options)
    {
    }

    private static final String MOLECULE_ID_HEADER = "X-Molecule-Id";

    private final MoleculeStore store = new MoleculeStore();
    private final MoleculeRenderer renderer = new MoleculeRenderer();

    @Override
    public void registerHandlers(EndpointRegistry registry)
    {
        registry.register(HttpMethod.POST, "/api/molecules", this::create);
        registry.register(HttpMethod.POST, "/api/molecules/:id/render", this::render);
        registry.register(HttpMethod.GET, "/api/molecules/:id/svg", this::svg);
        registry.register(HttpMethod.POST, "/api/molecules/:id/scene", this::renderScene);
        registry.register(HttpMethod.GET, "/api/molecules/:id/scene", this::scene);
        registry.register(HttpMethod.GET, "/api/molecules/:id/metadata", this::metadata);
    }

    private void create(Request request, ResponseContext responseContext) throws IOException
    {
        String smiles = request.body(CreateRequest.class).smiles();
        if (smiles == null || smiles.isBlank())
        {
            throw new HttpException(400, "INVALID_BODY", "smiles: is required");
        }
        smiles = smiles.trim();

        try
        {
            StoredMolecule stored = store.add(renderer.parse(smiles));
            responseContext.respond(201, Map.of(
                    "id", stored.id(),
                    "smiles", stored.smiles(),
                    "canonical_smiles", stored.canonicalSmiles()));
        }
        catch (RenderException failure)
        {
            throw new HttpException(400, failure.code(), failure.getMessage());
        }
    }

    private void render(Request request, ResponseContext responseContext) throws IOException
    {
        Rendering rendering = draw(request, responseContext);
        responseContext.respond(200, "image/svg+xml", rendering.svg());
    }

    private void renderScene(Request request, ResponseContext responseContext) throws IOException
    {
        Rendering rendering = draw(request, responseContext);
        responseContext.respond(200, SceneJson.of(rendering.scene()));
    }

    private void scene(Request request, ResponseContext responseContext) throws IOException
    {
        StoredMolecule stored = find(request);
        responseContext.header(MOLECULE_ID_HEADER, stored.id());
        responseContext.respond(200, SceneJson.of(latest(stored).scene()));
    }

    /** Draws the molecule in the request's path with the request's options, and makes it the molecule's latest. */
    private Rendering draw(Request request, ResponseContext responseContext)
    {
        StoredMolecule stored = find(request);
        RenderOptions options = request.hasBody() ? request.body(RenderRequest.class).options() : null;

        try
        {
            Rendering rendering = renderer.render(stored.molecule(), options == null ? RenderOptions.defaults() : options);
            stored.setLatest(rendering);
            responseContext.header(MOLECULE_ID_HEADER, stored.id());
            return rendering;
        }
        catch (RenderException failure)
        {
            throw new HttpException(400, failure.code(), failure.getMessage());
        }
    }

    private void svg(Request request, ResponseContext responseContext) throws IOException
    {
        StoredMolecule stored = find(request);
        responseContext.header(MOLECULE_ID_HEADER, stored.id());
        responseContext.respond(200, "image/svg+xml", latest(stored).svg());
    }

    private void metadata(Request request, ResponseContext responseContext) throws IOException
    {
        StoredMolecule stored = find(request);
        responseContext.header(MOLECULE_ID_HEADER, stored.id());
        responseContext.respond(200, stored.metadata());
    }

    private Rendering latest(StoredMolecule stored)
    {
        return stored.latest()
                .orElseThrow(() -> new HttpException(404, "NOT_RENDERED", "The molecule has not been rendered yet"));
    }

    private StoredMolecule find(Request request)
    {
        String id = request.pathParameter("id");
        return store.find(id)
                .orElseThrow(() -> new HttpException(404, "MOLECULE_NOT_FOUND", "No molecule has id " + id));
    }
}
