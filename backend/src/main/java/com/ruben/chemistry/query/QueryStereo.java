package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.util.IdentityMap;
import com.ruben.chemistry.util.IdentitySet;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stereochemistry requirements a {@link Query} declares, each paired with the atom positions
 * its verdict reads - the positions whose mapping decides whether it holds.
 * <p>
 * A requirement is therefore checkable as soon as those positions are all mapped, instead of only
 * once the whole query is: it reads nothing else, and an assignment only ever grows, so no later
 * placement can change a verdict already reachable. Checking one early rejects the whole subtree
 * below the placement that settled it rather than each leaf underneath separately.
 * <p>
 * A declared chirality reads its stereocentre and every neighbour position, since the real
 * chirality is reprojected onto the order those neighbours were mapped in. A declared double bond
 * stereo reads both endpoints and each endpoint's reference substituent, the substituents the
 * configuration is measured against.
 * <p>
 * This depends on the query alone, so {@link Query#stereo()} computes it once and caches it.
 */
final class QueryStereo
{
    /** Each declared requirement, held against every position it reads, by reference identity. */
    private final Map<QueryAtom, List<Constraint>> byDependency;

    private QueryStereo(Map<QueryAtom, List<Constraint>> byDependency)
    {
        this.byDependency = byDependency;
    }

    /**
     * @param query the pattern whose declared stereochemistry to analyse
     * @return the requirements {@code query} declares, indexed by the positions they read
     */
    static QueryStereo of(Query query)
    {
        List<Constraint> constraints = new ArrayList<>();
        query.declaredChirality().forEach((stereocentre, declared) ->
            constraints.add(new ChiralityConstraint(stereocentre, declared, chiralityDependencies(query, stereocentre))));
        query.declaredDoubleBondStereo().forEach((stereoBond, declared) ->
            constraints.add(new DoubleBondConstraint(stereoBond, declared, doubleBondDependencies(query, stereoBond))));

        Map<QueryAtom, List<Constraint>> byDependency = new IdentityHashMap<>();
        for (Constraint constraint : constraints)
        {
            for (QueryAtom dependency : constraint.dependencies())
            {
                byDependency.computeIfAbsent(dependency, position -> new ArrayList<>()).add(constraint);
            }
        }
        return new QueryStereo(IdentityMap.copyOf(byDependency));
    }

    /**
     * @param position an atom position, by reference identity
     * @return every requirement whose verdict reads {@code position}, so that placing it may have settled them
     */
    List<Constraint> reading(QueryAtom position)
    {
        // an explicit null check rather than getOrDefault, which probes the map twice on a miss
        List<Constraint> constraints = byDependency.get(position);
        return constraints == null ? List.of() : constraints;
    }

    /**
     * @param query the pattern {@code stereocentre} belongs to
     * @param stereocentre the position whose declared chirality to find the dependencies of
     * @return the positions a chirality declared at {@code stereocentre} reads
     */
    private static List<QueryAtom> chiralityDependencies(Query query, QueryAtom stereocentre)
    {
        Set<QueryAtom> dependencies = IdentitySet.of();
        dependencies.add(stereocentre);
        for (QueryBond bond : query.bondsOf(stereocentre))
        {
            dependencies.add(bond.getOther(stereocentre));
        }
        return List.copyOf(dependencies);
    }

    /**
     * @param query the pattern {@code stereoBond} belongs to
     * @param stereoBond the bond position whose declared stereo to find the dependencies of
     * @return the positions a stereo configuration declared on {@code stereoBond} reads
     */
    private static List<QueryAtom> doubleBondDependencies(Query query, QueryBond stereoBond)
    {
        Set<QueryAtom> dependencies = IdentitySet.of();
        for (QueryAtom endpoint : List.of(stereoBond.first(), stereoBond.second()))
        {
            dependencies.add(endpoint);
            // an endpoint with no reference substituent leaves the requirement unsatisfiable, which
            // is a verdict in itself, so both endpoints being mapped is already enough to give it
            query.referenceBondOf(stereoBond, endpoint).ifPresent(reference -> dependencies.add(reference.getOther(endpoint)));
        }
        return List.copyOf(dependencies);
    }

    /**
     * One declared stereochemistry requirement.
     */
    sealed interface Constraint
    {
        /**
         * @return the atom positions this requirement reads, by reference identity
         */
        List<QueryAtom> dependencies();

        /**
         * @param assignment the real atom each placed query atom is currently bound to
         * @return {@code true} if every position this requirement reads is mapped
         */
        default boolean isDecidable(Map<QueryAtom, Atom> assignment)
        {
            if (assignment.size() < dependencies().size())
            {
                return false;
            }

            for (QueryAtom dependency : dependencies())
            {
                if (!assignment.containsKey(dependency))
                {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * @param stereocentre the position the chirality is declared at
     * @param declared the declared chirality and hydrogen slot
     * @param dependencies the positions this requirement reads
     */
    record ChiralityConstraint(QueryAtom stereocentre, Query.DeclaredChirality declared, List<QueryAtom> dependencies) implements Constraint
    {
    }

    /**
     * @param stereoBond the bond position the configuration is declared on
     * @param declared the declared configuration
     * @param dependencies the positions this requirement reads
     */
    record DoubleBondConstraint(QueryBond stereoBond, DoubleBondStereo declared, List<QueryAtom> dependencies) implements Constraint
    {
    }
}
