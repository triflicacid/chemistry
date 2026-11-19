package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Hydrogens;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.server.molecule.Camera;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.server.molecule.StudioSnapshot;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SessionFactory
{
    private SessionFactory()
    {
    }

    /**
     * Opens a workspace on a stored molecule.
     *
     * @param sessionId what to call the session
     * @param source the molecule to open
     * @param conformers how many conformers to embed if the molecule has no shape yet
     * @return the open session, at version 1
     * @throws IllegalArgumentException if {@code conformers} is outside 1 to {@link StudioSession#MAX_CONFORMERS}
     * @throws IllegalStateException if no shape could be embedded, or the shape found does not describe the molecule's atoms one for one
     */
    static StudioSession open(String sessionId, StoredMolecule source, int conformers)
    {
        requireConformers(conformers);

        StudioSnapshot snapshot = source.studio();
        Structure3D structure = snapshot.structure();
        if (structure == null)
        {
            structure = Embedder.embed(source.molecule(), Embedder.Options.DEFAULT.withConformers(conformers));
            source.setStudio(snapshot.withStructure(structure));
        }

        return build(sessionId, source.id(), source.molecule(), structure, snapshot.lockedSites(), snapshot.pose(), snapshot.camera(), 1);
    }

    /**
     * Makes a session of the same name on a different molecule.
     *
     * @param from the session to take the name, source, camera and version from
     * @param molecule the molecule to rebuild on, whose hydrogens may still be counts
     * @param conformers how many conformers to embed
     * @return the new session, one version on from {@code from}
     * @throws IllegalArgumentException if {@code conformers} is outside 1 to {@link StudioSession#MAX_CONFORMERS}
     * @throws IllegalStateException if no shape could be embedded, or the shape found does not describe the molecule's atoms one for one
     */
    static StudioSession rebuilt(StudioSession from, Molecule molecule, int conformers)
    {
        requireConformers(conformers);

        Structure3D structure = Embedder.embed(molecule, Embedder.Options.DEFAULT.withConformers(conformers));
        return build(from.sessionId(), from.sourceMoleculeId(), molecule, structure, Set.of(), Pose.IDENTITY, from.camera().orElse(null), from.version() + 1);
    }

    private static void requireConformers(int conformers)
    {
        if (conformers < 1 || conformers > StudioSession.MAX_CONFORMERS)
        {
            throw new IllegalArgumentException("conformers must be 1 to " + StudioSession.MAX_CONFORMERS + ", but was " + conformers);
        }
    }

    /**
     * @param sessionId what to call the session
     * @param sourceMoleculeId the stored molecule it is said to have come from
     * @param source the molecule to work on, whose hydrogens may still be counts
     * @param structure a shape of {@code source}
     * @param lockedSites the sites of {@code structure} to start with locked, by position; any past its end are ignored
     * @param pose where the molecule sits in the scene
     * @param camera where the client's camera is, or {@code null} if it has never said
     * @param version the version to start at
     * @return the session
     * @throws IllegalStateException if the structure holds no conformer, or does not describe the molecule's atoms one for one
     */
    private static StudioSession build(
            String sessionId,
            String sourceMoleculeId,
            Molecule source,
            Structure3D structure,
            Set<Integer> lockedSites,
            Pose pose,
            Camera camera,
            long version)
    {
        Molecule working = Hydrogens.asAtoms(source);
        if (structure.conformers().isEmpty())
        {
            throw new IllegalStateException("The molecule's structure holds no conformer to open");
        }

        Structure3D.Conformer conformer = structure.conformers().getFirst();
        List<Atom> atoms = working.atoms();
        if (conformer.positions().size() != atoms.size())
        {
            throw new IllegalStateException("The structure describes " + conformer.positions().size() + " sites, but the molecule has " + atoms.size() + " atoms");
        }

        int written = source.atomCount();
        Map<Atom, String> atomIds = new IdentityHashMap<>();
        Map<String, Vec3> geometry = new LinkedHashMap<>();
        Set<String> wasImplicit = new LinkedHashSet<>();
        for (int atom = 0; atom < atoms.size(); atom++)
        {
            String id = "a" + (atom + 1);
            atomIds.put(atoms.get(atom), id);
            geometry.put(id, conformer.positions().get(atom));

            if (atom >= written)
            {
                wasImplicit.add(id);
            }
        }

        List<Bond> bonds = working.bonds();
        Map<Set<String>, String> bondIds = new LinkedHashMap<>();
        for (int bond = 0; bond < bonds.size(); bond++)
        {
            bondIds.put(Graph.endpoints(atomIds, bonds.get(bond)), "b" + (bond + 1));
        }

        // a site's atom is named by its position, as every atom is above
        Set<String> locked = new LinkedHashSet<>();
        lockedSites.stream().filter(site -> site >= 0 && site < atoms.size()).sorted().forEach(site -> locked.add("a" + (site + 1)));

        return new StudioSession(
                sessionId,
                sourceMoleculeId,
                new Graph(working, atomIds, bondIds, geometry),
                wasImplicit,
                conformer.strain(),
                new Names(atoms.size(), bonds.size()),
                locked,
                pose,
                camera,
                version);
    }
}
