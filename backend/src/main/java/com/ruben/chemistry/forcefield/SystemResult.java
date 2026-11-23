package com.ruben.chemistry.forcefield;

import com.ruben.chemistry.space.Vec3;

import java.util.List;

/**
 * A total system energy and the atom-ordered forces.
 *
 * @param energy the total intermolecular energy
 * @param forces the force on each atom, listed by molecule
 */
public record SystemResult(double energy, List<List<Vec3>> forces)
{
}
