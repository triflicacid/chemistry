package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.quantum.field.FieldGrid;

/**
 * The electron density at every point of a grid, with the electrostatic potential beside it.
 *
 * @param type always {@code esp.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry it was calculated from
 * @param status {@code complete} or {@code failed}
 * @param fragment an atom of the molecule it is of, as the client asked, or {@code null} for the whole session
 * @param grid where the points are, in the session's frame, in ångström, or {@code null} if there is no field
 * @param density every point's electron density, in electrons per cubic bohr, as a little-endian 32-bit float in base64, running along x
 *     fastest, then y, then z; or {@code null} if there is no field
 * @param potential the electrostatic potential at each point in the same form, in hartree per elementary charge, which is NaN at the points
 *     that are not near enough to a surface of the density to be worth finding it at; or {@code null} if there is no field
 * @param min the least density, or {@code null} if there is no field
 * @param max the greatest density, or {@code null} if there is no field
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why there is no field, or {@code null} if there is one
 */
public record EspReport(
        String type,
        String sessionId,
        long version,
        String status,
        String fragment,
        FieldGrid grid,
        String density,
        String potential,
        Double min,
        Double max,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "esp.result";

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule it is of, as an atom of it, or {@code null} for the whole session
     * @param grid where the values are
     * @param density the electron density at each point of the grid
     * @param potential the electrostatic potential at each point, which is NaN where it was not found
     * @return the density with its potential, with no time taken yet: see {@link #elapsed}
     */
    public static EspReport of(StudioSession session, long version, String fragment, FieldGrid grid, float[] density, float[] potential)
    {
        return new EspReport(TYPE, session.sessionId(), version, "complete", fragment, grid, FieldEncoding.encode(density), FieldEncoding.encode(potential),
                FieldEncoding.least(density), FieldEncoding.greatest(density), 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return the same report with that time
     */
    public EspReport elapsed(long milliseconds)
    {
        return new EspReport(type, sessionId, version, status, fragment, grid, density, potential, min, max, milliseconds, message);
    }

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule that was asked for, as an atom of it, or {@code null} for the whole session
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static EspReport failed(StudioSession session, long version, String fragment, long elapsedMs, String message)
    {
        return new EspReport(TYPE, session.sessionId(), version, "failed", fragment, null, null, null, null, null, elapsedMs, message);
    }
}
