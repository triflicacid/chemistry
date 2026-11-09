package com.ruben.chemistry.canonical;

import com.ruben.chemistry.core.Molecule;

/**
 * Describes a molecule in a way that depends on nothing but its structure, so that isomorphisms are described identically.
 */
public interface Canonicaliser
{
    /**
     * @param molecule the molecule to describe
     * @return the form this canonicalisation gives {@code molecule}
     */
    CanonicalForm canonicalise(Molecule molecule);
}
