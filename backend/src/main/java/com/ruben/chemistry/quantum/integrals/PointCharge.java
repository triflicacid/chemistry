package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.space.Vec3;

/**
 * A nucleus, treated as a point charge.
 *
 * @param position where the nucleus is, in bohr
 * @param charge the nuclear charge the valence electrons see (effective nuclear charge)
 */
public record PointCharge(Vec3 position, double charge)
{
}
