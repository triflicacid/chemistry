package com.ruben.chemistry.query;

/**
 * One atom position in a {@link Query}, matched against real atoms by its {@link AtomPredicate}.
 *
 * @param predicate the condition a real atom must satisfy to match this position
 */
public record QueryAtom(AtomPredicate predicate)
{
}
