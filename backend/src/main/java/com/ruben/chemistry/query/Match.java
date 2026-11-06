package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One way a {@link Query} was found to match a substructure of a molecule.
 *
 * @param atomOf the real atom each query atom was mapped to
 */
public record Match(Map<QueryAtom, Atom> atomOf)
{
    /**
     * @return the real atoms this match covers
     */
    public Set<Atom> atoms()
    {
        Set<Atom> atoms = Collections.newSetFromMap(new IdentityHashMap<>());
        atoms.addAll(atomOf.values());
        return atoms;
    }
}
