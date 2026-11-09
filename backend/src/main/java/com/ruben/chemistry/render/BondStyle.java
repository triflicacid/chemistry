package com.ruben.chemistry.render;

/**
 * How a {@link BondPlacement} leaves the plane of the page.
 * <p>
 * A bond that leaves the plane is drawn from a stereocentre outwards, so its narrow end always
 * sits on the placement's {@link BondPlacement#first() first} atom.
 */
public enum BondStyle
{
    /** A bond lying in the plane of the page, drawn as a plain line. */
    PLAIN,

    /** A bond rising out of the page towards the viewer, drawn as a solid wedge. */
    WEDGE,

    /** A bond falling behind the page away from the viewer, drawn as a hashed wedge. */
    HASH;

    /**
     * @return the style pointing the other way out of the plane, {@link #PLAIN} being its own opposite
     */
    public BondStyle flip()
    {
        return switch (this)
        {
            case PLAIN -> PLAIN;
            case WEDGE -> HASH;
            case HASH -> WEDGE;
        };
    }

    /**
     * @return whether this style leaves the plane of the page
     */
    public boolean isStereo()
    {
        return this != PLAIN;
    }
}
