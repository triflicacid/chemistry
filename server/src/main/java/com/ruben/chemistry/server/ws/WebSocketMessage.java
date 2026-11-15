package com.ruben.chemistry.server.ws;

import com.ruben.chemistry.server.json.Json;
import com.ruben.chemistry.server.json.JsonException;
import com.ruben.chemistry.server.json.JsonNames;
import com.ruben.chemistry.server.json.JsonParser;
import com.ruben.chemistry.server.json.RecordConverter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * One complete message read from a WebSocket connection.
 */
public final class WebSocketMessage
{
    private final Opcode type;
    private final byte[] payload;
    private final String text;

    private Map<String, Object> json;
    private JsonException invalid;
    private boolean parsed;

    private WebSocketMessage(Opcode type, byte[] payload, String text)
    {
        this.type = type;
        this.payload = payload;
        this.text = text;
    }

    /**
     * @param text the message text
     * @return a text message holding it
     */
    public static WebSocketMessage text(String text)
    {
        return new WebSocketMessage(Opcode.TEXT, text.getBytes(StandardCharsets.UTF_8), text);
    }

    /**
     * @param payload the message bytes
     * @return a binary message holding them
     */
    public static WebSocketMessage binary(byte[] payload)
    {
        return new WebSocketMessage(Opcode.BINARY, payload, null);
    }

    /**
     * @return whether this is a text message
     */
    public boolean isText()
    {
        return type == Opcode.TEXT;
    }

    /**
     * @return whether this is a binary message
     */
    public boolean isBinary()
    {
        return type == Opcode.BINARY;
    }

    /**
     * @return the message text
     * @throws WebSocketException if this is a binary message, which has no text
     */
    public String text()
    {
        if (text == null)
        {
            throw new WebSocketException(CloseCode.UNSUPPORTED_DATA, "This endpoint expects text messages, but a binary one arrived");
        }
        return text;
    }

    /**
     * @return the message bytes, which for a text message are its UTF-8 encoding
     */
    public byte[] bytes()
    {
        return payload;
    }

    /**
     * @return how many bytes the message carried
     */
    public int size()
    {
        return payload.length;
    }

    /**
     * @return whether this is a text message holding a JSON object
     */
    public boolean isJson()
    {
        return text != null && parse() != null;
    }

    /**
     * @return the message as a JSON object, or empty if it is binary or not valid JSON
     */
    public Optional<Map<String, Object>> asJson()
    {
        return text == null ? Optional.empty() : Optional.ofNullable(parse());
    }

    /**
     * @return the message as a JSON object
     * @throws WebSocketException if it is binary or not a valid JSON object
     */
    public Map<String, Object> json()
    {
        Map<String, Object> body = text == null ? null : parse();
        if (body == null)
        {
            throw new WebSocketException(
                    CloseCode.INVALID_PAYLOAD,
                    text == null
                            ? "This endpoint expects JSON text messages, but a binary one arrived"
                            : "The message is not a valid JSON object: " + invalid.getMessage());
        }
        return body;
    }

    /**
     * @param type the record the message should fit
     * @param <T> the record type
     * @return the message bound to {@code type}, reading {@code snake_case} keys into
     *     {@code camelCase} components
     * @throws WebSocketException if the message is not a JSON object, or does not fit {@code type}
     * @see RecordConverter#bind
     */
    public <T extends Record> T as(Class<T> type)
    {
        try
        {
            return RecordConverter.bind(json(), type, JsonNames::of);
        }
        catch (JsonException mismatch)
        {
            throw new WebSocketException(CloseCode.POLICY_VIOLATION, mismatch.getMessage());
        }
    }

    /**
     * @param path the keys to follow, as {@link Json#extractProperty} reads them
     * @return the value there, or empty if it is absent or null
     */
    public Optional<Object> value(String... path)
    {
        if (text == null || parse() == null)
        {
            return Optional.empty();
        }

        try
        {
            return Optional.ofNullable(Json.extractProperty(json, path));
        }
        catch (NoSuchElementException missing)
        {
            return Optional.empty();
        }
    }

    /**
     * @param path the keys to follow
     * @return the string there, or empty if it is absent, null, or not a string
     */
    public Optional<String> string(String... path)
    {
        return value(path).filter(String.class::isInstance).map(String.class::cast);
    }

    /**
     * @param path the keys to follow
     * @return the number there as a whole number, or empty if it is absent, null, or not a number
     */
    public Optional<Long> integer(String... path)
    {
        return value(path).filter(Number.class::isInstance).map(found -> ((Number) found).longValue());
    }

    /**
     * @param path the keys to follow
     * @return the number there, or empty if it is absent, null, or not a number
     */
    public Optional<Double> number(String... path)
    {
        return value(path).filter(Number.class::isInstance).map(found -> ((Number) found).doubleValue());
    }

    /**
     * @param path the keys to follow
     * @return the boolean there, or empty if it is absent, null, or not a boolean
     */
    public Optional<Boolean> bool(String... path)
    {
        return value(path).filter(Boolean.class::isInstance).map(Boolean.class::cast);
    }

    /**
     * @param path the keys to follow
     * @return the object there, or empty if it is absent, null, or not an object
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> object(String... path)
    {
        return value(path).filter(Map.class::isInstance).map(found -> (Map<String, Object>) found);
    }

    /**
     * @param path the keys to follow
     * @return the array there, or empty if it is absent, null, or not an array
     */
    @SuppressWarnings("unchecked")
    public Optional<List<Object>> array(String... path)
    {
        return value(path).filter(List.class::isInstance).map(found -> (List<Object>) found);
    }

    /**
     * @param path the keys to follow
     * @return the string there
     * @throws WebSocketException if it is absent, null, or not a string
     */
    public String requireString(String... path)
    {
        return string(path).orElseThrow(() -> missing("a string", path));
    }

    /**
     * @param path the keys to follow
     * @return the number there as a whole number
     * @throws WebSocketException if it is absent, null, or not a number
     */
    public long requireInteger(String... path)
    {
        return integer(path).orElseThrow(() -> missing("a whole number", path));
    }

    /**
     * @param path the keys to follow
     * @return the number there
     * @throws WebSocketException if it is absent, null, or not a number
     */
    public double requireNumber(String... path)
    {
        return number(path).orElseThrow(() -> missing("a number", path));
    }

    /**
     * @param path the keys to follow
     * @return the boolean there
     * @throws WebSocketException if it is absent, null, or not a boolean
     */
    public boolean requireBool(String... path)
    {
        return bool(path).orElseThrow(() -> missing("a boolean", path));
    }

    /**
     * @param path the keys to follow
     * @return the object there
     * @throws WebSocketException if it is absent, null, or not an object
     */
    public Map<String, Object> requireObject(String... path)
    {
        return object(path).orElseThrow(() -> missing("an object", path));
    }

    /**
     * @param path the keys to follow
     * @return the array there
     * @throws WebSocketException if it is absent, null, or not an array
     */
    public List<Object> requireArray(String... path)
    {
        return array(path).orElseThrow(() -> missing("an array", path));
    }

    @Override
    public String toString()
    {
        return "WebSocketMessage[" + (isText() ? "text" : "binary") + " " + payload.length + "B]";
    }

    /**
     * Parses the text as JSON once, remembering either the object or why it was not one.
     *
     * @return the parsed object, or {@code null} if the text is not a JSON object
     */
    private Map<String, Object> parse()
    {
        if (!parsed)
        {
            parsed = true;
            try
            {
                json = JsonParser.parse(text);
            }
            catch (JsonException notJson)
            {
                invalid = notJson;
            }
        }
        return json;
    }

    private WebSocketException missing(String expected, String[] path)
    {
        return new WebSocketException(
                CloseCode.POLICY_VIOLATION,
                "The message needs " + expected + " at '" + String.join(".", path) + "'");
    }
}
