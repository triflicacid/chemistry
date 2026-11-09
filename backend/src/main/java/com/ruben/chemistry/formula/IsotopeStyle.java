package com.ruben.chemistry.formula;

/**
 * How a formula names an isotopically labelled nuclide.
 */
public enum IsotopeStyle
{
    /**
     * The mass number prefixes every element alike, e.g. {@code [2H]} and {@code [13C]}.
     */
    MASS_NUMBER,

    /**
     * As {@link #MASS_NUMBER}, except that the heavy hydrogen isotopes take their conventional
     * names, {@code D} and {@code T}, instead of {@code [2H]} and {@code [3H]}.
     */
    HYDROGEN_ALIASES
}
