package com.ruben.chemistry.render;

/**
 * One disconnected part of a laid-out molecule.
 *
 * @param index the fragment's index, which is the {@link AtomPlacement#fragment()} of each of its atoms
 * @param bounds the smallest rectangle containing the fragment's atoms
 */
public record FragmentFrame(int index, Bounds bounds)
{
}
