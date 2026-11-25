package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.quantum.field.MolecularOrbitals;
import com.ruben.chemistry.quantum.field.OrbitalComposition;
import com.ruben.chemistry.quantum.field.PlaneSymmetry;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The molecular orbitals of a session, or of some of its molecules each on its own, as the energy levels of Hartree-Fock
 * calculations.
 *
 * @param type always {@code orbitals.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry the orbitals were calculated from
 * @param status {@code complete}, {@code unavailable} if there are no orbitals of the kind asked for, as when a closed-shell
 *     calculation is asked of electrons that do not pair up, or {@code failed}
 * @param requested the model the client asked for, which is not always the one that was used
 * @param fragments the molecules the client asked for, each as an atom of it, in the order asked; empty for the whole session
 *     calculated as one
 * @param model the kind of calculation that was made: {@code rhf}, {@code uhf} or {@code rohf}, or {@code mixed} if the
 *     molecules were not all calculated the same way; {@code null} if there was none
 * @param electrons how many electrons the calculations treat explicitly, which is every electron less those of any core
 *     replaced by a core potential; zero if there are no orbitals
 * @param channels the sets of orbitals: one, or a set for each spin in an unrestricted calculation, for each molecule asked
 *     for; empty if there are none
 * @param totalEnergy the total energy of the calculation, in hartree, which for several molecules is the sum of theirs, or
 *     {@code null} if there are no orbitals
 * @param iterations how many Fock builds it took, all together, or {@code null} if there are no orbitals
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why there are no orbitals, or {@code null} if there are
 */
public record OrbitalReport(
        String type,
        String sessionId,
        long version,
        String status,
        String requested,
        List<String> fragments,
        String model,
        int electrons,
        List<Channel> channels,
        Double totalEnergy,
        Integer iterations,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "orbitals.result";

    /** The model name of a result whose molecules were not all calculated the same way. */
    public static final String MIXED = "mixed";

    /**
     * One set of orbitals.
     *
     * @param fragment an atom of the molecule the set is of, or {@code null} if it is of the session calculated as one
     * @param spin {@code restricted} where both spins share the set, or {@code alpha} or {@code beta}
     * @param homo the index of the highest orbital that holds electrons, or {@code null} if none does
     * @param atoms the atoms the calculation was of, whose order the orbitals' weights follow
     * @param flatness how far the atoms lie off the plane they lie closest to, as a root mean square distance in ångström, or
     *     {@code null} if there are fewer than three or they lie on a line, where there is no one plane
     * @param levels the orbitals, the lowest in energy first
     */
    public record Channel(String fragment, String spin, Integer homo, List<String> atoms, Double flatness, List<Level> levels)
    {
    }

    /**
     * One orbital.
     *
     * @param index which orbital it is, counting from the lowest in energy at zero
     * @param energy its energy, in hartree
     * @param occupancy how many electrons it holds: two, one or none
     * @param weights how much of the orbital is on each atom, in the order of the channel's atoms, each as the share in its s
     *     functions, then its p, then its d and higher, so three numbers an atom; they add up to one
     * @param pi how much of the orbital is pi, from zero for a sigma orbital to one for a pi orbital, which is its share in the p
     *     functions that point out of the plane of the atoms; {@code null} if the atoms are not flat enough for sigma and pi to mean something
     */
    public record Level(int index, double energy, int occupancy, List<Double> weights, Double pi)
    {
    }

    /**
     * The orbitals of one molecule, or of the whole session.
     *
     * @param fragment an atom of the molecule, or {@code null} for the whole session
     * @param model the kind of calculation that made them
     * @param orbitals the orbitals
     * @param atoms the atom each site of the calculation is, in site order
     * @param positions where each of those atoms is, in ångström
     */
    public record Piece(String fragment, String model, MolecularOrbitals orbitals, List<String> atoms, List<Vec3> positions)
    {
    }

    /**
     * @param session the session the geometry is of
     * @param described the geometry that was calculated from
     * @param requested the model the client asked for
     * @param fragments the molecules the client asked for
     * @param pieces the orbitals of each, in that order
     * @return the levels, with no time taken yet: see {@link #elapsed}
     */
    public static OrbitalReport of(StudioSession session, StudioSession.Described described, String requested, List<String> fragments, List<Piece> pieces)
    {
        List<Channel> channels = new ArrayList<>();
        int electrons = 0;
        int iterations = 0;
        double energy = 0.0;
        for (Piece piece : pieces)
        {
            energy += piece.orbitals().totalEnergy();
            iterations += piece.orbitals().iterations();
            PlaneSymmetry.Plane plane = PlaneSymmetry.fit(piece.positions());
            Double flatness = plane == null ? null : Math.round(plane.flatness() * 1.0e3) / 1.0e3;
            for (MolecularOrbitals.Channel channel : piece.orbitals().channels())
            {
                List<Level> levels = new ArrayList<>(channel.energies().length);
                for (int index = 0; index < channel.energies().length; index++)
                {
                    Double pi = plane != null && plane.flat() ? Math.round(PlaneSymmetry.piShare(piece.orbitals(), channel, index, plane.normal()) * 1.0e4) / 1.0e4 : null;
                    levels.add(new Level(index, channel.energies()[index], channel.occupancy()[index], weights(piece, channel, index), pi));
                    electrons += channel.occupancy()[index];
                }
                channels.add(new Channel(piece.fragment(), channel.spin(), channel.homo() < 0 ? null : channel.homo(), piece.atoms(), flatness, List.copyOf(levels)));
            }
        }

        long kinds = pieces.stream().map(Piece::model).distinct().count();
        String model = kinds == 1 ? pieces.getFirst().model() : MIXED;
        return new OrbitalReport(TYPE, session.sessionId(), described.version(), "complete", requested, List.copyOf(fragments), model, electrons,
                List.copyOf(channels), energy, iterations, 0, null);
    }

    /**
     * @param piece the calculation
     * @param channel a set of its orbitals
     * @param index which orbital of the set
     * @return how much of the orbital is on each atom, by kind of function, to four places, which is as fine as can be read
     */
    private static List<Double> weights(Piece piece, MolecularOrbitals.Channel channel, int index)
    {
        double[][] shares = OrbitalComposition.gross(piece.orbitals(), channel, index, piece.atoms().size());
        List<Double> flat = new ArrayList<>(shares.length * OrbitalComposition.KINDS);
        for (double[] site : shares)
        {
            for (double kind : site)
            {
                flat.add(Math.round(kind * 1.0e4) / 1.0e4);
            }
        }
        return List.copyOf(flat);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return this report, saying it took that long
     */
    public OrbitalReport elapsed(long milliseconds)
    {
        return new OrbitalReport(type, sessionId, version, status, requested, fragments, model, electrons, channels, totalEnergy, iterations, milliseconds, message);
    }

    /**
     * @param session the session that has no orbitals to show
     * @param version the version of the geometry
     * @param requested the model the client asked for
     * @param fragments the molecules the client asked for
     * @param message why
     * @return a report that says so and nothing else
     */
    public static OrbitalReport unavailable(StudioSession session, long version, String requested, List<String> fragments, String message)
    {
        return new OrbitalReport(TYPE, session.sessionId(), version, "unavailable", requested, List.copyOf(fragments), null, 0, List.of(), null, null, 0, message);
    }

    /**
     * @param session the session whose orbitals could not be calculated
     * @param version the version of the geometry
     * @param requested the model the client asked for
     * @param fragments the molecules the client asked for
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static OrbitalReport failed(StudioSession session, long version, String requested, List<String> fragments, long elapsedMs, String message)
    {
        return new OrbitalReport(TYPE, session.sessionId(), version, "failed", requested, List.copyOf(fragments), null, 0, List.of(), null, null, elapsedMs, message);
    }
}
