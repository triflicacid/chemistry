package com.ruben.chemistry.render.scene;

import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.Point;

import java.util.List;

/**
 * A linear gradient a scene's shapes may reference by id.
 *
 * @param id    the identifier shapes refer to
 * @param from  where the gradient starts
 * @param to    where it ends
 * @param stops the colours along it, from its start to its end
 */
public record Gradient(String id, Point from, Point to, List<Stop> stops)
{
    public Gradient
    {
        if (stops.size() < 2)
            throw new IllegalArgumentException("a gradient needs at least two stops");
        stops = List.copyOf(stops);
    }

    /**
     * One colour along a gradient.
     *
     * @param offset how far along the gradient it sits, from 0 to 1
     * @param colour the colour there
     */
    public record Stop(double offset, Colour colour)
    {
        public Stop
        {
            if (!(offset >= 0 && offset <= 1))
                throw new IllegalArgumentException("a stop's offset must be between 0 and 1: " + offset);
        }
    }
}
