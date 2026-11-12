package com.ruben.chemistry.server.json;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes plain Java values as JSON.
 */
public final class JsonWriter
{
    private JsonWriter()
    {
    }

    /**
     * @param value what to write
     * @return it as JSON text
     * @throws JsonException if the value, or something inside it, cannot be written as JSON
     */
    public static String write(Object value)
    {
        StringBuilder out = new StringBuilder();
        value(value, out);
        return out.toString();
    }

    private static void value(Object value, StringBuilder out)
    {
        switch (value)
        {
            case null -> out.append("null");
            case String string -> string(string, out);
            case Boolean bool -> out.append(bool);
            case Enum<?> constant -> string(constant.name(), out);
            case Double number -> finite(number, out);
            case Float number -> finite(number, out);
            case Number number -> out.append(number);
            case Map<?, ?> map -> map(map, out);
            case Record record -> map(fields(record), out);
            case Iterable<?> items -> iterable(items, out);
            case Object[] items -> iterable(Arrays.asList(items), out);
            default -> throw new JsonException("Cannot write a " + value.getClass().getName() + " as JSON", -1);
        }
    }

    private static Map<String, Object> fields(Record record)
    {
        Map<String, Object> fields = new LinkedHashMap<>();

        for (RecordComponent component : record.getClass().getRecordComponents())
        {
            try
            {
                fields.put(JsonNames.of(component.getName()), component.getAccessor().invoke(record));
            }
            catch (ReflectiveOperationException e)
            {
                throw new JsonException("Cannot read " + record.getClass().getName() + "." + component.getName(), -1);
            }
        }

        return fields;
    }

    private static void finite(Number number, StringBuilder out)
    {
        double value = number.doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value))
        {
            throw new JsonException("Cannot write " + value + " as JSON", -1);
        }
        out.append(number);
    }

    private static void map(Map<?, ?> map, StringBuilder out)
    {
        out.append('{');
        boolean first = true;

        for (Map.Entry<?, ?> entry : map.entrySet())
        {
            if (!first)
            {
                out.append(',');
            }
            first = false;

            string(String.valueOf(entry.getKey()), out);
            out.append(':');
            value(entry.getValue(), out);
        }

        out.append('}');
    }

    private static void iterable(Iterable<?> items, StringBuilder out)
    {
        out.append('[');
        boolean first = true;

        for (Object item : items)
        {
            if (!first)
            {
                out.append(',');
            }
            first = false;

            value(item, out);
        }

        out.append(']');
    }

    private static void string(String text, StringBuilder out)
    {
        out.append('"');

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            switch (c)
            {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default ->
                {
                    if (c < 0x20)
                    {
                        out.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        out.append(c);
                    }
                }
            }
        }

        out.append('"');
    }
}
