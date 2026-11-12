package com.ruben.chemistry.server.http;

import com.ruben.chemistry.server.json.JsonWriter;

import java.io.IOException;
import java.util.Map;

/**
 * Respond to an incoming {@link Request}.
 */
public interface ResponseContext
{
    /**
     * Sets a header on the response.
     *
     * @param name the header name
     * @param value its value
     * @return this
     */
    ResponseContext header(String name, String value);

    /**
     * Answers with a status and no body.
     *
     * @param code the HTTP status
     * @throws IOException if the response cannot be sent
     */
    void respond(int code) throws IOException;

    /**
     * Answers 200 with a text or JSON body.
     *
     * @param body a {@link String} is written as text, anything else as a JSON object; see {@link JsonWriter}
     * @throws IOException if the response cannot be sent
     */
    void respond(Object body) throws IOException;

    /**
     * Answers with a status and a text or JSON body.
     *
     * @param code the HTTP status
     * @param body a {@link String} is written as text, anything else as a JSON object; see {@link JsonWriter}
     * @throws IOException if the response cannot be sent
     */
    void respond(int code, Object body) throws IOException;

    /**
     * Answers with a status and a body of any type, e.g. {@code image/svg+xml}.
     *
     * @param code the HTTP status
     * @param contentType the body's media type
     * @param body the text, sent as UTF-8
     * @throws IOException if the response cannot be sent
     */
    void respond(int code, String contentType, String body) throws IOException;

    /**
     * Answers with {@code {"error": {"code": ..., "message": ...}}}.
     *
     * @param status the HTTP status
     * @param code a short machine-readable reason
     * @param message a human-readable explanation
     * @throws IOException if the response cannot be sent
     */
    void error(int status, String code, String message) throws IOException;
}
