package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The symmetries of a {@link Query} with respect to one {@link Molecule}: the ways the query's
 * atom positions can be permuted among themselves without changing which substructures it matches.
 * <p>
 * A symmetry permutes positions only onto positions accepting the very same real atoms, joined by
 * bonds accepting the very same real bonds. Therefore, each tells how often one substructure is matched
 * over again under a different labelling of the pattern.
 * <p>
 * With {@code CC} against ethane, the two positions accept the same two carbons, so the one
 * symmetry swapping them says the pattern's two matches cover the same bond twice. Taking
 * {@code c1ccccc1} against benzene, the eleven symmetries of the ring account for all twelve
 * matches, leaving one substructure. {@code CCC} against cyclohexane has only the symmetry
 * reversing the chain, so its twelve matches are two apiece over the six places the chain sits.
 * <p>
 * Being relative to the molecule, the same query can be symmetric against one and not another:
 * {@code Cc} is symmetric against benzene, every carbon of which is aromatic and so accepted by
 * both positions, but not against toluene, whose aliphatic methyl only the first position accepts.
 * <p>
 * <strong>This does not preserve stereochemistry!</strong>
 * To be safe, {@link #of} returns {@link #NONE} for any query declaring chirality or double bond stereo.
 */
final class QuerySymmetry
{
    private static final int UNBOUND = -1;

    /** Used for a query with no usable symmetry, where every mapping is explored. */
    static final QuerySymmetry NONE = new QuerySymmetry(List.of(), Map.of(), Map.of());

    /** Caps the work spent on a highly symmetric query; a subset of the symmetries still prunes soundly. */
    private static final int MAX_PERMUTATIONS = 64;

    /** The non-identity symmetries of the query, mapping each position index to its permuted position index. */
    private final List<int[]> permutations;

    /** Maps each query atom to its index in the list returned by {@link Query#atoms()}. */
    private final Map<QueryAtom, Integer> positionIndices;

    /** Maps each molecule atom to its index in the molecule's atom ordering. */
    private final Map<Atom, Integer> moleculeIndices;

    /** The real atom bound to each position, by molecule atom index, or {@link #UNBOUND}. */
    private final int[] boundTo;

    private QuerySymmetry(List<int[]> permutations, Map<QueryAtom, Integer> positionIndices, Map<Atom, Integer> moleculeIndices)
    {
        this.permutations = permutations;
        this.positionIndices = positionIndices;
        this.moleculeIndices = moleculeIndices;
        this.boundTo = new int[positionIndices.size()];
        Arrays.fill(this.boundTo, UNBOUND);
    }

    /**
     * Note that the result tracks the bindings of one search, so a fresh instance is needed per search.
     *
     * @param query the pattern whose symmetries to find
     * @param molecule the molecule the query will be matched against
     * @return the symmetries of {@code query} with respect to {@code molecule}, or {@link #NONE}
     */
    static QuerySymmetry of(Query query, Molecule molecule)
    {
        // if any stereochemistry, ABORT
        if (!query.declaredChirality().isEmpty() || !query.declaredDoubleBondStereo().isEmpty())
        {
            return NONE;
        }

        // a lone position can only map to itself
        List<QueryAtom> positions = query.atoms();
        if (positions.size() < 2)
        {
            return NONE;
        }

        List<int[]> permutations = permutationsOf(query, molecule, positions);
        if (permutations.isEmpty())
        {
            return NONE;
        }
        return new QuerySymmetry(permutations, indexBy(positions), indexBy(molecule.atoms()));
    }

    /**
     * @param query the pattern whose symmetries to find
     * @param molecule the molecule the query will be matched against
     * @param positions the query's atom positions
     * @return every permutation of {@code positions} that preserves the query, leaving out the identity
     */
    private static List<int[]> permutationsOf(Query query, Molecule molecule, List<QueryAtom> positions)
    {
        int size = positions.size();
        // the invariants a symmetry has to preserve, gathered up front rather than recomputed per candidate
        int[] degrees = degreesOf(query, positions);
        List<BitSet> acceptedAtoms = acceptedAtomsOf(molecule, positions);
        QueryBond[][] joining = joiningBonds(query, positions);
        Map<QueryBond, BitSet> acceptedBonds = acceptedBondsOf(molecule, query);

        List<int[]> permutations = new ArrayList<>();
        extend(new int[size], new boolean[size], 0, degrees, acceptedAtoms, joining, acceptedBonds, permutations);
        return permutations;
    }

    /**
     * @param query the pattern the positions belong to
     * @param positions the query's atom positions
     * @return the number of bonds each of {@code positions} has, by position index
     */
    private static int[] degreesOf(Query query, List<QueryAtom> positions)
    {
        int[] degrees = new int[positions.size()];
        for (int i = 0; i < positions.size(); i++)
        {
            degrees[i] = query.bondsOf(positions.get(i)).size();
        }
        return degrees;
    }

    /**
     * @param molecule the molecule to test against
     * @param positions the query's atom positions
     * @return the real atoms each of {@code positions} accepts, by position index
     */
    private static List<BitSet> acceptedAtomsOf(Molecule molecule, List<QueryAtom> positions)
    {
        List<BitSet> acceptedAtoms = new ArrayList<>(positions.size());
        for (QueryAtom position : positions)
        {
            acceptedAtoms.add(accepted(molecule, position));
        }
        return acceptedAtoms;
    }

    /**
     * @param query the pattern the positions belong to
     * @param positions the query's atom positions
     * @return the query bond joining each pair of positions, by their two indices in either order (symmetric)
     */
    private static QueryBond[][] joiningBonds(Query query, List<QueryAtom> positions)
    {
        int size = positions.size();
        QueryBond[][] joining = new QueryBond[size][size];
        for (int i = 0; i < size; i++)
        {
            for (int j = i + 1; j < size; j++)
            {
                QueryBond bond = query.bondBetween(positions.get(i), positions.get(j)).orElse(null);
                joining[i][j] = bond;
                joining[j][i] = bond;
            }
        }
        return joining;
    }

    /**
     * @param molecule the molecule to test against
     * @param query the pattern whose bonds to test
     * @return the real bonds each of {@code query}'s bonds accepts, keyed by bond reference identity
     */
    private static Map<QueryBond, BitSet> acceptedBondsOf(Molecule molecule, Query query)
    {
        Map<QueryBond, BitSet> acceptedBonds = new IdentityHashMap<>();
        for (QueryBond bond : query.bonds())
        {
            acceptedBonds.put(bond, accepted(molecule, bond));
        }
        return acceptedBonds;
    }

    /**
     * Numbers a list by position, starting at 0
     *
     * @param items the items to number, in the order giving each its index
     * @param <T> the type of item
     * @return the index of each of {@code items}
     */
    private static <T> Map<T, Integer> indexBy(List<T> items)
    {
        Map<T, Integer> indices = new IdentityHashMap<>();
        for (int i = 0; i < items.size(); i++)
        {
            indices.put(items.get(i), i);
        }
        return indices;
    }

    /**
     * @param molecule the molecule to test against
     * @param position the query position to test
     * @return the real atoms of {@code molecule} that {@code position} accepts, by atom index
     */
    private static BitSet accepted(Molecule molecule, QueryAtom position)
    {
        List<Atom> atoms = molecule.atoms();
        BitSet set = new BitSet(atoms.size());
        for (int i = 0; i < atoms.size(); i++)
        {
            if (position.predicate().matches(molecule, atoms.get(i)))
            {
                set.set(i);
            }
        }
        return set;
    }

    /**
     * @param molecule the molecule to test against
     * @param bond the query bond to test
     * @return the real bonds of {@code molecule} that {@code bond} accepts, by bond index
     */
    private static BitSet accepted(Molecule molecule, QueryBond bond)
    {
        List<Bond> bonds = molecule.bonds();
        BitSet set = new BitSet(bonds.size());
        for (int i = 0; i < bonds.size(); i++)
        {
            if (bond.predicate().matches(molecule, bonds.get(i)))
            {
                set.set(i);
            }
        }
        return set;
    }

    /**
     * Builds every permutation of the positions that preserves which real atoms each position
     * accepts and which positions are bonded by what, collecting them into {@code into}.
     * Excludes the identity permutation.
     *
     * @param permutation the permutation being built, filled up to {@code position}
     * @param used which targets the permutation has already taken
     * @param position the position to choose a target for
     * @param degrees the number of bonds each position has
     * @param acceptedAtoms the real atoms each position accepts
     * @param joining the query bond between each pair of positions, or {@code null}
     * @param acceptedBonds the real bonds each query bond accepts
     * @param into collects the permutations found
     */
    private static void extend(
            int[] permutation,
            boolean[] used,
            int position,
            int[] degrees,
            List<BitSet> acceptedAtoms,
            QueryBond[][] joining,
            Map<QueryBond, BitSet> acceptedBonds,
            List<int[]> into)
    {
        if (into.size() >= MAX_PERMUTATIONS)
        {
            return;
        }

        int size = permutation.length;
        // a target has been chosen for every position, so the permutation is complete and ready to record
        if (position == size)
        {
            // the identity prunes nothing, so keep only a permutation that moves some position
            for (int i = 0; i < size; i++)
            {
                if (permutation[i] != i)
                {
                    into.add(permutation.clone());
                    return;
                }
            }
            return;
        }

        for (int target = 0; target < size; target++)
        {
            // a symmetry preserves bond count, so a target with a different one cannot work
            if (used[target] || degrees[position] != degrees[target])
            {
                continue;
            }
            // the two positions have to be interchangeable in every match
            if (!acceptedAtoms.get(position).equals(acceptedAtoms.get(target)))
            {
                continue;
            }
            if (!bondsAgree(permutation, position, target, joining, acceptedBonds))
            {
                continue;
            }

            // target survived every check, so take it and fill the positions after it, then give it back
            // up so the next iteration can try this same position against another target
            permutation[position] = target;
            used[target] = true;
            extend(permutation, used, position + 1, degrees, acceptedAtoms, joining, acceptedBonds, into);
            // only used is restored as permutation[position] is overwritten before anything reads it again
            used[target] = false;
        }
    }

    /**
     * @param permutation the permutation being built, filled below {@code position}
     * @param position the position being mapped
     * @param target the target it would be mapped to
     * @param joining the query bond between each pair of positions, or {@code null}
     * @param acceptedBonds the real bonds each query bond accepts
     * @return {@code true} if mapping {@code position} to {@code target} keeps every bond to an
     *     already-permuted position, joined by a bond accepting the same real bonds
     */
    private static boolean bondsAgree(
            int[] permutation,
            int position,
            int target,
            QueryBond[][] joining,
            Map<QueryBond, BitSet> acceptedBonds)
    {
        // only the positions already mapped; a later one tests its pair with this one when its turn comes
        for (int earlier = 0; earlier < position; earlier++)
        {
            QueryBond before = joining[position][earlier];
            QueryBond after = joining[target][permutation[earlier]];
            // the mapped pair has to be bonded exactly when this pair is
            if ((before == null) != (after == null))
            {
                return false;
            }
            if (before != null && !acceptedBonds.get(before).equals(acceptedBonds.get(after)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the number of non-identity symmetries found
     */
    int symmetryCount()
    {
        return permutations.size();
    }

    /**
     * Records that a position has been bound.
     *
     * @param position the query position just bound
     * @param atom the real atom it was bound to
     */
    void bind(QueryAtom position, Atom atom)
    {
        Integer index = positionIndices.get(position);
        if (index != null)
        {
            boundTo[index] = moleculeIndices.get(atom);
        }
    }

    /**
     * Undoes a {@link #bind}
     *
     * @param position the query position no longer bound
     */
    void unbind(QueryAtom position)
    {
        Integer index = positionIndices.get(position);
        if (index != null)
        {
            boundTo[index] = UNBOUND;
        }
    }

    /**
     * Decides whether the assignment bound so far is the one worth exploring, of the several
     * equivalent assignments these symmetries produce over the same real atoms.
     * The current assignment is dictated by {@link #bind}/{@link #unbind}.
     * <p>
     * E.g., matching {@code CC} against ethane binds its two positions to the two carbons either way round;
     * this method would keep the way round taking the first carbon for the first position, and abandons the branch below the other.
     *
     * @return {@code true} if the search should continue below what is currently bound, or
     *     {@code false} once an equivalent assignment coming earlier makes this branch redundant
     */
    boolean comesFirst()
    {
        for (int[] permutation : permutations)
        {
            if (!comesFirstUnder(permutation))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @param permutation one of this query's symmetries
     * @return {@code false} only once {@code permutation} is known to give an equivalent assignment that comes first
     */
    private boolean comesFirstUnder(int[] permutation)
    {
        for (int i = 0; i < permutation.length; i++)
        {
            int mine = boundTo[i];
            int theirs = boundTo[permutation[i]];
            if (mine == UNBOUND || theirs == UNBOUND)
            {
                // nothing to compare yet, so nothing to rule this assignment out
                return true;
            }
            // the first position whose atoms differ decides, as a lexicographic comparison of the two assignments
            if (mine != theirs)
            {
                return mine < theirs;
            }
        }
        // the permutation maps the assignment onto itself, so no equivalent one comes before it
        return true;
    }
}
