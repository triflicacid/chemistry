package com.ruben.chemistry.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Shares the directions leaving an atom out between the neighbours that have to go in them.
 */
final class AngleFan
{
    /** A whole turn, in radians. */
    static final double TURN = Math.PI * 2;

    /** How close two spacings have to be before the earlier gap is preferred. */
    private static final double TIE = 1e-9;

    private AngleFan()
    {
    }

    /**
     * Spreads directions evenly over the whole circle, which is what an atom with nothing fixed
     * around it, i.e. the one a layout starts from, wants.
     *
     * @param start the direction of the first neighbour, in radians
     * @param slots how many neighbours have to be placed
     * @return one direction per neighbour, each a whole turn divided by {@code slots} on from the last
     */
    static double[] evenlyAround(double start, int slots)
    {
        double[] directions = new double[slots];
        for (int slot = 0; slot < slots; slot++)
        {
            directions[slot] = start + slot * TURN / slots;
        }
        return directions;
    }

    /**
     * Fills the gaps between directions already fixed, splitting the roomiest gap each time.
     *
     * @param fixed the directions already spoken for, in radians, which must not be empty
     * @param count how many more directions are needed
     * @return the new directions, roomiest first
     * @throws IllegalArgumentException if {@code fixed} is empty
     */
    static List<Double> fillGaps(List<Double> fixed, int count)
    {
        if (fixed.isEmpty())
        {
            throw new IllegalArgumentException("Gaps can only be filled between directions that are already fixed");
        }
        if (count <= 0)
        {
            return List.of();
        }

        double[] edges = fixed.stream().mapToDouble(AngleFan::normalise).sorted().toArray();
        int gaps = edges.length;
        double[] span = new double[gaps];
        for (int gap = 0; gap < gaps; gap++)
        {
            // the last gap wraps past a whole turn back round to the first edge
            double end = gap + 1 < gaps ? edges[gap + 1] : edges[0] + TURN;
            span[gap] = end - edges[gap];
        }

        // hand each direction to whichever gap is left roomiest by taking it, so the earliest
        // directions returned are the ones with the most space around them
        int[] taken = new int[gaps];
        int[] chosenGap = new int[count];
        for (int slot = 0; slot < count; slot++)
        {
            int roomiest = 0;
            for (int gap = 1; gap < gaps; gap++)
            {
                if (spacingAfter(span, taken, gap) > spacingAfter(span, taken, roomiest) + TIE)
                {
                    roomiest = gap;
                }
            }
            taken[roomiest]++;
            chosenGap[slot] = roomiest;
        }

        int[] used = new int[gaps];
        List<Double> directions = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++)
        {
            int gap = chosenGap[slot];
            used[gap]++;
            directions.add(edges[gap] + span[gap] * used[gap] / (taken[gap] + 1.0));
        }
        return directions;
    }

    /**
     * @param span each gap's width
     * @param taken how many directions each gap has already been given
     * @param gap the gap to measure
     * @return how far apart {@code gap}'s directions would be if it were given one more
     */
    private static double spacingAfter(double[] span, int[] taken, int gap)
    {
        return span[gap] / (taken[gap] + 2);
    }

    /**
     * @param angle an angle in radians
     * @return the same direction, as an angle from zero up to but not including a whole turn
     */
    static double normalise(double angle)
    {
        double normalised = angle % TURN;
        return normalised < 0 ? normalised + TURN : normalised;
    }

    /**
     * @param angle the direction to reflect, in radians
     * @param axis the direction to reflect it across, in radians
     * @return the direction as far the other side of {@code axis} as {@code angle} is this side
     */
    static double mirror(double angle, double axis)
    {
        return 2 * axis - angle;
    }
}
