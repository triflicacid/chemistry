package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.util.IdentitySet;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One way a {@link Query} was found to match a substructure of a molecule.
 */
public final class Match
{
    private final Map<QueryAtom, Atom> atomOf;
    private final Molecule molecule;

    /**
     * @param atomOf the real atom each query atom was mapped to
     * @param molecule the molecule {@code atomOf}'s values belong to
     */
    Match(Map<QueryAtom, Atom> atomOf, Molecule molecule)
    {
        this.atomOf = atomOf;
        this.molecule = molecule;
    }

    /**
     * @param queryAtom a position in the matched query, by reference
     * @return the real atom {@code queryAtom} was mapped to, or empty if it isn't part of this match
     */
    public Optional<Atom> atomOf(QueryAtom queryAtom)
    {
        return Optional.ofNullable(atomOf.get(queryAtom));
    }

    /**
     * @return the real atoms this match covers
     */
    public Set<Atom> atoms()
    {
        return IdentitySet.copyOf(atomOf.values());
    }

    /**
     * Merges this match with another, independently-found match against the same molecule.
     *
     * @param other another match against the same molecule
     * @return a match covering both this match's and {@code other}'s query positions
     * @throws IllegalArgumentException if {@code other} is a match against a different molecule
     */
    Match combinedWith(Match other)
    {
        if (molecule != other.molecule)
        {
            throw new IllegalArgumentException("Cannot combine matches against different molecules");
        }
        if (other.atomOf.isEmpty())
        {
            return this;
        }

        Map<QueryAtom, Atom> merged = new IdentityHashMap<>(atomOf);
        merged.putAll(other.atomOf);
        return new Match(merged, molecule);
    }

    /**
     * Builds a standalone molecule covering this match's atoms with their actual bonds, as in the molecule.
     *
     * @return a sub-molecule of this match's molecule, induced by this match's atoms
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
