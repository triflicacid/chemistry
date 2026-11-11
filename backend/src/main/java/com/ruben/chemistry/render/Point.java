package com.ruben.chemistry.render;

/**
 * A point in depiction space.
 * <p>
 * Depiction space is measured in bond lengths.
 * <p>
 * The axes are standard mathematical ones: {@code y} increases upwards and an angle is measured
 * anticlockwise from the positive {@code x} axis.
 *
 * @param x the horizontal coordinate, increasing rightwards
 * @param y the vertical coordinate, increasing upwards
 */
public record Point(double x, double y)
{
    /** The point both of whose coordinates are zero. */
    public static final Point ORIGIN = new Point(0, 0);

    /**
     * @param angle the direction, in radians anticlockwise from the positive {@code x} axis
     * @param length how far along {@code angle} the point lies
     * @return the point {@code length} away from the origin in the direction of {@code angle}
     */
    public static Point polar(double angle, double length)
    {
        return new Point(Math.cos(angle) * length, Math.sin(angle) * length);
    }

    /**
     * @param other the point to add
     * @return the component-wise sum of this point and {@code other}
     */
    public Point plus(Point other)
    {
        return new Point(x + other.x, y + other.y);
    }

    /**
     * @param other the point to subtract
     * @return the vector from {@code other} to this point
     */
    public Point minus(Point other)
    {
        return new Point(x - other.x, y - other.y);
    }

    /**
     * @param dx how far to move along {@code x}
     * @param dy how far to move along {@code y}
     * @return this point, moved
     */
    public Point translate(double dx, double dy)
    {
        return new Point(x + dx, y + dy);
    }

    /**
     * @param factor what to multiply both coordinates by
     * @return this point, scaled about the origin
     */
    public Point scale(double factor)
    {
        return new Point(x * factor, y * factor);
    }

    /**
     * @param other the other vector
     * @return the {@code z} component of the cross product of this vector and {@code other},
     *     which is positive where {@code other} lies anticlockwise of this vector
     */
    public double cross(Point other)
    {
        return x * other.y - y * other.x;
    }

    /**
     * @return this point's distance from the origin
     */
    public double length()
    {
        return Math.hypot(x, y);
    }

    /**
     * @param other the point to measure to
     * @return the distance between this point and {@code other}
     */
    public double distanceTo(Point other)
    {
        return Math.hypot(x - other.x, y - other.y);
    }

    /**
     * @return this point's direction from the origin, in radians anticlockwise from the positive {@code x} axis
     */
    public double angle()
    {
        return Math.atan2(y, x);
    }
}
