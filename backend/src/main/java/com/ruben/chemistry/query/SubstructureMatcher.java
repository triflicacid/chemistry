package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.NeighbourReference;
import com.ruben.chemistry.util.IdentityMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            search(query, new IdentityHashMap<>(), new IdentityHashMap<>(), matches, false, QuerySymmetry.NONE);
        }
        return matches;
    }

    /**
     * Like {@link #findMatches}, but only considers matches with {@code queryAtom} bound to {@code anchor} specifically.
     *
     * @param query the pattern to search for
     * @param queryAtom a position in {@code query}, by reference identity
     * @param anchor the real atom {@code queryAtom} must bind to
     * @return every way {@code query} can be mapped with {@code queryAtom} bound to {@code anchor}
     * @throws IllegalArgumentException if {@code queryAtom} is not part of {@code query}
     */
    public List<Match> findMatches(Query query, QueryAtom queryAtom, Atom anchor)
    {
        List<Match> matches = new ArrayList<>();
        searchFrom(query, queryAtom, anchor, matches, false);
        return matches;
    }

    /**
     * Runs an anchored search.
     *
     * @param query the pattern to search for
     * @param queryAtom a position in {@code query}, by reference identity
     * @param anchor the real atom {@code queryAtom} must bind to
     * @param matches collects the matches found
     * @param firstOnly whether to stop as soon as one match is found
     * @return {@code true} if a match was found
     * @throws IllegalArgumentException if {@code queryAtom} is not part of {@code query}
     */
    private boolean searchFrom(Query query, QueryAtom queryAtom, Atom anchor, List<Match> matches, boolean firstOnly)
    {
        // bondsOf validates queryAtom is actually part of query, even one with no bonds at all
        query.bondsOf(queryAtom);

        // check the anchor is valid
        if (!queryAtom.predicate().matches(molecule, anchor))
        {
            return false;
        }

        // insert into maps, so we explore from this atom rather than a random one
        Map<QueryAtom, Atom> assignment = new IdentityHashMap<>();
        Map<Atom, QueryAtom> reverse = new IdentityHashMap<>();
        assignment.put(queryAtom, anchor);
        reverse.put(anchor, queryAtom);

        if (!satisfiesStereoConstraints(query, queryAtom, assignment))
        {
            return false;
        }
        return search(query, assignment, reverse, matches, firstOnly, QuerySymmetry.NONE);
    }

    /**
     * @param query the pattern to search for
     * @param queryAtom a position in {@code query}, by reference identity
     * @param anchor the real atom {@code queryAtom} must bind to
     * @return {@code true} if some match of {@code query} exists with {@code queryAtom} bound to {@code anchor}
     * @throws IllegalArgumentException if {@code queryAtom} is not part of {@code query}
     */
    public boolean matches(Query query, QueryAtom queryAtom, Atom anchor)
    {
        // only need one match
        return searchFrom(query, queryAtom, anchor, new ArrayList<>(1), true);
    }

    /**
     * Like {@link #findMatches}, but collapses the several matches a symmetric query produces
     * for one real substructure down to a single representative match.
     *
     * @param query the pattern to search for
     * @return one match per distinct real substructure {@code query} maps onto
     */
    public List<Match> findDistinctMatches(Query query)
    {
        List<Match> matches = new ArrayList<>();
        if (query.atomCount() > 0)
        {
            search(query, new IdentityHashMap<>(), new IdentityHashMap<>(), matches, false, QuerySymmetry.of(query, molecule));
        }

        List<Match> distinct = new ArrayList<>();
        Set<Set<Atom>> seenAtomSets = new HashSet<>();
        for (Match match : matches)
        {
            if (seenAtomSets.add(match.atoms()))
            {
                distinct.add(match);
            }
        }
        return distinct;
    }

    /**
     * @param query the pattern to search for
     * @return {@code true} if {@code query} matches some substructure of this matcher's molecule
     */
    public boolean matches(Query query)
    {
        if (query.atomCount() == 0)
        {
            return false;
        }
        return search(query, new IdentityHashMap<>(), new IdentityHashMap<>(), new ArrayList<>(1), true, QuerySymmetry.NONE);
    }

    /**
     * Extends {@code assignment} by one query atom in every way the molecule allows.
     * This recurses and collects every complete assignment that also satisfies the query's  stereochemistry.
     *
     * @param query the pattern to search for
     * @param assignment the real atom each placed query atom is currently bound to
     * @param reverse {@code assignment} inverted, so a candidate already in use is cheap to spot
     * @param matches collects the matches found
     * @param firstOnly whether to unwind as soon as one match is found
     * @return {@code true} if the search should stop
     */
    private boolean search(
            Query query,
            Map<QueryAtom, Atom> assignment,
            Map<Atom, QueryAtom> reverse,
            List<Match> matches,
            boolean firstOnly,
            QuerySymmetry symmetry)
    {
        if (assignment.size() == query.atomCount())
        {
            // every query atom is mapped, and each stereochemistry requirement was checked as the
            // placement deciding it was made, so nothing is left to reject this assignment
            matches.add(new Match(IdentityMap.copyOf(assignment), molecule));
            return firstOnly;
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
            symmetry.bind(next, candidate);

            // only care about the first match if we found a valid structure if we don't want all matches
            boolean stop = satisfiesStereoConstraints(query, next, assignment)
                && symmetry.comesFirst()
                && search(query, assignment, reverse, matches, firstOnly, symmetry);

            // backtrack: undo the assignment so the next candidate starts clean
            assignment.remove(next);
            reverse.remove(candidate);
            symmetry.unbind(next);

            if (stop)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the next unmapped query atom to assign a candidate to
     */
    private static QueryAtom nextQueryAtom(Query query, Map<QueryAtom, Atom> assignment)
    {
        QueryAtom best = null;
        long bestMappedNeighbours = -1;
        for (QueryAtom atom : query.atoms())
        {
            if (assignment.containsKey(atom))
            {
                // already placed: not a candidate to extend the search with
                continue;
            }

            // count how many of this atom's bonds already lead to a mapped atom
            long mappedNeighbours = query.bondsOf(atom).stream().filter(bond -> assignment.containsKey(bond.getOther(atom))).count();

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
    private List<Atom> candidateAtoms(Query query, QueryAtom queryAtom, Map<QueryAtom, Atom> assignment)
    {
        for (QueryBond queryBond : query.bondsOf(queryAtom))
        {
            // found a bond to an already-mapped neighbour
            // whatever real atom queryAtom ends up as must be one of that neighbour's real bonded atoms
            Atom mappedNeighbour = assignment.get(queryBond.getOther(queryAtom));
            if (mappedNeighbour != null)
            {
                return molecule.bondsOf(mappedNeighbour).stream().map(realBond -> realBond.getOther(mappedNeighbour)).toList();
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
     * Checks the stereochemistry requirements that binding one position has decided. Each is
     * checked once per search path, as the last position it reads is placed, which is equivalent
     * to checking it on every complete assignment below - see {@link QueryStereo}.
     *
     * @param query the pattern being searched for
     * @param placed the position just bound in {@code assignment}
     * @param assignment the real atom each placed query atom is currently bound to
     * @return {@code true} if every requirement {@code placed} has decided is satisfied
     */
    private boolean satisfiesStereoConstraints(Query query, QueryAtom placed, Map<QueryAtom, Atom> assignment)
    {
        for (QueryStereo.Constraint constraint : query.stereo().reading(placed))
        {
            if (!constraint.isDecidable(assignment))
            {
                continue;
            }

            // reproject the real stereochemistry onto the real atoms the matched positions ended up
            // as, the same way Molecule#chiralityOf does for any other neighbour order
            boolean satisfied = switch (constraint)
            {
                case QueryStereo.ChiralityConstraint chirality ->
                    chiralityMatches(query, chirality.stereocentre(), assignment, chirality.declared());
                case QueryStereo.DoubleBondConstraint doubleBond ->
                    doubleBondStereoMatches(query, doubleBond.stereoBond(), assignment, doubleBond.declared());
            };
            if (!satisfied)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @return {@code true} if the real atom {@code queryAtom} was mapped to has the chirality {@code declared} requires
     */
    private boolean chiralityMatches(Query query, QueryAtom queryAtom, Map<QueryAtom, Atom> assignment, Query.DeclaredChirality declared)
    {
        Atom realAtom = assignment.get(queryAtom);

        // a pattern counting an implicit hydrogen only fits an atom that has one, and vice versa
        boolean includesImplicitHydrogen = declared.includesImplicitHydrogen();
        if (includesImplicitHydrogen != (realAtom.hydrogenCount().orElse(0) > 0))
        {
            return false;
        }

        // build the real neighbour order corresponding to the query's own, starting with implicit hydrogen
        List<NeighbourReference> realOrder = new ArrayList<>();
        if (includesImplicitHydrogen)
        {
            realOrder.add(Molecule.IMPLICIT_HYDROGEN);
        }
        query.bondsOf(queryAtom).stream()
            .<NeighbourReference>map(queryBond -> molecule.bondBetween(realAtom, assignment.get(queryBond.getOther(queryAtom))).orElseThrow())
            .forEach(realOrder::add);

        // the query must specify exactly as many neighbours as the real atom actually has
        if (realOrder.size() != realNeighbourCount(realAtom))
        {
            return false;
        }
        return molecule.chiralityOf(realAtom, realOrder).filter(actual -> actual == declared.chirality()).isPresent();
    }

    /**
     * @return the number of positions {@code atom}'s chirality would actually be stored
     *     relative to: its implicit hydrogen, if any, plus each of its real bonds
     */
    private int realNeighbourCount(Atom atom)
    {
        return (atom.hydrogenCount().orElse(0) > 0 ? 1 : 0) + molecule.bondsOf(atom).size();
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
     * @return the real bond corresponding to {@code queryAtom}'s default reference substituent, as
     *     {@link Query#referenceBondOf} names it, or {@code null} if it has none
     */
    private Bond defaultReferenceBond(Query query, QueryBond queryBond, QueryAtom queryAtom, Map<QueryAtom, Atom> assignment)
    {
        QueryBond reference = query.referenceBondOf(queryBond, queryAtom).orElse(null);
        if (reference == null)
        {
            return null;
        }

        Atom realAtom = assignment.get(queryAtom);
        Atom realNeighbour = assignment.get(reference.getOther(queryAtom));
        return molecule.bondBetween(realAtom, realNeighbour).orElseThrow();
    }
}
