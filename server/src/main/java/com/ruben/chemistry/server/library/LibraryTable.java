package com.ruben.chemistry.server.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a library's entries from delimited text.
 */
public final class LibraryTable
{
    /**
     * Makes an entry out of one line's columns.
     *
     * @param <E> the kind of entry read
     */
    @FunctionalInterface
    public interface RowParser<E>
    {
        /**
         * @param columns the line's columns, each trimmed, padded with blanks to the number the table asked for
         * @return the entry they describe
         * @throws IllegalArgumentException if they do not describe one
         */
        E parse(String[] columns);
    }

    private static final char REMARK = '#';
    private static final String HEADING = "name";

    private LibraryTable()
    {
    }

    /**
     * @param resource the classpath location of the text to read
     * @param delimiter the character between columns
     * @param columns how many columns an entry has
     * @param parser makes an entry out of a line's columns
     * @param <E> the kind of entry read
     * @return the entries the resource holds, in the order written
     * @throws IllegalStateException if the resource is missing
     * @throws UncheckedIOException if it cannot be read
     * @throws IllegalArgumentException if a line is malformed
     */
    public static <E> List<E> load(String resource, char delimiter, int columns, RowParser<E> parser)
    {
        try (InputStream stream = LibraryTable.class.getResourceAsStream(resource))
        {
            if (stream == null)
            {
                throw new IllegalStateException("Missing resource " + resource);
            }
            return read(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), delimiter, columns, parser);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }

    /**
     * @param text the text to read
     * @param delimiter the character between columns
     * @param columns how many columns an entry has
     * @param parser makes an entry out of a line's columns
     * @param <E> the kind of entry read
     * @return the entries the text holds, in the order written
     * @throws IllegalArgumentException if a line is malformed
     */
    public static <E> List<E> parse(String text, char delimiter, int columns, RowParser<E> parser)
    {
        try
        {
            return read(new BufferedReader(new StringReader(text)), delimiter, columns, parser);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Splits a classes column into the categories it names.
     *
     * @param column the column as written, which may be blank
     * @return the lower-cased categories, in the order written and without repeats
     */
    public static List<String> tags(String column)
    {
        Set<String> tags = new LinkedHashSet<>();
        for (String piece : column.split(";"))
        {
            String tag = piece.trim().toLowerCase(Locale.ROOT);
            if (!tag.isEmpty())
            {
                tags.add(tag);
            }
        }
        return List.copyOf(tags);
    }

    private static <E> List<E> read(BufferedReader reader, char delimiter, int columns, RowParser<E> parser) throws IOException
    {
        Pattern separator = Pattern.compile(Pattern.quote(String.valueOf(delimiter)));
        List<E> entries = new ArrayList<>();
        int number = 0;

        for (String line = reader.readLine(); line != null; line = reader.readLine())
        {
            number++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == REMARK)
            {
                continue;
            }

            String[] cells = cells(separator, trimmed, columns, number);
            if (entries.isEmpty() && cells[0].equalsIgnoreCase(HEADING))
            {
                continue;
            }

            try
            {
                entries.add(parser.parse(cells));
            }
            catch (IllegalArgumentException e)
            {
                throw new IllegalArgumentException("Line " + number + ": " + e.getMessage() + ": " + trimmed, e);
            }
        }

        return entries;
    }

    /**
     * @param separator matches the delimiter between columns
     * @param line the line to split, already trimmed
     * @param columns how many columns an entry has
     * @param number the line's number, for the error message
     * @return the line's columns, each trimmed and padded with blanks to {@code columns}
     * @throws IllegalArgumentException if the line holds more columns than that
     */
    private static String[] cells(Pattern separator, String line, int columns, int number)
    {
        String[] split = separator.split(line, -1);
        if (split.length > columns)
        {
            throw new IllegalArgumentException("Line " + number + " has " + split.length + " columns, not " + columns + ": " + line);
        }

        String[] cells = Arrays.copyOf(split, columns);
        for (int index = 0; index < cells.length; index++)
        {
            cells[index] = cells[index] == null ? "" : cells[index].trim();
        }
        return cells;
    }
}
