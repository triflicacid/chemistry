package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.server.render.MoleculeMetadata;
import com.ruben.chemistry.server.render.Rendering;

import java.util.Optional;

public final class StoredMolecule
{
    private final String id;
    private final String smiles;
    private final Molecule molecule;
    private volatile Rendering latest;
    private volatile MoleculeMetadata metadata;

    StoredMolecule(String id, String smiles, Molecule molecule)
    {
        this.id = id;
        this.smiles = smiles;
        this.molecule = molecule;
    }

    public String id()
    {
        return id;
    }

    public String smiles()
    {
        return smiles;
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
            result = MoleculeMetadata.of(molecule, smiles);
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
