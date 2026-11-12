package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.server.render.MoleculeMetadata;
import com.ruben.chemistry.server.render.Rendering;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.smiles.SmilesWriter.Span;

import java.util.Map;
import java.util.Optional;

public final class StoredMolecule
{
    private final String id;
    private final String smiles;
    private final String canonicalSmiles;
    private final Map<Atom, Span> canonicalSpans;
    private final Molecule molecule;
    private volatile Rendering latest;
    private volatile MoleculeMetadata metadata;

    StoredMolecule(String id, String smiles, Molecule molecule)
    {
        this.id = id;
        this.smiles = smiles;
        this.molecule = molecule;
        SmilesWriter writer = new SmilesWriter(molecule).setRanker(new CanonicalRanker());
        this.canonicalSmiles = writer.write();
        this.canonicalSpans = writer.atomSpans();
    }

    public String id()
    {
        return id;
    }

    public String smiles()
    {
        return smiles;
    }

    public String canonicalSmiles()
    {
        return canonicalSmiles;
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
            result = MoleculeMetadata.of(molecule, smiles, canonicalSmiles, canonicalSpans::get);
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
}
