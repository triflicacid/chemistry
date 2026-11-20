package com.ruben.chemistry.quantum.population;

import com.ruben.chemistry.charges.ChargeConvention;
import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.charges.PartialCharges;
import com.ruben.chemistry.quantum.Units;
import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.quantum.basis.BasisSet;
import com.ruben.chemistry.quantum.basis.ElementBasis;
import com.ruben.chemistry.quantum.integrals.EcpCentre;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.quantum.scf.ClosedShellScf;
import com.ruben.chemistry.quantum.scf.ScfResult;
import com.ruben.chemistry.quantum.scf.ScfSettings;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

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
        BasisSet basis = BasisSet.build(structure, conformer, library);
        List<Vec3> positions = structure.conformers().get(conformer).positions();

        List<PointCharge> nuclei = new ArrayList<>();
        List<EcpCentre> cores = new ArrayList<>();
        double[] effectiveCharges = new double[structure.sites().size()];
        int valenceElectrons = 0;
        for (Structure3D.Site site : structure.sites())
        {
            ElementBasis element = library.element(site.element());
            Vec3 angstrom = positions.get(site.index());
            Vec3 bohr = new Vec3(Units.toBohr(angstrom.x()), Units.toBohr(angstrom.y()), Units.toBohr(angstrom.z()));
            int zeff = element.effectiveNuclearCharge();
            effectiveCharges[site.index()] = zeff;
            valenceElectrons += zeff - site.charge();
            nuclei.add(new PointCharge(bohr, zeff));
            if (element.ecp() != null)
            {
                cores.add(new EcpCentre(bohr, element.ecp()));
            }
        }
        if (valenceElectrons % 2 != 0)
        {
            throw new IllegalArgumentException("closed-shell calculation needs an even number of electrons, the molecule has " + valenceElectrons);
        }

        ScfResult result = ClosedShellScf.run(basis.functions(), nuclei, cores, valenceElectrons, settings);
        if (!result.converged())
        {
            throw new IllegalStateException("SCF did not converge in " + result.iterations() + " iterations");
        }
        double[] charges = MullikenAnalysis.charges(result, basis.siteOfFunction(), effectiveCharges);
        List<Double> bySite = new ArrayList<>();
        for (double charge : charges)
        {
            bySite.add(charge);
        }
        return new PartialCharges(ChargeConvention.MULLIKEN, bySite);
    }
}
