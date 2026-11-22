package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.space.Vec3;

import java.util.List;

/**
 * The electrostatic potential at every point of a flat grid across the molecule, which is what a slice plane shows.
 *
 * @param type always {@code plane.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry it was calculated from
 * @param status {@code complete} or {@code failed}
 * @param fragment an atom of the molecule it is of, as the client asked, or {@code null} for the whole session
 * @param axis the axis the plane is at right angles to: {@code x}, {@code y} or {@code z}
 * @param position how far along the molecule's box in that axis the plane is, from 0 to 1, as the client asked
 * @param corners the corners of the plane in the session's frame, in ångström, or {@code null} if there is no field: the low end of both
 *     of its axes, then the high end of the first with the low of the second, then the high of both, then the low of the first with the high
 *     of the second. Its two axes are the other two, in the order that goes round: x then y, y then z, z then x
 * @param width how many points there are along the first of the plane's axes, or zero if there is no field
 * @param height how many along the second
 * @param potential every point's potential in hartree per elementary charge as a little-endian 32-bit float in base64, running along the first
 *     axis fastest; or {@code null} if there is no field
 * @param min the least potential, or {@code null} if there is no field
 * @param max the greatest potential, or {@code null} if there is no field
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why there is no field, or {@code null} if there is one
 */
public record PlaneReport(
        String type,
        String sessionId,
        long version,
        String status,
        String fragment,
        String axis,
        double position,
        List<Vec3> corners,
        int width,
        int height,
        String potential,
        Double min,
        Double max,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "plane.result";

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule it is of, as an atom of it, or {@code null} for the whole session
     * @param axis the axis the plane is at right angles to
     * @param position how far along the box the plane is
     * @param corners the corners of the plane, as for {@link PlaneReport}
     * @param width how many points there are along the first axis
     * @param height how many along the second
     * @param potential the potential at each point
     * @return the potential, with no time taken yet: see {@link #elapsed}
     */
    public static PlaneReport of(StudioSession session, long version, String fragment, String axis, double position, List<Vec3> corners, int width, int height, float[] potential)
    {
        return new PlaneReport(TYPE, session.sessionId(), version, "complete", fragment, axis, position, List.copyOf(corners), width, height,
                FieldEncoding.encode(potential), FieldEncoding.least(potential), FieldEncoding.greatest(potential), 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return the same report with that time
     */
    public PlaneReport elapsed(long milliseconds)
    {
        return new PlaneReport(type, sessionId, version, status, fragment, axis, position, corners, width, height, potential, min, max, milliseconds, message);
    }

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule that was asked for, as an atom of it, or {@code null} for the whole session
     * @param axis the axis that was asked for
     * @param position where along it
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static PlaneReport failed(StudioSession session, long version, String fragment, String axis, double position, long elapsedMs, String message)
    {
        return new PlaneReport(TYPE, session.sessionId(), version, "failed", fragment, axis, position, null, 0, 0, null, null, null, elapsedMs, message);
    }
}
