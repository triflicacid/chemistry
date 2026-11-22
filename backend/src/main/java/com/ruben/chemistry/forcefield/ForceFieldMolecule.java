package com.ruben.chemistry.forcefield;

import java.util.List;
import java.util.Objects;

import static com.ruben.chemistry.forcefield.Validation.finite;

/**
 * A molecule and its atom-centred density expansion.
 *
 * @param name the molecule's name
 * @param atoms the atoms
 * @param nuclearCharges the effective nuclear charge of each atom
 * @param density the Gaussian components of the electron density
 * @param totalElectronPopulation the number of electrons the density holds
 */
public record ForceFieldMolecule(String name, List<ForceFieldAtom> atoms, List<Double> nuclearCharges, List<Gaussian> density, double totalElectronPopulation)
{
    public ForceFieldMolecule
    {
        Objects.requireNonNull(name);
        atoms = List.copyOf(atoms);
        nuclearCharges = List.copyOf(nuclearCharges);
        density = List.copyOf(density);
        if (atoms.isEmpty() || density.isEmpty())
        {
            throw new IllegalArgumentException("molecule needs atoms and density components");
        }
        if (atoms.size() != nuclearCharges.size())
        {
            throw new IllegalArgumentException("one nuclear charge is required per atom");
        }
        for (Gaussian gaussian : density)
        {
            if (gaussian.atomIndex() >= atoms.size())
            {
                throw new IllegalArgumentException("gaussian atom index is outside molecule");
            }
        }
        finite(totalElectronPopulation, "electron population");
    }
}
