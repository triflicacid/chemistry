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
}
