package com.ruben.chemistry.server.render;

/**
 * A molecule could not be drawn.
 */
public final class RenderException extends RuntimeException
{
    private final String code;

    /**
     * @param code a short machine-readable reason
     * @param message a human-readable explanation
     */
    public RenderException(String code, String message)
    {
        super(message);
        this.code = code;
    }

    /**
     * @return a short machine-readable reason
     */
    public String code()
    {
        return code;
    }
}
