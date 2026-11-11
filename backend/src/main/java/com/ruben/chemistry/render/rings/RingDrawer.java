package com.ruben.chemistry.render.rings;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.render.Point;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Draws ring systems: places their rings, then redraws the system if the first drawing collides with itself.
 * Drawings are cached.
 */
public final class RingDrawer
{
    private final RingEffort effort;
    private final Map<List<List<Identity>>, List<Map<Atom, Point>>> found = new HashMap<>();

    /**
     * @param effort what to try and how hard
     * @throws NullPointerException if {@code effort} is {@code null}
     */
    public RingDrawer(RingEffort effort)
    {
        this.effort = Objects.requireNonNull(effort, "effort");
    }

    /**
     * @param rings the rings of one connected ring system, each as its atoms in the order they are joined around it
     * @return where every atom of the system sits in each drawing, best first, with at most as many as the effort wants
     */
    public List<Map<Atom, Point>> draw(List<List<Atom>> rings)
    {
        List<List<Identity>> key = rings.stream().map(ring -> ring.stream().map(Identity::new).toList()).toList();
        return found.computeIfAbsent(key, ignored -> RingEnergyMinimiser.improve(rings, PolygonRingPlacer.place(rings), effort));
    }

    private record Identity(Atom atom)
    {
        @Override
        public boolean equals(Object other)
        {
            return other instanceof Identity identity && identity.atom == atom;
        }

        @Override
        public int hashCode()
        {
            return System.identityHashCode(atom);
        }
    }
}
