package com.ruben.chemistry.space;

/**
 * @param x the first component of the vector part
 * @param y the second component of the vector part
 * @param z the third component of the vector part
 * @param w the scalar part
 */
public record Quaternion(double x, double y, double z, double w)
{
    /** The rotation that leaves everything where it is. */
    public static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);

    /** How far from length one a quaternion may be and still be taken as a rotation without being rescaled. */
    private static final double UNIT_TOLERANCE = 1e-12;

    /**
     * @return whether every component is a finite number
     */
    public boolean isFinite()
    {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(w);
    }

    /**
     * @return the length of the quaternion as a four-component vector
     */
    public double length()
    {
        return Math.sqrt(x * x + y * y + z * z + w * w);
    }

    /**
     * @return this scaled to length one, or this very quaternion if it is already that to within rounding,
     *     so that a rotation that is sent back unchanged is still equal to itself
     * @throws IllegalStateException if the quaternion has no length, so describes no rotation
     */
    public Quaternion normalised()
    {
        double length = length();
        if (length == 0 || !Double.isFinite(length))
        {
            throw new IllegalStateException("A quaternion of no length is not a rotation");
        }
        if (Math.abs(length - 1) <= UNIT_TOLERANCE)
        {
            return this;
        }

        return new Quaternion(x / length, y / length, z / length, w / length);
    }

    /**
     * @param point a point
     * @return the point turned by this rotation about the origin, which must be a unit quaternion
     */
    public Vec3 rotate(Vec3 point)
    {
        // v' = v + 2w(u x v) + 2(u x (u x v)), where u is the vector part
        Vec3 u = new Vec3(x, y, z);
        Vec3 cross = u.cross(point);
        return point.plus(cross.times(2 * w)).plus(u.cross(cross).times(2));
    }
}
