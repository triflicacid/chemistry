package com.ruben.chemistry.render;

import java.util.List;
import java.util.Objects;

/**
 * One ring of a {@link Depiction}.
 *
 * @param atoms the indices of the ring's atoms, in the order they are joined around it
 * @param circle whether the ring is drawn with a circle inside it, as an aromatic ring is in a skeletal drawing
 */
public record RingPlacement(List<Integer> atoms, boolean circle)
{
    /**
     * @throws NullPointerException if {@code atoms} is {@code null}
     * @throws IllegalArgumentException if there are fewer than three atoms
     */
    public RingPlacement
    {
        Objects.requireNonNull(atoms, "atoms");
        atoms = List.copyOf(atoms);
        if (atoms.size() < 3)
        {
            throw new IllegalArgumentException("A ring needs at least three atoms, but this has " + atoms.size());
        }
    }
}
