package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.quantum.field.ElfBasins;
import com.ruben.chemistry.quantum.field.FieldGrid;
import com.ruben.chemistry.space.Vec3;

import java.util.List;

/**
 * The electron localisation function split into its basins, which are the electron pairs: lone pairs and bonds. It comes with the function itself and
 * which basin each point of the grid is in.
 *
 * @param type always {@code basins.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry it was calculated from
 * @param status {@code complete} or {@code failed}
 * @param fragment an atom of the molecule it is of, as the client asked, or {@code null} for the whole session
 * @param grid where the points are, in the session's frame, in ångström, or {@code null} if there is no field
 * @param values every point's value of the function, from zero to one, as a little-endian 32-bit float in base64, running along x fastest, then y, then
 *     z; or {@code null} if there is no field
 * @param labels every point's basin as a whole number from one, or zero for a point that is in none, in the same form; or {@code null} if there is no field
 * @param basins the basins, from the most localised; empty if there are none
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why there is no field, or {@code null} if there is one
 */
public record BasinReport(
        String type,
        String sessionId,
        long version,
        String status,
        String fragment,
        FieldGrid grid,
        String values,
        String labels,
        List<Basin> basins,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "basins.result";

    /**
     * One basin.
     *
     * @param id which basin it is, as it is numbered in {@code labels}
     * @param attractor where its maximum is, in the session's frame, in ångström
     * @param elf the value of the function at its maximum
     * @param population how many electrons it holds
     * @param kind {@code lone pair}, {@code bond}, {@code multicentre bond} or {@code unattached}
     * @param atoms the identifiers of the atoms it is attached to
     */
    public record Basin(int id, Vec3 attractor, double elf, double population, String kind, List<String> atoms)
    {
    }

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule it is of, as an atom of it, or {@code null} for the whole session
     * @param grid where the values are
     * @param elf the function at each point of the grid
     * @param partition the basins and which point is in which
     * @param atoms the identifier of each atom the partition was made with, in its order
     * @return the basins, with no time taken yet: see {@link #elapsed}
     */
    public static BasinReport of(StudioSession session, long version, String fragment, FieldGrid grid, float[] elf, ElfBasins.Partition partition, List<String> atoms)
    {
        List<Basin> basins = partition.basins().stream()
                .map(basin -> new Basin(basin.id(), basin.attractor(), basin.elf(), basin.population(), basin.kind(), basin.atoms().stream().map(atoms::get).toList()))
                .toList();
        return new BasinReport(TYPE, session.sessionId(), version, "complete", fragment, grid, FieldEncoding.encode(elf), FieldEncoding.encode(partition.labels()), basins, 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return the same report with that time
     */
    public BasinReport elapsed(long milliseconds)
    {
        return new BasinReport(type, sessionId, version, status, fragment, grid, values, labels, basins, milliseconds, message);
    }

    /**
     * @param session the session the geometry is of
     * @param version the version of the geometry
     * @param fragment the molecule that was asked for, as an atom of it, or {@code null} for the whole session
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static BasinReport failed(StudioSession session, long version, String fragment, long elapsedMs, String message)
    {
        return new BasinReport(TYPE, session.sessionId(), version, "failed", fragment, null, null, null, List.of(), elapsedMs, message);
    }
}
