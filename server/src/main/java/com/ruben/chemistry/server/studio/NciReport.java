package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.quantum.field.FieldGrid;
import com.ruben.chemistry.quantum.field.NonCovalent;

/**
 * The two fields a plot of non-covalent interactions is drawn from, on one grid.
 *
 * @param type always {@code nci.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry it was calculated from
 * @param status {@code complete} or {@code failed}
 * @param fragment an atom of the molecule it is of, as the client asked, or {@code null} for the whole session
 * @param grid where the points are, in the session's frame, in ångström, or {@code null} if there is no field
 * @param gradient every point's reduced density gradient, which has no unit, as a little-endian 32-bit float in base64, running along x
 *     fastest, then y, then z; or {@code null} if there is no field
 * @param signed every point's electron density in electrons per cubic bohr, negative where the middle eigenvalue of its Hessian is, in the same
 *     form; or {@code null} if there is no field
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why there is no field, or {@code null} if there is one
 */
public record NciReport(
        String type,
        String sessionId,
        long version,
        String status,
        String fragment,
        FieldGrid grid,
        String gradient,
        String signed,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "nci.result";

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule it is of, as an atom of it, or {@code null} for the whole session
     * @param grid where the values are
     * @param fields the two fields at each point of the grid
     * @return the fields, with no time taken yet: see {@link #elapsed}
     */
    public static NciReport of(StudioSession session, long version, String fragment, FieldGrid grid, NonCovalent.Fields fields)
    {
        return new NciReport(TYPE, session.sessionId(), version, "complete", fragment, grid, FieldEncoding.encode(fields.reducedGradient()),
                FieldEncoding.encode(fields.signedDensity()), 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return the same report with that time
     */
    public NciReport elapsed(long milliseconds)
    {
        return new NciReport(type, sessionId, version, status, fragment, grid, gradient, signed, milliseconds, message);
    }

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule that was asked for, as an atom of it, or {@code null} for the whole session
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static NciReport failed(StudioSession session, long version, String fragment, long elapsedMs, String message)
    {
        return new NciReport(TYPE, session.sessionId(), version, "failed", fragment, null, null, null, elapsedMs, message);
    }
}
