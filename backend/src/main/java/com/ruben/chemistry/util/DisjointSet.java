package com.ruben.chemistry.util;

/**
 * A union-find over a fixed number of elements, numbered from zero.
 */
public final class DisjointSet
{
    /** The parent of each element, which is the element itself at the root of its set. */
    private final int[] parent;

    /** An upper bound on the height of the tree rooted at each element, used to merge the shorter into the taller. */
    private final byte[] rank;

    /**
     * @param size the number of elements, each starting in a set of its own
     * @throws IllegalArgumentException if {@code size} is negative
     */
    public DisjointSet(int size)
    {
        if (size < 0)
        {
            throw new IllegalArgumentException("Size must not be negative");
        }

        this.parent = new int[size];
        this.rank = new byte[size];

        for (int element = 0; element < size; element++)
        {
            parent[element] = element;
        }
    }

    /**
     * Merges the sets containing the two specified elements.
     *
     * @param first the index of the first element whose set should be merged
     * @param second the index of the second element whose set should be merged
     * @return {@code true} if two separate sets were merged, or {@code false} if both elements already belonged to the same set
     * @throws IndexOutOfBoundsException if either element index is outside the range {@code [0, size)}
     */
    public boolean union(int first, int second)
    {
        int firstRoot = find(first);
        int secondRoot = find(second);

        if (firstRoot == secondRoot)
        {
            return false;
        }

        // hanging the shorter tree off the taller keeps the taller's height, so neither grows needlessly
        if (rank[firstRoot] < rank[secondRoot])
        {
            parent[firstRoot] = secondRoot;
        }
        else if (rank[firstRoot] > rank[secondRoot])
        {
            parent[secondRoot] = firstRoot;
        }
        else
        {
            parent[secondRoot] = firstRoot;
            rank[firstRoot]++;
        }

        return true;
    }

    /**
     * @param first the first element
     * @param second the second element
     * @return {@code true} if both elements are in the same set
     * @throws IndexOutOfBoundsException if either element is outside this set
     */
    public boolean areJoined(int first, int second)
    {
        return find(first) == find(second);
    }

    /**
     * @param element the element whose set to identify
     * @return the element representing {@code element}'s set, which is the same for every member
     * @throws IndexOutOfBoundsException if {@code element} is outside this set
     */
    public int find(int element)
    {
        if (element < 0 || element >= parent.length)
        {
            throw new IndexOutOfBoundsException("Element out of range: " + element);
        }

        int root = element;
        while (parent[root] != root)
        {
            root = parent[root];
        }

        // point everything on the way at the root, so the next walk from here is a single step
        int step = element;
        while (parent[step] != root)
        {
            int next = parent[step];
            parent[step] = root;
            step = next;
        }

        return root;
    }
}
