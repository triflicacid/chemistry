package com.ruben.chemistry.cli;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.render.three.SceneRenderer3D;
import com.ruben.chemistry.render.three.CameraInfo;
import com.ruben.chemistry.render.three.RenderStyle3D;
import com.ruben.chemistry.render.svg.SvgWriter;
import com.ruben.chemistry.space.Camera;
import com.ruben.chemistry.space.Diagnostics;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Projection;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws a molecule written in SMILES in three dimensions, as an SVG document seen from a camera.
 * <p>
 * Run it through Gradle, e.g.
 * <pre>{@code
 * ./gradlew :backend:render3d --args="C1CC2CCC1CC2 --out=bicyclo.svg"
 * ./gradlew :backend:render3d --args="CCO --style=spacefill --yaw=90 --pitch=0"
 * ./gradlew :backend:render3d --args="CCO --camera=5,5,5 --look-at=0,0,0 --fov=0"
 * }</pre>
 * Without an {@code --out} the document goes to standard output.
 */
final class Render3d
{
    static final String STANDARD_OUTPUT = "-";
    private static final double FIT_PADDING = 1.5;

    static final CliOption<String> OUT = CliOption.string(
            "out", "path", "where to write the document, or - for standard output", STANDARD_OUTPUT);

    private static final CliOption<RenderStyle3D.Mode> STYLE = CliOption.choice(
            "style",
            "how to draw atoms and bonds",
            modes(),
            () -> RenderStyle3D.Mode.BALL_AND_STICK
    ).withDefaultText("ball-and-stick");

    private static final CliOption<Boolean> HYDROGENS = CliOption.flag(
            "hydrogens", "whether to draw hydrogens", true);

    private static final CliOption<Double> SCALE = CliOption.number(
            "scale", "px", "how many pixels an angstrom is at the point looked at", 50);

    private static final CliOption<Boolean> DEPTH_CUE = CliOption.flag(
            "depth-cue", "whether things further away fade", true);

    private static final CliOption<Vec3> CAMERA = CliOption.parsed(
            "camera", "x,y,z", "where the camera is, instead of orbiting with --yaw, --pitch and --distance",
            Render3d::vector, null);

    private static final CliOption<Vec3> LOOK_AT = CliOption.parsed(
            "look-at", "x,y,z", "the point the camera looks at",
            Render3d::vector, () -> new Vec3(0, 0, 0)).withDefaultText("0,0,0");

    private static final CliOption<Vec3> UP = CliOption.parsed(
            "up", "x,y,z", "the direction that is up on the picture; needs --camera",
            Render3d::vector, () -> new Vec3(0, 1, 0)).withDefaultText("0,1,0")
            .withRequirement(options -> !options.has("up") || options.has("camera"), "--up needs --camera");

    private static final CliOption<Double> FOV = CliOption.number(
            "fov", "degrees", "the angle the view spans, or 0 for an orthographic view with no perspective", 30);

    private static final CliOption<Double> YAW = orbitOption(
            "yaw", "degrees", "how far round the vertical axis the camera orbits; 0 looks down the z axis", 30);
    private static final CliOption<Double> PITCH = orbitOption(
            "pitch", "degrees", "how far above the molecule's level the camera orbits", 20);
    private static final CliOption<Double> DISTANCE = orbitOption(
            "distance", "angstroms", "how far the camera is from the molecule; 0 fits the molecule in view", 0);

    private static final CliOption<Integer> CONFORMERS = CliOption.parsed(
            "conformers", "n", "how many different shapes to search for",
            input -> positive("conformers", input), () -> 1);

    private static final CliOption<Integer> CONFORMER = CliOption.parsed(
            "conformer", "n", "which of the shapes found to draw, counting from 1, the least strained",
            input -> positive("conformer", input), () -> 1);

    private static final CliOption<Long> SEED = CliOption.parsed(
            "seed", "n", "a number that makes the search repeatable",
            input -> {
                try
                {
                    return Long.parseLong(input);
                }
                catch (NumberFormatException invalid)
                {
                    throw new IllegalArgumentException("--seed must be a whole number, but was '" + input + "'");
                }
            }, null);

    private static final CliOption<Boolean> CHARGES = CliOption.flag(
            "charges", "whether atoms with a formal charge are labelled with it", false);

    private static final CliOption<Boolean> AXES = CliOption.flag(
            "axes", "whether to draw the x (red), y (green) and z (blue) axes from the origin", false);

    private static final CliOption<Boolean> CAMERA_INFO = CliOption.flag(
            "camera-info", "whether to add a gizmo of the axes and a readout of the camera below the picture", false);

    /** What --debug asks for: overlays to draw, and whether to print a text report. */
    private record DebugChoice(Set<RenderStyle3D.Debug> overlays, boolean report)
    {
    }

    private static final CliOption<DebugChoice> DEBUG = CliOption.parsed(
            "debug", "list",
            "what to show of the geometry, comma separated: bonds, angles, torsions, clashes, strain, lone-pairs "
                    + "(or all of those), and report to print every term past its tolerance to standard error",
            Render3d::debug, () -> new DebugChoice(Set.of(), false)).withDefaultText("none");

    private static final CliOption<Double> DEBUG_THRESHOLD = CliOption.number(
            "debug-threshold", "factor",
            "how many times the usual tolerances a term must stray before it is shown; 0 shows every term", 1);

    private static final List<CliOption<?>> OPTIONS = List.of(
            OUT, STYLE, HYDROGENS, SCALE, DEPTH_CUE, CAMERA, LOOK_AT, UP, FOV, YAW, PITCH, DISTANCE,
            CONFORMERS, CONFORMER, SEED, CHARGES, AXES, CAMERA_INFO, DEBUG, DEBUG_THRESHOLD);

    static final List<CliOption<?>> DEFINITIONS = allDefinitions();

    private static final String USAGE = CliParser.usage("Usage: render3d <smiles> [options]", DEFINITIONS);

    private Render3d()
    {
    }

    public static void main(String[] args) throws IOException
    {
        List<String> arguments = List.of(args);
        String destination;
        String document;

        try
        {
            CliOptions options = CliParser.parse(arguments, DEFINITIONS);
            if (options.get(CommandOptions.HELP))
            {
                System.out.println(USAGE);
                System.exit(0);
            }

            destination = options.get(OUT);
            document = render(options.onlySubject(), options);
        }
        catch (IllegalArgumentException wrong)
        {
            System.err.println(wrong.getMessage());
            System.err.println();
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        if (destination.equals(STANDARD_OUTPUT))
        {
            System.out.print(document);
        }
        else
        {
            Path path = Path.of(destination);
            Files.writeString(path, document, StandardCharsets.UTF_8);
            System.out.println("Wrote " + path.toAbsolutePath());
        }
    }

    /**
     * @param arguments the SMILES string to draw, followed by any options
     * @return the SVG document
     * @throws IllegalArgumentException if the arguments name no one molecule, or an option is not understood
     */
    public static String render(List<String> arguments)
    {
        CliOptions options = CliParser.parse(arguments, DEFINITIONS);
        return render(options.onlySubject(), options);
    }

    static String render(String smiles, CliOptions options)
    {
        View view = prepare(Molecule.fromSmiles(smiles), options);
        return SvgWriter.write(SceneRenderer3D.render(view.layout(), view.style()));
    }

    /** A molecule embedded and seen by a camera, ready to be drawn. */
    record View(Projection layout, RenderStyle3D style)
    {
    }

    /**
     * Embeds a molecule and works out how it is seen, printing any debug report or camera readout to standard error.
     *
     * @param molecule the molecule to embed
     * @param options the parsed options, which {@link #DEFINITIONS} understands
     * @return what to draw, and how
     */
    static View prepare(Molecule molecule, CliOptions options)
    {
        Embedder.Options embedding = Embedder.Options.DEFAULT.withConformers(options.get(CONFORMERS));
        if (options.get(SEED) != null)
        {
            embedding = embedding.withSeed(options.get(SEED));
        }
        Structure3D structure = Embedder.embed(molecule, embedding);

        int chosen = options.get(CONFORMER);
        if (chosen > structure.conformers().size())
        {
            throw new IllegalArgumentException("--conformer is " + chosen + ", but only " + structure.conformers().size() + " found");
        }

        Camera camera = camera(options, structure.conformers().get(chosen - 1));
        DebugChoice debug = options.get(DEBUG);
        Diagnostics diagnostics = null;
        if (!debug.overlays().isEmpty() || debug.report())
        {
            diagnostics = Diagnostics.of(molecule, structure, chosen - 1);
        }
        if (debug.report())
        {
            System.err.print(diagnostics.report(options.get(DEBUG_THRESHOLD)));
        }
        if (options.get(CAMERA_INFO))
        {
            System.err.println(CameraInfo.command(camera));
        }
        Projection layout = Projection.of(structure, chosen - 1, camera, diagnostics);

        RenderStyle3D style = RenderStyle3D.defaults()
                .withMode(options.get(STYLE))
                .withHydrogens(options.get(HYDROGENS))
                .withPixelsPerAngstrom(options.get(SCALE))
                .withMargin(options.get(CommandOptions.MARGIN))
                .withBackground(options.get(CommandOptions.BACKGROUND))
                .withDepthCue(options.get(DEPTH_CUE))
                .withCharges(options.get(CHARGES))
                .withAxes(options.get(AXES))
                .withCameraInfo(options.get(CAMERA_INFO))
                .withDebug(debug.overlays())
                .withDebugScale(options.get(DEBUG_THRESHOLD))
                .withMetadata(options.get(CommandOptions.METADATA));
        return new View(layout, style);
    }

    private static Camera camera(CliOptions options, Structure3D.Conformer conformer)
    {
        double fov = options.get(FOV);
        Vec3 target = options.get(LOOK_AT);
        if (options.has("camera"))
        {
            return new Camera(options.get(CAMERA), target, options.get(UP), fov);
        }

        double distance = options.get(DISTANCE);
        if (distance < 0)
        {
            throw new IllegalArgumentException("--distance cannot be negative, but was " + distance);
        }
        if (distance == 0)
        {
            distance = Camera.distanceFor(conformer.radius() + FIT_PADDING, fov);
        }
        return Camera.orbit(target, options.get(YAW), options.get(PITCH), distance, fov);
    }

    private static CliOption<Double> orbitOption(String name, String metavar, String help, double fallback)
    {
        return CliOption.number(name, metavar, help, fallback).withRequirement(
                options -> !options.has("camera") || !options.has(name),
                "--" + name + " cannot be given with --camera");
    }

    private static Map<String, RenderStyle3D.Mode> modes()
    {
        Map<String, RenderStyle3D.Mode> modes = new LinkedHashMap<>();
        modes.put("ball-and-stick", RenderStyle3D.Mode.BALL_AND_STICK);
        modes.put("sticks", RenderStyle3D.Mode.STICKS);
        modes.put("wireframe", RenderStyle3D.Mode.WIREFRAME);
        modes.put("spacefill", RenderStyle3D.Mode.SPACEFILL);
        return modes;
    }

    private static DebugChoice debug(String input)
    {
        Set<RenderStyle3D.Debug> overlays = EnumSet.noneOf(RenderStyle3D.Debug.class);
        boolean report = false;
        for (String name : input.split(","))
        {
            String word = name.trim().toLowerCase(java.util.Locale.ROOT);
            switch (word)
            {
                case "none", "" -> {
                }
                case "all" -> overlays.addAll(EnumSet.allOf(RenderStyle3D.Debug.class));
                case "report" -> report = true;
                default -> {
                    try
                    {
                        overlays.add(RenderStyle3D.Debug.valueOf(word.replace('-', '_').toUpperCase(java.util.Locale.ROOT)));
                    }
                    catch (IllegalArgumentException unknown)
                    {
                        throw new IllegalArgumentException("--debug does not know '" + word + "'; use bonds, angles, torsions, "
                                + "clashes, strain, lone-pairs, all or report");
                    }
                }
            }
        }
        return new DebugChoice(overlays, report);
    }

    private static Vec3 vector(String input)
    {
        String[] parts = input.split(",");
        if (parts.length != 3)
        {
            throw new IllegalArgumentException("Expected x,y,z, but got '" + input + "'");
        }
        try
        {
            return new Vec3(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()));
        }
        catch (NumberFormatException invalid)
        {
            throw new IllegalArgumentException("Expected three numbers as x,y,z, but got '" + input + "'");
        }
    }

    private static int positive(String name, String input)
    {
        try
        {
            int value = Integer.parseInt(input);
            if (value >= 1)
            {
                return value;
            }
        }
        catch (NumberFormatException invalid)
        {
            // falls through to the message below
        }
        throw new IllegalArgumentException("--" + name + " must be a whole number of at least 1, but was '" + input + "'");
    }

    private static List<CliOption<?>> allDefinitions()
    {
        List<CliOption<?>> all = new java.util.ArrayList<>(OPTIONS);
        all.add(CommandOptions.HELP);
        all.add(CommandOptions.BACKGROUND);
        all.add(CommandOptions.MARGIN);
        all.add(CommandOptions.METADATA);
        return all;
    }
}
