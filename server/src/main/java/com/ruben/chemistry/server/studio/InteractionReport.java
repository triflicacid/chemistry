package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.quantum.field.MolecularOrbitals;

import java.util.ArrayList;
import java.util.List;

/**
 * How the orbitals of everything in a session together are made of the orbitals of its molecules each on its own.
 *
 * @param type always {@code interaction.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry the orbitals were calculated from
 * @param status {@code complete}, {@code unavailable} if there is no diagram to draw, as when there is only one molecule, or {@code failed}
 * @param requested the model the client asked for
 * @param model the kind of calculation that was made, or {@code mixed} if the calculations were not all the same kind; {@code null} if none was
 * @param parts the calculations: everything together first, then each molecule on its own in the order of its first atom; empty if there are none
 * @param links which orbital of the whole is made of which orbital of a molecule, and how much; empty if there are none
 * @param elapsedMs how long the calculations took, in milliseconds
 * @param message why there is no diagram, or {@code null} if there is one
 */
public record InteractionReport(
        String type,
        String sessionId,
        long version,
        String status,
        String requested,
        String model,
        List<Part> parts,
        List<Link> links,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "interaction.result";

    /** The least share of an orbital that a link is made for, below which the line would be too faint to see. */
    public static final double LEAST_WEIGHT = 0.05;

    /**
     * One calculation's orbitals.
     *
     * @param fragment an atom of the molecule, or {@code null} for everything together
     * @param channels the sets of orbitals: one, or one for each spin
     */
    public record Part(String fragment, List<Channel> channels)
    {
    }

    /**
     * One set of orbitals.
     *
     * @param spin {@code restricted} where both spins share the set, or {@code alpha} or {@code beta}
     * @param homo the index of the highest orbital that holds electrons, or {@code null} if none does
     * @param levels the orbitals, the lowest in energy first
     */
    public record Channel(String spin, Integer homo, List<Level> levels)
    {
    }

    /**
     * One orbital.
     *
     * @param index which orbital it is, counting from the lowest in energy at zero
     * @param energy its energy, in hartree
     * @param occupancy how many electrons it holds: two, one or none
     */
    public record Level(int index, double energy, int occupancy)
    {
    }

    /**
     * An orbital of the whole that is partly an orbital of a molecule.
     *
     * @param spin which set of orbitals both are in
     * @param whole which orbital of everything together
     * @param fragment an atom of the molecule
     * @param orbital which orbital of the molecule's set
     * @param weight how much of the whole's orbital is the molecule's, from zero to one: the square of their overlap
     */
    public record Link(String spin, int whole, String fragment, int orbital, double weight)
    {
    }

    /**
     * @param session the session the geometry is of
     * @param described the geometry that was calculated from
     * @param requested the model the client asked for
     * @param whole the orbitals of everything together
     * @param wholeModel the kind of calculation that made them
     * @param molecules the orbitals of each molecule on its own
     * @param moleculeModels the kind of calculation that made each
     * @param fragments an atom of each molecule, in the same order
     * @param links which orbital is made of which
     * @return the diagram, with no time taken yet: see {@link #elapsed}
     */
    public static InteractionReport of(StudioSession session, StudioSession.Described described, String requested, MolecularOrbitals whole, String wholeModel,
            List<MolecularOrbitals> molecules, List<String> moleculeModels, List<String> fragments, List<Link> links)
    {
        List<Part> parts = new ArrayList<>();
        parts.add(part(null, whole));
        for (int m = 0; m < molecules.size(); m++)
        {
            parts.add(part(fragments.get(m), molecules.get(m)));
        }

        List<String> models = new ArrayList<>(moleculeModels);
        models.add(wholeModel);
        String model = models.stream().distinct().count() == 1 ? wholeModel : OrbitalReport.MIXED;
        return new InteractionReport(TYPE, session.sessionId(), described.version(), "complete", requested, model, List.copyOf(parts), List.copyOf(links), 0, null);
    }

    /**
     * @param milliseconds how long the calculations took
     * @return this report, saying they took that long
     */
    public InteractionReport elapsed(long milliseconds)
    {
        return new InteractionReport(type, sessionId, version, status, requested, model, parts, links, milliseconds, message);
    }

    /**
     * @param session the session that has no diagram
     * @param version the version of the geometry
     * @param requested the model the client asked for
     * @param message why
     * @return a report that says so and nothing else
     */
    public static InteractionReport unavailable(StudioSession session, long version, String requested, String message)
    {
        return new InteractionReport(TYPE, session.sessionId(), version, "unavailable", requested, null, List.of(), List.of(), 0, message);
    }

    /**
     * @param session the session whose diagram could not be made
     * @param version the version of the geometry
     * @param requested the model the client asked for
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static InteractionReport failed(StudioSession session, long version, String requested, long elapsedMs, String message)
    {
        return new InteractionReport(TYPE, session.sessionId(), version, "failed", requested, null, List.of(), List.of(), elapsedMs, message);
    }

    private static Part part(String fragment, MolecularOrbitals orbitals)
    {
        List<Channel> channels = new ArrayList<>();
        for (MolecularOrbitals.Channel channel : orbitals.channels())
        {
            List<Level> levels = new ArrayList<>();
            for (int index = 0; index < channel.energies().length; index++)
            {
                levels.add(new Level(index, channel.energies()[index], channel.occupancy()[index]));
            }
            channels.add(new Channel(channel.spin(), channel.homo() < 0 ? null : channel.homo(), List.copyOf(levels)));
        }
        return new Part(fragment, List.copyOf(channels));
    }
}
