package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Structure3D;
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
import com.ruben.chemistry.server.render.Render2dOptions;
import com.ruben.chemistry.server.render.Render2d;
import com.ruben.chemistry.server.render.Render2dJson;
import com.ruben.chemistry.server.render.Render3dJson;

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
    public record Render2dRequest(Render2dOptions options)
    {
    }

    private static final String MOLECULE_ID_HEADER = "X-Molecule-Id";

    private final MoleculeStore store = new MoleculeStore();
    private final MoleculeRenderer renderer = new MoleculeRenderer();

    @Override
    public void registerHandlers(EndpointRegistry registry)
    {
        registry.register(HttpMethod.POST, "/api/molecules", this::create);
        registry.register(HttpMethod.POST, "/api/molecules/:id/render/2d", this::render2d);
        registry.register(HttpMethod.GET, "/api/molecules/:id/svg/2d", this::svg2d);
        registry.register(HttpMethod.POST, "/api/molecules/:id/structure/2d", this::generateStructure2d);
        registry.register(HttpMethod.GET, "/api/molecules/:id/structure/2d", this::getStructure2d);
        registry.register(HttpMethod.GET, "/api/molecules/:id/metadata", this::metadata);
        registry.register(HttpMethod.POST, "/api/molecules/:id/structure/3d", this::generateStructure3d);
        registry.register(HttpMethod.GET, "/api/molecules/:id/structure/3d", this::getStructure3d);
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

    private void render2d(Request request, ResponseContext responseContext) throws IOException
    {
        Render2d Render2d = draw(request, responseContext);
        responseContext.respond(200, "image/svg+xml", Render2d.svg());
    }

    private void generateStructure2d(Request request, ResponseContext responseContext) throws IOException
    {
        Render2d Render2d = draw(request, responseContext);
        responseContext.respond(200, Render2dJson.of(Render2d.scene()));
    }

    private void getStructure2d(Request request, ResponseContext responseContext) throws IOException
    {
        StoredMolecule stored = find(request);
        responseContext.header(MOLECULE_ID_HEADER, stored.id());
        responseContext.respond(200, Render2dJson.of(latest(stored).scene()));
    }

    /** Draws the molecule in the request's path with the request's options, and makes it the molecule's latest. */
    private Render2d draw(Request request, ResponseContext responseContext)
    {
        StoredMolecule stored = find(request);
        Render2dOptions options = request.hasBody() ? request.body(Render2dRequest.class).options() : null;

        try
        {
            Render2d Render2d = renderer.render2d(stored.molecule(), options == null ? Render2dOptions.defaults() : options);
            stored.setLatest2d(Render2d);
            responseContext.header(MOLECULE_ID_HEADER, stored.id());
            return Render2d;
        }
        catch (RenderException failure)
        {
            throw new HttpException(400, failure.code(), failure.getMessage());
        }
    }

    private void svg2d(Request request, ResponseContext responseContext) throws IOException
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

    private void getStructure3d(Request request, ResponseContext responseContext) throws IOException
    {
        StoredMolecule stored = find(request);
        Map<String, Object> json = stored.latestStructure3d()
                .orElseThrow(() -> new HttpException(404, "NOT_GENERATED", "The molecule has no 3D structure yet"));
        responseContext.header(MOLECULE_ID_HEADER, stored.id());
        responseContext.respond(200, json);
    }

    private void generateStructure3d(Request request, ResponseContext responseContext) throws IOException
    {
        StoredMolecule stored = find(request);
        int conformers = (int) number(request, "conformers", 1, 1, 20);
        Long seed = request.queryParameter("seed").isPresent() ? (Long) number(request, "seed", 0, Long.MIN_VALUE, Long.MAX_VALUE) : null;

        Embedder.Options options = Embedder.Options.DEFAULT.withConformers(conformers);
        if (seed != null)
        {
            options = options.withSeed(seed);
        }
        Embedder.Options chosen = options;

        try
        {
            Structure3D structure = Embedder.embed(stored.molecule(), chosen);
            Map<String, Object> json = Render3dJson.of(stored.molecule(), structure);
            stored.setLatestStructure3d(json);
            responseContext.header(MOLECULE_ID_HEADER, stored.id());
            responseContext.respond(200, json);
        }
        catch (IllegalArgumentException | IllegalStateException failure)
        {
            throw new HttpException(422, "EMBEDDING_FAILED", failure.getMessage());
        }
    }

    private static long number(Request request, String name, long fallback, long least, long most)
    {
        String text = request.queryParameter(name).orElse(null);
        if (text == null)
        {
            return fallback;
        }
        try
        {
            long value = Long.parseLong(text);
            if (value < least || value > most)
            {
                throw new HttpException(400, "INVALID_QUERY", name + ": must be from " + least + " to " + most + ", but was " + value);
            }
            return value;
        }
        catch (NumberFormatException invalid)
        {
            throw new HttpException(400, "INVALID_QUERY", name + ": must be a whole number, but was '" + text + "'");
        }
    }

    private Render2d latest(StoredMolecule stored)
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
