package com.ruben.chemistry.core;

import com.ruben.chemistry.util.IdentitySet;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * How one ring of a {@link Molecule} is written, and the π system it belongs to.
 *
 * @param notation how the ring's bonds are written
 * @param system the π system the ring belongs to, or empty if its atoms cannot all join one
 * @see PiSystem
 */
public record RingAnalysis(Notation notation, Optional<PiSystem> system)
{
    /** How a ring's bonds are written, which is independent of whether the ring is aromatic. */
    public enum Notation
    {
        /** Every bond around the ring is an aromatic bond, e.g. benzene's {@code c1ccccc1}. */
        AROMATIC,

        /** Some bonds around the ring are aromatic and some are not. */
        MIXED,

        /** No bond around the ring is aromatic, e.g. benzene's {@code C1=CC=CC=C1} or cyclohexane's {@code C1CCCCC1}. */
        PLAIN
    }

    /**
     * Analyses one ring of a molecule.
     *
     * @param molecule the molecule the ring belongs to
     * @param ring the ring's atoms, by reference identity, in any order
     * @return how the ring is written and what π system it belongs to
     * @throws IllegalArgumentException if an atom of {@code ring} is not part of {@code molecule}
     */
    public static RingAnalysis of(Molecule molecule, Collection<Atom> ring)
    {
        Set<Atom> members = IdentitySet.copyOf(ring);
        return new RingAnalysis(notationOf(molecule, members), systemHolding(molecule, members));
    }

    /**
     * Finds the π system a whole ring belongs to.
     *
     * @param molecule the molecule the ring belongs to
     * @param ring the ring's atoms
     * @return the one π system holding all of them, or empty if there is none
     */
    private static Optional<PiSystem> systemHolding(Molecule molecule, Set<Atom> ring)
    {
        Optional<PiSystem> holding = Optional.empty();
        for (Atom atom : ring)
        {
            Optional<PiSystem> system = molecule.piSystemOf(atom);
            if (system.isEmpty())
            {
                return Optional.empty();
            }
            if (holding.isEmpty())
            {
                holding = system;
            }
            else if (holding.get() != system.get())
            {
                return Optional.empty();
            }
        }
        return holding;
    }

    /**
     * @return whether this ring belongs to an aromatic π system
     */
    public boolean isAromatic()
    {
        return system.map(PiSystem::isAromatic).orElse(false);
    }

    /**
     * @return whether this ring is aromatic but written with alternating single and double bonds, e.g. benzene's {@code C1=CC=CC=C1}
     */
    public boolean isKekule()
    {
        return notation == Notation.PLAIN && isAromatic();
    }

    /**
     * @return whether this ring is written entirely with aromatic bonds while the π system it belongs to is not aromatic
     */
    public boolean isMisdeclared()
    {
        return notation == Notation.AROMATIC && !isAromatic();
    }

    /**
     * @param molecule the molecule the ring belongs to
     * @param ring the ring's atoms
     * @return how the ring's bonds are written
     * @throws IllegalArgumentException if an atom of {@code ring} is not part of {@code molecule}
     */
    private static Notation notationOf(Molecule molecule, Set<Atom> ring)
    {
        boolean anyAromatic = false;
        boolean anyPlain = false;
        for (Atom atom : ring)
        {
            for (Bond bond : molecule.bondsOf(atom))
            {
                if (ring.contains(bond.getOther(atom)))
                {
                    if (bond.order() == BondOrder.AROMATIC)
                    {
                        anyAromatic = true;
                    }
                    else
                    {
                        anyPlain = true;
                    }
                }
            }
        }
        if (anyAromatic)
        {
            return anyPlain ? Notation.MIXED : Notation.AROMATIC;
        }
        return Notation.PLAIN;
    }
}
