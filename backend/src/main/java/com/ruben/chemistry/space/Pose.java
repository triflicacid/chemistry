package com.ruben.chemistry.space;

/**
 * Where a whole molecule sits in a scene.
 *
 * @param position where the molecule's origin is in the scene, in angstroms
 * @param rotation how the molecule is turned about its origin; a unit quaternion
 */
public record Pose(Vec3 position, Quaternion rotation)
{
    /** The pose of a molecule that sits where its own frame is the scene's. */
    public static final Pose IDENTITY = new Pose(Vec3.ZERO, Quaternion.IDENTITY);

    /**
     * @return whether the position and the rotation are all finite numbers
     */
    public boolean isFinite()
    {
        Vec3 at = position;
        return Double.isFinite(at.x()) && Double.isFinite(at.y()) && Double.isFinite(at.z()) && rotation.isFinite();
    }

    /**
     * @return this pose with its rotation scaled to a unit quaternion, which is this very pose if it is one already
     * @throws IllegalStateException if the rotation has no length
     */
    public Pose normalised()
    {
        Quaternion unit = rotation.normalised();
        return unit == rotation ? this : new Pose(position, unit);
    }

    /**
     * @param local a point in the molecule's own frame
     * @return where that point is in the scene
     */
    public Vec3 apply(Vec3 local)
    {
        return rotation.rotate(local).plus(position);
    }
}
