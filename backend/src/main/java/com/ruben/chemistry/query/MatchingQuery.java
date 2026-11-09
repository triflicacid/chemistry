package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link MoleculeQuery} that can also report every way it matched.
 * The operations defined here are set operations on the matches, not the query patterns themselves.
 */
public interface MatchingQuery extends MoleculeQuery
{
    /**
     * @param molecule the molecule to search
     * @return every way this query matched {@code molecule}, empty if it didn't match at all
     */
    List<Match> findMatches(Molecule molecule);

    /**
     * @param other another query
     * @return a query whose matches are the occurrences found by both this query and
     *     {@code other} - the same real atoms satisfying both patterns at once
     */
    default MatchingQuery and(MatchingQuery other)
    {
        MatchingQuery self = this;
        return new MatchingQuery()
        {
            @Override
            public List<Match> findMatches(Molecule molecule)
            {
                List<Match> left = self.findMatches(molecule);
                if (left.isEmpty())
                {
                    return List.of();
                }

                List<Match> right = other.findMatches(molecule);
                if (right.isEmpty())
                {
                    return List.of();
                }

                Map<Set<Atom>, Match> rightByAtoms = new LinkedHashMap<>();
                for (Match match : right)
                {
                    rightByAtoms.putIfAbsent(match.atoms(), match);
                }

                List<Match> intersection = new ArrayList<>();
                Set<Set<Atom>> seen = new HashSet<>();
                for (Match leftMatch : left)
                {
                    Set<Atom> atoms = leftMatch.atoms();
                    Match rightMatch = rightByAtoms.get(atoms);
                    if (rightMatch != null && seen.add(atoms))
                    {
                        intersection.add(leftMatch.combinedWith(rightMatch));
                    }
                }
                return intersection;
            }

            @Override
            public boolean matches(Molecule molecule)
            {
                // cheap pre-check: either side matching nothing at all makes the intersection empty
                return self.matches(molecule) && other.matches(molecule) && !findMatches(molecule).isEmpty();
            }
        };
    }

    /**
     * @param other another query
     * @return a query whose matches are the occurrences found by either this query or
     *     {@code other} (or both, reported once)
     */
    default MatchingQuery or(MatchingQuery other)
    {
        MatchingQuery self = this;
        return new MatchingQuery()
        {
            @Override
            public List<Match> findMatches(Molecule molecule)
            {
                List<Match> union = new ArrayList<>();
                Set<Set<Atom>> seen = new HashSet<>();
                for (Match match : self.findMatches(molecule))
                {
                    if (seen.add(match.atoms()))
                    {
                        union.add(match);
                    }
                }
                for (Match match : other.findMatches(molecule))
                {
                    if (seen.add(match.atoms()))
                    {
                        union.add(match);
                    }
                }
                return union;
            }

            @Override
            public boolean matches(Molecule molecule)
            {
                return self.matches(molecule) || other.matches(molecule);
            }
        };
    }

    /**
     * @param other another query
     * @return a query whose matches are the occurrences found by exactly one of this query and
     *     {@code other} - one found by both is excluded, even though each side matched it
     */
    default MatchingQuery exclusiveOr(MatchingQuery other)
    {
        MatchingQuery self = this;
        return new MatchingQuery()
        {
            @Override
            public List<Match> findMatches(Molecule molecule)
            {
                List<Match> left = self.findMatches(molecule);
                List<Match> right = other.findMatches(molecule);

                Set<Set<Atom>> leftAtoms = atomSetsOf(left);
                Set<Set<Atom>> rightAtoms = atomSetsOf(right);

                List<Match> symmetricDifference = new ArrayList<>();
                Set<Set<Atom>> seen = new HashSet<>();
                for (Match match : left)
                {
                    Set<Atom> atoms = match.atoms();
                    if (!rightAtoms.contains(atoms) && seen.add(atoms))
                    {
                        symmetricDifference.add(match);
                    }
                }
                for (Match match : right)
                {
                    Set<Atom> atoms = match.atoms();
                    if (!leftAtoms.contains(atoms) && seen.add(atoms))
                    {
                        symmetricDifference.add(match);
                    }
                }
                return symmetricDifference;
            }

            @Override
            public boolean matches(Molecule molecule)
            {
                boolean left = self.matches(molecule);
                boolean right = other.matches(molecule);
                if (left != right)
                {
                    // one side matched nothing at all, so whatever the other side found is exclusive
                    return true;
                }
                if (!left)
                {
                    return false;
                }
                // both sides matched something: only exclusive if their occurrences actually differ
                return !findMatches(molecule).isEmpty();
            }
        };
    }

    /**
     * @return a query matching a molecule that does not satisfy this query
     * @throws UnsupportedOperationException if {@link #findMatches(Molecule)} is called on the
     *     result, since a negated query has no matches to report
     */
    @Override
    default MatchingQuery negate()
    {
        MatchingQuery self = this;
        return new MatchingQuery()
        {
            @Override
            public List<Match> findMatches(Molecule molecule)
            {
                throw new UnsupportedOperationException("A negated query has no matches to report");
            }

            @Override
            public boolean matches(Molecule molecule)
            {
                return !self.matches(molecule);
            }
        };
    }

    /**
     * @param matches a list of matches
     * @return the distinct atom sets {@code matches} covers, as {@link Match#atoms()} would report for each
     */
    private static Set<Set<Atom>> atomSetsOf(List<Match> matches)
    {
        Set<Set<Atom>> atomSets = new HashSet<>();
        for (Match match : matches)
        {
            atomSets.add(match.atoms());
        }
        return atomSets;
    }
}
