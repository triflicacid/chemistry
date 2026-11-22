package com.ruben.chemistry.quantum.population;

import com.ruben.chemistry.quantum.Units;
import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.quantum.basis.BasisSet;
import com.ruben.chemistry.quantum.basis.ElementBasis;
import com.ruben.chemistry.quantum.integrals.EcpCentre;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a Hartree-Fock calculation on a molecule in one shape needs before the electrons are distributed.
 *
 * @param basis the basis functions placed on the molecule, in bohr
 * @param nuclei the effective point charges, in bohr
 * @param cores the core potentials of the atoms that have one
 * @param effectiveCharges the effective nuclear charge of every site
 * @param valenceElectrons the number of electrons treated explicitly, after the molecule's charge and any core potentials
 */
record MolecularSetup(BasisSet basis, List<PointCharge> nuclei, List<EcpCentre> cores, double[] effectiveCharges, int valenceElectrons)
{
    /**
     * @param structure the molecule in three dimensions, with positions in ångström
     * @param conformer the index of the shape to use
     * @param library the basis sets and core potentials of the elements it supports
     * @return the basis, nuclei, core potentials and electron count
     * @throws IllegalArgumentException if an element is not in the library or the conformer does not exist
     */
    static MolecularSetup of(Structure3D structure, int conformer, BasisLibrary library)
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
        return new MolecularSetup(basis, nuclei, cores, effectiveCharges, valenceElectrons);
    }
}
