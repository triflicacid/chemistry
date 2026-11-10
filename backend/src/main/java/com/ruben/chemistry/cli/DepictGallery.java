package com.ruben.chemistry.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Draws every molecule a list names, so that a whole shelf of drawings can be looked over at once.
 * <p>
 * The list is a comma separated file of {@code name,smiles}, one molecule to a line.
 * Each molecule is written to {@code <out-dir>/<name>.svg}, and a contact sheet gathering them all is written beside them.
 * <p>
 * Run it through Gradle, e.g.
 * <pre>{@code
 * ./gradlew :backend:gallery
 * ./gradlew :backend:gallery --args="examples/molecules.csv --out-dir=out --style=circles"
 * }</pre>
 */
public final class DepictGallery
{
    /** Where the drawings go unless told otherwise. */
    private static final String DEFAULT_DIRECTORY = "out";

    /** The contact sheet gathering every drawing. */
    private static final String CONTACT_SHEET = "index.html";

    /** The heading a list may begin with, which names no molecule. */
    private static final String HEADING = "name";

    /** What marks a line as a remark rather than a molecule. */
    private static final char REMARK = '#';

    /** What separates a name from its molecule. */
    private static final char SEPARATOR = ',';

    /** How to use the command, shown when its arguments make no sense. */
    static final String USAGE = """
        Usage: gallery <csv> [options]

          <csv>                        a file of name,smiles lines
          --out-dir=<path>             where the drawings go (default out)
        """ + CommandOptions.USAGE;

    private DepictGallery()
    {
    }

    /**
     * @param args the list to draw from, followed by any options
     * @throws IOException if the list cannot be read or the drawings cannot be written
     */
    public static void main(String[] args) throws IOException
    {
        CommandOptions options;
        Path list;
        Path directory;
        List<Entry> entries;
        try
        {
            options = CommandOptions.of(List.of(args));
            options.validate("out-dir");
            list = Path.of(options.onlySubject("list of molecules"));
            directory = Path.of(options.get("out-dir", DEFAULT_DIRECTORY));
            entries = read(Files.readString(list, StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException wrong)
        {
            System.err.println(wrong.getMessage());
            System.err.println();
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        List<Drawing> drawings = draw(entries, options);
        write(drawings, directory);

        long failed = drawings.stream().filter(Drawing::failed).count();
        System.out.println("Drew " + (drawings.size() - failed) + " of " + drawings.size()
            + " molecules into " + directory.toAbsolutePath());
        for (Drawing drawing : drawings)
        {
            if (drawing.failed())
            {
                System.out.println("  could not draw " + drawing.entry().name() + " (" + drawing.entry().smiles()
                    + "): " + drawing.complaint());
            }
        }
        if (failed > 0)
        {
            System.exit(1);
        }
    }

    /**
     * Reads a list of molecules.
     *
     * @param text the list, of {@code name,smiles} lines
     * @return the molecules it names, in the order it names them
     * @throws IllegalArgumentException if a line names no molecule, or a name is used twice
     */
    static List<Entry> read(String text)
    {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int number = 0;

        for (String line : text.lines().toList())
        {
            number++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == REMARK)
            {
                continue;
            }

            int split = trimmed.indexOf(SEPARATOR);
            if (split < 1 || split == trimmed.length() - 1)
            {
                throw new IllegalArgumentException("Line " + number + " is not name" + SEPARATOR + "smiles: " + trimmed);
            }

            String name = trimmed.substring(0, split).trim();
            String smiles = trimmed.substring(split + 1).trim();
            if (entries.isEmpty() && name.equalsIgnoreCase(HEADING))
            {
                continue;
            }
            if (name.isEmpty() || smiles.isEmpty())
            {
                throw new IllegalArgumentException("Line " + number + " is not name" + SEPARATOR + "smiles: " + trimmed);
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT)))
            {
                throw new IllegalArgumentException("Line " + number + " names '" + name + "' a second time");
            }
            entries.add(new Entry(name, smiles));
        }
        return entries;
    }

    /**
     * Draws every molecule, keeping going past any it cannot draw.
     *
     * @param entries the molecules to draw
     * @param options how to draw them
     * @return what became of each, in the order given
     */
    static List<Drawing> draw(List<Entry> entries, CommandOptions options)
    {
        List<Drawing> drawings = new ArrayList<>();
        for (Entry entry : entries)
        {
            try
            {
                drawings.add(new Drawing(entry, DepictSmiles.render(entry.smiles(), options), null));
            }
            catch (RuntimeException refused)
            {
                String complaint = refused.getMessage() == null ? refused.toString() : refused.getMessage();
                drawings.add(new Drawing(entry, null, complaint));
            }
        }
        return drawings;
    }

    /**
     * Writes every drawing, and a contact sheet gathering them.
     *
     * @param drawings what became of each molecule
     * @param directory where to write them
     * @throws IOException if they cannot be written
     */
    static void write(List<Drawing> drawings, Path directory) throws IOException
    {
        Files.createDirectories(directory);
        for (Drawing drawing : drawings)
        {
            if (!drawing.failed())
            {
                Files.writeString(directory.resolve(fileNameOf(drawing.entry().name())), drawing.document(),
                    StandardCharsets.UTF_8);
            }
        }
        Files.writeString(directory.resolve(CONTACT_SHEET), contactSheet(drawings), StandardCharsets.UTF_8);
    }

    /**
     * @param name what a molecule is called
     * @return the file its drawing goes in
     */
    static String fileNameOf(String name)
    {
        StringBuilder safe = new StringBuilder(name.length());
        for (char letter : name.toCharArray())
        {
            // Anything that might mean something to a shell or a file system becomes a dash.
            safe.append(Character.isLetterOrDigit(letter) || letter == '-' || letter == '_' || letter == '.'
                ? letter
                : '-');
        }
        return safe + ".svg";
    }

    /**
     * @param drawings what became of each molecule
     * @return a page showing every drawing beside the molecule it came from
     */
    private static String contactSheet(List<Drawing> drawings)
    {
        StringBuilder page = new StringBuilder();
        page.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <title>Molecule gallery</title>
            <style>
            body { font-family: system-ui, sans-serif; margin: 2rem; background: #f4f4f4; }
            ul { list-style: none; padding: 0; display: grid; gap: 1rem;
                 grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr)); }
            li { background: #fff; border: 1px solid #ddd; border-radius: .5rem; padding: 1rem; }
            h2 { font-size: 1rem; margin: 0 0 .25rem; }
            code { color: #555; font-size: .85rem; word-break: break-all; }
            figure { margin: .75rem 0 0; overflow: auto; }
            .failed { border-color: #c00; }
            .failed p { color: #c00; font-size: .85rem; margin: .75rem 0 0; }
            </style>
            </head>
            <body>
            <h1>Molecule gallery</h1>
            <ul>
            """);

        for (Drawing drawing : drawings)
        {
            page.append(drawing.failed() ? "<li class=\"failed\">\n" : "<li>\n");
            page.append("<h2>").append(escape(drawing.entry().name())).append("</h2>\n");
            page.append("<code>").append(escape(drawing.entry().smiles())).append("</code>\n");
            if (drawing.failed())
            {
                page.append("<p>").append(escape(drawing.complaint())).append("</p>\n");
            }
            else
            {
                page.append("<figure>").append(drawing.document().strip()).append("</figure>\n");
            }
            page.append("</li>\n");
        }

        return page.append("</ul>\n</body>\n</html>\n").toString();
    }

    /**
     * @param text what to write into the page
     * @return it, with any markup it holds made harmless
     */
    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * One molecule the list names.
     *
     * @param name what it is called, which names its file too
     * @param smiles the molecule, written in SMILES
     */
    record Entry(String name, String smiles)
    {
    }

    /**
     * What became of one molecule.
     *
     * @param entry the molecule it was drawn from
     * @param document the SVG document, or {@code null} where it could not be drawn
     * @param complaint why it could not be drawn, or {@code null} where it was
     */
    record Drawing(Entry entry, String document, String complaint)
    {
        /**
         * @return whether the molecule could not be drawn
         */
        boolean failed()
        {
            return document == null;
        }
    }
}
