package com.ruben.chemistry.cli;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.query.Match;
import com.ruben.chemistry.render.annotate.Annotations;
import com.ruben.chemistry.render.match.MatchAnnotator;
import com.ruben.chemistry.render.match.MatchStyle;
import com.ruben.chemistry.render.svg.SvgWriter;
import com.ruben.chemistry.render.three.SceneRenderer3D;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Draws a molecule written in SMILES in three dimensions with the matches of a SMARTS pattern highlighted, as an SVG
 * document. It takes the options of both {@code match} and {@code render3d}.
 * <p>
 * Run it through Gradle, e.g.
 * <pre>{@code
 * ./gradlew :backend:match3d --args="CC(=O)Oc1ccccc1C(=O)O C(=O)O --out=aspirin.svg"
 * ./gradlew :backend:match3d --args="c1ccccc1C c1ccccc1 --view=merged --labels --yaw=60"
 * }</pre>
 * Without an {@code --out} the document goes to standard output. Every match is drawn from the same camera, on the
 * same shape of the molecule.
 */
final class DepictMatch3d
{
    static final List<CliOption<?>> OPTIONS = Stream.concat(Render3d.DEFINITIONS.stream(), MatchOptions.ALL.stream()).toList();

    private static final String USAGE = CliParser.usage("Usage: match3d <smiles> <smarts> [options]", OPTIONS);

    private DepictMatch3d()
    {
    }

    /**
     * @param args the SMILES string, the SMARTS pattern, then any options
     * @throws IOException if the document cannot be written
     */
    public static void main(String[] args) throws IOException
    {
        String destination;
        String document;

        try
        {
            CliOptions options = parse(List.of(args));
            if (options.get(CommandOptions.HELP))
            {
                System.out.println(USAGE);
                System.exit(0);
            }

            destination = options.get(Render3d.OUT);
            document = render(options);
        }
        catch (IllegalArgumentException wrong)
        {
            System.err.println(wrong.getMessage());
            System.err.println();
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        if (destination.equals(Render3d.STANDARD_OUTPUT))
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
     * @param arguments the SMILES string, the SMARTS pattern, then any options
     * @return the SVG document
     * @throws IllegalArgumentException if the arguments are not understood, or the pattern matches nothing
     */
    public static String render(List<String> arguments)
    {
        return render(parse(arguments));
    }

    private static CliOptions parse(List<String> arguments)
    {
        return CliParser.parse(arguments, OPTIONS);
    }

    private static String render(CliOptions options)
    {
        MatchOptions.Found found = MatchOptions.find(options);
        Molecule molecule = found.molecule();
        List<Match> matches = found.matches();
        MatchStyle matchStyle = MatchOptions.style(options);
        Render3d.View view = Render3d.prepare(molecule, options);

        if (options.get(MatchOptions.VIEW) == MatchOptions.View.MERGED)
        {
            return draw(view, MatchAnnotator.annotate(matches, molecule, matchStyle), molecule);
        }

        List<String> panels = new ArrayList<>();
        for (Match match : matches)
        {
            panels.add(draw(view, MatchAnnotator.annotate(match, molecule, matchStyle), molecule));
        }
        return SvgPanels.row(panels);
    }

    private static String draw(Render3d.View view, Annotations annotations, Molecule molecule)
    {
        return SvgWriter.write(SceneRenderer3D.render(view.layout(), view.style(), annotations, molecule));
    }
}
