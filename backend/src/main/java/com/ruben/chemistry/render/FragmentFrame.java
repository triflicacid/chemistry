package com.ruben.chemistry.render;

import java.util.Objects;

/**
 * One disconnected part of a laid-out molecule.
 *
 * @param index the fragment's index, which is the {@link AtomPlacement#fragment()} of each of its atoms
 * @param bounds the smallest rectangle containing the fragment's atoms, in the fragment's own frame
 */
public record FragmentFrame(int index, Bounds bounds)
{
    public FragmentFrame
    {
        Objects.requireNonNull(bounds, "bounds");
        if (index < 0)
        {
            throw new IllegalArgumentException("A fragment cannot have index " + index);
        }
    }
}
