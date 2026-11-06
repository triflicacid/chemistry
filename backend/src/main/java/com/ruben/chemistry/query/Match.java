package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One way a {@link Query} was found to match a substructure of a molecule.
 *
 * @param atomOf the real atom each query atom was mapped to
 * @param molecule the molecule {@code atomOf}'s values belong to
 */
public record Match(Map<QueryAtom, Atom> atomOf, Molecule molecule)
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

    /**
     * Builds a standalone molecule covering this match's atoms with their actual bonds, as in the molecule.
     *
     * @return a sub-molecule of {@link #molecule} induced by this match's atoms
     */
    public Molecule substructure()
    {
        Set<Atom> atoms = atoms();

        Molecule.Builder builder = new Molecule.Builder();
        atoms.forEach(builder::addAtom);
        molecule.bonds().stream()
            .filter(bond -> atoms.contains(bond.first()) && atoms.contains(bond.second()))
            .forEach(builder::addBond);
        return builder.build();
    }
}
