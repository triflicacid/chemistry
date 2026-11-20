package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.quantum.basis.EffectiveCorePotential;
import com.ruben.chemistry.space.Vec3;

/**
 * An effective core potential sitting on an atom.
 *
 * @param position where the atom is, in bohr
 * @param potential the potential that replaces the atom's core electrons
 */
public record EcpCentre(Vec3 position, EffectiveCorePotential potential)
{
}
