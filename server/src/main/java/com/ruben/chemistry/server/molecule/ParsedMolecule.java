package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.Notation;
import com.ruben.chemistry.smiles.SmilesWriter;

/**
 * A molecule read from SMILES.
 *
 * @param molecule the molecule
 * @param source where its atoms and bonds are written in the SMILES it was read from
 */
public record ParsedMolecule(Molecule molecule, Notation source)
{
    /**
     * @param molecule a molecule that was not read from any text
     * @return it, with the SMILES it is written as standing for the one it was read from
     */
    public static ParsedMolecule written(Molecule molecule)
    {
        SmilesWriter writer = new SmilesWriter(molecule);
        String smiles = writer.write();
        return new ParsedMolecule(molecule, new Notation(smiles, writer.atomSpans(), writer.bondSpans()));
    }
}
