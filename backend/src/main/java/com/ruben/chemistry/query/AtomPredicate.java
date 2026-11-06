package com.ruben.chemistry.query;

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

    /**
     * @param other another predicate
     * @return a predicate matching an atom that satisfies both this predicate and {@code other}
     */
    default AtomPredicate and(AtomPredicate other)
    {
        return (molecule, atom) -> matches(molecule, atom) && other.matches(molecule, atom);
    }

    /**
     * @param other another predicate
     * @return a predicate matching an atom that satisfies either this predicate or {@code other}
     */
    default AtomPredicate or(AtomPredicate other)
    {
        return (molecule, atom) -> matches(molecule, atom) || other.matches(molecule, atom);
    }

    /**
     * @return a predicate matching an atom that does not satisfy this predicate
     */
    default AtomPredicate negate()
    {
        return (molecule, atom) -> !matches(molecule, atom);
    }
}
