package com.ruben.chemistry.quantum.interaction;

import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A group of atoms that is treated as one molecule in an interaction-energy calculation.
 *
 * @param elements the element symbol of each atom
 * @param positions the position of each atom, in bohr
 * @param charge the total charge of the fragment
 */
public record Fragment(List<String> elements, List<Vec3> positions, int charge)
{
    public Fragment
    {
        Objects.requireNonNull(elements);
        Objects.requireNonNull(positions);
        if (elements.isEmpty())
        {
            throw new IllegalArgumentException("a fragment needs atoms");
        }
        if (elements.size() != positions.size())
        {
            throw new IllegalArgumentException("every atom needs a position");
        }
        elements = List.copyOf(elements);
        positions = List.copyOf(positions);
    }

    /**
     * @return the number of atoms
     */
    public int size()
    {
        return elements.size();
    }

    /**
     * @param other the fragment to join this one to
     * @return the atoms of this fragment followed by those of {@code other}, with their charges added
     */
    public Fragment plus(Fragment other)
    {
        List<String> joinedElements = new ArrayList<>(elements);
        joinedElements.addAll(other.elements);
        List<Vec3> joinedPositions = new ArrayList<>(positions);
        joinedPositions.addAll(other.positions);
        return new Fragment(joinedElements, joinedPositions, charge + other.charge);
    }
}
