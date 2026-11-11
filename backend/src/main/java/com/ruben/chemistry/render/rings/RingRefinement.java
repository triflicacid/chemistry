package com.ruben.chemistry.render.rings;

import java.util.Locale;

/**
 * How hard a layout works to draw ring systems and what hangs off them without atoms colliding.
 *
 * @param effort how many times the usual number of drawings are searched; 0 means none are, so nothing is redrawn or moved
 */
public record RingRefinement(int effort)
{
    /** Ring systems are drawn as first laid out, and branches are not moved to clear them. */
    public static final RingRefinement NONE = new RingRefinement(0);

    /** Colliding ring systems are redrawn and branches are moved to clear them. */
    public static final RingRefinement STANDARD = new RingRefinement(1);

    /** As {@link #STANDARD}, searching several times as many drawings. */
    public static final RingRefinement THOROUGH = new RingRefinement(3);

    public RingRefinement
    {
        if (effort < 0)
        {
            throw new IllegalArgumentException("Ring refinement effort cannot be negative, but was " + effort);
        }
    }

    /**
     * @param text {@code none}, {@code standard}, {@code thorough}, or a whole number of times the usual effort
     * @return that refinement
     * @throws IllegalArgumentException if the text is none of these
     */
    public static RingRefinement parse(String text)
    {
        return switch (text.toLowerCase(Locale.ROOT))
        {
            case "none" -> NONE;
            case "standard" -> STANDARD;
            case "thorough" -> THOROUGH;
            default ->
            {
                try
                {
                    yield new RingRefinement(Integer.parseInt(text));
                }
                catch (NumberFormatException invalid)
                {
                    throw new IllegalArgumentException("--ring-refinement must be none, standard, thorough or a whole number, but was '" + text + "'");
                }
            }
        };
    }

    /**
     * @param effort the least effort wanted
     * @return this refinement, or one of that effort if it is less
     */
    public RingRefinement atLeast(int effort)
    {
        return this.effort >= effort ? this : new RingRefinement(effort);
    }

    /**
     * @return whether anything is redrawn or moved to avoid collisions
     */
    public boolean isEnabled()
    {
        return effort > 0;
    }
}
