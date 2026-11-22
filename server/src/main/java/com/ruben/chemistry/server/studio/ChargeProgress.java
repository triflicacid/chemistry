package com.ruben.chemistry.server.studio;

/**
 * How the calculation of a session's charges is getting on.
 *
 * @param type always {@code charges.progress}
 * @param sessionId the session the charges are for
 * @param version the version of the session whose geometry the charges are being calculated from
 * @param requested the model the client asked for
 * @param stage {@code integrals} while the integrals over the basis functions are worked out, {@code iterating} while the
 *     orbitals are made self-consistent, or {@code starting} before either
 * @param iteration how many Fock builds have been made, or zero if none has
 * @param energy the total energy so far, in hartree, or {@code null} before the first Fock build
 * @param error how far the orbitals are from self-consistent, or {@code null} before the first Fock build
 * @param elapsedMs how long the calculation has been running, in milliseconds
 */
public record ChargeProgress(
        String type,
        String sessionId,
        long version,
        String requested,
        String stage,
        int iteration,
        Double energy,
        Double error,
        long elapsedMs)
{
    /** The type this message is sent as. */
    public static final String TYPE = "charges.progress";
}
