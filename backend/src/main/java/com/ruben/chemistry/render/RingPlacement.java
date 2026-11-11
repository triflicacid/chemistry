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
}
