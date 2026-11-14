package com.ruben.chemistry.util;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * A maximum-cardinality matching of an undirected graph, by Edmonds' blossom algorithm.
 * <p>
 * A matching is a set of edges no two of which share an endpoint. Each edge in it pairs off the two
 * vertices it joins, and a vertex once paired cannot appear in another edge, so a matching is a way
 * of pairing vertices along the edges there are to pair them along. A perfect matching leaves no
 * vertex over. A maximum one pairs off as many as can be paired.
 * <p>
 * The algorithm grows alternating trees from the unmatched vertices and augments along any
 * alternating path it finds between two of them. An odd cycle would strand such a path, so each one
 * met is contracted to a single vertex, a blossom, and the search goes on through it.
 */
public final class MaximumMatching
{
    /** The absence of a partner, a parent, or a vertex. */
    public static final int NONE = -1;

    private final List<List<Integer>> neighbours;

    private final int[] partnerOf;

    private final int[] parentOf;

    /** The vertex each one has been contracted into, which is itself until a blossom takes it in. */
    private final int[] baseOf;

    private final boolean[] reached;

    private final boolean[] inBlossom;

    private MaximumMatching(List<List<Integer>> neighbours)
    {
        this.neighbours = neighbours;
        int order = neighbours.size();
        this.partnerOf = new int[order];
        this.parentOf = new int[order];
        this.baseOf = new int[order];
        this.reached = new boolean[order];
        this.inBlossom = new boolean[order];
        Arrays.fill(partnerOf, NONE);
    }

    /**
     * Matches as many vertices as can be matched.
     *
     * @param neighbours each vertex's neighbours, by vertex index, which must be symmetric
     * @return the vertex each one is matched to, by vertex index, or {@link #NONE} where a vertex is left unmatched
     */
    public static int[] of(List<List<Integer>> neighbours)
    {
        MaximumMatching matching = new MaximumMatching(neighbours);
        matching.run();
        return matching.partnerOf;
    }

    /**
     * @param partnerOf a matching, as {@link #of} returns it
     * @return whether it leaves no vertex unmatched
     */
    public static boolean isPerfect(int[] partnerOf)
    {
        return Arrays.stream(partnerOf).noneMatch(partner -> partner == NONE);
    }

    /**
     * Grows the matching until no augmenting path is left to find.
     */
    private void run()
    {
        for (int root = 0; root < partnerOf.length; root++)
        {
            if (partnerOf[root] == NONE)
            {
                int tip = augmentingPathFrom(root);
                if (tip != NONE)
                {
                    augmentAlong(tip);
                }
            }
        }
    }

    /**
     * Searches for a path of alternating unmatched and matched edges from an unmatched vertex to
     * another one, contracting any odd cycle it meets on the way.
     *
     * @param root the unmatched vertex to search from
     * @return the unmatched vertex the path ends at, or {@link #NONE} if there is no such path
     */
    private int augmentingPathFrom(int root)
    {
        Arrays.fill(reached, false);
        Arrays.fill(parentOf, NONE);
        Arrays.setAll(baseOf, vertex -> vertex);
        reached[root] = true;

        Deque<Integer> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty())
        {
            int vertex = pending.poll();
            for (int next : neighbours.get(vertex))
            {
                // an edge inside a blossom leads nowhere new, and a matched edge is not one to grow along
                if (baseOf[vertex] == baseOf[next] || partnerOf[vertex] == next)
                {
                    continue;
                }
                if (next == root || (partnerOf[next] != NONE && parentOf[partnerOf[next]] != NONE))
                {
                    contractBlossom(vertex, next, pending);
                }
                else if (parentOf[next] == NONE)
                {
                    parentOf[next] = vertex;
                    if (partnerOf[next] == NONE)
                    {
                        return next;
                    }
                    reached[partnerOf[next]] = true;
                    pending.add(partnerOf[next]);
                }
            }
        }
        return NONE;
    }

    /**
     * Contracts the odd cycle closed by an edge into the vertex where its two sides meet.
     *
     * @param vertex one end of the edge that closed the cycle
     * @param next the other end
     * @param pending the search queue, which every vertex newly taken into the blossom joins
     */
    private void contractBlossom(int vertex, int next, Deque<Integer> pending)
    {
        int meeting = meetingPointOf(vertex, next);
        Arrays.fill(inBlossom, false);
        markUpTo(vertex, meeting, next);
        markUpTo(next, meeting, vertex);
        for (int other = 0; other < partnerOf.length; other++)
        {
            if (inBlossom[baseOf[other]])
            {
                baseOf[other] = meeting;
                if (!reached[other])
                {
                    reached[other] = true;
                    pending.add(other);
                }
            }
        }
    }

    /**
     * @param first one end of the edge that closed an odd cycle
     * @param second the other end
     * @return the vertex nearest the root that both ends' paths back to it pass through
     */
    private int meetingPointOf(int first, int second)
    {
        boolean[] onPath = new boolean[partnerOf.length];
        int vertex = first;
        while (true)
        {
            vertex = baseOf[vertex];
            onPath[vertex] = true;
            if (partnerOf[vertex] == NONE)
            {
                break;
            }
            vertex = parentOf[partnerOf[vertex]];
        }

        vertex = second;
        while (true)
        {
            vertex = baseOf[vertex];
            if (onPath[vertex])
            {
                return vertex;
            }
            vertex = parentOf[partnerOf[vertex]];
        }
    }

    /**
     * Marks one side of an odd cycle, from an end of the closing edge up to where the sides meet,
     * pointing each vertex on it back towards that edge so the path can be walked either way.
     *
     * @param from an end of the closing edge
     * @param meeting the vertex the two sides meet at
     * @param towards the vertex to point the first step back at
     */
    private void markUpTo(int from, int meeting, int towards)
    {
        int vertex = from;
        int child = towards;
        while (baseOf[vertex] != meeting)
        {
            inBlossom[baseOf[vertex]] = true;
            inBlossom[baseOf[partnerOf[vertex]]] = true;
            parentOf[vertex] = child;
            child = partnerOf[vertex];
            vertex = parentOf[partnerOf[vertex]];
        }
    }

    /**
     * Flips the matched and unmatched edges along an augmenting path, which matches one more pair
     * of vertices than before.
     *
     * @param tip the unmatched vertex the path ends at
     */
    private void augmentAlong(int tip)
    {
        int vertex = tip;
        while (vertex != NONE)
        {
            int parent = parentOf[vertex];
            int next = partnerOf[parent];
            partnerOf[vertex] = parent;
            partnerOf[parent] = vertex;
            vertex = next;
        }
    }
}
