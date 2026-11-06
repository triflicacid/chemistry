package com.ruben.chemistry.query;

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
            // every query atom is mapped: a full candidate assignment
            // so now all that's left is stereochemistry
            if (satisfiesStereoConstraints(query, assignment))
            {
                // not Map.copyOf
                matches.add(new Match(Collections.unmodifiableMap(new IdentityHashMap<>(assignment))));
            }
            return;
        }

        // pick the next query atom to place, and only try real atoms that could plausibly match it
        QueryAtom next = nextQueryAtom(query, assignment);
        for (Atom candidate : candidateAtoms(query, next, assignment))
        {
            // reject: candidate already used, doesn't satisfy next's own predicate, or
            // breaks a bond constraint to a neighbour that's already mapped
            if (reverse.containsKey(candidate)
                || !next.predicate().matches(molecule, candidate)
                || !bondsConsistent(query, next, candidate, assignment))
            {
                continue;
            }

            // tentatively extend the assignment with this candidate and recurse
            assignment.put(next, candidate);
            reverse.put(candidate, next);

            search(query, assignment, reverse, matches);

            // backtrack: undo the assignment so the next candidate starts clean
            assignment.remove(next);
            reverse.remove(candidate);
        }
    }

    /**
     * @return the next unmapped query atom to assign a candidate to
     */
    private static QueryAtom nextQueryAtom(Query query, Map<QueryAtom, Atom> assignment)
    {
        QueryAtom best = null;
        int bestMappedNeighbours = -1;
        for (QueryAtom atom : query.atoms())
        {
            if (assignment.containsKey(atom))
            {
                // already placed: not a candidate to extend the search with
                continue;
            }

            // count how many of this atom's bonds already lead to a mapped atom
            int mappedNeighbours = 0;
            for (QueryBond bond : query.bondsOf(atom))
            {
                if (assignment.containsKey(bond.getOther(atom)))
                {
                    mappedNeighbours++;
                }
            }

            // keep the most-constrained atom seen so far (most bonds to mapped neighbours)
            if (mappedNeighbours > bestMappedNeighbours)
            {
                best = atom;
                bestMappedNeighbours = mappedNeighbours;
            }
        }
        if (best == null)
        {
            // shouldn't happen, guarded elsewhere
            throw new IllegalStateException("No unmapped query atom remains");
        }
        return best;
    }

    /**
     * @return the candidate real atoms to try {@code queryAtom} against
     */
    private Iterable<Atom> candidateAtoms(Query query, QueryAtom queryAtom, Map<QueryAtom, Atom> assignment)
    {
        for (QueryBond queryBond : query.bondsOf(queryAtom))
        {
            // found a bond to an already-mapped neighbour
            // whatever real atom queryAtom ends up as must be one of that neighbour's real bonded atoms
            Atom mappedNeighbour = assignment.get(queryBond.getOther(queryAtom));
            if (mappedNeighbour != null)
            {
                List<Atom> candidates = new ArrayList<>();
                for (Bond realBond : molecule.bondsOf(mappedNeighbour))
                {
                    candidates.add(realBond.getOther(mappedNeighbour));
                }
                return candidates;
            }
        }

        // no mapped neighbour to narrow the search with
        return molecule.atoms();
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
     * @return {@code true} if every chirality and double bond stereo requirement {@code query} declares is satisfied
     */
    private boolean satisfiesStereoConstraints(Query query, Map<QueryAtom, Atom> assignment)
    {
        // check each declared stereocentre by reprojecting it onto the real atoms the
        // matched neighbours ended up as, same as Molecule#chiralityOf does for any other order
        for (QueryAtom queryAtom : query.atoms())
        {
            Optional<Chirality> required = query.chiralityOf(queryAtom);
            if (required.isPresent() && !chiralityMatches(query, queryAtom, assignment, required.get()))
            {
                return false;
            }
        }
        // same reprojection, but for declared double bond stereo requirements
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
     * @return {@code true} if the real atom {@code queryAtom} was mapped to has chirality {@code required}
     */
    private boolean chiralityMatches(Query query, QueryAtom queryAtom, Map<QueryAtom, Atom> assignment, Chirality required)
    {
        Atom realAtom = assignment.get(queryAtom);

        // build the real bond order corresponding to queryAtom's bonds in query order
        List<NeighbourReference> realOrder = new ArrayList<>();
        for (QueryBond queryBond : query.bondsOf(queryAtom))
        {
            Atom realNeighbour = assignment.get(queryBond.getOther(queryAtom));
            realOrder.add(molecule.bondBetween(realAtom, realNeighbour).orElseThrow());
        }

        // the query must specify exactly as many neighbours as the real atom actually has
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
     * @return {@code true} if the real bond {@code queryBond}'s endpoints were mapped to has stereo configuration {@code required}
     */
    private boolean doubleBondStereoMatches(Query query, QueryBond queryBond, Map<QueryAtom, Atom> assignment, DoubleBondStereo required)
    {
        QueryAtom queryFirst = queryBond.first();
        QueryAtom querySecond = queryBond.second();
        Atom realFirst = assignment.get(queryFirst);
        Atom realSecond = assignment.get(querySecond);

        // each endpoint needs its own substituent to measure the stereo configuration against
        Bond queryFirstSideReference = defaultReferenceBond(query, queryBond, queryFirst, assignment);
        Bond querySecondSideReference = defaultReferenceBond(query, queryBond, querySecond, assignment);
        if (queryFirstSideReference == null || querySecondSideReference == null)
        {
            return false;
        }

        Bond realBond = molecule.bondBetween(realFirst, realSecond).orElseThrow();

        // Molecule.doubleBondStereoOf requires its two references relative to realBond's own fixed first()/second()
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
