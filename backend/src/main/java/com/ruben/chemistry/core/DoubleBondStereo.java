package com.ruben.chemistry.core;

/**
 * The configuration of a double bond: whether a chosen reference substituent on each of
 * its two atoms lies on the same side of the double bond, or on opposite sides.
 * <p>
 * Double bond stereo only makes sense relative to a particular choice of reference
 * substituent on each side, so it is only meaningful in the context of a {@link Molecule}.
 */
public enum DoubleBondStereo
{
    /** The reference substituents lie on the same side of the double bond. */
    SAME_SIDE,

    /** The reference substituents lie on opposite sides of the double bond. */
    OPPOSITE_SIDE;

    /**
     * @return the opposite configuration, as results from swapping the reference substituent
     *     on one (but not both) side of the double bond
     */
    public DoubleBondStereo flip()
    {
        return this == SAME_SIDE ? OPPOSITE_SIDE : SAME_SIDE;
    }
}
