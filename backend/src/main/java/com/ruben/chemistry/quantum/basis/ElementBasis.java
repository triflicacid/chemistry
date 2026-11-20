package com.ruben.chemistry.quantum.basis;

import java.util.List;

/**
 * Everything a basis-set file says about one element.
 *
 * @param symbol the element symbol in standard capitalisation, such as {@code "Ne"}
 * @param atomicNumber the nuclear charge
 * @param shells the valence shells, in file order
 * @param ecp the effective core potential, or {@code null} if the atom keeps all its electrons
 */
public record ElementBasis(String symbol, int atomicNumber, List<Shell> shells, EffectiveCorePotential ecp)
{
    public ElementBasis
    {
        if (shells.isEmpty())
        {
            throw new IllegalArgumentException(symbol + " has no shells");
        }
        shells = List.copyOf(shells);
        if (ecp != null && ecp.coreElectrons() >= atomicNumber)
        {
            throw new IllegalArgumentException(symbol + " core potential replaces " + ecp.coreElectrons() + " of " + atomicNumber + " electrons");
        }
    }

    /**
     * @return how many electrons the core potential replaces, 0 if there is none
     */
    public int coreElectrons()
    {
        return ecp == null ? 0 : ecp.coreElectrons();
    }

    /**
     * @return the nuclear charge the valence electrons see
     */
    public int effectiveNuclearCharge()
    {
        return atomicNumber - coreElectrons();
    }

    /**
     * @return the number of basis functions this element contributes to a molecule
     */
    public int functionCount()
    {
        int count = 0;
        for (Shell shell : shells)
        {
            count += AngularMomentum.shell(shell.angularMomentum()).size();
        }
        return count;
    }
}
