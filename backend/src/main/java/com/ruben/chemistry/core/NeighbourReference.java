package com.ruben.chemistry.core;

/**
 * A neighbour slot used to order an atom's substituents:
 * either an actual {@link Bond}, or {@link ImplicitHydrogen} standing in for an atom's implicit hydrogen, which
 * has no bond of its own.
 */
public sealed interface NeighbourReference permits Bond, ImplicitHydrogen
{
}
