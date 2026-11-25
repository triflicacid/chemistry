package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.forcefield.ElectronDensityForceField;
import com.ruben.chemistry.forcefield.ForceField;
import com.ruben.chemistry.forcefield.ForceFieldAtom;
import com.ruben.chemistry.forcefield.ForceFieldAtoms;
import com.ruben.chemistry.forcefield.ForceFieldMolecule;
import com.ruben.chemistry.forcefield.ModelType;
import com.ruben.chemistry.forcefield.SystemResult;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The force the electron-density force field puts on every atom of a session, from the other molecules in it.
 * <p>
 * The force field describes how separate molecules act on one another, so a session of a single molecule has nothing to
 * report. The charges it needs are those of each molecule on its own, which is how the field was fitted, so each
 * connected part of the session is given its own charges, whatever else is in the session.
 *
 * @param type always {@code forces.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry the forces were calculated from
 * @param status {@code complete}, {@code unavailable} if the session is one molecule and so has no forces to report, or
 *     {@code failed}
 * @param model the parameterisation of the force field, or {@code null} if the calculation failed or is unavailable
 * @param energy the total intermolecular energy, in hartree, or {@code null} if there are no forces
 * @param atoms the force on each atom, in hartree per bohr; empty if there are none
 * @param fragments the molecules, each with the total force and torque on it; empty if there are none
 * @param interactions the energy between each pair of molecules, split into its parts; empty if there are none
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why the forces are unavailable or could not be calculated, or {@code null} if they could
 */
public record ForceReport(
        String type,
        String sessionId,
        long version,
        String status,
        String model,
        Double energy,
        List<AtomForce> atoms,
        List<FragmentForce> fragments,
        List<PairEnergy> interactions,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "forces.result";

    /** The parameterisation used, which is the one fitted to charges from the basis set that the studio calculates them with. */
    public static final ModelType MODEL = ModelType.ECP;

    /** How many bohr there are in an angstrom. */
    private static final double BOHR_PER_ANGSTROM = 1.0 / 0.529177210903;

    /**
     * One atom's force.
     *
     * @param id the atom
     * @param force the force on it, in hartree per bohr
     */
    public record AtomForce(String id, Vec3 force)
    {
    }

    /**
     * One molecule's total force and torque.
     *
     * @param atoms the atoms of the molecule
     * @param centre the mean position of its atoms, in angstroms, which the torque is about
     * @param force the total force on it, in hartree per bohr
     * @param torque the total torque on it about {@code centre}, in hartree
     */
    public record FragmentForce(List<String> atoms, Vec3 centre, Vec3 force, Vec3 torque)
    {
    }

    /**
     * The energy between two molecules.
     *
     * @param first an atom of the first molecule, which names it
     * @param second an atom of the second molecule, which names it
     * @param terms what the energy is made of, in hartree
     * @param energy the interaction energy of the two, in hartree, which is the sum of the terms
     */
    public record PairEnergy(String first, String second, EnergyTerms terms, double energy)
    {
    }

    /**
     * @param session the session the geometry is of
     * @param described the geometry to calculate forces for, taken from {@code session}
     * @param charges assigns each molecule's charges, which must follow the convention of {@link #MODEL}
     * @return the forces, with no time taken yet: see {@link #elapsed}; {@code unavailable} if the session is one molecule
     * @throws RuntimeException if the charges cannot be assigned or the force field has no parameters for an element
     */
    public static ForceReport of(StudioSession session, StudioSession.Described described, PartialChargeModel charges)
    {
        Structure3D structure = described.structure();
        List<List<Integer>> parts = connectedParts(structure);
        if (parts.size() < 2)
        {
            return new ForceReport(TYPE, session.sessionId(), described.version(), "unavailable", null, null, List.of(), List.of(), List.of(), 0,
                    "The force field acts between separate molecules, and this is one. Add another, as in O.O");
        }

        ForceField forceField = ElectronDensityForceField.forceField(MODEL);
        List<ForceFieldMolecule> molecules = new ArrayList<>();
        List<List<ForceFieldAtom>> atoms = new ArrayList<>();
        for (int part = 0; part < parts.size(); part++)
        {
            Structure3D alone = alone(structure, parts.get(part));
            List<ForceFieldAtom> forceAtoms = ForceFieldAtoms.of(alone, 0, charges.calculate(alone, 0), MODEL);
            atoms.add(forceAtoms);
            molecules.add(forceField.molecule("molecule " + (part + 1), forceAtoms));
        }

        SystemResult result = ElectronDensityForceField.systemEnergyAndForces(molecules, forceField.xc());

        List<AtomForce> byAtom = new ArrayList<>();
        List<FragmentForce> byFragment = new ArrayList<>();
        for (int part = 0; part < parts.size(); part++)
        {
            List<Integer> sites = parts.get(part);
            List<ForceFieldAtom> own = atoms.get(part);
            Vec3 centre = Vec3.ZERO;
            for (ForceFieldAtom atom : own)
            {
                centre = centre.plus(new Vec3(atom.x(), atom.y(), atom.z()));
            }
            // the torque is worked out in bohr, where the forces are, about the middle of the molecule's atoms
            centre = centre.times(1.0 / own.size());

            Vec3 force = Vec3.ZERO;
            Vec3 torque = Vec3.ZERO;
            List<String> ids = new ArrayList<>();
            for (int atom = 0; atom < own.size(); atom++)
            {
                Vec3 each = result.forces().get(part).get(atom);
                Vec3 arm = new Vec3(own.get(atom).x(), own.get(atom).y(), own.get(atom).z()).minus(centre);
                force = force.plus(each);
                torque = torque.plus(arm.cross(each));
                String id = described.sites().get(sites.get(atom));
                ids.add(id);
                byAtom.add(new AtomForce(id, each));
            }
            byFragment.add(new FragmentForce(List.copyOf(ids), centre.times(1.0 / BOHR_PER_ANGSTROM), force, torque));
        }

        List<PairEnergy> byPair = new ArrayList<>();
        for (int first = 0; first < molecules.size(); first++)
        {
            for (int second = first + 1; second < molecules.size(); second++)
            {
                EnergyTerms terms = EnergyTerms.of(ElectronDensityForceField.interaction(molecules.get(first), molecules.get(second), forceField.xc()));
                byPair.add(new PairEnergy(described.sites().get(parts.get(first).getFirst()), described.sites().get(parts.get(second).getFirst()), terms, terms.total()));
            }
        }

        return new ForceReport(TYPE, session.sessionId(), described.version(), "complete", MODEL.name(), result.energy(),
                List.copyOf(byAtom), List.copyOf(byFragment), List.copyOf(byPair), 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return this report, saying it took that long
     */
    public ForceReport elapsed(long milliseconds)
    {
        return new ForceReport(type, sessionId, version, status, model, energy, atoms, fragments, interactions, milliseconds, message);
    }

    /**
     * @param session the session whose forces could not be calculated
     * @param version the version of the geometry
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static ForceReport failed(StudioSession session, long version, long elapsedMs, String message)
    {
        return new ForceReport(TYPE, session.sessionId(), version, "failed", null, null, List.of(), List.of(), List.of(), elapsedMs, message);
    }

    /**
     * @param structure the whole session's geometry
     * @return the sites of each connected part, in site order, the parts in the order of their first site
     */
    static List<List<Integer>> connectedParts(Structure3D structure)
    {
        int count = structure.sites().size();
        int[] parent = new int[count];
        for (int site = 0; site < count; site++)
        {
            parent[site] = site;
        }
        for (Structure3D.Connection connection : structure.connections())
        {
            parent[root(parent, connection.first())] = root(parent, connection.second());
        }

        List<Integer> roots = new ArrayList<>();
        List<List<Integer>> parts = new ArrayList<>();
        for (int site = 0; site < count; site++)
        {
            int root = root(parent, site);
            int at = roots.indexOf(root);
            if (at < 0)
            {
                roots.add(root);
                parts.add(new ArrayList<>());
                at = parts.size() - 1;
            }
            parts.get(at).add(site);
        }
        return parts;
    }

    private static int root(int[] parent, int site)
    {
        int root = site;
        while (parent[root] != root)
        {
            root = parent[root];
        }
        return root;
    }

    /**
     * @param structure the whole session's geometry
     * @param sites the sites of one connected part, in site order
     * @return that part as a structure of its own, whose sites are the given ones in the same order
     */
    static Structure3D alone(Structure3D structure, List<Integer> sites)
    {
        // a part is found by any atom of it, and implied hydrogens belong to the atom they are on
        int atom = sites.stream().map(structure.sites()::get).filter(site -> !site.implied()).findFirst()
                .orElseThrow(() -> new IllegalStateException("A molecule has no atoms")).atom();
        return structure.restrictedTo(Set.of(atom));
    }
}
