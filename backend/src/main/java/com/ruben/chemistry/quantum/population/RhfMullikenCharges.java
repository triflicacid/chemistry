package com.ruben.chemistry.quantum.population;

import com.ruben.chemistry.charges.ChargeConvention;
import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.charges.PartialCharges;
import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.quantum.basis.BasisSet;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.quantum.scf.ClosedShellScf;
import com.ruben.chemistry.quantum.scf.ScfResult;
import com.ruben.chemistry.quantum.scf.ScfSettings;
import com.ruben.chemistry.space.Structure3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Mulliken charges from a closed-shell restricted Hartree-Fock calculation in a given basis set, with each atom's
 * core electrons replaced by its effective core potential where the basis set has one.
 */
public final class RhfMullikenCharges implements PartialChargeModel
{
    private final BasisLibrary library;
    private final ScfSettings settings;

    /**
     * @param library the basis sets and core potentials of the elements it supports
     */
    public RhfMullikenCharges(BasisLibrary library)
    {
        this(library, ScfSettings.defaults());
    }

    /**
     * @param library the basis sets and core potentials of the elements it supports
     * @param settings the convergence controls for the calculation
     */
    public RhfMullikenCharges(BasisLibrary library, ScfSettings settings)
    {
        this.library = library;
        this.settings = settings;
    }

    @Override
    public String name()
    {
        return "rhf-mulliken";
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
     * @param result the converged SCF result
     * @param charges the Mulliken charge of every site
     */
    public record Calculation(BasisSet basis, List<PointCharge> nuclei, double[] effectiveCharges, ScfResult result, double[] charges)
    {
    }

    /**
     * @param structure the molecule in three dimensions, with positions in ångström
     * @param conformer the index of the shape to calculate
     * @return the Mulliken charge of every site
     * @throws IllegalArgumentException if an element is not in the library, the conformer does not exist, or the
     *     molecule does not have a closed-shell electron count
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
     * @return the basis, nuclei, SCF result and Mulliken charges
     * @throws IllegalArgumentException if an element is not in the library, the conformer does not exist, or the
     *     molecule does not have a closed-shell electron count
     * @throws IllegalStateException if the calculation does not converge
     */
    public Calculation run(Structure3D structure, int conformer)
    {
        MolecularSetup setup = MolecularSetup.of(structure, conformer, library);
        BasisSet basis = setup.basis();
        List<PointCharge> nuclei = setup.nuclei();
        double[] effectiveCharges = setup.effectiveCharges();
        int valenceElectrons = setup.valenceElectrons();
        if (valenceElectrons % 2 != 0)
        {
            throw new IllegalArgumentException("closed-shell calculation needs an even number of electrons, the molecule has " + valenceElectrons);
        }

        ScfResult result = ClosedShellScf.run(basis.functions(), nuclei, setup.cores(), valenceElectrons, settings);
        if (!result.converged())
        {
            throw new IllegalStateException("SCF did not converge in " + result.iterations() + " iterations");
        }
        double[] charges = MullikenAnalysis.charges(result, basis.siteOfFunction(), effectiveCharges);
        return new Calculation(basis, nuclei, effectiveCharges, result, charges);
    }
}
