package com.ruben.chemistry.server.molecule;

/**
 * A request about a molecule could not be carried out: it could not be read, matched or drawn, or an option was invalid.
 */
public final class MoleculeException extends RuntimeException
{
    private final String code;

    /**
     * @param code a short machine-readable reason
     * @param message a human-readable explanation
     */
    public MoleculeException(String code, String message)
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
