package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;

/**
 * Factory methods for common {@link BondPredicate}s.
 */
public final class BondPredicates
{
    private BondPredicates()
    {
    }

    /**
     * @return a predicate matching any bond at all (the SMARTS {@code ~} wildcard)
     */
    public static BondPredicate any()
    {
        return (molecule, bond) -> true;
    }

    /**
     * @param order the bond order to match
     * @return a predicate matching bonds of exactly that order
     */
    public static BondPredicate order(BondOrder order)
    {
        return (molecule, bond) -> bond.order() == order;
    }

    /**
     * @return a predicate matching a bond that lies on a ring (the SMARTS {@code @} primitive)
     */
    public static BondPredicate ringBond()
    {
        return (molecule, bond) -> molecule.ringBonds().contains(bond);
    }

    /**
     * @return a predicate matching a bond that doesn't lie on a ring (the SMARTS {@code !@} primitive)
     */
    public static BondPredicate chainBond()
    {
        return (molecule, bond) -> !molecule.ringBonds().contains(bond);
    }

    /**
     * @param template the bond to match exactly
     * @return a predicate matching a bond with the same order as {@code template}
     */
    public static BondPredicate exactly(Bond template)
    {
        return (molecule, bond) -> bond.order() == template.order();
    }
}
