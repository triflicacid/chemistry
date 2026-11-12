package com.ruben.chemistry.server.render;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.render.Layout;
import com.ruben.chemistry.smiles.SmilesParser;
import com.ruben.chemistry.render.MoleculeLayout;
import com.ruben.chemistry.render.SvgRenderer;

public final class MoleculeRenderer
{
    /** The longest SMILES string accepted. */
    public static final int MAX_SMILES_LENGTH = 5_000;

    /**
     * A molecule read from SMILES.
     *
     * @param molecule the molecule
     * @param source where its atoms and bonds are written in the SMILES it was read from
     */
    public record Parsed(Molecule molecule, MoleculeMetadata.Notation source)
    {
    }

    /**
     * @param smiles a molecule, written in SMILES
     * @return the molecule, with where each of its atoms and bonds is written in {@code smiles}
     * @throws RenderException if the SMILES is too long or invalid
     */
    public Parsed parse(String smiles)
    {
        if (smiles.length() > MAX_SMILES_LENGTH)
        {
            throw new RenderException("SMILES_TOO_LONG", "The SMILES string is longer than " + MAX_SMILES_LENGTH + " characters");
        }

        try
        {
            // parsed here rather than by Molecule.fromSmiles, which does not keep where anything was written
            SmilesParser parser = new SmilesParser(smiles);
            Molecule molecule = parser.parse();
            return new Parsed(molecule, new MoleculeMetadata.Notation(smiles, parser.atomSpans(), parser.bondSpans()));
        }
        catch (InvalidNotationException invalid)
        {
            throw new RenderException("INVALID_SMILES", invalid.getMessage());
        }
    }

    /**
     * @param molecule the molecule to draw
     * @param options how to draw it
     * @return the drawing, with the snapshot it was laid out from
     * @throws RenderException if an option is invalid or the molecule cannot be laid out
     */
    public Rendering render(Molecule molecule, RenderOptions options)
    {
        OptionsMapper.Settings settings = OptionsMapper.map(options);

        try
        {
            Layout layout = MoleculeLayout.layout(molecule, settings.depiction());
            return new Rendering(SvgRenderer.render(layout, settings.style()), layout);
        }
        catch (IllegalArgumentException | IllegalStateException unsupported)
        {
            throw new RenderException("CANNOT_RENDER", unsupported.getMessage());
        }
    }
}
