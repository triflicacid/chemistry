package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.server.render.MoleculeMetadata;
import com.ruben.chemistry.server.render.MoleculeRenderer;
import com.ruben.chemistry.server.render.Render2d;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.space.Structure3D;

import java.util.Map;
import java.util.Optional;

public final class StoredMolecule
{
    private final String id;
    private final MoleculeMetadata.Notation source;
    private final MoleculeMetadata.Notation canonical;
    private final Molecule molecule;
    private volatile Render2d latest2d;
    private volatile MoleculeMetadata metadata;
    private volatile Structure3D latestStructure3d;

    StoredMolecule(String id, MoleculeRenderer.Parsed parsed)
    {
        this.id = id;
        this.source = parsed.source();
        this.molecule = parsed.molecule();
        SmilesWriter writer = new SmilesWriter(molecule).setRanker(new CanonicalRanker());
        String written = writer.write();
        this.canonical = new MoleculeMetadata.Notation(written, writer.atomSpans(), writer.bondSpans());
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
}
