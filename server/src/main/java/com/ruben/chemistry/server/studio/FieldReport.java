package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.quantum.field.FieldGrid;

/**
 * One molecular orbital at every point of a grid.
 *
 * @param type always {@code field.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry the field was calculated from
 * @param status {@code complete} or {@code failed}
 * @param orbital which orbital it is, counting from the lowest in energy at zero
 * @param spin which set of orbitals it is of: {@code restricted}, {@code alpha} or {@code beta}
 * @param fragment an atom of the molecule the field is of, as the client asked, or {@code null} for the whole session
 * @param grid where the points are, in the session's frame, in ångström, or {@code null} if there is no field
 * @param values every point's amplitude as a little-endian 32-bit float in base64, running along x fastest, then y, then z; or
 *     {@code null} if there is no field
 * @param min the least value, or {@code null} if there is no field
 * @param max the greatest value, or {@code null} if there is no field
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why there is no field, or {@code null} if there is one
 */
public record FieldReport(
        String type,
        String sessionId,
        long version,
        String status,
        Integer orbital,
        String spin,
        String fragment,
        FieldGrid grid,
        String values,
        Double min,
        Double max,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "field.result";

    /** The kind of field that can be asked for. */
    public static final String ORBITAL = "orbital";

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param orbital which orbital
     * @param spin which set of orbitals
     * @param fragment the molecule the field is of, as an atom of it, or {@code null} for the whole session
     * @param grid where the values are
     * @param values the value at each point of the grid
     * @return the field, with no time taken yet: see {@link #elapsed}
     */
    public static FieldReport of(StudioSession session, long version, int orbital, String spin, String fragment, FieldGrid grid, float[] values)
    {
        return new FieldReport(TYPE, session.sessionId(), version, "complete", orbital, spin, fragment, grid, FieldEncoding.encode(values),
                FieldEncoding.least(values), FieldEncoding.greatest(values), 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return the same field with that time
     */
    public FieldReport elapsed(long milliseconds)
    {
        return new FieldReport(type, sessionId, version, status, orbital, spin, fragment, grid, values, min, max, milliseconds, message);
    }

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param orbital which orbital was to be calculated
     * @param spin which set of orbitals
     * @param fragment the molecule that was asked for, as an atom of it, or {@code null} for the whole session
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static FieldReport failed(StudioSession session, long version, int orbital, String spin, String fragment, long elapsedMs, String message)
    {
        return new FieldReport(TYPE, session.sessionId(), version, "failed", orbital, spin, fragment, null, null, null, null, elapsedMs, message);
    }
}
