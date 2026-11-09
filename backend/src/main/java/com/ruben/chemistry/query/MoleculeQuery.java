package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Molecule;

/**
 * A predicate against a whole {@link Molecule}.
 */
@FunctionalInterface
public interface MoleculeQuery
{
    /**
     * @param molecule the molecule to test
     * @return {@code true} if {@code molecule} satisfies this query
     */
    boolean matches(Molecule molecule);

    /**
     * @param other another query
     * @return a query matching a molecule that satisfies both this query and {@code other}
     */
    default MoleculeQuery and(MoleculeQuery other)
    {
        return molecule -> matches(molecule) && other.matches(molecule);
    }

    /**
     * @param other another query
     * @return a query matching a molecule that satisfies either this query or {@code other}
     */
    default MoleculeQuery or(MoleculeQuery other)
    {
        return molecule -> matches(molecule) || other.matches(molecule);
    }

    /**
     * @param other another query
     * @return a query matching a molecule that satisfies exactly one of this query and {@code other}, not both and not neither
     */
    default MoleculeQuery exclusiveOr(MoleculeQuery other)
    {
        return molecule -> matches(molecule) ^ other.matches(molecule);
    }

    /**
     * @return a query matching a molecule that does not satisfy this query
     */
    default MoleculeQuery negate()
    {
        return molecule -> !matches(molecule);
    }
}
