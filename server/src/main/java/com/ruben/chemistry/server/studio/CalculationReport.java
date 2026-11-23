package com.ruben.chemistry.server.studio;

/**
 * What a Hartree-Fock calculation was.
 *
 * @param type always {@code calculation.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry it was calculated from
 * @param fragment an atom of the molecule it is of, or {@code null} for the whole session as one
 * @param model the kind of calculation that was made: {@code rhf}, {@code uhf} or {@code rohf}
 * @param multiplicity the spin multiplicity it was made at
 * @param electrons how many electrons were treated explicitly
 * @param iterations how many iterations the SCF took
 * @param energy the total energy, in hartree
 * @param elapsedMs how long it took, in milliseconds
 */
public record CalculationReport(
        String type,
        String sessionId,
        long version,
        String fragment,
        String model,
        int multiplicity,
        int electrons,
        int iterations,
        double energy,
        long elapsedMs)
{
    /** The type this message is sent as. */
    public static final String TYPE = "calculation.result";

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule it is of, as an atom of it, or {@code null} for the whole session
     * @param model the kind of calculation that was made
     * @param multiplicity the spin multiplicity it was made at
     * @param electrons how many electrons were treated explicitly
     * @param iterations how many iterations it took
     * @param energy the total energy, in hartree
     * @param elapsedMs how long it took, in milliseconds
     * @return the report
     */
    public static CalculationReport of(StudioSession session, long version, String fragment, String model, int multiplicity, int electrons, int iterations, double energy, long elapsedMs)
    {
        return new CalculationReport(TYPE, session.sessionId(), version, fragment, model, multiplicity, electrons, iterations, energy, elapsedMs);
    }
}
