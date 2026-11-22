package com.ruben.chemistry.quantum.population;

import com.ruben.chemistry.charges.ChargeConvention;
import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.charges.PartialCharges;
import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.quantum.basis.BasisSet;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.quantum.scf.RestrictedOpenShellScf;
import com.ruben.chemistry.quantum.scf.RestrictedOpenShellScfResult;
import com.ruben.chemistry.quantum.scf.ScfSettings;
import com.ruben.chemistry.space.Structure3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Mulliken charges and spin populations from a restricted open-shell Hartree-Fock calculation in a given basis set,
 * for a molecule with a chosen spin multiplicity, with each atom's core electrons replaced by its effective core
 * potential where the basis set has one.
 */
public final class RohfMullikenCharges implements PartialChargeModel
{
    private final BasisLibrary library;
    private final int multiplicity;
    private final ScfSettings settings;

    /**
     * @param library the basis sets and core potentials of the elements it supports
     * @param multiplicity the spin multiplicity {@code 2S + 1}: 1 for a singlet, 2 for a doublet, 3 for a triplet
     * @throws IllegalArgumentException if the multiplicity is below 1
     */
    public RohfMullikenCharges(BasisLibrary library, int multiplicity)
    {
        this(library, multiplicity, ScfSettings.defaults());
    }

    /**
     * @param library the basis sets and core potentials of the elements it supports
     * @param multiplicity the spin multiplicity {@code 2S + 1}: 1 for a singlet, 2 for a doublet, 3 for a triplet
     * @param settings the convergence controls for the calculation
     * @throws IllegalArgumentException if the multiplicity is below 1
     */
    public RohfMullikenCharges(BasisLibrary library, int multiplicity, ScfSettings settings)
    {
        if (multiplicity < 1)
        {
            throw new IllegalArgumentException("multiplicity must be at least 1, was " + multiplicity);
        }
        this.library = library;
        this.multiplicity = multiplicity;
        this.settings = settings;
    }

    @Override
    public String name()
    {
        return "rohf-mulliken";
    }

    @Override
    public ChargeConvention convention()
    {
        return ChargeConvention.MULLIKEN;
    }

    /**
     * Everything a calculation produced, for callers that want more than the charges.
     *
     * @param basis the basis functions placed on the molecule, in bohr
     * @param nuclei the effective point charges, in bohr
     * @param effectiveCharges the effective nuclear charge of every site
     * @param result the converged restricted open-shell result
     * @param charges the Mulliken charge of every site
     * @param spinPopulations the Mulliken spin population of every site: its spin-up electrons less its spin-down electrons
     */
    public record Calculation(BasisSet basis, List<PointCharge> nuclei, double[] effectiveCharges, RestrictedOpenShellScfResult result, double[] charges, double[] spinPopulations)
    {
    }

    /**
     * @param structure the molecule in three dimensions, with positions in ångström
     * @param conformer the index of the shape to calculate
     * @return the Mulliken charge of every site
     * @throws IllegalArgumentException if an element is not in the library, the conformer does not exist, or the
     *     multiplicity is not possible for the molecule's electron count
     * @throws IllegalStateException if the calculation does not converge
     */
    @Override
    public PartialCharges calculate(Structure3D structure, int conformer)
    {
        List<Double> bySite = new ArrayList<>();
        for (double charge : run(structure, conformer).charges())
        {
            bySite.add(charge);
        }
        return new PartialCharges(ChargeConvention.MULLIKEN, bySite);
    }

    /**
     * @param structure the molecule in three dimensions, with positions in ångström
     * @param conformer the index of the shape to calculate
     * @return the basis, nuclei, restricted open-shell result, Mulliken charges and spin populations
     * @throws IllegalArgumentException if an element is not in the library, the conformer does not exist, or the
     *     multiplicity is not possible for the molecule's electron count
     * @throws IllegalStateException if the calculation does not converge
     */
    public Calculation run(Structure3D structure, int conformer)
    {
        MolecularSetup setup = MolecularSetup.of(structure, conformer, library);
        int[] electrons = setup.spinElectrons(multiplicity);

        RestrictedOpenShellScfResult result = RestrictedOpenShellScf.run(setup.basis().functions(), setup.nuclei(), setup.cores(), electrons[0], electrons[1], settings);
        if (!result.converged())
        {
            throw new IllegalStateException("SCF did not converge in " + result.iterations() + " iterations");
        }
        double[] charges = MullikenAnalysis.charges(result, setup.basis().siteOfFunction(), setup.effectiveCharges());
        double[] spin = MullikenAnalysis.spinPopulations(result, setup.basis().siteOfFunction(), setup.effectiveCharges().length);
        return new Calculation(setup.basis(), setup.nuclei(), setup.effectiveCharges(), result, charges, spin);
    }
}
