package com.ruben.chemistry.core;

import com.ruben.chemistry.util.CachedFunction;

import java.util.function.Function;

/**
 * A {@link Ranker} that remembers the {@link Ranking} it produced for each molecule.
 */
public final class CachedRanker implements Ranker
{
    private final Function<Molecule, Ranking> rankings;

    /**
     * @param delegate ranks a molecule this ranker has not seen before
     */
    public CachedRanker(Ranker delegate)
    {
        this.rankings = CachedFunction.byIdentity(delegate::rank);
    }

    @Override
    public Ranking rank(Molecule molecule)
    {
        return rankings.apply(molecule);
    }
}
