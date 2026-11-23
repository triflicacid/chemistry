package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.quantum.field.FieldGrid;

/**
 * A field at every point of a grid: the electron density, the spin density, what bonding changes in the electron density, minus its Laplacian, or
 * the electron localisation function.
 *
 * @param type {@link #DENSITY}, {@link #SPIN}, {@link #DIFFERENCE}, {@link #LAPLACIAN} or {@link #ELF}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry it was calculated from
 * @param status {@code complete} or {@code failed}
 * @param fragment an atom of the molecule it is of, as the client asked, or {@code null} for the whole session
 * @param grid where the points are, in the session's frame, in ångström, or {@code null} if there is no field
 * @param density every point's density, in electrons per cubic bohr, which is signed for the spin and the difference, as a little-endian 32-bit float in base64, running along x
 *     fastest, then y, then z; or {@code null} if there is no field
 * @param min the least density, or {@code null} if there is no field
 * @param max the greatest density, or {@code null} if there is no field
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why there is no field, or {@code null} if there is one
 */
public record DensityReport(
        String type,
        String sessionId,
        long version,
        String status,
        String fragment,
        FieldGrid grid,
        String density,
        Double min,
        Double max,
        long elapsedMs,
        String message)
{
    /** The type of the electron density, which is never negative. */
    public static final String DENSITY = "density.result";

    /** The type of the spin density: the electrons of spin up less those of spin down. */
    public static final String SPIN = "spin.result";

    /** The type of the difference density: the density of the whole less the densities of its molecules each on its own. */
    public static final String DIFFERENCE = "difference.result";

    /** The type of minus the Laplacian of the electron density, which is positive where charge is concentrated and negative where it is depleted. */
    public static final String LAPLACIAN = "laplacian.result";

    /** The type of the electron localisation function, from zero to one, which is high where electrons are paired and apart from others. */
    public static final String ELF = "elf.result";

    /**
     * @param type what the density is
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule it is of, as an atom of it, or {@code null} for the whole session
     * @param grid where the values are
     * @param density the density at each point of the grid
     * @return the density, with no time taken yet: see {@link #elapsed}
     */
    public static DensityReport of(String type, StudioSession session, long version, String fragment, FieldGrid grid, float[] density)
    {
        return new DensityReport(type, session.sessionId(), version, "complete", fragment, grid, FieldEncoding.encode(density),
                FieldEncoding.least(density), FieldEncoding.greatest(density), 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return the same report with that time
     */
    public DensityReport elapsed(long milliseconds)
    {
        return new DensityReport(type, sessionId, version, status, fragment, grid, density, min, max, milliseconds, message);
    }

    /**
     * @param type what the density was to be
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule that was asked for, as an atom of it, or {@code null} for the whole session
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static DensityReport failed(String type, StudioSession session, long version, String fragment, long elapsedMs, String message)
    {
        return new DensityReport(type, session.sessionId(), version, "failed", fragment, null, null, null, null, elapsedMs, message);
    }
}
