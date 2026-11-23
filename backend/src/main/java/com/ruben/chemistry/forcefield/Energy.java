package com.ruben.chemistry.forcefield;

/**
 * The individual energy contributions, in hartree.
 *
 * @param nuclearNuclear the nuclear-nuclear repulsion
 * @param nuclearElectron the nuclear-electron attraction
 * @param electronElectron the electron-electron repulsion
 * @param xcElectronElectron the exchange-correlation fit for electron-electron pairs
 * @param xcElectronNuclear the exchange-correlation fit for electron-nuclear pairs
 */
public record Energy(double nuclearNuclear, double nuclearElectron, double electronElectron, double xcElectronElectron, double xcElectronNuclear)
{
    public double naive()
    {
        return nuclearNuclear + nuclearElectron + electronElectron;
    }

    public double exchangeCorrelation()
    {
        return xcElectronElectron + xcElectronNuclear;
    }

    public double total()
    {
        return naive() + exchangeCorrelation();
    }
}
