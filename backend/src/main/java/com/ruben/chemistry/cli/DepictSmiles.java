package com.ruben.chemistry.cli;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.render.DepictionOptions;
import com.ruben.chemistry.render.Layout;
import com.ruben.chemistry.render.MoleculeLayout;
import com.ruben.chemistry.render.SvgRenderer;
import com.ruben.chemistry.smiles.SmilesParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Draws a molecule written in SMILES as an SVG document.
 * <p>
 * Run it through Gradle, e.g.
 * <pre>{@code
 * ./gradlew :backend:depict --args="CCO --out=ethanol.svg"
 * ./gradlew :backend:depict --args="N[C@@H](C)C(=O)O --style=circles --bond-length=70"
 * }</pre>
 * Without an {@code --out} the document goes to standard output.
 */
final class DepictSmiles
{
    /** What to give {@code --out} to write to standard output rather than to a file. */
    private static final String STANDARD_OUTPUT = "-";

    /** The output destination option specific to this command. */
    private static final CliOption<String> OUT = CliOption.string(
            "out",
            "path",
            "where to write the document, or - for standard output",
            STANDARD_OUTPUT
    );

    static final List<CliOption<?>> OPTIONS = Stream.concat(Stream.of(OUT), CommandOptions.DRAWING_OPTIONS.stream()).toList();

    private static final String USAGE = CliParser.usage(
            "Usage: depict <smiles> [options]",
            OPTIONS);

    private DepictSmiles()
    {
    }

    /**
     * @param args the SMILES string to draw, followed by any options
     * @throws IOException if the document cannot be written
     */
    public static void main(String[] args) throws IOException
    {
        List<String> arguments = List.of(args);
        String destination;
        String document;

        try
        {
            CliOptions options = CommandOptions.of(arguments, OUT);
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
     * Draws the molecule the arguments name.
     *
     * @param arguments the SMILES string to draw, followed by any options
     * @return the SVG document
     * @throws IllegalArgumentException if the arguments name no one molecule, or an option is not understood
     */
    public static String render(List<String> arguments)
    {
        CliOptions options = CommandOptions.of(arguments, OUT);
        return render(options.onlySubject(), options);
    }

    /**
     * Draws one molecule.
     *
     * @param smiles the molecule, written in SMILES
     * @param options how to draw it
     * @return the SVG document
     * @throws IllegalArgumentException if the molecule cannot be read or laid out
     */
    static String render(String smiles, CliOptions options)
    {
        Molecule molecule = new SmilesParser(smiles).parse();
        DepictionOptions depiction = CommandOptions.depictionOptions(options);

        List<Layout> layouts = MoleculeLayout.layouts(molecule, depiction, options.get(CommandOptions.ALTERNATIVES));

        return SvgRenderer.render(Layout.sideBySide(layouts), CommandOptions.style(options));
    }
}