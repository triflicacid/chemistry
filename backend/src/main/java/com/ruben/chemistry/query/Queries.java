package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Factory methods for building a {@link Query} from an existing structure rather than through predicates.
 */
public final class Queries
{
    private Queries()
    {
    }

    /**
     * Builds a query that matches {@code molecule} itself as a literal substructure.
     *
     * @param molecule the molecule to turn into a query
     * @return a query matching {@code molecule} as a substructure of any molecule it's found in
     */
    public static Query fromMolecule(Molecule molecule)
    {
        Map<Atom, QueryAtom> queryAtomOf = new IdentityHashMap<>();
        Query.Builder builder = new Query.Builder();

        // same atoms
        molecule.atoms().forEach(atom ->
        {
            QueryAtom queryAtom = new QueryAtom(AtomPredicates.exactly(atom));
            queryAtomOf.put(atom, queryAtom);
            builder.addAtom(queryAtom);
        });

        // same bonds
        molecule.bonds().forEach(bond -> builder.addBond(queryAtomOf.get(bond.first()), queryAtomOf.get(bond.second()), BondPredicates.exactly(bond)));

        // same chiralities
        molecule.atoms().forEach(atom -> molecule.chiralityOf(atom).ifPresent(chirality ->
            builder.setChirality(queryAtomOf.get(atom), chirality, atom.hydrogenCount().orElse(0) > 0)));

        // same stereochemistry
        molecule.bonds().forEach(bond -> molecule.doubleBondStereoOf(bond)
            .ifPresent(stereo -> builder.setDoubleBondStereo(queryAtomOf.get(bond.first()), queryAtomOf.get(bond.second()), stereo)));

        return builder.build();
    }
}
