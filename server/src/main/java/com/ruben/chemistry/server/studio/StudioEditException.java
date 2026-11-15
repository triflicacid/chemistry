package com.ruben.chemistry.server.studio;

/**
 * An edit a session refused, leaving the session as it was.
 */
public final class StudioEditException extends RuntimeException
{
    private final String code;

    /**
     * @param code the protocol error code to report, such as {@code LOCKED}
     * @param message what to tell the user
     */
    public StudioEditException(String code, String message)
    {
        super(message);
        this.code = code;
    }

    /**
     * @return the protocol error code to report
     */
    public String code()
    {
        return code;
    }
}
