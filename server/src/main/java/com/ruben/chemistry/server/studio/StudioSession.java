package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Hydrogens;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One open Molecular Studio workspace.
 */
public final class StudioSession
{
    /** How many conformers to embed when none is asked for. */
    public static final int DEFAULT_CONFORMERS = 1;

    /** The most conformers one message may ask to embed. */
    public static final int MAX_CONFORMERS = 20;

    private final String sessionId;
    private final String sourceMoleculeId;
    private final Molecule molecule;
    private final Map<Atom, String> atomIds;
    private final Map<Set<String>, String> bondIds;
    private final Map<String, Vec3> geometry;
    private final Set<String> wasImplicit;
    private final double strain;

    /** Atoms relaxation and optimisation must leave where they are. */
    private final Set<String> pinned = new LinkedHashSet<>();

    /** Atoms and bonds that refuse an edit. */
    private final Set<String> locked = new LinkedHashSet<>();

    /** The molecule this session last saved, or null if it has saved none. */
    private String savedMoleculeId;

    private long version = 1;
    private String smiles;
    private long smilesVersion;

    private StudioSession(
            String sessionId,
            String sourceMoleculeId,
            Molecule molecule,
            Map<Atom, String> atomIds,
            Map<Set<String>, String> bondIds,
            Map<String, Vec3> geometry,
            Set<String> wasImplicit,
            double strain)
    {
        this.sessionId = sessionId;
        this.sourceMoleculeId = sourceMoleculeId;
        this.molecule = molecule;
        this.atomIds = atomIds;
        this.bondIds = bondIds;
        this.geometry = geometry;
        this.wasImplicit = wasImplicit;
        this.strain = strain;
    }

    /**
     * Opens a workspace on a stored molecule, making its counted hydrogens atoms and seeding the
     * working geometry from the molecule's lowest-strain shape.
     *
     * @param sessionId what to call the session
     * @param source the molecule to open
     * @param conformers how many conformers to embed if the molecule has no shape yet
     * @return the open session, at version 1
     * @throws IllegalArgumentException if {@code conformers} is outside 1 to {@link #MAX_CONFORMERS}
     * @throws IllegalStateException if no shape could be embedded, or the shape found does not describe the molecule's atoms one for one
     */
    public static StudioSession open(String sessionId, StoredMolecule source, int conformers)
    {
        if (conformers < 1 || conformers > MAX_CONFORMERS)
        {
            throw new IllegalArgumentException("conformers must be 1 to " + MAX_CONFORMERS + ", but was " + conformers);
        }

        Structure3D structure = source.latestStructure3d().orElse(null);
        if (structure == null)
        {
            // kept on the stored molecule, so the viewer and a second session do not embed it again
            structure = Embedder.embed(source.molecule(), Embedder.Options.DEFAULT.withConformers(conformers));
            source.setLatestStructure3d(structure);
        }

        return build(sessionId, source.id(), source.molecule(), structure);
    }

    /**
     * Makes a session of the same name on a different molecule.
     *
     * @param molecule the molecule to rebuild on, whose hydrogens may still be counts
     * @param conformers how many conformers to embed
     * @return the new session, one version on from this one, with this session's name, source and saved molecule
     * @throws IllegalArgumentException if {@code conformers} is outside 1 to {@link #MAX_CONFORMERS}
     * @throws IllegalStateException if no shape could be embedded, or the shape found does not describe the molecule's atoms one for one
     */
    public StudioSession rebuilt(Molecule molecule, int conformers)
    {
        if (conformers < 1 || conformers > MAX_CONFORMERS)
        {
            throw new IllegalArgumentException("conformers must be 1 to " + MAX_CONFORMERS + ", but was " + conformers);
        }

        Structure3D structure = Embedder.embed(molecule, Embedder.Options.DEFAULT.withConformers(conformers));
        StudioSession next = build(sessionId, sourceMoleculeId, molecule, structure);
        next.version = version + 1;
        next.savedMoleculeId = savedMoleculeId;
        return next;
    }

    /**
     * @param sessionId what to call the session
     * @param sourceMoleculeId the stored molecule it is said to have come from
     * @param source the molecule to work on, whose hydrogens may still be counts
     * @param structure a shape of {@code source}
     * @return a session at version 1
     * @throws IllegalStateException if the structure holds no conformer, or does not describe the molecule's atoms one for one
     */
    private static StudioSession build(String sessionId, String sourceMoleculeId, Molecule source, Structure3D structure)
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
            bondIds.put(endpoints(atomIds, bonds.get(bond)), "b" + (bond + 1));
        }

        return new StudioSession(sessionId, sourceMoleculeId, working, atomIds, bondIds, geometry, wasImplicit, conformer.strain());
    }

    /**
     * @return what this session is called, for the lifetime of the session only
     */
    public String sessionId()
    {
        return sessionId;
    }

    /**
     * @return the ID of the stored molecule this session was opened from
     */
    public String sourceMoleculeId()
    {
        return sourceMoleculeId;
    }

    /**
     * @return the ID of the molecule this session last saved, or {@code null} if it has saved none
     */
    public String savedMoleculeId()
    {
        return savedMoleculeId;
    }

    /**
     * @return the working graph, whose hydrogens are all atoms
     */
    public Molecule molecule()
    {
        return molecule;
    }

    /**
     * @return how many mutations this session has applied, counting from 1
     */
    public long version()
    {
        return version;
    }

    /**
     * @return the strain of the shape this session was opened with
     */
    public double strain()
    {
        return strain;
    }

    /**
     * @return the SMILES of the working graph, in its own atom order, with its hydrogens taken
     *     back into counts
     */
    public String smiles()
    {
        if (smiles == null || smilesVersion != version)
        {
            smiles = SmilesWriter.write(Hydrogens.asCounts(molecule));
            smilesVersion = version;
        }

        return smiles;
    }

    /**
     * @param atom an atom of this session's molecule, by reference
     * @return what this session calls it
     * @throws IllegalArgumentException if the atom is not part of this session
     */
    public String atomId(Atom atom)
    {
        String id = atomIds.get(atom);
        if (id == null)
        {
            throw new IllegalArgumentException("The atom is not part of this session: " + atom);
        }

        return id;
    }

    /**
     * @param bond a bond of this session's molecule
     * @return what this session calls it
     * @throws IllegalArgumentException if either of its atoms is not part of this session
     */
    public String bondId(Bond bond)
    {
        String id = bondIds.get(endpoints(atomIds, bond));
        if (id == null)
        {
            throw new IllegalArgumentException("The bond is not part of this session: " + bond);
        }

        return id;
    }

    /**
     * @param atomId an atom of this session
     * @return where it is, in angstroms
     * @throws IllegalArgumentException if this session holds no such atom
     */
    public Vec3 positionOf(String atomId)
    {
        Vec3 position = geometry.get(atomId);
        if (position == null)
        {
            throw new IllegalArgumentException("This session holds no atom '" + atomId + "'");
        }

        return position;
    }

    /**
     * @param atomId an atom of this session
     * @return whether it is a hydrogen the source molecule counted rather than wrote
     */
    public boolean wasImplicit(String atomId)
    {
        return wasImplicit.contains(atomId);
    }

    /**
     * @return the atoms relaxation must not move
     */
    public Set<String> pinned()
    {
        return Collections.unmodifiableSet(pinned);
    }

    /**
     * @return the atoms and bonds that refuse an edit
     */
    public Set<String> locked()
    {
        return Collections.unmodifiableSet(locked);
    }

    /**
     * @param atomIds what each atom is called
     * @param bond a bond between two of them
     * @return the pair of names a bond is identified by
     */
    private static Set<String> endpoints(Map<Atom, String> atomIds, Bond bond)
    {
        String first = atomIds.get(bond.first());
        String second = atomIds.get(bond.second());
        if (first == null || second == null)
        {
            throw new IllegalArgumentException("The bond joins an atom this session does not hold: " + bond);
        }

        return Set.of(first, second);
    }
}
