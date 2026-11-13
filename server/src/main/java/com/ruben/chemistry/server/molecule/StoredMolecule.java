package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.server.render.MoleculeMetadata;
import com.ruben.chemistry.server.render.MoleculeRenderer;
import com.ruben.chemistry.server.render.Rendering;
import com.ruben.chemistry.smiles.SmilesWriter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class StoredMolecule
{
    private final String id;
    private final MoleculeMetadata.Notation source;
    private final MoleculeMetadata.Notation canonical;
    private final Molecule molecule;
    private volatile Rendering latest;
    private volatile MoleculeMetadata metadata;
    private final Map<String, Map<String, Object>> structures = new ConcurrentHashMap<>();

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

    public Optional<Rendering> latest()
    {
        return Optional.ofNullable(latest);
    }

    public void setLatest(Rendering rendering)
    {
        this.latest = rendering;
    }

    /** The 3D structure for these settings, worked out the first time and kept, so the shape does not change between requests. */
    public Map<String, Object> structure3d(String settings, Supplier<Map<String, Object>> compute)
    {
        return structures.computeIfAbsent(settings, key -> compute.get());
    }
}
