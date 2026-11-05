package com.ruben.chemistry.core;

/**
 * Computes a {@link Ranking} of a molecule's atoms, used to pick a deterministic
 * traversal order independent of how the molecule happened to be built.
 */
public interface Ranker
{
    /**
     * @param molecule the molecule to rank
     * @return a read-only view of each atom's rank
     */
    Ranking rank(Molecule molecule);
}
