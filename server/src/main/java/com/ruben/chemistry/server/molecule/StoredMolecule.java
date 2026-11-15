package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.Notation;
import com.ruben.chemistry.server.render.Render2d;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.util.CachedValue;
import com.ruben.chemistry.validate.MoleculeValidator;

import java.util.Optional;

public final class StoredMolecule
{
    /** The checks every molecule is held to, which are the ones on unless a caller asks otherwise. */
    private static final MoleculeValidator VALIDATOR = new MoleculeValidator();

    private final String id;
    private final Notation source;
    private final CachedValue<Notation> canonical;
    private final Molecule molecule;
    private volatile Render2d latest2d;
    private final CachedValue<MoleculeMetadata> metadata;
    private final CachedValue<ValidationReport> validation;
    private volatile StudioSnapshot studio = StudioSnapshot.EMPTY;

    StoredMolecule(String id, ParsedMolecule parsed)
    {
        this.id = id;
        this.source = parsed.source();
        this.molecule = parsed.molecule();
        this.canonical = new CachedValue<>(() ->
        {
            SmilesWriter writer = new SmilesWriter(molecule).setRanker(new CanonicalRanker());
            String written = writer.write();
            return new Notation(written, writer.atomSpans(), writer.bondSpans());
        });
        this.metadata = new CachedValue<>(() -> MoleculeMetadata.of(molecule, source, canonical.get()));
        this.validation = new CachedValue<>(() -> ValidationReport.of(molecule, VALIDATOR, source));
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
        return canonical.get().smiles();
    }

    public Molecule molecule()
    {
        return molecule;
    }

    public MoleculeMetadata metadata()
    {
        return metadata.get();
    }

    /**
     * @return what checking this molecule found, placed in the SMILES it was read from
     */
    public ValidationReport validation()
    {
        return validation.get();
    }

    public Optional<Render2d> latest()
    {
        return Optional.ofNullable(latest2d);
    }

    public void setLatest2d(Render2d render)
    {
        this.latest2d = render;
    }

    /**
     * @return what an editing session saved with this molecule, which is {@link StudioSnapshot#EMPTY} if none did
     */
    public StudioSnapshot studio()
    {
        return studio;
    }

    /**
     * @param snapshot the scene to keep with this molecule, replacing what was kept
     */
    public void setStudio(StudioSnapshot snapshot)
    {
        this.studio = snapshot;
    }
}
