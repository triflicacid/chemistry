package com.ruben.chemistry.core;

/**
 * Singleton sentinel standing in for an atom's implicit hydrogen wherever a
 * {@link NeighbourReference} is expected, since an implicit hydrogen has no {@link Bond} of its own.
 */
public enum ImplicitHydrogen implements NeighbourReference
{
    INSTANCE
}
