package com.ruben.chemistry.render.rings;

/**
 * What a {@link RingDrawer} is asked to do.
 *
 * @param projectCages whether a ring system that cannot be drawn flat may be drawn as a view of its 3D structure
 * @param refinement how hard to work at keeping ring systems clear of themselves
 * @param wanted how many different drawings of each ring system are wanted, counting the best
 */
public record RingEffort(boolean projectCages, RingRefinement refinement, int wanted)
{
}
