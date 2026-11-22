package com.ruben.chemistry.forcefield;

import com.ruben.chemistry.space.Vec3;

import java.util.List;
import java.util.Objects;

/**
 * The energy of a pair of molecules and the forces on the atoms of both.
 *
 * @param energy the energy contributions
 * @param forcesA the force on each atom of the first molecule
 * @param forcesB the force on each atom of the second molecule
 */
public record InteractionResult(Energy energy, List<Vec3> forcesA, List<Vec3> forcesB)
{
    public InteractionResult
    {
        Objects.requireNonNull(energy);
        forcesA = List.copyOf(forcesA);
        forcesB = List.copyOf(forcesB);
    }
}
