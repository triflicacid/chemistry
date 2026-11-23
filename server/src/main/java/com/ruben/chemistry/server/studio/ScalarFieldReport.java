package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.quantum.field.FieldGrid;

/**
 * One scalar field at every point of a grid.
 *
 * @param type always {@code scalar.result}
 * @param field which field it is, one of {@link WavefunctionScheduler#SCALAR_FIELDS}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry it was calculated from
 * @param status {@code complete} or {@code failed}
 * @param fragment an atom of the molecule it is of, as the client asked, or {@code null} for the whole session
 * @param grid where the points are, in the session's frame, in ångström, or {@code null} if there is no field
 * @param values every point's value, in the unit of the field, as a little-endian 32-bit float in base64, running along x fastest, then y, then z; or
 *     {@code null} if there is no field
 * @param min the least value, or {@code null} if there is no field
 * @param max the greatest value, or {@code null} if there is no field
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why there is no field, or {@code null} if there is one
 */
public record ScalarFieldReport(
        String type,
        String field,
        String sessionId,
        long version,
        String status,
        String fragment,
        FieldGrid grid,
        String values,
        Double min,
        Double max,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "scalar.result";

    /**
     * @param field which field it is
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule it is of, as an atom of it, or {@code null} for the whole session
     * @param grid where the values are
     * @param values the value at each point of the grid
     * @return the field, with no time taken yet: see {@link #elapsed}
     */
    public static ScalarFieldReport of(String field, StudioSession session, long version, String fragment, FieldGrid grid, float[] values)
    {
        return new ScalarFieldReport(TYPE, field, session.sessionId(), version, "complete", fragment, grid, FieldEncoding.encode(values),
                FieldEncoding.least(values), FieldEncoding.greatest(values), 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return the same report with that time
     */
    public ScalarFieldReport elapsed(long milliseconds)
    {
        return new ScalarFieldReport(type, field, sessionId, version, status, fragment, grid, values, min, max, milliseconds, message);
    }

    /**
     * @param field which field was asked for
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule that was asked for, as an atom of it, or {@code null} for the whole session
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static ScalarFieldReport failed(String field, StudioSession session, long version, String fragment, long elapsedMs, String message)
    {
        return new ScalarFieldReport(TYPE, field, session.sessionId(), version, "failed", fragment, null, null, null, null, elapsedMs, message);
    }
}
