package com.ruben.chemistry.charges;

/**
 * What a set of atomic partial charges means.
 */
public enum ChargeConvention
{
    /** Mulliken population analysis, which the force field's parameters were fitted with. */
    MULLIKEN,

    /** Gasteiger-Marsili partial equalisation of orbital electronegativity. */
    GASTEIGER,

    /** Electronegativity equalisation. */
    EEM,

    /** Every site carries only its formal charge, because no real model could be applied. */
    FORMAL
}
