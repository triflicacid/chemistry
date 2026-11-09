package com.ruben.chemistry.formula;

/**
 * How a molecule's connected fragments are gathered into the units written either side of a {@code .} in a formula.
 */
public enum FragmentGrouping
{
    /**
     * Writes nothing fragmented: the whole molecule is counted as one formula, so copper(II)
     * sulfate pentahydrate is {@code CuH10SO9}.
     */
    NONE,

    /**
     * Writes one unit per connected fragment, so copper(II) sulfate pentahydrate is
     * {@code Cu2+.SO42-.5H2O}. Identical fragments are gathered into a single unit with a count.
     */
    COMPONENTS,

    /**
     * As {@link #COMPONENTS}, except that every charged fragment is combined into a single
     * uncharged unit, so copper(II) sulfate pentahydrate is {@code CuSO4.5H2O}.
     */
    NEUTRAL_UNITS
}
