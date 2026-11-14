package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.smiles.SmilesParser;

/**
 * Reads molecules from SMILES.
 */
public final class MoleculeParser
{
    /** The longest SMILES string accepted. */
    public static final int MAX_SMILES_LENGTH = 5_000;

    /**
     * @param smiles a molecule, written in SMILES
     * @return the molecule, with where each of its atoms and bonds is written in {@code smiles}
     * @throws MoleculeException if the SMILES is too long or invalid
     */
    public ParsedMolecule parse(String smiles)
    {
        if (smiles.length() > MAX_SMILES_LENGTH)
        {
            throw new MoleculeException("SMILES_TOO_LONG", "The SMILES string is longer than " + MAX_SMILES_LENGTH + " characters");
        }

        try
        {
            SmilesParser parser = new SmilesParser(smiles);
            Molecule molecule = parser.parse();
            return new ParsedMolecule(molecule, new MoleculeMetadata.Notation(smiles, parser.atomSpans(), parser.bondSpans()));
        }
        catch (InvalidNotationException invalid)
        {
            throw new MoleculeException("INVALID_SMILES", invalid.getMessage());
        }
    }
}
