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
import java.util.stream.Stream;

/**
 * Draws every molecule a list names.
 * <p>
 * The list is a comma separated file of {@code name,smiles,classes,layout}, one molecule to a line.
 * The classes, such as {@code ring;aromatic}, are divided by semicolons and may be left out.
 * The layout, such as {@code row:2} or {@code grid:x xx x}, arranges a disconnected molecule's parts in the
 * way {@code --layout} does, and may be left out to use the command's own.
 * Each molecule is written to {@code <out-dir>/<name>.svg}.
 * {@code index.html} gathers them for viewing and filtering.
 * <p>
 * Run it through Gradle, e.g.
 * <pre>{@code
 * ./gradlew :backend:gallery
 * ./gradlew :backend:gallery --args="examples/molecules.csv --out-dir=out --style=circles"
 * }</pre>
 */
final class DepictGallery
{
    /** Where the drawings go unless told otherwise. */
    private static final String DEFAULT_DIRECTORY = "out";

    /** The contact sheet gathering every drawing. */
    private static final String CONTACT_SHEET = "index.html";

    /** The heading a list may begin with, which names no molecule. */
    private static final String HEADING = "name";

    /** What marks a line as a remark rather than a molecule. */
    private static final char REMARK = '#';

    /** What separates a name from its molecule, and a molecule from its classes. */
    private static final char SEPARATOR = ',';

    /** What separates one class from the next within the classes column. */
    private static final String CLASS_SEPARATOR = ";";

    /** The output directory option specific to this command. */
    private static final CliOption<String> OUT_DIR = CliOption.string(
            "out-dir",
            "path",
            "where the drawings go",
            DEFAULT_DIRECTORY);

    private static final List<CliOption<?>> OPTIONS = Stream.concat(Stream.of(OUT_DIR), CommandOptions.DRAWING_OPTIONS.stream()).toList();

    static final String USAGE = CliParser.usage(
            "Usage: gallery <csv> [options]",
            OPTIONS);

    private DepictGallery()
    {
    }

    /**
     * @param args the list to draw from, followed by any options
     * @throws IOException if the list cannot be read or the drawings cannot be written
     */
    public static void main(String[] args) throws IOException
    {
        try
        {
            CliOptions options = CommandOptions.of(List.of(args), OUT_DIR);
            if (options.get(CommandOptions.HELP))
            {
                System.out.println(USAGE);
                System.exit(0);
            }

            Path list = Path.of(options.onlySubject());
            Path directory = Path.of(options.get(OUT_DIR));
            List<Entry> entries = read(Files.readString(list, StandardCharsets.UTF_8));

            List<Drawing> drawings = draw(entries, options);
            write(drawings, directory);

            long failed = drawings.stream().filter(Drawing::failed).count();
            System.out.println("Drew " + (drawings.size() - failed) + " of " + drawings.size() + " molecules into " + directory.toAbsolutePath());
            System.out.println("Wrote HTML file to " + directory.resolve(CONTACT_SHEET).toAbsolutePath());

            for (Drawing drawing : drawings)
            {
                if (drawing.failed())
                {
                    System.out.println("  could not draw " + drawing.entry().name() + " (" + drawing.entry().smiles() + "): " + drawing.complaint());
                }
            }

            if (failed > 0)
            {
                System.exit(1);
            }
        }
        catch (IllegalArgumentException wrong)
        {
            System.err.println(wrong.getMessage());
            System.err.println();
            System.err.println(USAGE);
            System.exit(2);
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
            String[] columns = trimmed.substring(split + 1).split(String.valueOf(SEPARATOR), 3);
            String smiles = columns[0].trim();
            List<String> classes = columns.length < 2 ? List.of() : classesOf(columns[1]);
            String layout = columns.length < 3 ? "" : columns[2].trim();

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

            entries.add(new Entry(name, smiles, classes, layout));
        }

        return entries;
    }

    /**
     * @param text the classes column of a line, with classes divided by {@value #CLASS_SEPARATOR}
     * @return the distinct classes it names, lower-cased, in the order given
     */
    private static List<String> classesOf(String text)
    {
        Set<String> classes = new LinkedHashSet<>();
        for (String piece : text.split(CLASS_SEPARATOR))
        {
            String tag = piece.trim().toLowerCase(Locale.ROOT);
            if (!tag.isEmpty())
            {
                classes.add(tag);
            }
        }
        return List.copyOf(classes);
    }

    /**
     * Draws every molecule, keeping going past any it cannot draw.
     *
     * @param entries the molecules to draw
     * @param options how to draw them
     * @return what became of each, in the order given
     */
    static List<Drawing> draw(List<Entry> entries, CliOptions options)
    {
        List<Drawing> drawings = new ArrayList<>();
        for (Entry entry : entries)
        {
            try
            {
                CliOptions own = entry.layout().isEmpty()
                        ? options
                        : CommandOptions.withLayout(options, entry.layout());

                drawings.add(new Drawing(entry, DepictSmiles.render(entry.smiles(), own), null));
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
                .tags { margin: .5rem 0 0; display: flex; flex-wrap: wrap; gap: .25rem; }
                .tag { background: #e3edf9; color: #1d4f91; border-radius: 1rem; padding: .1rem .55rem; font-size: .75rem; }
                #filter { display: flex; flex-wrap: wrap; gap: .5rem; align-items: center; margin-bottom: 1rem; }
                #chosen { display: contents; }
                #chosen .tag { cursor: pointer; background: #1d4f91; color: #fff; font-size: .85rem; }
                #chosen .tag::after { content: " \\00d7"; }
                #count { color: #555; font-size: .85rem; }
                li.hidden { display: none; }
                </style>
                </head>
                <body>
                <h1>Molecule gallery</h1>
                <div id="filter">
                <input id="search" type="search" placeholder="Search by name…">
                <select id="picker"></select>
                <span id="chosen"></span>
                <span id="count"></span>
                </div>
                <ul>
                """);

        for (Drawing drawing : drawings)
        {
            String classes = String.join(" ", drawing.entry().classes());
            page.append(drawing.failed() ? "<li class=\"failed\"" : "<li");
            page.append(" data-classes=\"").append(escape(classes)).append("\">\n");
            page.append("<h2>").append(escape(drawing.entry().name())).append("</h2>\n");
            page.append("<code>").append(escape(drawing.entry().smiles())).append("</code>\n");

            if (!drawing.entry().classes().isEmpty())
            {
                page.append("<div class=\"tags\">");
                for (String tag : drawing.entry().classes())
                {
                    page.append("<span class=\"tag\">").append(escape(tag)).append("</span>");
                }
                page.append("</div>\n");
            }

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

        // searching script
        return page.append("""
                </ul>
                <script>
                const items = [...document.querySelectorAll("li[data-classes]")];
                const picker = document.getElementById("picker");
                const chosenBox = document.getElementById("chosen");
                const count = document.getElementById("count");
                const all = [...new Set(items.flatMap(li => li.dataset.classes.split(" ").filter(Boolean)))].sort();
                const search = document.getElementById("search");
                const chosen = [];
                let query = "";

                function refresh() {
                  picker.innerHTML = "";
                  picker.add(new Option("Filter by class…", ""));
                  for (const tag of all.filter(tag => !chosen.includes(tag))) {
                    picker.add(new Option(tag + " (" + items.filter(li => li.dataset.classes.split(" ").includes(tag)).length + ")", tag));
                  }

                  chosenBox.innerHTML = "";
                  for (const tag of chosen) {
                    const label = document.createElement("span");
                    label.className = "tag";
                    label.title = "Remove this filter";
                    label.textContent = tag;
                    label.onclick = () => { chosen.splice(chosen.indexOf(tag), 1); refresh(); };
                    chosenBox.append(label);
                  }

                  let shown = 0;
                  for (const li of items) {
                    const own = li.dataset.classes.split(" ");
                    const name = li.querySelector("h2").textContent.toLowerCase();
                    const match = chosen.every(tag => own.includes(tag)) && name.includes(query);
                    li.classList.toggle("hidden", !match);
                    if (match) shown++;
                  }
                  count.textContent = "Showing " + shown + " of " + items.length;
                }

                picker.onchange = () => { if (picker.value) { chosen.push(picker.value); refresh(); } };
                search.oninput = () => { query = search.value.trim().toLowerCase(); refresh(); };
                refresh();
                </script>
                </body>
                </html>
                """).toString();
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
     * @param classes the classes it belongs to, such as {@code ring}, lower-cased
     * @param layout how its parts are arranged, as {@code --layout} takes it, or empty to use the command's
     */
    record Entry(String name, String smiles, List<String> classes, String layout)
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
