package com.ruben.chemistry.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaximumMatchingTest
{
    @Test
    void matches_nothing_in_a_graph_with_no_edges()
    {
        int[] matching = MaximumMatching.of(graph(3));

        assertThat(matching).containsExactly(-1, -1, -1);
        assertThat(MaximumMatching.isPerfect(matching)).isFalse();
    }

    @Test
    void matches_the_two_ends_of_a_single_edge()
    {
        int[] matching = MaximumMatching.of(graph(2, 0, 1));

        assertThat(matching).containsExactly(1, 0);
        assertThat(MaximumMatching.isPerfect(matching)).isTrue();
    }

    @Test
    void matches_every_vertex_of_an_even_cycle()
    {
        int[] matching = MaximumMatching.of(graph(6, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 0));

        assertThat(MaximumMatching.isPerfect(matching)).isTrue();
        assertMutual(matching);
    }

    @Test
    void leaves_one_vertex_of_an_odd_cycle_unmatched()
    {
        int[] matching = MaximumMatching.of(graph(5, 0, 1, 1, 2, 2, 3, 3, 4, 4, 0));

        assertThat(MaximumMatching.isPerfect(matching)).isFalse();
        assertThat(unmatched(matching)).isEqualTo(1);
        assertMutual(matching);
    }

    @Test
    void matches_an_odd_cycle_with_a_tail_leading_off_it()
    {
        // a triangle with a tail of three, where the pairing has to run out of the odd cycle
        int[] matching = MaximumMatching.of(graph(6, 0, 1, 1, 2, 2, 0, 2, 3, 3, 4, 4, 5));

        assertThat(MaximumMatching.isPerfect(matching)).isTrue();
        assertMutual(matching);
    }

    @Test
    void matches_an_odd_cycle_sharing_a_vertex_with_an_even_one()
    {
        int[] matching = MaximumMatching.of(graph(8,
            0, 1, 1, 2, 2, 0,
            2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 2));

        assertThat(MaximumMatching.isPerfect(matching)).isTrue();
        assertMutual(matching);
    }

    @Test
    void matches_two_odd_cycles_joined_by_a_path()
    {
        int[] matching = MaximumMatching.of(graph(10,
            0, 1, 1, 2, 2, 3, 3, 4, 4, 0,
            5, 6, 6, 7, 7, 8, 8, 9, 9, 5,
            0, 5));

        assertThat(MaximumMatching.isPerfect(matching)).isTrue();
        assertMutual(matching);
    }

    @Test
    void matches_a_path_of_even_length_and_not_one_of_odd()
    {
        assertThat(MaximumMatching.isPerfect(MaximumMatching.of(graph(4, 0, 1, 1, 2, 2, 3)))).isTrue();
        assertThat(MaximumMatching.isPerfect(MaximumMatching.of(graph(3, 0, 1, 1, 2)))).isFalse();
    }

    @Test
    void matches_as_many_as_can_be_matched_when_none_is_perfect()
    {
        // a star: whatever the centre is matched to, the other two leaves are left over
        int[] matching = MaximumMatching.of(graph(4, 0, 1, 0, 2, 0, 3));

        assertThat(unmatched(matching)).isEqualTo(2);
        assertMutual(matching);
    }

    /**
     * @param matching a matching
     * @return how many vertices it leaves unmatched
     */
    private static int unmatched(int[] matching)
    {
        return (int) Arrays.stream(matching).filter(partner -> partner == -1).count();
    }

    /**
     * Checks that each matched vertex is matched to one that is matched back to it.
     *
     * @param matching a matching
     */
    private static void assertMutual(int[] matching)
    {
        for (int vertex = 0; vertex < matching.length; vertex++)
        {
            if (matching[vertex] != -1)
            {
                assertThat(matching[matching[vertex]]).isEqualTo(vertex);
            }
        }
    }

    /**
     * @param order how many vertices the graph has
     * @param edges the edges, as pairs of vertex indices
     * @return the graph as each vertex's neighbours
     */
    private static List<List<Integer>> graph(int order, int... edges)
    {
        List<List<Integer>> neighbours = new ArrayList<>();
        for (int vertex = 0; vertex < order; vertex++)
        {
            neighbours.add(new ArrayList<>());
        }
        for (int index = 0; index < edges.length; index += 2)
        {
            neighbours.get(edges[index]).add(edges[index + 1]);
            neighbours.get(edges[index + 1]).add(edges[index]);
        }
        return neighbours;
    }
}
