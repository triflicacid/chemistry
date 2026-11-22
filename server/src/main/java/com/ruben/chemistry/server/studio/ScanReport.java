package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.forcefield.ElectronDensityForceField;
import com.ruben.chemistry.forcefield.Energy;
import com.ruben.chemistry.forcefield.ForceField;
import com.ruben.chemistry.forcefield.ForceFieldAtom;
import com.ruben.chemistry.forcefield.ForceFieldAtoms;
import com.ruben.chemistry.forcefield.ForceFieldMolecule;
import com.ruben.chemistry.forcefield.HydrogenBondScan;
import com.ruben.chemistry.forcefield.ModelType;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How the interaction energy of a session's molecules changes as one is drawn away from another along a hydrogen bond.
 * <p>
 * Every N-H, O-H or F-H of one molecule that lies near an N, O or F of another is a candidate bond, and the closest
 * {@link #MAX_BONDS} of them are each scanned. A scan moves the acceptor's molecule rigidly along the line of the donor's X-H
 * bond, as {@link HydrogenBondScan} does, so each point comes with where the moved molecule's atoms are.
 *
 * @param type always {@code scan.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry the scan was made from
 * @param status {@code complete}, {@code unavailable} if the session has no pair of molecules with a hydrogen bond between
 *     them to scan, or {@code failed}
 * @param molecules how many separate molecules the session has
 * @param bonds the bonds scanned, the one whose hydrogen is nearest its acceptor first; empty if there is no scan
 * @param model the parameterisation of the force field, or {@code null} if there is no scan
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why there is no scan, or {@code null} if there is one
 */
public record ScanReport(
        String type,
        String sessionId,
        long version,
        String status,
        int molecules,
        List<BondScan> bonds,
        String model,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "scan.result";

    /** The nearest the hydrogen is brought to the acceptor, in angstroms. */
    public static final double FROM = 1.3;

    /** The furthest the hydrogen is taken from the acceptor, in angstroms. */
    public static final double TO = 4.5;

    /** How many distances are tried, which puts them a tenth of an angstrom apart. */
    public static final int STEPS = 33;

    /** The most bonds that are scanned, which are the ones whose hydrogen is nearest its acceptor. */
    public static final int MAX_BONDS = 8;

    /** The parameterisation used, which is the one the forces use. */
    private static final ModelType MODEL = ForceReport.MODEL;

    /** How many bohr there are in an angstrom. */
    private static final double BOHR_PER_ANGSTROM = 1.0 / 0.529177210903;

    /**
     * One hydrogen bond, scanned.
     *
     * @param donor the heavy atom that gives the hydrogen
     * @param hydrogen the hydrogen
     * @param acceptor the atom that receives it
     * @param current how far the hydrogen is from the acceptor in the geometry as it is, in angstroms
     * @param points the scan, in order of increasing distance
     * @param minimumDistance the distance of the lowest energy between the two molecules, in angstroms, refined between points
     * @param minimumEnergy the interaction energy there, in hartree
     */
    public record BondScan(String donor, String hydrogen, String acceptor, double current, List<Point> points, double minimumDistance, double minimumEnergy)
    {
    }

    /**
     * The energy at one distance.
     *
     * @param distance the distance from the hydrogen to the acceptor, in angstroms
     * @param energy the interaction energy of the two molecules, in hartree
     * @param energyWithOthers the interaction energy of the moved molecule with every other molecule, in hartree, which counts
     *     a third molecule that the move brings closer or takes away; {@code null} if there are only two molecules, when it
     *     is the same as {@code energy}
     * @param terms what {@code energy} is made of
     * @param atoms where the atoms of the moved molecule are, in the session's frame
     */
    public record Point(double distance, double energy, Double energyWithOthers, Terms terms, List<AtomPosition> atoms)
    {
    }

    /**
     * The parts of an interaction energy, in hartree.
     *
     * @param nuclearNuclear the repulsion of the nuclei for each other
     * @param nuclearElectron the attraction of each molecule's nuclei for the other's electrons
     * @param electronElectron the repulsion of the electrons for each other
     * @param exchangeCorrelation the exchange-correlation fit
     */
    public record Terms(double nuclearNuclear, double nuclearElectron, double electronElectron, double exchangeCorrelation)
    {
        /**
         * @param energy the contributions as the force field gives them
         * @return them with the two exchange-correlation fits taken together
         */
        static Terms of(Energy energy)
        {
            return new Terms(energy.nuclearNuclear(), energy.nuclearElectron(), energy.electronElectron(), energy.exchangeCorrelation());
        }
    }

    /**
     * An atom and where it is.
     *
     * @param id the atom
     * @param position where it is, in angstroms
     */
    public record AtomPosition(String id, Vec3 position)
    {
    }

    /** A donor in one molecule and an acceptor in another. */
    private record Candidate(int donorMolecule, HydrogenBondScan.Donor donor, int acceptorMolecule, int acceptor, double distance)
    {
    }

    /**
     * @param session the session the geometry is of
     * @param described the geometry to scan, taken from {@code session}
     * @param charges assigns each molecule's charges, which must follow the convention of the force field
     * @return the scan, with no time taken yet: see {@link #elapsed}; {@code unavailable} if there is nothing to scan
     * @throws RuntimeException if the charges cannot be assigned or the force field has no parameters for an element
     */
    public static ScanReport of(StudioSession session, StudioSession.Described described, PartialChargeModel charges)
    {
        Structure3D structure = described.structure();
        List<List<Integer>> parts = ForceReport.connectedParts(structure);
        if (parts.size() < 2)
        {
            return unavailable(session, described.version(), parts.size(), "A scan needs at least two molecules, and this session has only one. Add another, as in O.O");
        }

        ForceField forceField = ElectronDensityForceField.forceField(MODEL);
        List<ForceFieldMolecule> molecules = new ArrayList<>();
        for (int part = 0; part < parts.size(); part++)
        {
            Structure3D alone = ForceReport.alone(structure, parts.get(part));
            molecules.add(forceField.molecule("molecule " + (part + 1), ForceFieldAtoms.of(alone, 0, charges.calculate(alone, 0), MODEL)));
        }

        List<Candidate> candidates = closest(molecules);
        if (candidates.isEmpty())
        {
            return unavailable(session, described.version(), parts.size(), "No N-H, O-H or F-H of one molecule has an N, O or F of another to bond to");
        }

        List<BondScan> bonds = new ArrayList<>();
        for (Candidate candidate : candidates)
        {
            bonds.add(scan(described, parts, molecules, forceField, candidate));
        }
        return new ScanReport(TYPE, session.sessionId(), described.version(), "complete", parts.size(), List.copyOf(bonds), MODEL.name(), 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return this report, saying it took that long
     */
    public ScanReport elapsed(long milliseconds)
    {
        return new ScanReport(type, sessionId, version, status, molecules, bonds, model, milliseconds, message);
    }

    /**
     * @param session the session whose scan could not be calculated
     * @param version the version of the geometry
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static ScanReport failed(StudioSession session, long version, long elapsedMs, String message)
    {
        return new ScanReport(TYPE, session.sessionId(), version, "failed", 0, List.of(), null, elapsedMs, message);
    }

    private static ScanReport unavailable(StudioSession session, long version, int molecules, String message)
    {
        return new ScanReport(TYPE, session.sessionId(), version, "unavailable", molecules, List.of(), null, 0, message);
    }

    /**
     * Scans one bond.
     *
     * @param described the geometry, which says which atom each site is
     * @param parts the sites of each molecule
     * @param molecules the molecules
     * @param forceField the force field
     * @param candidate the bond to scan
     * @return the energy at each distance
     */
    private static BondScan scan(StudioSession.Described described, List<List<Integer>> parts, List<ForceFieldMolecule> molecules, ForceField forceField, Candidate candidate)
    {
        ForceFieldMolecule donorMolecule = molecules.get(candidate.donorMolecule());
        ForceFieldMolecule acceptorMolecule = molecules.get(candidate.acceptorMolecule());
        List<Integer> acceptorSites = parts.get(candidate.acceptorMolecule());
        HydrogenBondScan.Donor donor = candidate.donor();

        HydrogenBondScan.Result scan = HydrogenBondScan.scan(donorMolecule, donor, acceptorMolecule, candidate.acceptor(), forceField,
                FROM * BOHR_PER_ANGSTROM, TO * BOHR_PER_ANGSTROM, STEPS);

        // what the other molecules contribute among themselves does not depend on where the moved one goes
        List<ForceFieldMolecule> rest = new ArrayList<>(molecules);
        rest.remove(candidate.acceptorMolecule());
        double restEnergy = rest.size() > 1 ? ElectronDensityForceField.totalEnergy(rest, forceField.xc()) : 0.0;

        List<Point> points = new ArrayList<>();
        for (HydrogenBondScan.Point point : scan.points())
        {
            ForceFieldMolecule moved = HydrogenBondScan.placeAcceptor(donorMolecule, donor, acceptorMolecule, candidate.acceptor(), forceField, point.distance());

            Double withOthers = null;
            if (molecules.size() > 2)
            {
                List<ForceFieldMolecule> all = new ArrayList<>(molecules);
                all.set(candidate.acceptorMolecule(), moved);
                withOthers = ElectronDensityForceField.totalEnergy(all, forceField.xc()) - restEnergy;
            }

            List<AtomPosition> atoms = new ArrayList<>();
            for (int atom = 0; atom < moved.atoms().size(); atom++)
            {
                ForceFieldAtom at = moved.atoms().get(atom);
                atoms.add(new AtomPosition(described.sites().get(acceptorSites.get(atom)),
                        new Vec3(at.x() / BOHR_PER_ANGSTROM, at.y() / BOHR_PER_ANGSTROM, at.z() / BOHR_PER_ANGSTROM)));
            }
            points.add(new Point(point.distance() / BOHR_PER_ANGSTROM, point.energy().total(), withOthers, Terms.of(point.energy()), List.copyOf(atoms)));
        }

        List<Integer> donorSites = parts.get(candidate.donorMolecule());
        return new BondScan(
                described.sites().get(donorSites.get(donor.heavyAtom())),
                described.sites().get(donorSites.get(donor.hydrogen())),
                described.sites().get(acceptorSites.get(candidate.acceptor())),
                candidate.distance() / BOHR_PER_ANGSTROM, List.copyOf(points),
                scan.minimumDistance() / BOHR_PER_ANGSTROM, scan.minimumEnergy());
    }

    /**
     * @param molecules the molecules
     * @return the {@link #MAX_BONDS} pairs of a donor and an acceptor in different molecules that lie closest together, the
     *     closest first
     */
    private static List<Candidate> closest(List<ForceFieldMolecule> molecules)
    {
        List<Candidate> all = new ArrayList<>();
        for (int donorMolecule = 0; donorMolecule < molecules.size(); donorMolecule++)
        {
            ForceFieldMolecule donors = molecules.get(donorMolecule);
            for (HydrogenBondScan.Donor donor : HydrogenBondScan.donors(donors))
            {
                for (int acceptorMolecule = 0; acceptorMolecule < molecules.size(); acceptorMolecule++)
                {
                    if (acceptorMolecule == donorMolecule)
                    {
                        continue;
                    }
                    ForceFieldMolecule acceptors = molecules.get(acceptorMolecule);
                    for (int acceptor : HydrogenBondScan.acceptors(acceptors))
                    {
                        all.add(new Candidate(donorMolecule, donor, acceptorMolecule, acceptor,
                                distance(donors.atoms().get(donor.hydrogen()), acceptors.atoms().get(acceptor))));
                    }
                }
            }
        }
        all.sort(Comparator.comparingDouble(Candidate::distance));
        return all.subList(0, Math.min(MAX_BONDS, all.size()));
    }

    private static double distance(ForceFieldAtom first, ForceFieldAtom second)
    {
        return Math.sqrt(Math.pow(first.x() - second.x(), 2) + Math.pow(first.y() - second.y(), 2) + Math.pow(first.z() - second.z(), 2));
    }
}
