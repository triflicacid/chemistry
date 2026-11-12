package com.ruben.chemistry.server.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads JSON (RFC 8259) into plain Java values.
 * <p>
 * The document must be an object. It becomes a {@code Map<String, Object>} (in document
 * order); inside it, nested objects are maps, arrays {@code List<Object>}, strings
 * {@code String}, true/false {@code Boolean}, null {@code null}, and numbers {@code Long}
 * when written as an integer that fits, else {@code Double}.
 * <p>
 * Strict: no trailing commas or data, no comments, no repeated keys in one object,
 * no NaN or Infinity, and nesting is limited to 64 levels.
 */
public final class JsonParser
{
    private static final int MAX_DEPTH = 64;

    private final String text;
    private int position;

    private JsonParser(String text)
    {
        this.text = text;
    }

    /**
     * @param text the JSON document
     * @return the object it holds
     * @throws JsonException if the text is not valid JSON, or is not an object
     */
    public static Map<String, Object> parse(String text)
    {
        JsonParser parser = new JsonParser(text);
        parser.skipWhitespace();

        if (parser.peek() != '{')
        {
            throw parser.fail("Expected a JSON object");
        }

        Map<String, Object> value = parser.object(0);
        parser.skipWhitespace();

        if (parser.position < text.length())
        {
            throw parser.fail("Unexpected data after the JSON value");
        }

        return value;
    }

    private Object value(int depth)
    {
        if (depth > MAX_DEPTH)
        {
            throw fail("Nested deeper than " + MAX_DEPTH + " levels");
        }

        if (position >= text.length())
        {
            throw fail("Unexpected end of input");
        }

        char next = text.charAt(position);
        return switch (next)
        {
            case '{' -> object(depth);
            case '[' -> array(depth);
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default ->
            {
                if (next == '-' || isDigit(next))
                {
                    yield number();
                }
                throw fail("Unexpected character '" + next + "'");
            }
        };
    }

    private Map<String, Object> object(int depth)
    {
        Map<String, Object> members = new LinkedHashMap<>();
        position++;
        skipWhitespace();

        if (consume('}'))
        {
            return members;
        }

        while (true)
        {
            skipWhitespace();
            if (peek() != '"')
            {
                throw fail("Expected a string key");
            }

            int keyStart = position;
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();

            if (members.containsKey(key))
            {
                throw new JsonException("Repeated key \"" + key + "\"", keyStart);
            }
            members.put(key, value(depth + 1));

            skipWhitespace();
            if (consume('}'))
            {
                return members;
            }
            expect(',');
        }
    }

    private List<Object> array(int depth)
    {
        List<Object> elements = new ArrayList<>();
        position++;
        skipWhitespace();

        if (consume(']'))
        {
            return elements;
        }

        while (true)
        {
            skipWhitespace();
            elements.add(value(depth + 1));
            skipWhitespace();

            if (consume(']'))
            {
                return elements;
            }
            expect(',');
        }
    }

    private String string()
    {
        position++;
        StringBuilder result = new StringBuilder();

        while (true)
        {
            if (position >= text.length())
            {
                throw fail("Unterminated string");
            }

            char c = text.charAt(position++);
            if (c == '"')
            {
                return result.toString();
            }

            if (c < 0x20)
            {
                throw new JsonException("Unescaped control character in string", position - 1);
            }

            if (c != '\\')
            {
                result.append(c);
                continue;
            }

            if (position >= text.length())
            {
                throw fail("Unterminated string");
            }

            char escape = text.charAt(position++);
            switch (escape)
            {
                case '"', '\\', '/' -> result.append(escape);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> result.append(unicodeEscape());
                default -> throw new JsonException("Invalid escape '\\" + escape + "'", position - 2);
            }
        }
    }

    private char unicodeEscape()
    {
        if (position + 4 > text.length())
        {
            throw fail("Incomplete \\u escape");
        }

        int code = 0;
        for (int i = 0; i < 4; i++)
        {
            int digit = Character.digit(text.charAt(position + i), 16);
            if (digit < 0)
            {
                throw new JsonException("Invalid \\u escape", position + i);
            }
            code = code * 16 + digit;
        }

        position += 4;
        return (char) code;
    }

    private Object number()
    {
        int start = position;
        boolean integral = true;

        consume('-');

        if (!consume('0'))
        {
            if (!isDigit(peek()))
            {
                throw fail("Invalid number");
            }
            digits();
        }

        if (consume('.'))
        {
            integral = false;
            if (!isDigit(peek()))
            {
                throw fail("Expected a digit after the decimal point");
            }
            digits();
        }

        if (consume('e') || consume('E'))
        {
            integral = false;
            if (!consume('+'))
            {
                consume('-');
            }
            if (!isDigit(peek()))
            {
                throw fail("Expected a digit in the exponent");
            }
            digits();
        }

        String literal = text.substring(start, position);

        if (integral)
        {
            try
            {
                return Long.parseLong(literal);
            }
            catch (NumberFormatException tooLarge)
            {
                // too big for a long: read as a double
            }
        }

        double value = Double.parseDouble(literal);
        if (Double.isInfinite(value))
        {
            throw new JsonException("Number out of range", start);
        }
        return value;
    }

    private void digits()
    {
        while (isDigit(peek()))
        {
            position++;
        }
    }

    private Object literal(String word, Object value)
    {
        if (!text.startsWith(word, position))
        {
            throw fail("Unexpected character '" + text.charAt(position) + "'");
        }

        position += word.length();
        return value;
    }

    private void skipWhitespace()
    {
        while (position < text.length())
        {
            char c = text.charAt(position);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
            {
                return;
            }
            position++;
        }
    }

    private char peek()
    {
        return position < text.length() ? text.charAt(position) : '\0';
    }

    private boolean consume(char expected)
    {
        if (position < text.length() && text.charAt(position) == expected)
        {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char expected)
    {
        if (!consume(expected))
        {
            throw fail(position >= text.length() ? "Unexpected end of input" : "Expected '" + expected + "'");
        }
    }

    private static boolean isDigit(char c)
    {
        return c >= '0' && c <= '9';
    }

    private JsonException fail(String message)
    {
        return new JsonException(message, position);
    }
}
