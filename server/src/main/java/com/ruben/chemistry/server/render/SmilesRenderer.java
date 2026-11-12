package com.ruben.chemistry.server.render;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.render.Layout;
import com.ruben.chemistry.render.MoleculeLayout;
import com.ruben.chemistry.render.SvgRenderer;

public final class SmilesRenderer
{
    /** The longest SMILES string accepted. */
    public static final int MAX_SMILES_LENGTH = 5_000;

    /**
     * @param smiles the molecule, written in SMILES
     * @param options how to draw it
     * @return the SVG document
     * @throws RenderException if the SMILES is too long or invalid, an option is invalid, or the molecule cannot be laid out
     */
    public String render(String smiles, RenderOptions options)
    {
        OptionsMapper.Settings settings = OptionsMapper.map(options);

        if (smiles.length() > MAX_SMILES_LENGTH)
        {
            throw new RenderException("SMILES_TOO_LONG", "The SMILES string is longer than " + MAX_SMILES_LENGTH + " characters");
        }

        Molecule molecule;
        try
        {
            molecule = Molecule.fromSmiles(smiles);
        }
        catch (InvalidNotationException invalid)
        {
            throw new RenderException("INVALID_SMILES", invalid.getMessage());
        }

        try
        {
            Layout layout =             MoleculeLayout.layout(molecule, settings.depiction());
                        return SvgRenderer.render(layout, settings.style());
        }
        catch (IllegalArgumentException | IllegalStateException unsupported)
        {
            throw new RenderException("CANNOT_RENDER", unsupported.getMessage());
        }
    }
}
