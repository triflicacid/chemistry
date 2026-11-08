
package com.ruben.chemistry.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisjointSetTest
{
    @Test
    void should_create_individual_sets_for_each_element()
    {
        DisjointSet sets = new DisjointSet(5);

        for (int element = 0; element < 5; element++)
        {
            assertEquals(element, sets.find(element));
        }

        for (int first = 0; first < 5; first++)
        {
            for (int second = first + 1; second < 5; second++)
            {
                assertFalse(sets.areJoined(first, second));
            }
        }
    }

    @Test
    void should_merge_two_separate_sets()
    {
        DisjointSet sets = new DisjointSet(3);

        assertTrue(sets.union(0, 1));
        assertTrue(sets.areJoined(0, 1));
    }

    @Test
    void should_return_false_when_merging_elements_already_in_same_set()
    {
        DisjointSet sets = new DisjointSet(3);

        sets.union(0, 1);

        assertFalse(sets.union(0, 1));
        assertFalse(sets.union(1, 0));
    }

    @Test
    void should_merge_existing_sets()
    {
        DisjointSet sets = new DisjointSet(5);

        sets.union(0, 1);
        sets.union(2, 3);

        assertTrue(sets.union(1, 2));

        assertTrue(sets.areJoined(0, 1));
        assertTrue(sets.areJoined(0, 2));
        assertTrue(sets.areJoined(0, 3));
        assertFalse(sets.areJoined(0, 4));
    }

    @Test
    void should_connect_elements_transitively()
    {
        DisjointSet sets = new DisjointSet(5);

        sets.union(0, 1);
        sets.union(1, 2);
        sets.union(2, 3);

        assertTrue(sets.areJoined(0, 3));
        assertTrue(sets.areJoined(1, 3));
        assertTrue(sets.areJoined(2, 0));
        assertFalse(sets.areJoined(0, 4));
    }

    @Test
    void should_return_the_same_representative_for_all_elements_in_a_set()
    {
        DisjointSet sets = new DisjointSet(4);

        sets.union(0, 1);
        sets.union(1, 2);

        assertEquals(sets.find(0), sets.find(1));
        assertEquals(sets.find(1), sets.find(2));
        assertNotEquals(sets.find(0), sets.find(3));
    }

    @Test
    void should_merge_sets_regardless_of_union_order()
    {
        DisjointSet first = new DisjointSet(4);
        DisjointSet second = new DisjointSet(4);

        first.union(0, 1);
        first.union(2, 3);
        first.union(1, 2);

        second.union(2, 3);
        second.union(0, 1);
        second.union(3, 0);

        for (int element = 0; element < 4; element++)
        {
            for (int other = 0; other < 4; other++)
            {
                assertEquals(
                        first.areJoined(element, other),
                        second.areJoined(element, other)
                );
            }
        }
    }

    @Test
    void should_handle_single_element()
    {
        DisjointSet sets = new DisjointSet(1);

        assertEquals(0, sets.find(0));
        assertTrue(sets.areJoined(0, 0));
        assertFalse(sets.union(0, 0));
    }

    @Test
    void should_handle_zero_elements()
    {
        DisjointSet sets = new DisjointSet(0);

        assertThrows(IndexOutOfBoundsException.class, () -> sets.find(0));
    }
}
