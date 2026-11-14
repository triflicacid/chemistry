package com.ruben.chemistry.space;

/**
 * @param x the first component
 * @param y the second component
 * @param z the third component
 */
public record Vec3(double x, double y, double z)
{
    /** The origin. */
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    /**
     * @param other another vector
     * @return the sum of this and {@code other}
     */
    public Vec3 plus(Vec3 other)
    {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    /**
     * @param other another vector
     * @return this less {@code other}
     */
    public Vec3 minus(Vec3 other)
    {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    /**
     * @param factor a number
     * @return this, every component multiplied by {@code factor}
     */
    public Vec3 times(double factor)
    {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    /**
     * @param other another vector
     * @return the dot product of this and {@code other}
     */
    public double dot(Vec3 other)
    {
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * @param other another vector
     * @return the cross product of this and {@code other}
     */
    public Vec3 cross(Vec3 other)
    {
        return new Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    /**
     * @return the length
     */
    public double length()
    {
        return Math.sqrt(dot(this));
    }

    /**
     * @param other another point
     * @return the distance from this to {@code other}
     */
    public double distanceTo(Vec3 other)
    {
        return minus(other).length();
    }

    /**
     * @return this scaled to length one, or the zero vector if it has no length
     */
    public Vec3 normalised()
    {
        double length = length();
        return length == 0 ? ZERO : times(1 / length);
    }

    /**
     * @param first the first point
     * @param second the point the angle is at
     * @param third the third point
     * @return the angle at {@code second}, in degrees
     */
    public static double angle(Vec3 first, Vec3 second, Vec3 third)
    {
        Vec3 a = first.minus(second).normalised();
        Vec3 b = third.minus(second).normalised();
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.dot(b)))));
    }

    /**
     * @param first the first point of the chain of four
     * @param second the second point
     * @param third the third point
     * @param fourth the fourth point
     * @return the dihedral angle about the second-third axis, in degrees from -180 to 180; zero means
     *     {@code first} and {@code fourth} are on the same side (cis)
     */
    public static double torsion(Vec3 first, Vec3 second, Vec3 third, Vec3 fourth)
    {
        Vec3 before = second.minus(first);
        Vec3 axis = third.minus(second);
        Vec3 after = fourth.minus(third);
        Vec3 normalBefore = before.cross(axis);
        Vec3 normalAfter = axis.cross(after);
        double y = axis.length() * before.dot(normalAfter);
        double x = normalBefore.dot(normalAfter);
        return Math.toDegrees(Math.atan2(y, x));
    }

    /**
     * @param centre the point the other three are around
     * @param first the first of three points
     * @param second the second
     * @param third the third
     * @return the triple product of the three unit directions from {@code centre}: zero when flat, about
     *     0.77 in magnitude for the three ligands of an ideal tetrahedron. Its sign is the handedness
     */
    public static double handedness(Vec3 centre, Vec3 first, Vec3 second, Vec3 third)
    {
        Vec3 a = first.minus(centre).normalised();
        Vec3 b = second.minus(centre).normalised();
        Vec3 c = third.minus(centre).normalised();
        return a.dot(b.cross(c));
    }
}
