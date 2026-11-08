package com.ruben.chemistry.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds permutations subject to constraints supplied by the caller.
 */
public final class PermutationSearch
{
    private PermutationSearch()
    {
    }

    /**
     * Finds up to {@code maxResults} permutations of {@code size} positions.
     *
     * @param size the number of positions to permute
     * @param maxResults the maximum number of permutations to return
     * @param constraint whether a position may be mapped to a target, given the partial permutation
     * @return the permutations found, including the identity if it satisfies the constraints
     */
    public static List<int[]> find(int size, int maxResults, MappingConstraint constraint)
    {
        if (size < 0 || maxResults < 0)
        {
            throw new IllegalArgumentException("Size and maximum results must not be negative");
        }

        List<int[]> permutations = new ArrayList<>();
        extend(new int[size], new boolean[size], 0, maxResults, constraint, permutations);
        return permutations;
    }

    private static void extend(
            int[] permutation,
            boolean[] used,
            int position,
            int maxResults,
            MappingConstraint constraint,
            List<int[]> into)
    {
        if (into.size() >= maxResults)
        {
            return;
        }

        if (position == permutation.length)
        {
            into.add(permutation.clone());
            return;
        }

        for (int target = 0; target < permutation.length && into.size() < maxResults; target++)
        {
            if (used[target] || !constraint.canMap(permutation, position, target))
            {
                continue;
            }

            permutation[position] = target;
            used[target] = true;

            extend(permutation, used, position + 1, maxResults, constraint, into);

            used[target] = false;
        }
    }

    /**
     * Tests whether a position can be mapped to a target, given the partial permutation.
     */
    @FunctionalInterface
    public interface MappingConstraint
    {
        boolean canMap(int[] permutation, int position, int target);
    }
}