package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.space.Vec3;

/**
 * @param position where the camera is in the scene, in angstroms
 * @param target the point it looks at and orbits, in angstroms
 * @param fov its vertical field of view, in degrees
 * @param roll how far the picture is turned about the way the camera looks, from level, in degrees
 */
public record Camera(Vec3 position, Vec3 target, double fov, double roll)
{
    public Camera
    {
        if (position == null || target == null)
        {
            throw new IllegalArgumentException("position and target are required");
        }
        if (!isFinite(position) || !isFinite(target) || !Double.isFinite(roll))
        {
            throw new IllegalArgumentException("The camera must be made of finite numbers");
        }
        if (!(fov > 0 && fov < 180))
        {
            throw new IllegalArgumentException("fov must be between 0 and 180 degrees, but was " + fov);
        }
    }

    private static boolean isFinite(Vec3 point)
    {
        return Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z());
    }
}
