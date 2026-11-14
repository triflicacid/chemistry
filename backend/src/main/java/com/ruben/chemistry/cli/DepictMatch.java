package com.ruben.chemistry.cli;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.query.Match;
import com.ruben.chemistry.render.Structure2D;
import com.ruben.chemistry.render.MoleculeLayout;
import com.ruben.chemistry.render.RenderStyle;
import com.ruben.chemistry.render.SceneRenderer;
import com.ruben.chemistry.render.svg.SvgWriter;
import com.ruben.chemistry.render.match.MatchAnnotator;
import com.ruben.chemistry.render.match.MatchStyle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Draws a molecule written in SMILES with the matches of a SMARTS pattern highlighted, as an SVG document.
 * <p>
 * Run it through Gradle, e.g.
 * <pre>{@code
 * ./gradlew :backend:match --args="CC(=O)Oc1ccccc1C(=O)O C(=O)O --out=aspirin.svg"
 * ./gradlew :backend:match --args="c1ccccc1C c1ccccc1 --view=merged --labels"
 * }</pre>
 * Without an {@code --out} the document goes to standard output.
 */
final class DepictMatch
{
    private static final String STANDARD_OUTPUT = "-";

    private static final CliOption<String> OUT = CliOption.string(
            "out",
            "path",
            "where to write the document, or - for standard output",
            STANDARD_OUTPUT
    );

    static final List<CliOption<?>> OPTIONS = Stream.concat(
            Stream.concat(Stream.of(OUT), MatchOptions.ALL.stream()),
            CommandOptions.DRAWING_OPTIONS.stream()).toList();

    private static final String USAGE = CliParser.usage(
            "Usage: match <smiles> <smarts> [options]",
            OPTIONS);

    private DepictMatch()
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

            destination = options.get(OUT);
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

        Structure2D layout = MoleculeLayout.layout(molecule, CommandOptions.depictionOptions(options));
        RenderStyle style = CommandOptions.style(options);

        if (options.get(MatchOptions.VIEW) == MatchOptions.View.MERGED)
        {
            return SvgWriter.write(SceneRenderer.render(layout, style, MatchAnnotator.annotate(matches, molecule, matchStyle)));
        }

        List<String> panels = new ArrayList<>();
        for (Match match : matches)
        {
            panels.add(SvgWriter.write(SceneRenderer.render(layout, style, MatchAnnotator.annotate(match, molecule, matchStyle))));
        }
        return SvgPanels.row(panels);
    }
}
