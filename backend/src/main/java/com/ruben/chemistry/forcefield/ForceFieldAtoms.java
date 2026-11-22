package com.ruben.chemistry.forcefield;

import com.ruben.chemistry.charges.PartialCharges;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link Structure3D} and its {@link PartialCharges} into the atoms the force field works with.
 */
public final class ForceFieldAtoms
{
    /** How many bohr there are in an angstrom. */
    private static final double BOHR_PER_ANGSTROM = 1.0 / 0.529177210903;

    /** How far the charges may stray from the total formal charge before they are rejected. */
    private static final double CHARGE_TOLERANCE = 1.0e-3;

    private ForceFieldAtoms()
    {
    }

    /**
     * @param structure the molecule in three dimensions
     * @param conformer the index of the shape to take positions from
     * @param charges the partial charge of every site
     * @param type the model the atoms are for, which fixes the convention the charges must follow
     * @return an atom for every site, in site order, with positions in bohr
     * @throws IndexOutOfBoundsException if there is no such conformer
     * @throws IllegalArgumentException if the charges follow a different convention from the model, are not one per
     *     site, or do not add up to the sum of the sites' formal charges
     */
    public static List<ForceFieldAtom> of(Structure3D structure, int conformer, PartialCharges charges, ModelType type)
    {
        if (charges.convention() != type.chargeConvention())
        {
            throw new IllegalArgumentException("The " + type + " model needs " + type.chargeConvention() + " charges, but these are " + charges.convention());
        }
        if (charges.bySite().size() != structure.sites().size())
        {
            throw new IllegalArgumentException("Expected a charge for each of " + structure.sites().size() + " sites, but got " + charges.bySite().size());
        }
        int formal = structure.sites().stream().mapToInt(Structure3D.Site::charge).sum();
        if (Math.abs(charges.total() - formal) > CHARGE_TOLERANCE)
        {
            throw new IllegalArgumentException("The charges add up to " + charges.total() + ", but the structure's formal charges add up to " + formal);
        }

        List<Vec3> positions = structure.conformers().get(conformer).positions();
        List<ForceFieldAtom> atoms = new ArrayList<>();
        for (Structure3D.Site site : structure.sites())
        {
            Vec3 position = positions.get(site.index());
            atoms.add(new ForceFieldAtom(site.element(), position.x() * BOHR_PER_ANGSTROM, position.y() * BOHR_PER_ANGSTROM, position.z() * BOHR_PER_ANGSTROM, charges.bySite().get(site.index())));
        }
        return atoms;
    }
}
