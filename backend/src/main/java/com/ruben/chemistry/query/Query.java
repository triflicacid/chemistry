package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A SMARTS substructure pattern: a graph of {@link QueryAtom} positions connected by
 * {@link QueryBond}s, each matched against a real molecule by its own predicate rather than
 * a fixed element or bond order.
 */
public final class Query
{
    private final List<QueryAtom> atoms;
    private final List<QueryBond> bonds;
    private final Map<QueryAtom, List<QueryBond>> incidentBonds;
    private final Map<QueryAtom, Chirality> chiralityByAtom;
    private final Map<QueryBond, DoubleBondStereo> doubleBondStereoByBond;

    Query(List<QueryAtom> atoms, List<QueryBond> bonds, Map<QueryAtom, Chirality> chirality, Map<QueryBond, DoubleBondStereo> doubleBondStereo)
    {
        this.atoms = List.copyOf(atoms);
        this.bonds = List.copyOf(bonds);
        this.incidentBonds = buildIncidentBonds(this.atoms, this.bonds);
        this.chiralityByAtom = new IdentityHashMap<>(chirality);
        this.doubleBondStereoByBond = new IdentityHashMap<>(doubleBondStereo);
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
        List<QueryBond> result = incidentBonds.get(atom);
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
        return bondsOf(first).stream().filter(bond -> bond.getOther(first) == second).findFirst();
    }

    /**
     * Returns the declared chirality requirement for a query atom, if any.
     *
     * @param atom an atom position of this query, by reference identity
     * @return the declared chirality, relative to {@code atom}'s bonds in {@link #bondsOf}
     *     insertion order, or empty if none was declared
     * @throws IllegalArgumentException if {@code atom} is not part of this query
     */
    public Optional<Chirality> chiralityOf(QueryAtom atom)
    {
        if (!incidentBonds.containsKey(atom))
        {
            throw new IllegalArgumentException("Query atom is not part of this query: " + atom);
        }
        return Optional.ofNullable(chiralityByAtom.get(atom));
    }

    /**
     * Returns the declared stereo configuration requirement for a query bond, if any.
     *
     * @param bond a bond position of this query, by reference identity
     * @return the declared configuration, relative to each endpoint's default reference
     *     substituent (the first other bond in {@link #bondsOf} insertion order), or empty
     *     if none was declared
     * @throws IllegalArgumentException if {@code bond} is not part of this query
     */
    public Optional<DoubleBondStereo> doubleBondStereoOf(QueryBond bond)
    {
        requireBond(bond);
        return Optional.ofNullable(doubleBondStereoByBond.get(bond));
    }

    private void requireBond(QueryBond bond)
    {
        if (bonds.stream().noneMatch(candidate -> candidate == bond))
        {
            throw new IllegalArgumentException("Query bond is not part of this query: " + bond);
        }
    }

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

    /**
     * Incrementally builds a {@link Query} by adding atom positions and bonding them together.
     */
    public static final class Builder
    {
        private final List<QueryAtom> atoms = new ArrayList<>();
        private final List<QueryBond> bonds = new ArrayList<>();
        private final Set<QueryAtom> added = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<QueryAtom, Chirality> chirality = new IdentityHashMap<>();
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
            requireAdded(atom);
            if (chirality == null)
            {
                this.chirality.remove(atom);
            }
            else
            {
                this.chirality.put(atom, chirality);
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

        private QueryBond findBond(QueryAtom first, QueryAtom second)
        {
            return bonds.stream().filter(bond -> bond.matches(first, second)).findFirst().orElse(null);
        }

        private void requireAdded(QueryAtom atom)
        {
            if (!added.contains(atom))
            {
                throw new IllegalArgumentException("Query atom was not added to this builder: " + atom);
            }
        }
    }
}
