package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.forcefield.Energy;

/**
 * The parts of an interaction energy, in hartree.
 *
 * @param nuclearNuclear the repulsion of the nuclei for each other
 * @param nuclearElectron the attraction of each molecule's nuclei for the other's electrons
 * @param electronElectron the repulsion of the electrons for each other
 * @param exchangeCorrelation the exchange-correlation fit
 */
public record EnergyTerms(double nuclearNuclear, double nuclearElectron, double electronElectron, double exchangeCorrelation)
{
    /**
     * @param energy the contributions as the force field gives them
     * @return them with the two exchange-correlation fits taken together
     */
    static EnergyTerms of(Energy energy)
    {
        return new EnergyTerms(energy.nuclearNuclear(), energy.nuclearElectron(), energy.electronElectron(), energy.exchangeCorrelation());
    }

    /**
     * @return the four parts added together, which is the interaction energy
     */
    public double total()
    {
        return nuclearNuclear + nuclearElectron + electronElectron + exchangeCorrelation;
    }
}
