package com.ruben.chemistry.server.library;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * The library of SMARTS patterns.
 */
public final class QueryLibrary extends Library<QueryEntry>
{
    private static final char SEPARATOR = '|';
    private static final int COLUMNS = 5;

    private QueryLibrary(List<QueryEntry> entries)
    {
        super(entries, QueryEntry::name, QueryEntry::classes);
    }

    /**
     * @param resource the classpath location of a {@code name|smarts|classes|description|example} file
     * @return the library it holds
     * @throws IllegalStateException if the resource is missing
     * @throws UncheckedIOException if it cannot be read
     * @throws IllegalArgumentException if it is malformed
     */
    public static QueryLibrary load(String resource)
    {
        return new QueryLibrary(LibraryTable.load(resource, SEPARATOR, COLUMNS, QueryLibrary::entry));
    }

    /**
     * @param text the contents of a {@code name|smarts|classes|description|example} file
     * @return the library it holds
     * @throws IllegalArgumentException if it is malformed
     */
    public static QueryLibrary parse(String text)
    {
        return new QueryLibrary(LibraryTable.parse(text, SEPARATOR, COLUMNS, QueryLibrary::entry));
    }

    /**
     * @param columns a line's {@code name|smarts|classes|description|example} columns
     * @return the pattern they describe
     * @throws IllegalArgumentException if the name, the pattern or the description is missing
     */
    private static QueryEntry entry(String[] columns)
    {
        if (columns[0].isEmpty() || columns[1].isEmpty())
        {
            throw new IllegalArgumentException("not name|smarts");
        }
        if (columns[3].isEmpty())
        {
            throw new IllegalArgumentException("'" + columns[0] + "' has no description");
        }
        return new QueryEntry(columns[0], columns[1], LibraryTable.tags(columns[2]), columns[3], columns[4]);
    }
}
