package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.util.PermutationSearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds symmetries of a query relative to a particular target molecule.
 * <p>
 * Two query positions are considered interchangeable when swapping them
 * preserves their degrees, accepted target atoms, and accepted target bonds.
 * The resulting permutations therefore describe equivalent query
 * assignments for the supplied molecule, rather than intrinsic symmetries
 * of the query or automorphisms of the molecule itself.
 * <p>
 * Queries containing atom chirality or double-bond stereochemistry are
 * not supported, as stereochemistry needs to be relative to atom bindings,
 * and this just involves atom predicates.
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
     * Creates the query symmetries that are valid for the supplied target molecule.
     *
     * @param query the query whose positions are compared
     * @param molecule the target molecule against which symmetry is evaluated
     * @return the target-relative query symmetries, or {@link #NONE} when symmetry analysis is not supported for the query
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

        int[] degrees = degreesOf(query, positions);
        List<BitSet> acceptedAtoms = acceptedAtomsOf(molecule, positions);
        QueryBond[][] joining = joiningBonds(query, positions);
        Map<QueryBond, BitSet> acceptedBonds = acceptedBondsOf(molecule, query);

        List<int[]> candidates = PermutationSearch.find(
                size,
                MAX_PERMUTATIONS + 1,
                (permutation, position, target) ->
                        degrees[position] == degrees[target]
                                && acceptedAtoms.get(position).equals(acceptedAtoms.get(target))
                                && bondsAgree(permutation, position, target, joining, acceptedBonds));

        return candidates.stream()
                .filter(permutation -> !isIdentity(permutation))
                .limit(MAX_PERMUTATIONS)
                .toList();
    }

    private static boolean isIdentity(int[] permutation)
    {
        for (int i = 0; i < permutation.length; i++)
        {
            if (permutation[i] != i)
            {
                return false;
            }
        }
        return true;
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
