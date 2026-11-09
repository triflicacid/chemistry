package com.ruben.chemistry.render;

/**
 * How a chain of bonded atoms carries on through an atom it passes through.
 */
public enum ChainGeometry
{
    /**
     * The chain leaves an atom in the direction it arrived in, so a run of bonded atoms is drawn as one straight line.
     */
    STRAIGHT,

    /**
     * The chain turns through 120 degrees at every atom, alternating sides, so a run of bonded
     * atoms is drawn as a zig-zag.
     */
    ZIGZAG
}
