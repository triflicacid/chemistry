package com.ruben.chemistry.server.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/**
 * The library of molecules, indexed for searching by name and by category.
 * <p>
 * Categories are looked up exactly in a balanced tree ({@link TreeMap}) and the smallest chosen category supplies
 * the molecules to check. Names are matched as a substring, which a tree cannot narrow, so they are scanned.
 */
public final class MoleculeLibrary
{
    private static final char SEPARATOR = ',';
    private static final String CLASS_SEPARATOR = ";";
    private static final char REMARK = '#';
    private static final String HEADING = "name";

    private final TreeMap<String, LibraryEntry> byName = new TreeMap<>();
    private final TreeMap<String, List<LibraryEntry>> byCategory = new TreeMap<>();

    private MoleculeLibrary(List<LibraryEntry> entries)
    {
        for (LibraryEntry entry : entries)
        {
            if (byName.putIfAbsent(entry.name().toLowerCase(Locale.ROOT), entry) != null)
            {
                throw new IllegalArgumentException("The library names '" + entry.name() + "' twice");
            }

            for (String category : entry.classes())
            {
                byCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(entry);
            }
        }
    }

    /**
     * @param resource the classpath location of a {@code name,smiles,classes} CSV file
     * @return the library it holds
     * @throws IllegalStateException if the resource is missing
     * @throws UncheckedIOException if it cannot be read
     * @throws IllegalArgumentException if it is malformed
     */
    public static MoleculeLibrary load(String resource)
    {
        try (InputStream stream = MoleculeLibrary.class.getResourceAsStream(resource))
        {
            if (stream == null)
            {
                throw new IllegalStateException("Missing resource " + resource);
            }
            return parse(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }

    /**
     * @param text the contents of a {@code name,smiles,classes} CSV file
     * @return the library it holds
     * @throws IllegalArgumentException if it is malformed
     */
    public static MoleculeLibrary parse(String text)
    {
        try
        {
            return parse(new BufferedReader(new StringReader(text)));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static MoleculeLibrary parse(BufferedReader reader) throws IOException
    {
        List<LibraryEntry> entries = new ArrayList<>();
        int number = 0;

        for (String line = reader.readLine(); line != null; line = reader.readLine())
        {
            number++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == REMARK)
            {
                continue;
            }

            String[] columns = trimmed.split(String.valueOf(SEPARATOR), 4);
            String name = columns[0].trim();
            if (entries.isEmpty() && name.equalsIgnoreCase(HEADING))
            {
                continue;
            }

            if (columns.length < 2 || name.isEmpty() || columns[1].isBlank())
            {
                throw new IllegalArgumentException("Line " + number + " is not name,smiles: " + trimmed);
            }

            Set<String> classes = new LinkedHashSet<>();
            if (columns.length > 2)
            {
                for (String piece : columns[2].split(CLASS_SEPARATOR))
                {
                    String tag = piece.trim().toLowerCase(Locale.ROOT);
                    if (!tag.isEmpty())
                    {
                        classes.add(tag);
                    }
                }
            }

            entries.add(new LibraryEntry(name, columns[1].trim(), List.copyOf(classes)));
        }

        return new MoleculeLibrary(entries);
    }

    /**
     * @return the number of molecules held
     */
    public int size()
    {
        return byName.size();
    }

    /**
     * @return every category in use, in alphabetical order
     */
    public Collection<String> categories()
    {
        return byCategory.keySet();
    }

    /**
     * @return all entries in the library ordered by name
     */
    public List<LibraryEntry> all()
    {
        return List.copyOf(byName.values());
    }

    /**
     * Finds molecules, narrowing by each criterion given.
     *
     * @param namePart if not blank, only molecules whose name contains this anywhere, ignoring case
     * @param categories only molecules belonging to all of these categories, ignoring case; may be empty
     * @return the matches ordered by name
     */
    public List<LibraryEntry> search(String namePart, Collection<String> categories)
    {
        List<String> wanted = categories.stream()
                .map(category -> category.trim().toLowerCase(Locale.ROOT))
                .filter(category -> !category.isEmpty())
                .distinct()
                .toList();

        for (String category : wanted)
        {
            if (!byCategory.containsKey(category))
            {
                return List.of();
            }
        }

        Collection<LibraryEntry> candidates;
        String fragment = namePart == null ? "" : namePart.trim().toLowerCase(Locale.ROOT);
        if (!wanted.isEmpty())
        {
            candidates = wanted.stream()
                    .map(byCategory::get)
                    .min(Comparator.comparingInt(List::size))
                    .orElseThrow();
        }
        else
        {
            candidates = byName.values();
        }

        List<LibraryEntry> found = new ArrayList<>();
        for (LibraryEntry entry : candidates)
        {
            if (entry.name().toLowerCase(Locale.ROOT).contains(fragment) && new HashSet<>(entry.classes()).containsAll(wanted))
            {
                found.add(entry);
            }
        }

        found.sort(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        return found;
    }
}
