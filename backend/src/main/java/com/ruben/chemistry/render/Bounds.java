package com.ruben.chemistry.render;

import java.util.Collection;

/**
 * An axis-aligned rectangle in depiction space, as described by {@link Point}.
 *
 * @param minX the smallest {@code x} coordinate
 * @param minY the smallest {@code y} coordinate
 * @param maxX the largest {@code x} coordinate
 * @param maxY the largest {@code y} coordinate
 */
public record Bounds(double minX, double minY, double maxX, double maxY)
{
    /** The rectangle of zero size at the origin, which is what a depiction of nothing occupies. */
    public static final Bounds EMPTY = new Bounds(0, 0, 0, 0);

    /**
     * @throws IllegalArgumentException if either maximum is below its matching minimum
     */
    public Bounds
    {
        if (maxX < minX || maxY < minY)
        {
            throw new IllegalArgumentException("Bounds cannot have a maximum below its minimum: " + minX + ".." + maxX + ", " + minY + ".." + maxY);
        }
    }

    /**
     * @param points the points to enclose
     * @return the smallest rectangle containing every point, or {@link #EMPTY} if there are none
     */
    public static Bounds around(Collection<Point> points)
    {
        if (points.isEmpty())
        {
            return EMPTY;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Point point : points)
        {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    /**
     * @return how far this rectangle spans along {@code x}
     */
    public double width()
    {
        return maxX - minX;
    }

    /**
     * @return how far this rectangle spans along {@code y}
     */
    public double height()
    {
        return maxY - minY;
    }

    /**
     * @return the point at the middle of this rectangle
     */
    public Point centre()
    {
        return new Point((minX + maxX) / 2, (minY + maxY) / 2);
    }

    /**
     * @param margin how far to grow this rectangle on every side
     * @return this rectangle, grown
     */
    public Bounds pad(double margin)
    {
        return new Bounds(minX - margin, minY - margin, maxX + margin, maxY + margin);
    }
}
