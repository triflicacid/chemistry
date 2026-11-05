package com.ruben.chemistry.smarts;

/**
 * A bond between two {@link QueryAtom} positions in a {@link Query}, matched against real
 * bonds by its {@link BondPredicate}.
 *
 * @param first one endpoint position
 * @param second the other endpoint position
 * @param predicate the condition a real bond must satisfy to match this position
 */
public record QueryBond(QueryAtom first, QueryAtom second, BondPredicate predicate)
{
    /**
     * @param first one atom position to check, by reference identity
     * @param second the other atom position to check, by reference identity
     * @return {@code true} if this bond connects {@code first} and {@code second}, in either order
     */
    public boolean matches(QueryAtom first, QueryAtom second)
    {
        return (this.first == first && this.second == second) || (this.first == second && this.second == first);
    }

    /**
     * @param atom one endpoint, by reference identity
     * @return the endpoint other than {@code atom}
     * @throws IllegalArgumentException if {@code atom} is neither endpoint of this bond
     */
    public QueryAtom getOther(QueryAtom atom)
    {
        if (atom == first)
        {
            return second;
        }
        if (atom == second)
        {
            return first;
        }
        throw new IllegalArgumentException("Query atom is not part of this query bond: " + atom);
    }
}
