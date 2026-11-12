package com.ruben.chemistry.server.json;

public final class JsonException extends IllegalArgumentException
{
    private final int offset;

    /**
     * @param message what is wrong
     * @param offset where in the text, or -1 if it has no position
     */
    public JsonException(String message, int offset)
    {
        super(offset < 0 ? message : message + " (at offset " + offset + ")");
        this.offset = offset;
    }

    /**
     * @return the character offset of the problem, or -1 if it has none
     */
    public int offset()
    {
        return offset;
    }
}
