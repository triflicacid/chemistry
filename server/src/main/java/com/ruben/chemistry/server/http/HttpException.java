package com.ruben.chemistry.server.http;

/**
 * Stops handling a request with an error response.
 */
public final class HttpException extends RuntimeException
{
    private final int status;
    private final String code;

    /**
     * @param status the HTTP status to answer with
     * @param code e.g. {@code INVALID_JSON}
     * @param message a human-readable explanation
     */
    public HttpException(int status, String code, String message)
    {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * @return the HTTP status to answer with
     */
    public int status()
    {
        return status;
    }

    /**
     * @return the machine-readable reason
     */
    public String code()
    {
        return code;
    }
}
