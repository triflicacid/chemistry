package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.Notation;
import com.ruben.chemistry.server.render.Render2d;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.validate.MoleculeValidator;

import java.util.Optional;
import java.util.Set;

public final class StoredMolecule
{
    /** The checks every molecule is held to, which are the ones on unless a caller asks otherwise. */
    private static final MoleculeValidator VALIDATOR = new MoleculeValidator();

    private final String id;
    private final Notation source;
    private final Notation canonical;
    private final Molecule molecule;
    private volatile Render2d latest2d;
    private volatile MoleculeMetadata metadata;
    private volatile ValidationReport validation;
    private volatile Structure3D latestStructure3d;
    private volatile Set<Integer> lockedSites = Set.of();
    private volatile Pose pose = Pose.IDENTITY;
    private volatile Camera camera;

    StoredMolecule(String id, ParsedMolecule parsed)
    {
        this.id = id;
        this.source = parsed.source();
        this.molecule = parsed.molecule();
        SmilesWriter writer = new SmilesWriter(molecule).setRanker(new CanonicalRanker());
        String written = writer.write();
        this.canonical = new Notation(written, writer.atomSpans(), writer.bondSpans());
    }

    public String id()
    {
        return id;
    }

    public String smiles()
    {
        return source.smiles();
    }

    public String canonicalSmiles()
    {
        return canonical.smiles();
    }

    public Molecule molecule()
    {
        return molecule;
    }

    public MoleculeMetadata metadata()
    {
        MoleculeMetadata result = metadata;
        if (result == null)
        {
            result = MoleculeMetadata.of(molecule, source, canonical);
            metadata = result;
        }
        return result;
    }

    /**
     * @return what checking this molecule found, placed in the SMILES it was read from
     */
    public ValidationReport validation()
    {
        ValidationReport result = validation;
        if (result == null)
        {
            result = ValidationReport.of(molecule, VALIDATOR, source);
            validation = result;
        }
        return result;
    }

    public Optional<Render2d> latest()
    {
        return Optional.ofNullable(latest2d);
    }

    public void setLatest2d(Render2d render)
    {
        this.latest2d = render;
    }

    /** The most recently generated 3D structure, if any. */
    public Optional<Structure3D> latestStructure3d()
    {
        return Optional.ofNullable(latestStructure3d);
    }

    public void setLatestStructure3d(Structure3D structure)
    {
        this.latestStructure3d = structure;
    }

    /**
     * @return the sites of {@link #latestStructure3d()} that an editing session held locked when it saved, by
     *     position in the structure; empty if none were
     */
    public Set<Integer> lockedSites()
    {
        return lockedSites;
    }

    /**
     * @param sites the sites of the latest structure that are locked, by position in the structure
     */
    public void setLockedSites(Set<Integer> sites)
    {
        this.lockedSites = Set.copyOf(sites);
    }

    /**
     * @return where an editing session left the molecule in its scene when it saved; the identity if it never
     *     moved the molecule as a whole
     */
    public Pose pose()
    {
        return pose;
    }

    /**
     * @param pose where the molecule sits in its scene, which the structure's own coordinates are unaffected by
     */
    public void setPose(Pose pose)
    {
        this.pose = pose;
    }

    /**
     * @return where an editing session left the camera when it saved, if it ever said
     */
    public Optional<Camera> camera()
    {
        return Optional.ofNullable(camera);
    }

    /**
     * @param camera where the camera was and how it looked, which a session opened on this molecule starts with
     */
    public void setCamera(Camera camera)
    {
        this.camera = camera;
    }
}
