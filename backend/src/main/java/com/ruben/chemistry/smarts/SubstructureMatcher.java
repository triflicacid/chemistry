package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.NeighbourReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds occurrences of a {@link Query} as a substructure of a {@link Molecule}.
 * <b>NOTE</b> this is not optimised and brute-force.
 */
public final class SubstructureMatcher
{
    private final Molecule molecule;

    public SubstructureMatcher(Molecule molecule)
    {
        this.molecule = molecule;
    }

    /**
     * @param query the pattern to search for
     * @return every way {@code query} can be mapped onto a distinct substructure of this
     *     matcher's molecule; empty if {@code query} has no atoms
     */
    public List<Match> findMatches(Query query)
    {
        List<Match> matches = new ArrayList<>();
        if (query.atomCount() > 0)
        {
            search(query, new IdentityHashMap<>(), new IdentityHashMap<>(), matches);
        }
        return matches;
    }

    /**
     * @param query the pattern to search for
     * @return {@code true} if {@code query} matches some substructure of this matcher's molecule
     */
    public boolean matches(Query query)
    {
        return !findMatches(query).isEmpty();
    }

    private void search(Query query, Map<QueryAtom, Atom> assignment, Map<Atom, QueryAtom> reverse, List<Match> matches)
    {
        if (assignment.size() == query.atomCount())
        {
            if (satisfiesStereoConstraints(query, assignment))
            {
                // not Map.copyOf
                matches.add(new Match(Collections.unmodifiableMap(new IdentityHashMap<>(assignment))));
            }
            return;
        }

        QueryAtom next = nextQueryAtom(query, assignment);
        for (Atom candidate : molecule.atoms())
        {
            if (reverse.containsKey(candidate)
                || !next.predicate().matches(molecule, candidate)
                || !bondsConsistent(query, next, candidate, assignment))
            {
                continue;
            }

            assignment.put(next, candidate);
            reverse.put(candidate, next);

            search(query, assignment, reverse, matches);

            assignment.remove(next);
            reverse.remove(candidate);
        }
    }

    /**
     * @return an unmapped query atom connected to one that's already mapped, if any,
     *     otherwise an arbitrary unmapped query atom (the first step, or a fresh component)
     */
    private static QueryAtom nextQueryAtom(Query query, Map<QueryAtom, Atom> assignment)
    {
        for (QueryAtom atom : query.atoms())
        {
            if (assignment.containsKey(atom))
            {
                continue;
            }
            for (QueryBond bond : query.bondsOf(atom))
            {
                if (assignment.containsKey(bond.getOther(atom)))
                {
                    return atom;
                }
            }
        }
        for (QueryAtom atom : query.atoms())
        {
            if (!assignment.containsKey(atom))
            {
                return atom;
            }
        }
        throw new IllegalStateException("No unmapped query atom remains");
    }

    /**
     * @return {@code true} if, for every one of {@code queryAtom}'s bonds to an
     *     already-mapped neighbour, a real bond satisfying that bond's predicate exists
     *     between {@code candidate} and the neighbour's mapped atom
     */
    private boolean bondsConsistent(Query query, QueryAtom queryAtom, Atom candidate, Map<QueryAtom, Atom> assignment)
    {
        for (QueryBond queryBond : query.bondsOf(queryAtom))
        {
            Atom mappedNeighbour = assignment.get(queryBond.getOther(queryAtom));
            if (mappedNeighbour == null)
            {
                continue;
            }

            Bond realBond = molecule.bondBetween(candidate, mappedNeighbour).orElse(null);
            if (realBond == null || !queryBond.predicate().matches(molecule, realBond))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks every chirality and double bond stereo requirement {@code query} declares,
     * once {@code assignment} is complete, by reprojecting each one from the query's own
     * declared neighbour order onto the real atoms the matched neighbours ended up as -
     * the same reprojection {@link Molecule#chiralityOf(com.ruben.chemistry.core.Atom, List)}
     * and {@link Molecule#doubleBondStereoOf} already do for any other caller-supplied order.
     *
     * @return {@code true} if every declared requirement is satisfied
     */
    private boolean satisfiesStereoConstraints(Query query, Map<QueryAtom, Atom> assignment)
    {
        for (QueryAtom queryAtom : query.atoms())
        {
            Optional<Chirality> required = query.chiralityOf(queryAtom);
            if (required.isPresent() && !chiralityMatches(query, queryAtom, assignment, required.get()))
            {
                return false;
            }
        }
        for (QueryBond queryBond : query.bonds())
        {
            Optional<DoubleBondStereo> required = query.doubleBondStereoOf(queryBond);
            if (required.isPresent() && !doubleBondStereoMatches(query, queryBond, assignment, required.get()))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @return {@code true} if the real atom {@code queryAtom} was mapped to has chirality
     *     {@code required}, reprojected onto the real bonds corresponding to {@code
     *     queryAtom}'s bonds in query order; {@code false} if the query doesn't specify
     *     exactly as many neighbours as the real atom actually has, so there's nothing
     *     sound to reproject onto
     */
    private boolean chiralityMatches(Query query, QueryAtom queryAtom, Map<QueryAtom, Atom> assignment, Chirality required)
    {
        Atom realAtom = assignment.get(queryAtom);

        List<NeighbourReference> realOrder = new ArrayList<>();
        for (QueryBond queryBond : query.bondsOf(queryAtom))
        {
            Atom realNeighbour = assignment.get(queryBond.getOther(queryAtom));
            realOrder.add(molecule.bondBetween(realAtom, realNeighbour).orElseThrow());
        }

        if (realOrder.size() != realNeighbourCount(realAtom))
        {
            return false;
        }
        return molecule.chiralityOf(realAtom, realOrder).filter(actual -> actual == required).isPresent();
    }

    /**
     * @return the number of positions {@code atom}'s chirality would actually be stored
     *     relative to: its implicit hydrogen, if any, plus each of its real bonds
     */
    private int realNeighbourCount(Atom atom)
    {
        int count = atom.hydrogenCount().orElse(0) > 0 ? 1 : 0;
        for (Bond ignored : molecule.bondsOf(atom))
        {
            count++;
        }
        return count;
    }

    /**
     * @return {@code true} if the real bond {@code queryBond}'s endpoints were mapped to
     *     has stereo configuration {@code required}
     */
    private boolean doubleBondStereoMatches(Query query, QueryBond queryBond, Map<QueryAtom, Atom> assignment, DoubleBondStereo required)
    {
        QueryAtom queryFirst = queryBond.first();
        QueryAtom querySecond = queryBond.second();
        Atom realFirst = assignment.get(queryFirst);
        Atom realSecond = assignment.get(querySecond);

        Bond queryFirstSideReference = defaultReferenceBond(query, queryBond, queryFirst, assignment);
        Bond querySecondSideReference = defaultReferenceBond(query, queryBond, querySecond, assignment);
        if (queryFirstSideReference == null || querySecondSideReference == null)
        {
            return false;
        }

        Bond realBond = molecule.bondBetween(realFirst, realSecond).orElseThrow();

        // Molecule.doubleBondStereoOf requires its two references relative to realBond's own
        // fixed first()/second(), which may be the opposite of how queryBond's first()/second()
        // happened to map onto it (e.g. a symmetric double bond matched "back to front")
        boolean queryFirstIsRealFirst = realBond.first() == realFirst;
        Bond realBondFirstReference = queryFirstIsRealFirst ? queryFirstSideReference : querySecondSideReference;
        Bond realBondSecondReference = queryFirstIsRealFirst ? querySecondSideReference : queryFirstSideReference;

        return molecule.doubleBondStereoOf(realBond, realBondFirstReference, realBondSecondReference).filter(actual -> actual == required).isPresent();
    }

    /**
     * @return the real bond corresponding to {@code queryAtom}'s default reference
     *     substituent (its first other query bond, excluding {@code queryBond} itself), or
     *     {@code null} if it has none
     */
    private Bond defaultReferenceBond(Query query, QueryBond queryBond, QueryAtom queryAtom, Map<QueryAtom, Atom> assignment)
    {
        for (QueryBond candidate : query.bondsOf(queryAtom))
        {
            if (candidate == queryBond)
            {
                continue;
            }
            Atom realAtom = assignment.get(queryAtom);
            Atom realNeighbour = assignment.get(candidate.getOther(queryAtom));
            return molecule.bondBetween(realAtom, realNeighbour).orElseThrow();
        }
        return null;
    }
}
