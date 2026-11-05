package com.ruben.chemistry.core;

/**
 * A bond between two atoms within a {@link Molecule}.
 *
 * @param first one endpoint atom
 * @param second the other endpoint atom
 * @param order the bond order
 */
public record Bond(Atom first, Atom second, BondOrder order)
{
    /**
     * @param first one atom to check, by reference identity
     * @param second the other atom to check, by reference identity
     * @return {@code true} if this bond connects {@code first} and {@code second}, in either order
     */
    public boolean matches(Atom first, Atom second)
    {
        return (this.first == first && this.second == second) || (this.first == second && this.second == first);
    }

    /**
     * @param atom one endpoint of this bond, by reference identity
     * @return the endpoint atom other than {@code atom}
     * @throws IllegalArgumentException if {@code atom} is neither endpoint of this bond
     */
    public Atom getOther(Atom atom)
    {
        if (atom == first)
        {
            return second;
        }
        if (atom == second)
        {
            return first;
        }
        throw new IllegalArgumentException("Atom is not part of this bond: " + atom);
    }
}
