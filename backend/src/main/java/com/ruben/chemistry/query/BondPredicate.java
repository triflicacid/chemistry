package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;

/**
 * A SMARTS bond primitive: a test a bond either satisfies or doesn't, within the context of
 * the molecule it belongs to. Context is needed due to ring membership, etc.
 */
@FunctionalInterface
public interface BondPredicate
{
    /**
     * @param molecule the molecule {@code bond} belongs to
     * @param bond the bond to test
     * @return {@code true} if {@code bond} satisfies this predicate
     */
    boolean matches(Molecule molecule, Bond bond);

    /**
     * @param other another predicate
     * @return a predicate matching a bond that satisfies both this predicate and {@code other}
     */
    default BondPredicate and(BondPredicate other)
    {
        return (molecule, bond) -> matches(molecule, bond) && other.matches(molecule, bond);
    }

    /**
     * @param other another predicate
     * @return a predicate matching a bond that satisfies either this predicate or {@code other}
     */
    default BondPredicate or(BondPredicate other)
    {
        return (molecule, bond) -> matches(molecule, bond) || other.matches(molecule, bond);
    }

    /**
     * @return a predicate matching a bond that does not satisfy this predicate
     */
    default BondPredicate negate()
    {
        return (molecule, bond) -> !matches(molecule, bond);
    }
}
