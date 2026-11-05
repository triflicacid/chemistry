package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.Atom;

import java.util.Map;

/**
 * One way a {@link Query} was found to match a substructure of a molecule.
 *
 * @param atomOf the real atom each query atom was mapped to
 */
public record Match(Map<QueryAtom, Atom> atomOf)
{
}
