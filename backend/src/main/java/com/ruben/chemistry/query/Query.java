package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.util.CachedFunction;
import com.ruben.chemistry.util.CachedValue;
import com.ruben.chemistry.util.IdentityMap;
import com.ruben.chemistry.util.IdentitySet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A SMARTS substructure pattern: a graph of {@link QueryAtom} positions connected by
 * {@link QueryBond}s, each matched against a real molecule by its own predicate rather than
 * a fixed element or bond order.
 */
public final class Query implements MatchingQuery
{
    private final List<QueryAtom> atoms;
    private final List<QueryBond> bonds;
    private final Supplier<Map<QueryAtom, List<QueryBond>>> incidentBonds;
    private final Function<QueryAtom, Map<QueryAtom, QueryBond>> bondsByNeighbour = CachedFunction.byIdentity(this::buildBondsByNeighbour);
    private final Map<QueryAtom, DeclaredChirality> chiralityByAtom;
    private final Map<QueryBond, DoubleBondStereo> doubleBondStereoByBond;
    private final Supplier<QueryStereo> stereo = new CachedValue<>(() -> QueryStereo.of(this));

    /**
     * Creates a query from already-built atom and bond lists.
     *
     * @param atoms the atom positions of the query, in the order they were added
     * @param bonds the bond positions of the query
     * @param chirality each stereocentre's declared chirality and hydrogen slot, keyed by atom identity
     * @param doubleBondStereo each bond's declared stereo configuration, keyed by bond identity
     */
    Query(List<QueryAtom> atoms, List<QueryBond> bonds, Map<QueryAtom, DeclaredChirality> chirality, Map<QueryBond, DoubleBondStereo> doubleBondStereo)
    {
        this.atoms = List.copyOf(atoms);
        this.bonds = List.copyOf(bonds);
        this.incidentBonds = new CachedValue<>(() -> buildIncidentBonds(this.atoms, this.bonds));
        this.chiralityByAtom = IdentityMap.copyOf(chirality);
        this.doubleBondStereoByBond = IdentityMap.copyOf(doubleBondStereo);
    }

    /**
     * Searches {@code molecule} for every occurrence of this query, as {@link SubstructureMatcher#findMatches(Query)} does.
     *
     * @param molecule the molecule to search
     * @return every way this query matched {@code molecule}, empty if it didn't match at all
     */
    @Override
    public List<Match> findMatches(Molecule molecule)
    {
        return new SubstructureMatcher(molecule).findMatches(this);
    }

    /**
     * Tests whether this query matches {@code molecule} at all, stopping at the first match
     * found rather than enumerating every one.
     *
     * @param molecule the molecule to test
     * @return {@code true} if this query matches some substructure of {@code molecule}
     */
    @Override
    public boolean matches(Molecule molecule)
    {
        return new SubstructureMatcher(molecule).matches(this);
    }

    /**
     * @return this query's atom positions
     */
    public List<QueryAtom> atoms()
    {
        return atoms;
    }

    /**
     * @return this query's bond positions
     */
    public List<QueryBond> bonds()
    {
        return bonds;
    }

    /**
     * @return the number of atom positions in this query
     */
    public int atomCount()
    {
        return atoms.size();
    }

    /**
     * Returns this query's first atom position - the position a recursive sub-pattern
     * binds to the atom it's being tested against.
     *
     * @return this query's first atom position, or empty if it has none
     */
    public Optional<QueryAtom> anchorAtom()
    {
        return atoms.isEmpty() ? Optional.empty() : Optional.of(atoms.getFirst());
    }

    /**
     * @param atom an atom position of this query, by reference identity
     * @return the bond positions incident to {@code atom}, in no particular order
     * @throws IllegalArgumentException if {@code atom} is not part of this query
     */
    public List<QueryBond> bondsOf(QueryAtom atom)
    {
        List<QueryBond> result = incidentBonds.get().get(atom);
        if (result == null)
        {
            throw new IllegalArgumentException("Query atom is not part of this query: " + atom);
        }
        return result;
    }

    /**
     * @param first one atom position, by reference identity
     * @param second the other atom position, by reference identity
     * @return the bond position between {@code first} and {@code second}, or empty if they are not directly bonded
     * @throws IllegalArgumentException if {@code first} is not part of this query
     */
    public Optional<QueryBond> bondBetween(QueryAtom first, QueryAtom second)
    {
        return Optional.ofNullable(bondsByNeighbour.apply(first).get(second));
    }

    /**
     * @param atom an atom position of this query, by reference identity
     * @return every bonded neighbour position, mapped against the bond position between them
     * @throws IllegalArgumentException if {@code atom} is not part of this query
     */
    private Map<QueryAtom, QueryBond> buildBondsByNeighbour(QueryAtom atom)
    {
        Map<QueryAtom, QueryBond> byNeighbour = new IdentityHashMap<>();
        for (QueryBond bond : bondsOf(atom))
        {
            // putIfAbsent so a repeated pair resolves to the same bond a scan of bondsOf would find
            byNeighbour.putIfAbsent(bond.getOther(atom), bond);
        }
        return byNeighbour;
    }

    /**
     * Returns the declared chirality requirement for a query atom, if any.
     *
     * @param atom an atom position of this query, by reference identity
     * @return the declared chirality
     * @throws IllegalArgumentException if {@code atom} is not part of this query
     */
    public Optional<Chirality> chiralityOf(QueryAtom atom)
    {
        requireAtom(atom);
        return Optional.ofNullable(chiralityByAtom.get(atom)).map(DeclaredChirality::chirality);
    }

    /**
     * Returns whether a stereocentre's declared chirality counts an implicit hydrogen among its neighbours,
     *
     * @param atom an atom position of this query, by reference identity
     * @return {@code true} if {@code atom}'s declared chirality counts an implicit hydrogen
     * @throws IllegalArgumentException if {@code atom} is not part of this query
     */
    public boolean chiralityIncludesImplicitHydrogen(QueryAtom atom)
    {
        requireAtom(atom);
        DeclaredChirality declared = chiralityByAtom.get(atom);
        return declared != null && declared.includesImplicitHydrogen();
    }

    /**
     * @throws IllegalArgumentException if {@code atom} is not one of this query's atom positions
     */
    private void requireAtom(QueryAtom atom)
    {
        if (!incidentBonds.get().containsKey(atom))
        {
            throw new IllegalArgumentException("Query atom is not part of this query: " + atom);
        }
    }

    /**
     * Returns only the atom positions that declare a chirality requirement.
     *
     * @return each stereocentre's declared chirality, keyed by atom reference identity
     */
    Map<QueryAtom, DeclaredChirality> declaredChirality()
    {
        return chiralityByAtom;
    }

    /**
     * Returns only the bond positions that declare a stereo configuration requirement.
     *
     * @return each stereo bond's declared configuration, relative to the references described by {@link #doubleBondStereoOf}
     */
    Map<QueryBond, DoubleBondStereo> declaredDoubleBondStereo()
    {
        return doubleBondStereoByBond;
    }

    /**
     * @return query's declared stereochemistry requirements.
     */
    QueryStereo stereo()
    {
        return stereo.get();
    }

    /**
     * @param chirality the declared chirality
     * @param includesImplicitHydrogen whether an implicit hydrogen is the first of the neighbours
     */
    record DeclaredChirality(Chirality chirality, boolean includesImplicitHydrogen)
    {
    }

    /**
     * Returns the declared stereo configuration requirement for a query bond, if any.
     *
     * @param bond a bond position of this query, by reference identity
     * @return the declared configuration, relative to each endpoint's default reference
     *     substituent (the first other bond in {@link #bondsOf} insertion order), or empty if none
     * @throws IllegalArgumentException if {@code bond} is not part of this query
     */
    public Optional<DoubleBondStereo> doubleBondStereoOf(QueryBond bond)
    {
        requireBond(bond);
        return Optional.ofNullable(doubleBondStereoByBond.get(bond));
    }

    /**
     * Returns the bond position to an endpoint's default reference substituent, the one a
     * configuration declared on {@code bond} is measured against at that end.
     *
     * @param bond a bond position of this query, by reference identity
     * @param endpoint one of {@code bond}'s endpoints, by reference identity
     * @return the first of {@code endpoint}'s bonds in {@link #bondsOf} order other than
     *     {@code bond} itself, or empty if it has none
     * @throws IllegalArgumentException if {@code endpoint} is not part of this query
     */
    Optional<QueryBond> referenceBondOf(QueryBond bond, QueryAtom endpoint)
    {
        for (QueryBond candidate : bondsOf(endpoint))
        {
            if (candidate != bond)
            {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * @throws IllegalArgumentException if {@code bond} is not one of this query's bonds
     */
    private void requireBond(QueryBond bond)
    {
        if (bonds.stream().noneMatch(candidate -> candidate == bond))
        {
            throw new IllegalArgumentException("Query bond is not part of this query: " + bond);
        }
    }

    /**
     * @param atoms the atom positions of the query
     * @param bonds the bond positions of the query
     * @return a map from each atom position to its incident-bond list
     */
    private static Map<QueryAtom, List<QueryBond>> buildIncidentBonds(List<QueryAtom> atoms, List<QueryBond> bonds)
    {
        Map<QueryAtom, List<QueryBond>> adjacency = new IdentityHashMap<>();
        for (QueryAtom atom : atoms)
        {
            adjacency.put(atom, new ArrayList<>());
        }

        for (QueryBond bond : bonds)
        {
            adjacency.get(bond.first()).add(bond);
            adjacency.get(bond.second()).add(bond);
        }

        Map<QueryAtom, List<QueryBond>> immutable = new IdentityHashMap<>();
        for (Map.Entry<QueryAtom, List<QueryBond>> entry : adjacency.entrySet())
        {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    public static final class Builder
    {
        private final List<QueryAtom> atoms = new ArrayList<>();
        private final List<QueryBond> bonds = new ArrayList<>();
        private final Set<QueryAtom> added = IdentitySet.of();
        private final Map<QueryAtom, DeclaredChirality> chirality = new IdentityHashMap<>();
        private final Map<QueryBond, DoubleBondStereo> doubleBondStereo = new IdentityHashMap<>();

        /**
         * @param atom the atom position to add
         * @return this builder, for chaining
         */
        public Builder addAtom(QueryAtom atom)
        {
            added.add(atom);
            atoms.add(atom);
            return this;
        }

        /**
         * @param atoms the atom positions to add
         * @return this builder, for chaining
         */
        public Builder addAtoms(QueryAtom... atoms)
        {
            for (QueryAtom atom : atoms)
            {
                addAtom(atom);
            }
            return this;
        }

        /**
         * @param first one endpoint, previously added
         * @param second the other endpoint, previously added
         * @param predicate the condition a real bond must satisfy to match this position
         * @return this builder, for chaining
         * @throws IllegalArgumentException if either atom was not added via {@link #addAtom}
         */
        public Builder addBond(QueryAtom first, QueryAtom second, BondPredicate predicate)
        {
            requireAdded(first);
            requireAdded(second);
            bonds.add(new QueryBond(first, second, predicate));
            return this;
        }

        /**
         * Declares a previously added atom position as a stereocentre, relative to the
         * fixed neighbour order documented on {@link Query#chiralityOf(QueryAtom)}.
         *
         * @param atom a previously added atom position, by reference identity
         * @param chirality the declared chirality, or {@code null} to remove any previously declared chirality
         * @return this builder, for chaining
         * @throws IllegalArgumentException if {@code atom} was not added via {@link #addAtom}
         */
        public Builder setChirality(QueryAtom atom, Chirality chirality)
        {
            return setChirality(atom, chirality, false);
        }

        /**
         * Declares a previously added atom position as a stereocentre whose neighbours include an
         * implicit hydrogen, as {@code [C@H]} writes, held first in the order the chirality is
         * relative to.
         *
         * @param atom a previously added atom position, by reference identity
         * @param chirality the declared chirality, or {@code null} to remove any previously declared chirality
         * @param includesImplicitHydrogen whether an implicit hydrogen is the first of {@code atom}'s neighbours
         * @return this builder, for chaining
         * @throws IllegalArgumentException if {@code atom} was not added via {@link #addAtom}
         */
        public Builder setChirality(QueryAtom atom, Chirality chirality, boolean includesImplicitHydrogen)
        {
            requireAdded(atom);
            if (chirality == null)
            {
                this.chirality.remove(atom);
            }
            else
            {
                this.chirality.put(atom, new DeclaredChirality(chirality, includesImplicitHydrogen));
            }
            return this;
        }

        /**
         * Declares the stereo configuration of the bond between two previously bonded atom
         * positions, relative to the default reference substituent documented on
         * {@link Query#doubleBondStereoOf(QueryBond)}.
         *
         * @param first one endpoint
         * @param second the other endpoint
         * @param stereo the declared configuration, or {@code null} to remove any previously declared stereo
         * @return this builder, for chaining
         * @throws IllegalArgumentException if no bond exists between {@code first} and {@code second}
         */
        public Builder setDoubleBondStereo(QueryAtom first, QueryAtom second, DoubleBondStereo stereo)
        {
            QueryBond bond = findBond(first, second);
            if (bond == null)
            {
                throw new IllegalArgumentException("No bond exists between the given atoms");
            }
            if (stereo == null)
            {
                doubleBondStereo.remove(bond);
            }
            else
            {
                doubleBondStereo.put(bond, stereo);
            }
            return this;
        }

        /**
         * @return the built query
         */
        public Query build()
        {
            return new Query(atoms, bonds, chirality, doubleBondStereo);
        }

        /**
         * @param first one endpoint
         * @param second the other endpoint
         * @return the bond between the two positions, in either direction, or {@code null} if they are not bonded
         */
        private QueryBond findBond(QueryAtom first, QueryAtom second)
        {
            return bonds.stream().filter(bond -> bond.matches(first, second)).findFirst().orElse(null);
        }

        /**
         * @param atom the atom position to check, by reference identity
         * @throws IllegalArgumentException if {@code atom} was not added via {@link #addAtom}
         */
        private void requireAdded(QueryAtom atom)
        {
            if (!added.contains(atom))
            {
                throw new IllegalArgumentException("Query atom was not added to this builder: " + atom);
            }
        }
    }
}
