package com.ruben.chemistry.cli;

import com.ruben.chemistry.render.Depiction;
import com.ruben.chemistry.render.Layout;
import com.ruben.chemistry.render.MoleculeLayout;
import com.ruben.chemistry.render.SvgRenderer;
import com.ruben.chemistry.smiles.SmilesParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
public final class DepictSmiles
{
    /** What to give {@code --out} to write to standard output rather than to a file. */
    private static final String STANDARD_OUTPUT = "-";

    /** How to use the command, shown when its arguments make no sense. */
    static final String USAGE = """
        Usage: depict <smiles> [options]

          --out=<path>                 where to write the document, or - for standard output (default -)
        """ + CommandOptions.USAGE;

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
            CommandOptions options = CommandOptions.of(arguments);
            options.validate("out");
            destination = options.get("out", STANDARD_OUTPUT);
            document = render(arguments);
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
        CommandOptions options = CommandOptions.of(arguments);
        return render(options.onlySubject("SMILES string"), options);
    }

    /**
     * Draws one molecule.
     *
     * @param smiles the molecule, written in SMILES
     * @param options how to draw it
     * @return the SVG document
     * @throws IllegalArgumentException if the molecule cannot be read or laid out
     */
    static String render(String smiles, CommandOptions options)
    {
        Layout layout = MoleculeLayout.layout(new SmilesParser(smiles).parse(), options.depictionOptions());
        return SvgRenderer.render(layout.depiction(), options.style());
    }
}
