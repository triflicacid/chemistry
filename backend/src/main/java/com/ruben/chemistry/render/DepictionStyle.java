package com.ruben.chemistry.render;

/**
 * How much of a molecule a depiction spells out.
 */
public enum DepictionStyle
{
    /**
     * Every atom is written out as its own symbol.
     */
    EXPLICIT,

    /**
     * Carbon is left as a bare vertex and its hydrogens go unwritten.
     */
    SKELETAL
}
