
package com.ruben.chemistry.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CliOptions
{
    /** Definitions for all accepted arguments. */
    private final Map<String, CliOption<?>> definitions;

    /** Parsed values provided to the mapped argument. */
    private final Map<String, List<Object>> values;

    /** String values not associated with an argument. */
    private final List<String> subjects;

    CliOptions(
            Map<String, CliOption<?>> definitions,
            Map<String, List<Object>> values,
            List<String> subjects)
    {

        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));

        Map<String, List<Object>> copiedValues = new LinkedHashMap<>();
        values.forEach((name, entries) -> copiedValues.put(name, List.copyOf(entries)));

        this.values = Collections.unmodifiableMap(copiedValues);
        this.subjects = List.copyOf(subjects);
    }

    /**
     * Returns all positional arguments.
     */
    public List<String> subjects()
    {
        return subjects;
    }

    /**
     * Returns the only positional argument.
     *
     * @throws IllegalStateException if there is not exactly one subject
     */
    public String onlySubject()
    {
        if (subjects.size() != 1)
        {
            throw new IllegalStateException("Expected exactly one argument, got " + subjects.size());
        }

        return subjects.getFirst();
    }

    /**
     * Returns whether an option was explicitly supplied.
     */
    public boolean has(String name)
    {
        return values.containsKey(name);
    }

    /**
     * Returns the last supplied value, or the given fallback.
     */
    public String get(String name, String fallback)
    {
        List<Object> entries = values.get(name);

        if (entries == null || entries.isEmpty())
        {
            return fallback;
        }

        return Objects.toString(entries.getLast());
    }

    /**
     * Returns the parsed value for an option, or its default.
     */
    public <T> T get(CliOption<T> option)
    {
        Objects.requireNonNull(option, "option");

        List<Object> entries = values.get(option.name());

        if (entries == null || entries.isEmpty())
        {
            return option.defaultValue();
        }

        return cast(entries.getLast());
    }

    /**
     * Returns all values supplied for a repeatable option.
     * If the option was not supplied, returns an empty list.
     */
    public <T> List<T> getAll(CliOption<T> option)
    {
        Objects.requireNonNull(option, "option");

        List<Object> entries = values.get(option.name());
        if (entries == null)
        {
            return List.of();
        }

        List<T> result = new ArrayList<>(entries.size());
        for (Object entry : entries)
        {
            result.add(cast(entry));
        }

        return List.copyOf(result);
    }

    /**
     * Returns a boolean option.
     */
    public boolean flag(String name)
    {
        return flag(name, false);
    }

    /**
     * Returns a boolean option, or the given fallback.
     */
    public boolean flag(String name, boolean fallback)
    {
        List<Object> entries = values.get(name);

        if (entries == null || entries.isEmpty()) {
            return fallback;
        }

        Object value = entries.get(entries.size() - 1);

        if (value instanceof Boolean bool) {
            return bool;
        }

        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Returns a numeric option, or the given fallback.
     */
    public double number(String name, double fallback)
    {
        String value = get(name, null);

        return value == null
                ? fallback
                : Double.parseDouble(value);
    }

    /**
     * Returns the option definitions used to parse these arguments.
     */
    public Map<String, CliOption<?>> definitions()
    {
        return definitions;
    }

    /**
     * Returns the raw parsed values.
     */
    public Map<String, List<Object>> values()
    {
        return values;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value)
    {
        return (T) value;
    }
}
