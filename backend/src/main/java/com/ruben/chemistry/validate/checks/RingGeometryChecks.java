package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.util.IdentitySet;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports what a ring's size will not let its bonds do:
 * {@link Rule#RING_CUMULATED_DOUBLE_BONDS}, {@link Rule#RING_TRIPLE_BOND_IN_SMALL_RING}, {@link Rule#RING_TRIPLE_BOND_STRAINED} and {@link Rule#RING_STRAINED}.
 */
public final class RingGeometryChecks implements Check
{
    /** The smallest ring that can hold a linear run of three atoms. */
    private static final int SMALLEST_RING_FOR_A_LINEAR_RUN = 8;

    /** The largest ring whose strain is worth remarking on by itself. */
    private static final int LARGEST_STRAINED_RING = 4;

    @Override
    public Set<Rule> rules()
    {
        return Set.of(
                Rule.RING_CUMULATED_DOUBLE_BONDS,
                Rule.RING_TRIPLE_BOND_IN_SMALL_RING,
                Rule.RING_TRIPLE_BOND_STRAINED,
                Rule.RING_STRAINED);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (Molecule ring : molecule.rings())
        {
            int size = ring.atomCount();
            if (size <= LARGEST_STRAINED_RING)
            {
                findings.accept(new Finding(Rule.RING_STRAINED,
                    "A ring of " + size + " atoms is strained", ring.atoms(), ring.bonds()));
            }
            if (size >= SMALLEST_RING_FOR_A_LINEAR_RUN)
            {
                continue;
            }

            Set<Atom> members = IdentitySet.copyOf(ring.atoms());
            for (Atom atom : ring.atoms())
            {
                if (doubleBondsWithin(molecule, members, atom) > 1)
                {
                    findings.accept(Finding.atAtom(Rule.RING_CUMULATED_DOUBLE_BONDS, atom,
                        atom.element() + " carries two double bonds, which leave it straight, in a ring of " + size + " atoms that cannot come back round"));
                }
            }
            for (Bond bond : ring.bonds())
            {
                if (bond.order() != BondOrder.TRIPLE || Rings.smallestSizeHolding(molecule, bond) != size)
                {
                    continue;
                }
                findings.accept(size < SMALLEST_RING_FOR_A_LINEAR_RUN - 1
                    ? Finding.atBond(Rule.RING_TRIPLE_BOND_IN_SMALL_RING, bond, "A triple bond leaves three atoms straight, which a ring of " + size + " cannot hold")
                    : Finding.atBond(Rule.RING_TRIPLE_BOND_STRAINED, bond, "A triple bond in a ring of " + size + " is severely strained, cycloheptyne being a transient"));
            }
        }
    }

    /**
     * @param molecule the molecule the ring belongs to
     * @param ring the ring's atoms
     * @param atom one of them
     * @return how many double bonds the atom holds whose other end is in the ring too
     */
    private static int doubleBondsWithin(Molecule molecule, Set<Atom> ring, Atom atom)
    {
        return (int) molecule.bondsOf(atom).stream()
            .filter(bond -> bond.order() == BondOrder.DOUBLE)
            .filter(bond -> ring.contains(bond.getOther(atom)))
            .count();
    }
}
