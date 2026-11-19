package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Structure3D;

import java.util.Set;

/**
 * What an editing session keeps with a molecule when it saves.
 *
 * @param structure the most recent 3D structure, or {@code null} if none has been made
 * @param lockedSites the sites of {@code structure} that were locked, by position in the structure
 * @param pose where the molecule sits in its scene, which the structure's own coordinates are unaffected by
 * @param camera where the camera was and how it looked, or {@code null} if it was never said
 */
public record StudioSnapshot(Structure3D structure, Set<Integer> lockedSites, Pose pose, Camera camera)
{
    /** A molecule no session has saved anything for. */
    public static final StudioSnapshot EMPTY = new StudioSnapshot(null, Set.of(), Pose.IDENTITY, null);

    public StudioSnapshot
    {
        lockedSites = Set.copyOf(lockedSites);
    }

    /**
     * @param structure the new structure
     * @return this, with the structure replaced
     */
    public StudioSnapshot withStructure(Structure3D structure)
    {
        return new StudioSnapshot(structure, lockedSites, pose, camera);
    }
}
