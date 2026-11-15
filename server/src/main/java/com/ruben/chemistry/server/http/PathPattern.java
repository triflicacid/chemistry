package com.ruben.chemistry.server.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A route's path, as a series of segments matched against a request's path.
 * <p>
 * A segment written {@code :name} matches any one segment and captures it under
 * {@code name}; every other segment must match exactly. Both the HTTP router and the
 * WebSocket router route with this.
 */
public final class PathPattern
{
    private final List<String> segments;

    private PathPattern(List<String> segments)
    {
        this.segments = Collections.unmodifiableList(segments);
    }

    /**
     * @param path the route, starting with {@code /}, e.g. {@code /api/molecules/:id}
     * @return the compiled pattern
     * @throws IllegalArgumentException if the path does not start with {@code /}, or a
     *     {@code :} parameter segment has no name
     */
    public static PathPattern compile(String path)
    {
        if (!path.startsWith("/"))
        {
            throw new IllegalArgumentException("A route must start with '/', but was '" + path + "'");
        }

        List<String> segments = segments(path);
        for (String segment : segments)
        {
            if (segment.equals(":"))
            {
                throw new IllegalArgumentException("A path parameter needs a name, in '" + path + "'");
            }
        }

        return new PathPattern(segments);
    }

    /**
     * Splits a path into its non-empty segments, so that leading, trailing and repeated
     * slashes do not affect matching.
     *
     * @param path the path to split
     * @return its segments, in order
     */
    public static List<String> segments(String path)
    {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/"))
        {
            if (!segment.isEmpty())
            {
                segments.add(segment);
            }
        }
        return segments;
    }

    /**
     * @param given a request path's segments, as {@link #segments} splits them
     * @return the captured {@code :name} segments, empty if the pattern has none, or
     *     {@code null} if the path does not match this pattern at all
     */
    public Map<String, String> match(List<String> given)
    {
        if (given.size() != segments.size())
        {
            return null;
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++)
        {
            String expected = segments.get(i);
            String actual = given.get(i);

            if (expected.startsWith(":"))
            {
                parameters.put(expected.substring(1), actual);
            }
            else if (!expected.equals(actual))
            {
                return null;
            }
        }
        return parameters;
    }

    /**
     * Whether two patterns match exactly the same set of paths, which is what makes one
     * route a duplicate of another. Parameter names do not matter, only their positions.
     *
     * @param other the pattern to compare with
     * @return whether both patterns match the same paths
     */
    public boolean sameShape(PathPattern other)
    {
        if (other.segments.size() != segments.size())
        {
            return false;
        }

        for (int i = 0; i < segments.size(); i++)
        {
            String mine = segments.get(i);
            String theirs = other.segments.get(i);
            boolean mineVariable = mine.startsWith(":");

            if (mineVariable != theirs.startsWith(":") || (!mineVariable && !mine.equals(theirs)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @return this pattern's segments, in order
     */
    public List<String> segments()
    {
        return segments;
    }

    @Override
    public String toString()
    {
        return "/" + String.join("/", segments);
    }
}
