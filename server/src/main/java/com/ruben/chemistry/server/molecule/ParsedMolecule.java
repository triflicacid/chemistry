package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.Notation;

/**
 * A molecule read from SMILES.
 *
 * @param molecule the molecule
 * @param source where its atoms and bonds are written in the SMILES it was read from
 */
public record ParsedMolecule(Molecule molecule, Notation source)
{
}
