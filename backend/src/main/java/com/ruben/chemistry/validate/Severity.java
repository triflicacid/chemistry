package com.ruben.chemistry.validate;

/**
 * Severity of a {@link Finding}.
 */
public enum Severity
{
    /** The molecule cannot stand as written, whether because no such structure exists or because its notation cannot be read. */
    ERROR,

    /** The molecule can stand as written, but it is probably not what was meant. */
    WARNING,

    /** Worth remarking on, but nothing is wrong. */
    INFO
}
