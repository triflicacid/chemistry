package com.ruben.chemistry.notation;

import java.util.ArrayList;
import java.util.List;

/**
 * Thrown when a chemical notation string cannot be parsed.
 *
 * <p>Carries the source text and the character offset where parsing failed,
 * optionally followed by a chain of enclosing-context frames:
 *
 * <pre>
 * Unexpected atom 'Hg' at line 1, column 10
 * c1ccccc1[Hg]
 *          ^
 *   inside aromatic ring opened at line 1, column 1
 * c1ccccc[Hg]
 * ^
 * </pre>
 */
public class InvalidNotationException extends RuntimeException
{
    /**
     * One enclosing context the parser was inside when it failed.
     *
     * @param description a human-readable description of the context, e.g. "inside branch opened at ..."
     * @param position 0-based offset into the exception's {@link #source()} where the context began
     * @param endPosition 0-based offset just past the last character the context spans
     */
    public record ContextFrame(String description, int position, int endPosition)
    {
        /**
         * Creates a context frame spanning a single character at {@code position}.
         *
         * @param description a human-readable description of the context, e.g. "inside branch opened at ..."
         * @param position 0-based offset into the exception's {@link #source()} where the context began
         */
        public ContextFrame(String description, int position)
        {
            this(description, position, position + 1);
        }
    }

    private final String source;
    private final int position;
    private final String rawMessage;
    private final List<ContextFrame> contextFrames;

    /**
     * Creates an exception for a parse failure at a single position.
     *
     * @param message description of the parse failure
     * @param source the notation string being parsed
     * @param position 0-based offset into {@code source} where the failure occurred
     */
    public InvalidNotationException(String message, String source, int position)
    {
        this(message, source, position, List.of(), null);
    }

    /**
     * Creates an exception for a parse failure at a single position, caused by another throwable.
     *
     * @param message description of the parse failure
     * @param source the notation string being parsed
     * @param position 0-based offset into {@code source} where the failure occurred
     * @param cause the underlying cause of this exception
     */
    public InvalidNotationException(String message, String source, int position, Throwable cause)
    {
        this(message, source, position, List.of(), cause);
    }

    /**
     * Creates an exception for a parse failure with enclosing-context frames.
     *
     * @param message description of the parse failure
     * @param source the notation string being parsed
     * @param position 0-based offset into {@code source} where the failure occurred
     * @param contextFrames enclosing-context frames, innermost first
     */
    public InvalidNotationException(
        String message,
        String source,
        int position,
        List<ContextFrame> contextFrames)
    {
        this(message, source, position, contextFrames, null);
    }

    /**
     * Creates an exception for a parse failure with enclosing-context frames, caused by another throwable.
     *
     * @param message description of the parse failure
     * @param source the notation string being parsed
     * @param position 0-based offset into {@code source} where the failure occurred
     * @param contextFrames enclosing-context frames, innermost first
     * @param cause the underlying cause of this exception
     */
    public InvalidNotationException(
        String message,
        String source,
        int position,
        List<ContextFrame> contextFrames,
        Throwable cause)
    {
        super(render(message, source, position, contextFrames), cause);
        this.rawMessage = message;
        this.source = source;
        this.position = clamp(position, source);
        this.contextFrames = List.copyOf(contextFrames);
    }

    /**
     * Returns a copy of this exception with an additional context frame appended.
     *
     * @param frame the context frame to append
     * @return a new exception with the same message, source and position, and {@link #contextFrames()} extended by one entry
     */
    public InvalidNotationException withContext(ContextFrame frame)
    {
        List<ContextFrame> enriched = new ArrayList<>(contextFrames);
        enriched.add(frame);
        return new InvalidNotationException(rawMessage, source, position, enriched, getCause());
    }

    /**
     * @return the notation string being parsed when this exception occurred
     */
    public String source()
    {
        return source;
    }

    /**
     * @return 0-based character offset into {@link #source()} where parsing failed
     */
    public int position()
    {
        return position;
    }

    /**
     * @return 1-based line number of {@link #position()} within {@link #source()}
     */
    public int line()
    {
        return lineOf(source, position);
    }

    /**
     * @return 1-based column number of {@link #position()} within {@link #source()}
     */
    public int column()
    {
        return columnOf(source, position);
    }

    /**
     * @return the message without position decoration or context frames
     */
    public String rawMessage()
    {
        return rawMessage;
    }

    /**
     * @return enclosing-context frames, innermost first
     */
    public List<ContextFrame> contextFrames()
    {
        return contextFrames;
    }

    /**
     * Clamps a position to a valid offset within a source string.
     *
     * @param position candidate offset, which may be out of range
     * @param source the string {@code position} is relative to
     * @return {@code position} clamped to the range {@code [0, source.length()]}
     */
    private static int clamp(int position, String source)
    {
        return Math.clamp(position, 0, source.length());
    }

    /**
     * Renders a full exception message: the base message with its position, followed by one
     * rendered block per context frame.
     *
     * @param message description of the parse failure
     * @param source the notation string being parsed
     * @param position 0-based offset into {@code source} where the failure occurred
     * @param contextFrames enclosing-context frames, innermost first
     * @return the fully formatted exception message
     */
    private static String render(
        String message,
        String source,
        int position,
        List<ContextFrame> contextFrames)
    {
        int clamped = clamp(position, source);
        StringBuilder sb = new StringBuilder();
        sb.append(message)
            .append(" at line ").append(lineOf(source, clamped))
            .append(", column ").append(columnOf(source, clamped))
            .append(System.lineSeparator())
            .append(pointerBlock(source, clamped));

        for (ContextFrame frame : contextFrames)
        {
            int framePos = clamp(frame.position(), source);
            int frameEnd = clamp(frame.endPosition(), source);
            sb.append(System.lineSeparator())
                .append("  ").append(frame.description())
                .append(" at line ").append(lineOf(source, framePos))
                .append(", column ").append(columnOf(source, framePos))
                .append(System.lineSeparator())
                .append(pointerBlock(source, framePos, frameEnd));
        }

        return sb.toString();
    }

    /**
     * @param source the string {@code position} is relative to
     * @param position 0-based offset into {@code source}
     * @return 1-based line number containing {@code position}
     */
    private static int lineOf(String source, int position)
    {
        int line = 1;
        for (int i = 0; i < position && i < source.length(); i++)
        {
            if (source.charAt(i) == '\n')
            {
                line++;
            }
        }
        return line;
    }

    /**
     * @param source the string {@code position} is relative to
     * @param position 0-based offset into {@code source}
     * @return 1-based column number of {@code position} within its line
     */
    private static int columnOf(String source, int position)
    {
        int lineStart = lineStart(source, position);
        return position - lineStart + 1;
    }

    /**
     * @param source the string {@code position} is relative to
     * @param position 0-based offset into {@code source}
     * @return 0-based offset of the first character of the line containing {@code position}
     */
    private static int lineStart(String source, int position)
    {
        // search from the character before position, so a position sitting on a newline
        // still belongs to the line it terminates rather than to the next one
        int searchFrom = Math.max(0, Math.min(position, source.length()) - 1);
        int newline = source.lastIndexOf('\n', searchFrom);
        return newline + 1;
    }

    /**
     * @param source the string {@code position} is relative to
     * @param position 0-based offset into {@code source} to point at
     * @return the source line containing {@code position}, followed by a caret marking it
     */
    private static String pointerBlock(String source, int position)
    {
        return pointerBlock(source, position, position + 1);
    }

    /**
     * @param source the string {@code position} and {@code endPosition} are relative to
     * @param position 0-based start offset (inclusive) to mark
     * @param endPosition 0-based end offset (exclusive) to mark
     * @return the source line with markers inserted
     */
    private static String pointerBlock(String source, int position, int endPosition)
    {
        int lineStart = lineStart(source, position);
        int lineEnd = source.indexOf('\n', position);
        if (lineEnd == -1)
        {
            lineEnd = source.length();
        }
        String line = source.substring(lineStart, lineEnd);
        int column = position - lineStart;
        int spanLength = Math.max(1, Math.min(endPosition, lineEnd) - position);

        StringBuilder marker = new StringBuilder(" ".repeat(Math.max(0, column)));
        marker.append('^');
        marker.repeat("~", spanLength - 1);

        return line + System.lineSeparator() + marker;
    }
}
