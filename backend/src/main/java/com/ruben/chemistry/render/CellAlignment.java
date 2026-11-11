package com.ruben.chemistry.render;

/**
 * Where a part sits horizontally within the cell.
 */
public enum CellAlignment
{
    /** Against the cell's left edge. */
    LEFT(0),

    /** In the middle of the cell. */
    CENTRE(0.5),

    /** Against the cell's right edge. */
    RIGHT(1);

    private final double fraction;

    CellAlignment(double fraction)
    {
        this.fraction = fraction;
    }

    /**
     * @return how far across the spare room in a cell the part is pushed, from 0 (left) to 1 (right)
     */
    public double fraction()
    {
        return fraction;
    }
}
