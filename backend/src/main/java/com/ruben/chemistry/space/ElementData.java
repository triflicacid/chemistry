package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Elements;

/**
 * Per-element constants the 3D embedder needs: covalent and van der Waals radii and the
 * count of valence electrons.
 */
public final class ElementData
{
    /** Cordero et al. covalent radii in angstroms, indexed by atomic number - 1. */
    private static final double[] COVALENT = {
        0.31, 0.28,
        1.28, 0.96, 0.84, 0.76, 0.71, 0.66, 0.57, 0.58,
        1.66, 1.41, 1.21, 1.11, 1.07, 1.05, 1.02, 1.06,
        2.03, 1.76, 1.70, 1.60, 1.53, 1.39, 1.39, 1.32, 1.26, 1.24, 1.32, 1.22, 1.22, 1.20, 1.19, 1.20, 1.20, 1.16,
        2.20, 1.95, 1.90, 1.75, 1.64, 1.54, 1.47, 1.46, 1.42, 1.39, 1.45, 1.44, 1.42, 1.39, 1.39, 1.38, 1.39, 1.40,
        2.44, 2.15, 2.07, 2.04, 2.03, 2.01, 1.99, 1.98, 1.98, 1.96, 1.94, 1.92, 1.92, 1.89, 1.90, 1.87, 1.87,
        1.75, 1.70, 1.62, 1.51, 1.44, 1.41, 1.36, 1.36, 1.32, 1.45, 1.46, 1.48, 1.40, 1.50, 1.50,
        2.60, 2.21, 2.15, 2.06, 2.00, 1.96, 1.90, 1.87, 1.80, 1.69
    };

    /** Bondi / Alvarez van der Waals radii in angstroms, indexed by atomic number - 1, for the first four periods. */
    private static final double[] VAN_DER_WAALS = {
        1.10, 1.40,
        1.81, 1.53, 1.92, 1.70, 1.55, 1.52, 1.47, 1.54,
        2.27, 1.73, 1.84, 2.10, 1.80, 1.80, 1.75, 1.88,
        2.75, 2.31, 2.11, 2.00, 2.00, 2.00, 2.00, 2.00, 2.00, 1.63, 1.40, 1.39, 1.87, 2.11, 1.85, 1.90, 1.85, 2.02
    };

    private static final double DEFAULT_COVALENT = 1.5;

    private static final double DEFAULT_VAN_DER_WAALS = 2.0;

    private ElementData()
    {
    }

    /**
     * @param element an element symbol
     * @return the atomic number, or 0 if the symbol is not an element (e.g. a wildcard)
     */
    public static int numberOf(String element)
    {
        return Elements.isSymbol(element) ? Elements.atomicNumberOf(element) : 0;
    }

    /**
     * @param atomicNumber an atomic number, or 0 if unknown
     * @return the covalent radius in angstroms
     */
    public static double covalentRadius(int atomicNumber)
    {
        return atomicNumber >= 1 && atomicNumber <= COVALENT.length ? COVALENT[atomicNumber - 1] : DEFAULT_COVALENT;
    }

    /**
     * @param atomicNumber an atomic number, or 0 if unknown
     * @return the van der Waals radius in angstroms
     */
    public static double vanDerWaalsRadius(int atomicNumber)
    {
        return atomicNumber >= 1 && atomicNumber <= VAN_DER_WAALS.length ? VAN_DER_WAALS[atomicNumber - 1] : DEFAULT_VAN_DER_WAALS;
    }

    /**
     * @param atomicNumber an atomic number, or 0 if unknown
     * @return the periodic table period, or 0 if unknown
     */
    static int period(int atomicNumber)
    {
        if (atomicNumber < 1)
        {
            return 0;
        }
        int[] ends = {2, 10, 18, 36, 54, 86, 118};
        for (int period = 0; period < ends.length; period++)
        {
            if (atomicNumber <= ends[period])
            {
                return period + 1;
            }
        }
        return ends.length;
    }

    /**
     * @param atomicNumber an atomic number, or 0 if unknown
     * @return the number of valence electrons of a main-group element, or -1 for the transition,
     *     lanthanide, actinide and unknown elements, which are given no lone pairs
     */
    static int valenceElectrons(int atomicNumber)
    {
        if (atomicNumber < 1)
        {
            return -1;
        }
        if (atomicNumber <= 2)
        {
            return atomicNumber;
        }
        // each period's first two elements are its s block and its last six are its p block
        int[][] blocks = {{3, 10}, {11, 18}, {19, 36}, {37, 54}, {55, 86}, {87, 118}};
        for (int[] block : blocks)
        {
            if (atomicNumber < block[0] || atomicNumber > block[1])
            {
                continue;
            }
            int offset = atomicNumber - block[0];
            if (offset < 2)
            {
                return offset + 1;
            }
            int width = block[1] - block[0] + 1;
            // the final six elements of the period are its p block
            int fromEnd = width - 1 - offset;
            return fromEnd < 6 ? 8 - fromEnd : -1;
        }
        return -1;
    }
}
