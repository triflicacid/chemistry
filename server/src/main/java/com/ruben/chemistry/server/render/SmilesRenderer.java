package com.ruben.chemistry.server.render;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.render.DepictionOptions;
import com.ruben.chemistry.render.Layout;
import com.ruben.chemistry.render.MoleculeLayout;
import com.ruben.chemistry.render.RenderStyle;
import com.ruben.chemistry.render.SvgRenderer;

public final class SmilesRenderer
{
    /** The longest SMILES string accepted. */
    public static final int MAX_SMILES_LENGTH = 5_000;

    private static final RenderStyle STYLE = RenderStyle.builder().metadata(true).build();

    /**
     * @param smiles the molecule, written in SMILES
     * @return the SVG document
     * @throws RenderException if the SMILES is too long or invalid, or the molecule cannot be laid out
     */
    public String render(String smiles)
    {
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
            Layout layout = MoleculeLayout.layout(molecule, DepictionOptions.defaults());
            return SvgRenderer.render(layout, STYLE);
        }
        catch (IllegalArgumentException | IllegalStateException unsupported)
        {
            throw new RenderException("CANNOT_RENDER", unsupported.getMessage());
        }
    }
}
