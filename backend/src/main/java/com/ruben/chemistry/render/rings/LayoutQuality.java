package com.ruben.chemistry.render.rings;

import com.ruben.chemistry.render.Point;

/**
 * Measures how well a drawing keeps clear of itself.
 * <p>
 * A drawing is a set of points joined by bonds, each bond being a pair of indices into the points.
 */
public final class LayoutQuality
{
    /** How close to zero a length has to be to count as nothing. */
    private static final double EPSILON = 1e-9;

    /** Two bonds at an atom whose angle has a cosine below this are drawn as one straight line. */
    private static final double STRAIGHT_COSINE = -0.94;

    /** Two bonds at an atom whose angle has a cosine above this are drawn one on top of the other. */
    private static final double SHARP_COSINE = 0.94;

    private LayoutQuality()
    {
    }

    /**
     * @param points where each atom sits
     * @param bonds each bond as the pair of atoms it joins
     * @return how many pairs of bonds at an atom are drawn as one line: either so near straight that the atom looks as
     *     if it were not a bend at all, or so near each other that one bond lies on top of the other
     */
    public static int hiddenBends(Point[] points, int[][] bonds)
    {
        boolean[][] joined = joinedPairs(points.length, bonds);
        int hidden = 0;
        for (int centre = 0; centre < points.length; centre++)
        {
            for (int first = 0; first < points.length; first++)
            {
                for (int second = first + 1; second < points.length; second++)
                {
                    if (!joined[centre][first] || !joined[centre][second])
                    {
                        continue;
                    }
                    Point one = points[first].minus(points[centre]);
                    Point other = points[second].minus(points[centre]);
                    double lengths = one.length() * other.length();
                    if (lengths <= EPSILON)
                    {
                        continue;
                    }
                    double cosine = (one.x() * other.x() + one.y() * other.y()) / lengths;
                    if (cosine < STRAIGHT_COSINE || cosine > SHARP_COSINE)
                    {
                        hidden++;
                    }
                }
            }
        }
        return hidden;
    }

    /**
     * @param points where each atom sits
     * @param bonds each bond as the pair of atoms it joins
     * @return the distance between the closest two atoms that are not bonded to one another, or {@link Double#MAX_VALUE} if there are none
     */
    public static double minimumAtomDistance(Point[] points, int[][] bonds)
    {
        boolean[][] joined = joinedPairs(points.length, bonds);
        double closest = Double.MAX_VALUE;
        for (int first = 0; first < points.length; first++)
        {
            for (int second = first + 1; second < points.length; second++)
            {
                if (!joined[first][second])
                {
                    closest = Math.min(closest, points[first].distanceTo(points[second]));
                }
            }
        }
        return closest;
    }

    /**
     * @param points where each atom sits
     * @param bonds each bond as the pair of atoms it joins
     * @return how many pairs of bonds that share no atom cross one another
     */
    public static int crossings(Point[] points, int[][] bonds)
    {
        int crossings = 0;
        for (int first = 0; first < bonds.length; first++)
        {
            for (int second = first + 1; second < bonds.length; second++)
            {
                if (crosses(points, bonds[first], bonds[second]))
                {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    /**
     * @param points where each atom sits
     * @param first one bond
     * @param second another bond
     * @return whether the two share no atom but cross
     */
    public static boolean crosses(Point[] points, int[] first, int[] second)
    {
        if (first[0] == second[0] || first[0] == second[1] || first[1] == second[0] || first[1] == second[1])
        {
            return false;
        }
        return segmentsCross(points[first[0]], points[first[1]], points[second[0]], points[second[1]]);
    }

    /**
     * @param points where each atom sits
     * @param bonds each bond as the pair of atoms it joins
     * @param length how long every bond is meant to be
     * @return how far the worst bond is from that length
     */
    public static double lengthError(Point[] points, int[][] bonds, double length)
    {
        double worst = 0;
        for (int[] bond : bonds)
        {
            worst = Math.max(worst, Math.abs(points[bond[0]].distanceTo(points[bond[1]]) - length));
        }
        return worst;
    }

    /**
     * @param a one end of the first segment
     * @param b the other end of the first segment
     * @param c one end of the second segment
     * @param d the other end of the second segment
     * @return whether the two segments touch or cross
     */
    public static boolean segmentsCross(Point a, Point b, Point c, Point d)
    {
        double first = orientation(a, b, c);
        double second = orientation(a, b, d);
        double third = orientation(c, d, a);
        double fourth = orientation(c, d, b);
        if (((first > EPSILON && second < -EPSILON) || (first < -EPSILON && second > EPSILON))
            && ((third > EPSILON && fourth < -EPSILON) || (third < -EPSILON && fourth > EPSILON)))
        {
            return true;
        }
        return (Math.abs(first) <= EPSILON && withinSpan(a, b, c))
            || (Math.abs(second) <= EPSILON && withinSpan(a, b, d))
            || (Math.abs(third) <= EPSILON && withinSpan(c, d, a))
            || (Math.abs(fourth) <= EPSILON && withinSpan(c, d, b));
    }

    /**
     * @param from one end of a line
     * @param to the other end
     * @param point a point on that line
     * @return whether the point lies between the two ends
     */
    private static boolean withinSpan(Point from, Point to, Point point)
    {
        return point.x() >= Math.min(from.x(), to.x()) - EPSILON
            && point.x() <= Math.max(from.x(), to.x()) + EPSILON
            && point.y() >= Math.min(from.y(), to.y()) - EPSILON
            && point.y() <= Math.max(from.y(), to.y()) + EPSILON;
    }

    /**
     * @param a the start of a line
     * @param b the end of the line
     * @param c a point
     * @return a positive number if the point lies anticlockwise of the line, a negative one if clockwise, or zero if on it
     */
    private static double orientation(Point a, Point b, Point c)
    {
        return b.minus(a).cross(c.minus(a));
    }

    /**
     * @param count how many atoms there are
     * @param bonds each bond as the pair of atoms it joins
     * @return which pairs of atoms are joined directly
     */
    private static boolean[][] joinedPairs(int count, int[][] bonds)
    {
        boolean[][] joined = new boolean[count][count];
        for (int[] bond : bonds)
        {
            joined[bond[0]][bond[1]] = true;
            joined[bond[1]][bond[0]] = true;
        }
        return joined;
    }
}
