package com.ruben.chemistry.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermutationSearchTest
{
    @Test
    void finds_all_permutations_when_unconstrained()
    {
        List<int[]> permutations = PermutationSearch.find(
                3,
                Integer.MAX_VALUE,
                (permutation, position, target) -> true);

        assertEquals(6, permutations.size());

        Set<String> actual = new HashSet<>();
        for (int[] permutation : permutations)
        {
            actual.add(java.util.Arrays.toString(permutation));
        }

        assertEquals(Set.of(
                "[0, 1, 2]",
                "[0, 2, 1]",
                "[1, 0, 2]",
                "[1, 2, 0]",
                "[2, 0, 1]",
                "[2, 1, 0]"), actual);
    }

    @Test
    void respects_mapping_constraint()
    {
        List<int[]> permutations = PermutationSearch.find(
                3,
                Integer.MAX_VALUE,
                (permutation, position, target) -> position != target);

        assertEquals(2, permutations.size());

        assertTrue(permutations.stream().allMatch(permutation ->
                permutation[0] != 0
                        && permutation[1] != 1
                        && permutation[2] != 2));
    }

    @Test
    void generates_only_one_to_one_mappings()
    {
        List<int[]> permutations = PermutationSearch.find(
                4,
                Integer.MAX_VALUE,
                (permutation, position, target) -> true);

        for (int[] permutation : permutations)
        {
            assertEquals(
                    4,
                    new HashSet<>(java.util.Arrays.stream(permutation)
                            .boxed()
                            .toList()).size());
        }
    }

    @Test
    void stops_at_maximum_result_count()
    {
        List<int[]> permutations = PermutationSearch.find(
                4,
                5,
                (permutation, position, target) -> true);

        assertEquals(5, permutations.size());
    }

    @Test
    void returns_no_results_when_maximum_is_zero()
    {
        List<int[]> permutations = PermutationSearch.find(
                3,
                0,
                (permutation, position, target) -> true);

        assertTrue(permutations.isEmpty());
    }

    @Test
    void returns_empty_permutation_for_zero_size()
    {
        List<int[]> permutations = PermutationSearch.find(
                0,
                1,
                (permutation, position, target) -> true);

        assertEquals(1, permutations.size());
        assertArrayEquals(new int[0], permutations.getFirst());
    }

    @Test
    void returns_no_results_when_constraint_rejects_every_mapping()
    {
        List<int[]> permutations = PermutationSearch.find(
                3,
                10,
                (permutation, position, target) -> false);

        assertTrue(permutations.isEmpty());
    }

    @Test
    void returns_independent_permutation_arrays()
    {
        List<int[]> permutations = PermutationSearch.find(
                2,
                10,
                (permutation, position, target) -> true);

        int[] secondBeforeMutation = permutations.get(1).clone();

        permutations.get(0)[0] = 99;

        assertArrayEquals(secondBeforeMutation, permutations.get(1));
    }

    @Test
    void rejects_negative_size()
    {
        assertThrows(
                IllegalArgumentException.class,
                () -> PermutationSearch.find(
                        -1,
                        10,
                        (permutation, position, target) -> true));
    }

    @Test
    void rejects_negative_maximum_result_count()
    {
        assertThrows(
                IllegalArgumentException.class,
                () -> PermutationSearch.find(
                        3,
                        -1,
                        (permutation, position, target) -> true));
    }

    @Test
    void allows_constraints_to_inspect_previously_mapped_positions()
    {
        List<int[]> permutations = PermutationSearch.find(
                3,
                Integer.MAX_VALUE,
                (permutation, position, target) ->
                        position == 0 || target != permutation[0]);

        assertTrue(permutations.stream().allMatch(permutation ->
                permutation[0] != permutation[1]
                        && permutation[0] != permutation[2]));
    }
}