package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.space.Diagnostics;
import com.ruben.chemistry.space.Embedder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the geometry of a session says about how strained it is.
 * <p>
 * Each bond length, angle and contact is scored by how many of its tolerances it strays by, which is its
 * severity. A term at or within its tolerance counts for nothing. One beyond it adds the square of its severity less
 * one, so that the score is continuous at the tolerance, and is shared equally among the atoms it is between. The
 * strain is the square root of the total, which puts it back in tolerances: zero is a shape with nothing wrong with it,
 * and three is about as far out as one term three tolerances off. Each atom's share is the square root of what the terms
 * it is in add up to.
 * <p>
 * Torsions are left out. The ideal torsion is a staggered one, and a shape that has only been embedded has not been
 * relaxed into one, so counting them would show every fresh molecule as strained and hide what an edit has done.
 *
 * @param type always {@code geometry.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry this describes
 * @param status {@code complete}, or {@code failed} if the geometry could not be examined
 * @param strain the total strain in tolerances, or {@code null} if the calculation failed
 * @param findings the worst terms beyond their tolerance, worst first
 * @param clashes the pairs of atoms closer than their contact distance, worst first
 * @param atomStrain each atom's share of the strain, for the atoms with a share worth showing, in atom order
 * @param message why the calculation failed, or {@code null} if it did not
 */
public record GeometryReport(
        String type,
        String sessionId,
        long version,
        String status,
        Double strain,
        List<Finding> findings,
        List<ClashState> clashes,
        List<AtomStrain> atomStrain,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "geometry.result";

    /** The most findings one report lists. */
    public static final int MAX_FINDINGS = 100;

    /** The most clashes one report lists. */
    public static final int MAX_CLASHES = 100;

    /** The least share of the strain an atom must have to be listed, so that the atoms at ease are not. */
    public static final double MIN_ATOM_STRAIN = 0.05;

    /**
     * One term beyond its tolerance.
     *
     * @param kind {@code bond}, {@code angle} or {@code clash}
     * @param atoms the atoms it is between, in order
     * @param actual its value: angstroms for a bond or clash, degrees for an angle
     * @param ideal what it should be, in the same unit
     * @param severity how many tolerances it strays by
     */
    public record Finding(String kind, List<String> atoms, double actual, double ideal, double severity)
    {
    }

    /**
     * Two atoms closer than they should be.
     *
     * @param first one atom
     * @param second the other
     * @param distance how far apart they are, in angstroms
     * @param contact how far apart they should be at least, in angstroms
     */
    public record ClashState(String first, String second, double distance, double contact)
    {
    }

    /**
     * One atom's share of the strain.
     *
     * @param id the atom
     * @param strain its share of the strain
     */
    public record AtomStrain(String id, double strain)
    {
    }

    /**
     * @param session the session the geometry is of
     * @param snapshot the geometry to examine, taken from {@code session}
     * @return what it says
     */
    public static GeometryReport of(StudioSession session, StudioSession.Snapshot snapshot)
    {
        Molecule molecule = session.molecule();
        Diagnostics diagnostics = Diagnostics.of(molecule, Embedder.placed(molecule, snapshot.positions(), 0), 0);

        // the working graph has no counted hydrogens, so a node is the atom at the same position in its atom list
        List<String> ids = molecule.atoms().stream().map(session::atomId).toList();

        List<Finding> findings = new ArrayList<>();
        for (Diagnostics.Length length : diagnostics.lengths())
        {
            if (length.deviation() > Diagnostics.LENGTH_TOLERANCE)
            {
                findings.add(new Finding("bond", List.of(ids.get(length.first()), ids.get(length.second())),
                        length.actual(), length.ideal(), length.deviation() / Diagnostics.LENGTH_TOLERANCE));
            }
        }
        for (Diagnostics.Angle angle : diagnostics.angles())
        {
            if (angle.deviation() > Diagnostics.ANGLE_TOLERANCE)
            {
                findings.add(new Finding("angle", List.of(ids.get(angle.first()), ids.get(angle.middle()), ids.get(angle.second())),
                        angle.actual(), angle.ideal(), angle.deviation() / Diagnostics.ANGLE_TOLERANCE));
            }
        }
        List<Diagnostics.Clash> clashing = diagnostics.clashes().stream()
                .filter(clash -> clash.overlap() > Diagnostics.CLASH_TOLERANCE)
                .sorted(Comparator.comparingDouble(Diagnostics.Clash::overlap).reversed())
                .toList();
        for (Diagnostics.Clash clash : clashing)
        {
            findings.add(new Finding("clash", List.of(ids.get(clash.first()), ids.get(clash.second())),
                    clash.distance(), clash.contact(), clash.overlap() / Diagnostics.CLASH_TOLERANCE));
        }
        findings.sort(Comparator.comparingDouble(Finding::severity).reversed());

        // a term beyond its tolerance adds the square of its severity less one, shared among the atoms it is between
        double total = 0;
        Map<String, Double> shared = new LinkedHashMap<>();
        for (Finding finding : findings)
        {
            double excess = finding.severity() * finding.severity() - 1;
            total += excess;
            for (String atom : finding.atoms())
            {
                shared.merge(atom, excess / finding.atoms().size(), Double::sum);
            }
        }

        List<ClashState> clashes = clashing.stream()
                .limit(MAX_CLASHES)
                .map(clash -> new ClashState(ids.get(clash.first()), ids.get(clash.second()), clash.distance(), clash.contact()))
                .toList();

        List<AtomStrain> shares = new ArrayList<>();
        for (String id : ids)
        {
            double share = Math.sqrt(shared.getOrDefault(id, 0.0));
            if (share >= MIN_ATOM_STRAIN)
            {
                shares.add(new AtomStrain(id, share));
            }
        }

        return new GeometryReport(TYPE, session.sessionId(), snapshot.version(), "complete", Math.sqrt(total),
                List.copyOf(findings.subList(0, Math.min(findings.size(), MAX_FINDINGS))), clashes, List.copyOf(shares), null);
    }

    /**
     * @param session the session whose geometry could not be examined
     * @param version the version of the geometry
     * @param message why
     * @return a report that says so and nothing else
     */
    public static GeometryReport failed(StudioSession session, long version, String message)
    {
        return new GeometryReport(TYPE, session.sessionId(), version, "failed", null, List.of(), List.of(), List.of(), message);
    }
}
