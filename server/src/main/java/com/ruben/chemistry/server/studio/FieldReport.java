package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.quantum.field.FieldGrid;
import com.ruben.chemistry.space.Vec3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * A number at every point of a grid.
 *
 * @param type always {@code field.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry the field was calculated from
 * @param status {@code complete} or {@code failed}
 * @param kind what the field is: {@code orbital}
 * @param orbital which orbital it is, counting from the lowest in energy at zero, or {@code null} if the field is not of one
 * @param spin which set of orbitals it is of: {@code restricted}, {@code alpha} or {@code beta}; or {@code null} if the field is not of one
 * @param fragment an atom of the molecule the field is of, as the client asked, or {@code null} for the whole session
 * @param origin where the point with all indices zero is, in the session's frame, in ångström, or {@code null} if there is no field
 * @param spacing the distance between neighbouring points, in ångström, or {@code null} if there is no field
 * @param nx how many points there are along x, or zero if there is no field
 * @param ny how many along y
 * @param nz how many along z
 * @param values every point's value as a little-endian 32-bit float in base64, running along x fastest, then y, then z; or
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
        String kind,
        Integer orbital,
        String spin,
        String fragment,
        Vec3 origin,
        Double spacing,
        int nx,
        int ny,
        int nz,
        String values,
        Double min,
        Double max,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "field.result";

    /** The kind of a field that is the value of an orbital. */
    public static final String ORBITAL = "orbital";

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param kind what the field is
     * @param orbital which orbital, if it is one
     * @param grid where the values are
     * @param values the value at each point of the grid
     * @return the field, with no time taken yet: see {@link #elapsed}
     */
    public static FieldReport of(StudioSession session, long version, String kind, Integer orbital, String spin, String fragment, FieldGrid grid, float[] values)
    {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (float value : values)
        {
            bytes.putFloat(value);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return new FieldReport(TYPE, session.sessionId(), version, "complete", kind, orbital, spin, fragment, grid.origin(), grid.spacing(),
                grid.nx(), grid.ny(), grid.nz(), Base64.getEncoder().encodeToString(bytes.array()), min, max, 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return this report, saying it took that long
     */
    public FieldReport elapsed(long milliseconds)
    {
        return new FieldReport(type, sessionId, version, status, kind, orbital, spin, fragment, origin, spacing, nx, ny, nz, values, min, max, milliseconds, message);
    }

    /**
     * @param session the session whose field could not be calculated
     * @param version the version of the geometry
     * @param kind what the field was to be
     * @param orbital which orbital, if it was to be one
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static FieldReport failed(StudioSession session, long version, String kind, Integer orbital, String spin, String fragment, long elapsedMs, String message)
    {
        return new FieldReport(TYPE, session.sessionId(), version, "failed", kind, orbital, spin, fragment, null, null, 0, 0, 0, null, null, null, elapsedMs, message);
    }
}
