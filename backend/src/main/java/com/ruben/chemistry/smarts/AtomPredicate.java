package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;

/**
 * A SMARTS atom primitive: a test an atom either satisfies or doesn't, within the context
 * of the molecule it belongs to. Context is needed due to ring membership, degree, etc.
 */
@FunctionalInterface
public interface AtomPredicate
{
    /**
     * @param molecule the molecule {@code atom} belongs to
     * @param atom the atom to test
     * @return {@code true} if {@code atom} satisfies this predicate
     */
    boolean matches(Molecule molecule, Atom atom);
}
