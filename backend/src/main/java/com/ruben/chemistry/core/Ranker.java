package com.ruben.chemistry.core;

/**
 * Computes a {@link Ranking} of a molecule's atoms, used to pick a deterministic
 * traversal order independent of how the molecule happened to be built.
 * <p>
 * A conforming implementation must satisfy, for every atom in the molecule:
 * <ul>
 *     <li>every atom is assigned a rank</li>
 *     <li>two atoms that play the same structural role always get the same rank</li>
 *     <li>two atoms get the same rank only if they are structurally equivalent</li>
 *     <li>ranks are dense: for {@code N} atoms, ranks are exactly {@code 0..N-1}</li>
 * </ul>
 */
public interface Ranker
{
    /**
     * @param molecule the molecule to rank
     * @return a read-only view of each atom's rank
     */
    Ranking rank(Molecule molecule);
}
