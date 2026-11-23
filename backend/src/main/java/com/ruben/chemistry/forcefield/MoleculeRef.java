package com.ruben.chemistry.forcefield;

/**
 * Identifies a molecule whose position remains fixed during optimisation.
 *
 * @param moleculeIndex the index of the molecule in the system
 */
public record MoleculeRef(int moleculeIndex)
{
}
