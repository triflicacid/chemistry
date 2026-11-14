package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.space.Camera;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.StructureReader;
import com.ruben.chemistry.space.Vec3;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public record Vector(double x, double y, double z)
    {
        Vec3 toVec3()
        {
            return new Vec3(x, y, z);
        }
    }

    /**
     * @param eye where the camera is
     * @param target what it looks at
     * @param up which way is up on the picture
     * @param fieldOfView the angle the view spans in degrees, or 0 or absent for an orthographic view
     */
    public record CameraRequest(Vector eye, Vector target, Vector up, Double fieldOfView)
    {
        Camera toCamera()
        {
            if (eye == null || target == null || up == null)
            {
                throw new HttpException(400, "INVALID_BODY", "camera: eye, target and up are required");
            }
            return new Camera(eye.toVec3(), target.toVec3(), up.toVec3(), fieldOfView == null ? 0 : fieldOfView);
        }
    }

    /**
     * @param mode {@code best} to choose the clearest direction, or {@code force} to draw as {@code camera} sees it (default {@code best})
     * @param conformer which conformer to draw (default 0)
     * @param camera where to look from; required for {@code force}
     * @param options how to draw it (optional)
     * @param atoms draw only the disconnected parts these atoms (by index) are in; every part if absent
     */
    public record ProjectRequest(String mode, Integer conformer, CameraRequest camera, Render2dOptions options, List<Integer> atoms)
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
        registry.register(HttpMethod.POST, "/api/molecules/:id/projection/2d", this::projectStructure2d);
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
        Render2d render = draw(request, responseContext);
        responseContext.respond(200, "image/svg+xml", render.svg());
    }

    private void generateStructure2d(Request request, ResponseContext responseContext) throws IOException
    {
        Render2d render = draw(request, responseContext);
        responseContext.respond(200, Render2dJson.of(render.scene()));
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
            Render2d render = renderer.render2d(stored.molecule(), options == null ? Render2dOptions.defaults() : options);
            stored.setLatest2d(render);
            responseContext.header(MOLECULE_ID_HEADER, stored.id());
            return render;
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
        Structure3D structure = latestStructure3d(stored);
        responseContext.header(MOLECULE_ID_HEADER, stored.id());
        responseContext.respond(200, Render3dJson.of(stored.molecule(), structure));
    }

    private Structure3D latestStructure3d(StoredMolecule stored)
    {
        return stored.latestStructure3d()
                .orElseThrow(() -> new HttpException(404, "NOT_GENERATED", "The molecule has no 3D structure yet"));
    }

    /** Draws the molecule's latest 3D structure flat, either as the given camera sees it or from its best direction. */
    private void projectStructure2d(Request request, ResponseContext responseContext) throws IOException
    {
        StoredMolecule stored = find(request);
        Structure3D structure = latestStructure3d(stored);
        ProjectRequest body = request.body(ProjectRequest.class);

        boolean forced = switch (body.mode() == null ? "best" : body.mode())
        {
            case "best" -> false;
            case "force" -> true;
            default -> throw new HttpException(400, "INVALID_BODY", "mode: must be best or force, but was '" + body.mode() + "'");
        };
        Camera camera = null;
        if (forced)
        {
            if (body.camera() == null)
            {
                throw new HttpException(400, "INVALID_BODY", "camera: is required when mode is force");
            }
            camera = body.camera().toCamera();
        }
        int conformer = body.conformer() == null ? 0 : body.conformer();
        if (body.atoms() != null && body.atoms().isEmpty())
        {
            throw new HttpException(400, "INVALID_BODY", "atoms: must not be empty");
        }
        Set<Integer> only = body.atoms() == null ? null : new HashSet<>(body.atoms());

        try
        {
            Render2d render = renderer.project2d(stored.molecule(), structure, conformer, camera,
                    body.options() == null ? Render2dOptions.defaults() : body.options(), only);
            stored.setLatest2d(render);
            responseContext.header(MOLECULE_ID_HEADER, stored.id());
            Map<String, Object> json = Render2dJson.of(render.scene());
            json.put("generated", generatedNotation(stored, structure, conformer, only));
            responseContext.respond(200, json);
        }
        catch (RenderException failure)
        {
            throw new HttpException(400, failure.code(), failure.getMessage());
        }
    }

    private static Map<String, Object> generatedNotation(StoredMolecule stored, Structure3D structure, int conformer, Set<Integer> only)
    {
        Structure3D shown = only == null ? structure : structure.restrictedTo(only);
        StructureReader.Reading reading;
        try
        {
            reading = StructureReader.read(shown, conformer);
        }
        catch (RuntimeException unreadable)
        {
            return null;
        }
        String canonical = new SmilesWriter(reading.molecule()).setRanker(new CanonicalRanker()).write();

        return Map.of(
                "smiles", SmilesWriter.write(reading.molecule()),
                "canonical_smiles", canonical,
                "matches_source", canonical.equals(stored.canonicalSmiles()),
                "issues", reading.issues().stream().map(StructureReader.Issue::message).toList()
        );
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
            stored.setLatestStructure3d(structure);
            responseContext.header(MOLECULE_ID_HEADER, stored.id());
            responseContext.respond(200, Render3dJson.of(stored.molecule(), structure));
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
