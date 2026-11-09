package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Molecule;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link MoleculeQuery} that can also report every way it matched.
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
     * @return a query which tests {@code this} AND {@code other} and reports the matches.
     *     Each match pairs one of this query's matches with one of {@code other}'s, found
     *     independently - unlike SMARTS {@code "."}, the two are not required to land on
     *     distinct real atoms. {@link #matches} short-circuits as plain boolean {@code &&}
     *     would, without enumerating either side's matches
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

                // Cartesian product: every way left matched, paired with every way right did
                List<Match> right = other.findMatches(molecule);
                List<Match> combined = new ArrayList<>(left.size() * right.size());
                for (Match leftMatch : left)
                {
                    for (Match rightMatch : right)
                    {
                        combined.add(leftMatch.combinedWith(rightMatch));
                    }
                }
                return combined;
            }

            @Override
            public boolean matches(Molecule molecule)
            {
                return self.matches(molecule) && other.matches(molecule);
            }
        };
    }

    /**
     * @param other another query
     * @return a query whose matches are this query's matches together with {@code other}'s -
     *     both sides are always searched in full by {@link #findMatches}, since either may have
     *     matches the other doesn't. {@link #matches} short-circuits as plain boolean {@code ||}
     *     would, without enumerating either side's matches
     */
    default MatchingQuery or(MatchingQuery other)
    {
        MatchingQuery self = this;
        return new MatchingQuery()
        {
            @Override
            public List<Match> findMatches(Molecule molecule)
            {
                List<Match> combined = new ArrayList<>(self.findMatches(molecule));
                combined.addAll(other.findMatches(molecule));
                return combined;
            }

            @Override
            public boolean matches(Molecule molecule)
            {
                return self.matches(molecule) || other.matches(molecule);
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
}
