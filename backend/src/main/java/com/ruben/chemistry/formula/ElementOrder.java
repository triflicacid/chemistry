package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Elements;

/**
 * The order a formula's elements are written in.
 */
public enum ElementOrder
{
    /**
     * Conventional ordering
     * <ul>
     *     <li>Organic: carbon, then hydrogen, then every other element alphabetically</li>
     *     <li>Inorganic: follows {@link Elements#iupacOrderOf}</li>
     * </ul>
     */
    CONVENTIONAL,

    /**
     * Hill-system ordering
     * <ul>
     *     <li>Organic: carbon, then hydrogen, then every other element alphabetically</li>
     *     <li>Inorganic: alphabetical throughout (including hydrogen)</li>
     * </ul>
     */
    HILL
}
