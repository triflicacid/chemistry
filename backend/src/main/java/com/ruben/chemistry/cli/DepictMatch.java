package com.ruben.chemistry.cli;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.query.Match;
import com.ruben.chemistry.query.Query;
import com.ruben.chemistry.query.SubstructureMatcher;
import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.Layout;
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
import java.util.Map;
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
    /** How several matches are shown. */
    private enum View
    {
        /** One drawing of the molecule per match, side by side. */
        EACH,

        /** One drawing, with every match highlighted in a colour of its own. */
        MERGED
    }

    private static final String STANDARD_OUTPUT = "-";

    private static final CliOption<String> OUT = CliOption.string(
            "out",
            "path",
            "where to write the document, or - for standard output",
            STANDARD_OUTPUT
    );

    private static final CliOption<View> VIEW = CliOption.choice(
            "view",
            "whether each match gets its own drawing of the molecule, or all are highlighted on one",
            Map.of("each", View.EACH, "merged", View.MERGED),
            () -> View.EACH
    ).withDefaultText("each");

    private static final CliOption<Boolean> ALL_MATCHES = CliOption.flag(
            "all-matches",
            "whether matches covering the same atoms in another order count as matches of their own",
            false
    );

    private static final CliOption<Integer> MAX_MATCHES = CliOption.parsed(
            "max-matches",
            "n",
            "the most matches to draw",
            input -> {
                try
                {
                    int count = Integer.parseInt(input);
                    if (count < 1)
                    {
                        throw new IllegalArgumentException("--max-matches must be at least 1, but was " + count);
                    }
                    return count;
                }
                catch (NumberFormatException invalid)
                {
                    throw new IllegalArgumentException("--max-matches must be a whole number, but was '" + input + "'");
                }
            },
            () -> 10
    ).withDefaultText("10");

    private static final CliOption<Double> DIM = CliOption.number(
            "dim",
            "0-1",
            "how much everything outside the match is faded, from 0 (not at all) to 1 (invisible)",
            0
    );

    private static final CliOption<Boolean> LABELS = CliOption.flag(
            "labels",
            "whether matched atoms and the bonds between them are numbered",
            false
    );

    private static final CliOption<Boolean> RECOLOUR = CliOption.flag(
            "recolour",
            "whether matched atoms are filled with the colour of their match",
            false
    );

    private static final CliOption<Colour> MATCH_COLOUR = CliOption.parsed(
            "match-colour",
            "colour",
            "the colour to highlight every match in, instead of a colour each",
            Colour::of,
            null
    );

    private static final List<CliOption<?>> MATCH_OPTIONS = List.of(VIEW, ALL_MATCHES, MAX_MATCHES, DIM, LABELS, RECOLOUR, MATCH_COLOUR);

    static final List<CliOption<?>> OPTIONS = Stream.concat(
            Stream.concat(Stream.of(OUT), MATCH_OPTIONS.stream()),
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
        List<String> subjects = options.subjects();
        if (subjects.size() != 2)
        {
            throw new IllegalArgumentException("Expected a SMILES string and a SMARTS pattern, but got " + subjects.size() + " arguments");
        }

        Molecule molecule = Molecule.fromSmiles(subjects.get(0));
        Query query = Query.fromSmarts(subjects.get(1));

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);
        List<Match> found = options.get(ALL_MATCHES) ? matcher.findMatches(query) : matcher.findDistinctMatches(query);
        if (found.isEmpty())
        {
            throw new IllegalArgumentException("'" + subjects.get(1) + "' does not match '" + subjects.get(0) + "'");
        }

        List<Match> matches = found.subList(0, Math.min(found.size(), options.get(MAX_MATCHES)));
        if (matches.size() < found.size())
        {
            System.err.println("Drawing " + matches.size() + " of " + found.size() + " matches; see --max-matches");
        }

        MatchStyle matchStyle = MatchStyle.defaults()
                .withDimRest(options.get(DIM))
                .withLabels(options.get(LABELS))
                .withRecolour(options.get(RECOLOUR));
        if (options.has(MATCH_COLOUR.name()))
        {
            matchStyle = matchStyle.withColour(options.get(MATCH_COLOUR));
        }

        Layout layout = MoleculeLayout.layout(molecule, CommandOptions.depictionOptions(options));
        RenderStyle style = CommandOptions.style(options);

        if (options.get(VIEW) == View.MERGED)
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
