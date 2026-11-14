package com.ruben.chemistry.server.library;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * A library of named entries, indexed for searching by name and by category.
 *
 * @param <E> the kind of entry held
 */
public abstract class Library<E>
{
    private final Function<E, String> name;
    private final Function<E, List<String>> categories;
    private final TreeMap<String, E> byName = new TreeMap<>();
    private final TreeMap<String, List<E>> byCategory = new TreeMap<>();

    /**
     * @param entries the entries to hold
     * @param name reads an entry's unique name
     * @param categories reads the lower-cased categories an entry belongs to
     * @throws IllegalArgumentException if two entries share a name, ignoring case
     */
    protected Library(List<E> entries, Function<E, String> name, Function<E, List<String>> categories)
    {
        this.name = name;
        this.categories = categories;

        for (E entry : entries)
        {
            if (byName.putIfAbsent(name.apply(entry).toLowerCase(Locale.ROOT), entry) != null)
            {
                throw new IllegalArgumentException("The library names '" + name.apply(entry) + "' twice");
            }

            for (String category : categories.apply(entry))
            {
                byCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(entry);
            }
        }
    }

    /**
     * @return the number of entries held
     */
    public final int size()
    {
        return byName.size();
    }

    /**
     * @return every category in use, in alphabetical order
     */
    public final Collection<String> categories()
    {
        return byCategory.keySet();
    }

    /**
     * @return every entry held, ordered by name
     */
    public final List<E> all()
    {
        return List.copyOf(byName.values());
    }

    /**
     * @param namePart if not blank, only entries whose name contains this anywhere, ignoring case
     * @param wanted only entries belonging to all of these categories, ignoring case; may be empty
     * @return the matches ordered by name
     */
    public final List<E> search(String namePart, Collection<String> wanted)
    {
        List<String> asked = wanted.stream()
                .map(category -> category.trim().toLowerCase(Locale.ROOT))
                .filter(category -> !category.isEmpty())
                .distinct()
                .toList();

        for (String category : asked)
        {
            if (!byCategory.containsKey(category))
            {
                return List.of();
            }
        }

        Collection<E> candidates;
        String fragment = namePart == null ? "" : namePart.trim().toLowerCase(Locale.ROOT);
        if (!asked.isEmpty())
        {
            candidates = asked.stream()
                    .map(byCategory::get)
                    .min(Comparator.comparingInt(List::size))
                    .orElseThrow();
        }
        else
        {
            candidates = byName.values();
        }

        List<E> found = new ArrayList<>();
        for (E entry : candidates)
        {
            if (name.apply(entry).toLowerCase(Locale.ROOT).contains(fragment)
                    && new HashSet<>(categories.apply(entry)).containsAll(asked))
            {
                found.add(entry);
            }
        }

        found.sort(Comparator.comparing(entry -> name.apply(entry).toLowerCase(Locale.ROOT)));
        return found;
    }
}
