package com.ruben.chemistry.forcefield;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The selected atomic data and the matching exchange-correlation fit.
 *
 * @param type the parameterization
 * @param atomic the atomic parameters, by element symbol
 * @param xc the exchange-correlation fit
 */
public record ForceField(ModelType type, Map<String, AtomicParameters> atomic, XCParameters xc)
{
    public ForceField
    {
        Objects.requireNonNull(type);
        Objects.requireNonNull(xc);
        atomic = Map.copyOf(atomic);
    }

    /**
     * @param name the molecule's name
     * @param atoms the atoms, with positions in bohr and partial charges in the convention of {@link #type()}
     * @return the molecule, its density built from the selected atomic expansion
     * @throws IllegalArgumentException if an element has no parameters, or an atom's charge exceeds its effective nuclear charge
     */
    public ForceFieldMolecule molecule(String name, List<ForceFieldAtom> atoms)
    {
        List<Gaussian> density = new ArrayList<>();
        List<Double> nuclearCharges = new ArrayList<>();
        double electronPopulation = 0.0;
        for (int atomIndex = 0; atomIndex < atoms.size(); atomIndex++)
        {
            ForceFieldAtom atom = atoms.get(atomIndex);
            AtomicParameters parameters = atomic.get(atom.element());
            if (parameters == null)
            {
                throw new IllegalArgumentException("no " + type + " parameters for " + atom.element());
            }
            double zeff = parameters.effectiveNuclearCharge();
            if (zeff == 0.0)
            {
                throw new IllegalArgumentException("zero effective nuclear charge");
            }
            if (atom.partialCharge() > zeff)
            {
                throw new IllegalArgumentException("atom " + atomIndex + " (" + atom.element() + ") has charge " + atom.partialCharge() + ", more than its effective nuclear charge " + zeff);
            }
            nuclearCharges.add(zeff);

            // the partial charge scales the neutral-atom density, as in supplementary equation S1
            double scale = (zeff - atom.partialCharge()) / zeff;
            double[] c = parameters.coefficients();
            double[] alpha = parameters.exponents();
            for (int k = 0; k < c.length; k++)
            {
                double population = scale * c[k];
                if (population != 0.0)
                {
                    density.add(new Gaussian(atomIndex, atom.x(), atom.y(), atom.z(), population, alpha[k]));
                    electronPopulation += population;
                }
            }
        }
        return new ForceFieldMolecule(name, atoms, nuclearCharges, density, electronPopulation);
    }
}
