
package com.ruben.chemistry.server.ws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The WebSocket opening handshake (RFC 6455 section 4.2): the client's upgrade request and the {@code 101} answer that completes it.
 *
 * @param path the path asked for, without its query string
 * @param rawQuery the query string, or an empty string if there was none
 * @param key the {@code Sec-WebSocket-Key} the client sent
 * @param headers every header sent, looked up without regard to case
 */
public record Handshake(String path, String rawQuery, String key, Map<String, List<String>> headers)
{
    /** Appended to the client's key before hashing, fixed by RFC 6455 section 1.3. */
    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** The largest upgrade request accepted, in bytes, bounding what an unknown peer can make us buffer. */
    private static final int MAX_REQUEST = 16 * 1024;

    /** The most headers accepted on an upgrade request. */
    private static final int MAX_HEADERS = 64;

    public Handshake
    {
        Objects.requireNonNull(path);
        Objects.requireNonNull(rawQuery);
        Objects.requireNonNull(key);
        Objects.requireNonNull(headers);

        Map<String, List<String>> immutableHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, values) -> immutableHeaders.put(name, List.copyOf(values)));
        headers = Collections.unmodifiableMap(immutableHeaders);
    }

    /**
     * Reads the client's upgrade request and checks that it is one.
     *
     * @param in the client's stream
     * @return the request, if it is a well-formed WebSocket upgrade
     * @throws IOException if the stream cannot be read
     * @throws HandshakeException if it is not a WebSocket upgrade this server can accept
     */
    public static Handshake read(InputStream in) throws IOException
    {
        RequestReader reader = new RequestReader(in);

        String requestLine = reader.line();
        String target = getUpgradeRequestTarget(requestLine);
        if (!target.startsWith("/") || target.startsWith("//") || target.indexOf('#') >= 0)
        {
            throw new HandshakeException(400, "The request target must use origin-form");
        }

        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int headerCount = 0;

        for (String header = reader.line(); !header.isEmpty(); header = reader.line())
        {
            if (header.startsWith(" ") || header.startsWith("\t"))
            {
                throw new HandshakeException(400, "Obsolete folded headers are not supported");
            }

            int colon = header.indexOf(':');
            if (colon <= 0)
            {
                throw new HandshakeException(400, "Malformed header: " + header);
            }

            String name = header.substring(0, colon);
            String value = header.substring(colon + 1).trim();

            if (!validHeaderName(name))
            {
                throw new HandshakeException(400, "Invalid header name: " + name);
            }

            if (!validHeaderValue(value))
            {
                throw new HandshakeException(400, "Invalid header value for: " + name);
            }

            if (++headerCount > MAX_HEADERS)
            {
                throw new HandshakeException(431, "The upgrade request has more than " + MAX_HEADERS + " headers");
            }

            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }

        if (first(headers, "Host").isEmpty())
        {
            throw new HandshakeException(400, "An HTTP/1.1 request needs a Host header");
        }

        if (headers.get("Host").size() != 1 || headers.get("Host").getFirst().isEmpty())
        {
            throw new HandshakeException(400, "The upgrade request needs exactly one non-empty Host header");
        }

        if (!hasToken(headers, "Upgrade", "websocket"))
        {
            throw new HandshakeException(426, "This path serves WebSocket connections; the request must ask to upgrade to one");
        }

        // Connection is a comma-separated list, and a browser may send "keep-alive, Upgrade"
        if (!hasToken(headers, "Connection", "upgrade"))
        {
            throw new HandshakeException(400, "An upgrade request needs a 'Connection: Upgrade' header");
        }

        List<String> versions = headers.getOrDefault("Sec-WebSocket-Version", List.of());
        if (versions.size() != 1 || !versions.getFirst().equals("13"))
        {
            String version = versions.isEmpty() ? "" : versions.getFirst();
            throw new HandshakeException(426, "Only WebSocket version 13 is supported, but version " + describe(version) + " was asked for");
        }

        List<String> keys = headers.getOrDefault("Sec-WebSocket-Key", List.of());
        if (keys.size() != 1 || keys.getFirst().isEmpty())
        {
            throw new HandshakeException(400, "An upgrade request needs exactly one Sec-WebSocket-Key header");
        }

        String key = keys.getFirst();

        if (decodeKey(key).length != 16)
        {
            throw new HandshakeException(400, "Sec-WebSocket-Key must be 16 bytes, base64-encoded");
        }

        int question = target.indexOf('?');
        String path = question < 0 ? target : target.substring(0, question);
        String rawQuery = question < 0 ? "" : target.substring(question + 1);

        return new Handshake(path, rawQuery, key, headers);
    }

    private static String getUpgradeRequestTarget(String requestLine)
    {
        String[] parts = requestLine.split(" ", -1);

        if (parts.length != 3
                || parts[0].isEmpty()
                || parts[1].isEmpty()
                || !parts[2].equals("HTTP/1.1"))
        {
            throw new HandshakeException(400, "Malformed HTTP/1.1 request line");
        }

        if (!parts[0].equals("GET"))
        {
            throw new HandshakeException(405, "A WebSocket upgrade must be a GET, but was " + parts[0]);
        }

        return parts[1];
    }

    /**
     * Writes the {@code 101} response that completes the handshake. Everything written to
     * the socket after this is WebSocket frames.
     *
     * @param out the client's stream
     * @throws IOException if the stream cannot be written
     */
    public void accept(OutputStream out) throws IOException
    {
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acceptKey() + "\r\n"
                + "\r\n";

        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * @return the {@code Sec-WebSocket-Accept} value that proves this server read the key
     */
    public String acceptKey()
    {
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest((key + MAGIC).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("Every JVM is required to provide SHA-1", impossible);
        }
    }

    /**
     * @param name a header name, in any case
     * @return its first value, if sent
     */
    public Optional<String> header(String name)
    {
        return first(headers, name);
    }

    /**
     * @return the query parameters, decoded, keeping the first value given for a repeated name
     */
    public Map<String, String> query()
    {
        Map<String, String> query = new LinkedHashMap<>();
        if (rawQuery.isEmpty())
        {
            return Collections.unmodifiableMap(query);
        }

        for (String pair : rawQuery.split("&", -1))
        {
            if (pair.isEmpty())
            {
                continue;
            }

            int equals = pair.indexOf('=');
            String name = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
            query.putIfAbsent(name, value);
        }

        return Collections.unmodifiableMap(query);
    }

    private static byte[] decodeKey(String key)
    {
        try
        {
            byte[] decoded = Base64.getDecoder().decode(key);

            if (!Base64.getEncoder().encodeToString(decoded).equals(key))
            {
                throw new IllegalArgumentException("Non-canonical base64");
            }

            return decoded;
        }
        catch (IllegalArgumentException notBase64)
        {
            throw new HandshakeException(400, "Sec-WebSocket-Key is not valid base64");
        }
    }

    private static String decode(String encoded)
    {
        try
        {
            // URLDecoder uses form encoding, where '+' means a space. Preserve literal plus signs.
            return URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException malformed)
        {
            throw new HandshakeException(400, "Malformed percent-encoding in the request target");
        }
    }

    private static boolean hasToken(Map<String, List<String>> headers, String name, String token)
    {
        for (String value : headers.getOrDefault(name, List.of()))
        {
            for (String part : value.split(","))
            {
                if (part.trim().toLowerCase(Locale.ROOT).equals(token))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static Optional<String> first(Map<String, List<String>> headers, String name)
    {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    private static boolean validHeaderName(String name)
    {
        if (name.isEmpty())
        {
            return false;
        }

        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);

            if (!(c >= 'a' && c <= 'z')
                    && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9')
                    && "!#$%&'*+-.^_`|~".indexOf(c) < 0)
            {
                return false;
            }
        }

        return true;
    }

    private static boolean validHeaderValue(String value)
    {
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);

            if ((c < 0x20 && c != '\t') || c == 0x7f)
            {
                return false;
            }
        }

        return true;
    }

    private static String describe(String version)
    {
        return version.isEmpty() ? "none" : "'" + version + "'";
    }

    /**
     * Reads CRLF-terminated lines while enforcing a limit across the entire request.
     */
    private static final class RequestReader
    {
        private final InputStream in;
        private int bytesRead;

        private RequestReader(InputStream in)
        {
            this.in = Objects.requireNonNull(in);
        }

        private String line() throws IOException
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            while (true)
            {
                int read = in.read();

                if (read < 0)
                {
                    throw new HandshakeException(400, "The connection ended inside the upgrade request");
                }

                if (++bytesRead > MAX_REQUEST)
                {
                    throw new HandshakeException(431, "The upgrade request exceeds " + MAX_REQUEST + " bytes");
                }

                if (read == '\r')
                {
                    int next = in.read();

                    if (next < 0)
                    {
                        throw new HandshakeException(400, "The connection ended inside the upgrade request");
                    }

                    if (++bytesRead > MAX_REQUEST)
                    {
                        throw new HandshakeException(431, "The upgrade request exceeds " + MAX_REQUEST + " bytes");
                    }

                    if (next != '\n')
                    {
                        throw new HandshakeException(400, "HTTP lines must end with CRLF");
                    }

                    return buffer.toString(StandardCharsets.UTF_8);
                }

                if (read == '\n')
                {
                    throw new HandshakeException(400, "HTTP lines must end with CRLF");
                }

                buffer.write(read);
            }
        }
    }

    /**
     * A request that is not a WebSocket upgrade this server can accept, carrying the HTTP
     * status to refuse it with.
     * <p>
     * The listener answers these as ordinary HTTP, because the connection has not become a
     * WebSocket yet and so cannot carry a close frame.
     */
    public static final class HandshakeException extends RuntimeException
    {
        private final int status;

        /**
         * @param status the HTTP status to refuse the request with
         * @param message a human-readable explanation, sent as the response body
         */
        public HandshakeException(int status, String message)
        {
            super(message);
            this.status = status;
        }

        /**
         * @return the HTTP status to refuse the request with
         */
        public int status()
        {
            return status;
        }
    }
}
