package com.ruben.chemistry.space;

/**
 * Where a viewer stands and what they look at.
 *
 * @param eye where the camera is
 * @param target the point it looks at, which lands at the middle of the picture
 * @param up the direction that points upwards on the picture, which need not be exact
 * @param fieldOfView the angle the view spans in degrees, or 0 for an orthographic view
 */
public record Camera(Vec3 eye, Vec3 target, Vec3 up, double fieldOfView)
{
    /**
     * A point seen by a camera.
     *
     * @param visible false if the point is level with or behind the camera, and so is clipped
     */
    public record Projected(double x, double y, double depth, double scale, boolean visible)
    {
    }

    /**
     * Places a camera on a sphere around a point, looking in at it.
     *
     * @param target the point to look at and circle
     * @param yaw how far round the vertical axis the camera is, in degrees; 0 puts it on the +z side
     * @param pitch how far above the target's level it is, in degrees
     * @param distance how far it is from the target
     * @param fieldOfView the angle the view spans in degrees, or 0 for an orthographic view
     * @return the camera, with the y axis upwards
     */
    public static Camera orbit(Vec3 target, double yaw, double pitch, double distance, double fieldOfView)
    {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        Vec3 offset = new Vec3(Math.cos(p) * Math.sin(y), Math.sin(p), Math.cos(p) * Math.cos(y)).times(distance);
        return new Camera(target.plus(offset), target, new Vec3(0, 1, 0), fieldOfView);
    }

    /**
     * @param radius the radius of a sphere to fit in view
     * @param fieldOfView the angle the view spans in degrees, or 0 for an orthographic view
     * @return how far from the sphere's centre a camera must stand to see all of it
     */
    public static double distanceFor(double radius, double fieldOfView)
    {
        if (fieldOfView <= 0)
        {
            return 3 * radius;
        }
        return 1.1 * radius / Math.sin(Math.toRadians(fieldOfView) / 2);
    }

    /** @return the unit vectors of the picture: right, above and forward, in that order */
    private Vec3[] basis()
    {
        Vec3 forward = target.minus(eye).normalised();
        Vec3 right = forward.cross(up);
        if (right.length() < 1e-9)
        {
            // looking along the up direction leaves the picture's turn open, so pick any
            right = forward.cross(Math.abs(forward.x()) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1));
        }
        right = right.normalised();
        return new Vec3[]{right, right.cross(forward), forward};
    }

    /**
     * @param direction a direction in space
     * @return how it lies in the picture: x to the right, y upwards, z away from the viewer
     */
    public Vec3 view(Vec3 direction)
    {
        Vec3[] basis = basis();
        return new Vec3(direction.dot(basis[0]), direction.dot(basis[1]), direction.dot(basis[2]));
    }

    /** @return how far the camera is from its target */
    public double distance()
    {
        return eye.distanceTo(target);
    }

    /** @return the camera's yaw about the target in degrees, as {@link #orbit} takes it */
    public double yaw()
    {
        Vec3 offset = eye.minus(target);
        return Math.toDegrees(Math.atan2(offset.x(), offset.z()));
    }

    /** @return the camera's pitch above the target's level in degrees, as {@link #orbit} takes it */
    public double pitch()
    {
        Vec3 offset = eye.minus(target);
        return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, offset.y() / offset.length()))));
    }

    /**
     * @param point a point in space
     * @return where it lands on the picture; if it is level with or behind the camera it is not visible, and
     * its position is only a stand-in
     */
    public Projected project(Vec3 point)
    {
        Vec3[] basis = basis();
        Vec3 right = basis[0];
        Vec3 above = basis[1];
        Vec3 forward = basis[2];

        Vec3 offset = point.minus(eye);
        double depth = offset.dot(forward);
        double x = offset.dot(right);
        double y = offset.dot(above);
        if (fieldOfView == 0)
        {
            return new Projected(x, y, depth, 1, true);
        }

        double distance = eye.distanceTo(target);
        double near = 0.05 * distance;
        double scale = distance / Math.max(depth, near);
        return new Projected(x * scale, y * scale, depth, scale, depth > near);
    }
}
