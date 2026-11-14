package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;

import java.util.Comparator;
import java.util.Optional;

final class Rings
{
    /** The size reported for a bond lying on no ring at all, which is larger than any real ring. */
    static final int UNRINGED = Integer.MAX_VALUE;

    private Rings()
    {
    }

    /**
     * @param molecule the molecule the bond belongs to
     * @param bond a bond of it
     * @return the smallest ring holding it, or empty if it lies on none
     */
    static Optional<Molecule> smallestHolding(Molecule molecule, Bond bond)
    {
        return molecule.rings().stream()
            .filter(ring -> holds(ring, bond))
            .min(Comparator.comparingInt(Molecule::atomCount));
    }

    /**
     * @param molecule the molecule the bond belongs to
     * @param bond a bond of it
     * @return the size of the smallest ring holding it, or {@link #UNRINGED} if it lies on none
     */
    static int smallestSizeHolding(Molecule molecule, Bond bond)
    {
        return smallestHolding(molecule, bond).map(Molecule::atomCount).orElse(UNRINGED);
    }

    /**
     * @param ring a ring of the molecule
     * @param bond a bond of the molecule
     * @return whether the ring runs along that bond
     */
    private static boolean holds(Molecule ring, Bond bond)
    {
        return ring.bonds().stream().anyMatch(member -> member.matches(bond.first(), bond.second()));
    }
}
