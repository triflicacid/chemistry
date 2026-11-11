package com.ruben.chemistry.server.json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Binds values in a {@link Map} to a record.
 */
public final class RecordConverter
{
    private RecordConverter()
    {
    }

    /**
     * @param object keys and values
     * @param type the record to build
     * @param <T> the record type
     * @return the record
     * @throws JsonException if the JSON does not fit the record
     * @throws IllegalArgumentException if {@code type} is not a record or uses an unsupported component type
     */
    public static <T extends Record> T bind(Map<String, Object> object, Class<T> type)
    {
        return bind(object, type, JsonNames::of);
    }

    /**
     * @param object keys and values
     * @param type the record to build
     * @param keys turns a component name into the key to read it from, e.g. {@code myValue} into {@code my_value}
     * @param <T> the record type
     * @return the record
     * @throws JsonException if the JSON does not fit the record
     * @throws IllegalArgumentException if {@code type} is not a record or uses an unsupported component type
     */
    public static <T extends Record> T bind(Map<String, Object> object, Class<T> type, Function<String, String> keys)
    {
        return type.cast(bindRecord(object, type, "", keys));
    }

    /**
     * Parses {@code json} and binds it.
     *
     * @param json JSON text whose top level is an object
     * @param type the record to build
     * @param <T> the record type
     * @return the record
     * @throws JsonException if the text is not valid JSON or does not fit the record
     */
    public static <T extends Record> T parse(String json, Class<T> type)
    {
        return bind(JsonParser.parse(json), type);
    }

    private static Object bindRecord(Object value, Class<?> type, String path, Function<String, String> keys)
    {
        if (!type.isRecord())
        {
            throw new IllegalArgumentException(type.getName() + " is not a record");
        }
        if (!(value instanceof Map<?, ?> map))
        {
            throw fail(path, "expected an object, got " + describe(value));
        }

        RecordComponent[] components = type.getRecordComponents();

        for (Object key : map.keySet())
        {
            boolean known = false;
            for (RecordComponent component : components)
            {
                known |= keys.apply(component.getName()).equals(key);
            }
            if (!known)
            {
                throw fail(join(path, String.valueOf(key)), "unknown property");
            }
        }

        Class<?>[] types = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++)
        {
            RecordComponent component = components[i];
            String key = keys.apply(component.getName());
            String childPath = join(path, key);
            types[i] = component.getType();

            Object raw = map.get(key);
            if (raw == null)
            {
                if (component.getType().isPrimitive())
                {
                    throw fail(childPath, "is required");
                }
                arguments[i] = null;
            }
            else
            {
                arguments[i] = convert(raw, component.getGenericType(), childPath, keys);
            }
        }

        try
        {
            return type.getDeclaredConstructor(types).newInstance(arguments);
        }
        catch (InvocationTargetException e)
        {
            if (e.getCause() instanceof JsonException json)
            {
                throw json;
            }
            if (e.getCause() instanceof RuntimeException runtime)
            {
                throw fail(path, runtime.getMessage() == null ? runtime.getClass().getSimpleName() : runtime.getMessage());
            }
            throw new IllegalStateException(e.getCause());
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalArgumentException("Cannot construct " + type.getName(), e);
        }
    }

    private static Object convert(Object value, Type type, String path, Function<String, String> keys)
    {
        if (type instanceof ParameterizedType parameterized)
        {
            return convertGeneric(value, parameterized, path, keys);
        }
        if (!(type instanceof Class<?> cls))
        {
            throw new IllegalArgumentException("Unsupported type " + type + " at " + path);
        }

        if (cls == Object.class)
        {
            return value;
        }
        if (cls == String.class)
        {
            if (value instanceof String)
            {
                return value;
            }
            throw fail(path, "expected a string, got " + describe(value));
        }
        if (cls == boolean.class || cls == Boolean.class)
        {
            if (value instanceof Boolean)
            {
                return value;
            }
            throw fail(path, "expected a boolean, got " + describe(value));
        }
        if (cls == long.class || cls == Long.class)
        {
            return integral(value, Long.MIN_VALUE, Long.MAX_VALUE, path);
        }
        if (cls == int.class || cls == Integer.class)
        {
            return (int) integral(value, Integer.MIN_VALUE, Integer.MAX_VALUE, path);
        }
        if (cls == short.class || cls == Short.class)
        {
            return (short) integral(value, Short.MIN_VALUE, Short.MAX_VALUE, path);
        }
        if (cls == byte.class || cls == Byte.class)
        {
            return (byte) integral(value, Byte.MIN_VALUE, Byte.MAX_VALUE, path);
        }
        if (cls == double.class || cls == Double.class)
        {
            return number(value, path).doubleValue();
        }
        if (cls == float.class || cls == Float.class)
        {
            return number(value, path).floatValue();
        }
        if (cls.isEnum())
        {
            return constant(value, cls, path);
        }
        if (cls.isRecord())
        {
            return bindRecord(value, cls, path, keys);
        }

        throw new IllegalArgumentException("Unsupported type " + cls.getName() + " at " + path);
    }

    private static Object convertGeneric(Object value, ParameterizedType type, String path, Function<String, String> keys)
    {
        Class<?> raw = (Class<?>) type.getRawType();
        Type[] arguments = type.getActualTypeArguments();

        if (raw == List.class)
        {
            if (!(value instanceof List<?> list))
            {
                throw fail(path, "expected an array, got " + describe(value));
            }
            List<Object> converted = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++)
            {
                converted.add(list.get(i) == null ? null : convert(list.get(i), arguments[0], path + "[" + i + "]", keys));
            }
            return Collections.unmodifiableList(converted);
        }

        if (raw == Map.class && arguments[0] == String.class)
        {
            if (!(value instanceof Map<?, ?> map))
            {
                throw fail(path, "expected an object, got " + describe(value));
            }
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                String key = (String) entry.getKey();
                converted.put(key, entry.getValue() == null ? null : convert(entry.getValue(), arguments[1], join(path, key), keys));
            }
            return Collections.unmodifiableMap(converted);
        }

        throw new IllegalArgumentException("Unsupported type " + type + " at " + path);
    }

    private static Number number(Object value, String path)
    {
        if (value instanceof Number number)
        {
            return number;
        }
        throw fail(path, "expected a number, got " + describe(value));
    }

    private static long integral(Object value, long min, long max, String path)
    {
        if (!(value instanceof Long number))
        {
            throw fail(path, "expected an integer, got " + describe(value));
        }
        if (number < min || number > max)
        {
            throw fail(path, "must be between " + min + " and " + max);
        }
        return number;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object constant(Object value, Class<?> type, String path)
    {
        if (!(value instanceof String name))
        {
            throw fail(path, "expected a string, got " + describe(value));
        }
        try
        {
            return Enum.valueOf((Class<? extends Enum>) type, name);
        }
        catch (IllegalArgumentException e)
        {
            throw fail(path, "must be one of " + List.of(type.getEnumConstants()));
        }
    }

    private static String describe(Object value)
    {
        if (value == null)
        {
            return "null";
        }
        if (value instanceof Map)
        {
            return "an object";
        }
        if (value instanceof List)
        {
            return "an array";
        }
        if (value instanceof String)
        {
            return "a string";
        }
        if (value instanceof Boolean)
        {
            return "a boolean";
        }
        return value instanceof Long ? "an integer" : "a number";
    }

    private static String join(String path, String key)
    {
        return path.isEmpty() ? key : path + "." + key;
    }

    private static JsonException fail(String path, String message)
    {
        return new JsonException(path.isEmpty() ? message : path + ": " + message, -1);
    }
}
