package com.ruben.chemistry.quantum;

public final class Units
{
    /** Ångström per bohr, the CODATA 2018 value of the Bohr radius. */
    public static final double ANGSTROM_PER_BOHR = 0.529177210903;

    /** Bohr per ångström. */
    public static final double BOHR_PER_ANGSTROM = 1.0 / ANGSTROM_PER_BOHR;

    private Units()
    {
    }

    /**
     * @param angstrom a length in ångström
     * @return the same length in bohr
     */
    public static double toBohr(double angstrom)
    {
        return angstrom * BOHR_PER_ANGSTROM;
    }

    /**
     * @param bohr a length in bohr
     * @return the same length in ångström
     */
    public static double toAngstrom(double bohr)
    {
        return bohr * ANGSTROM_PER_BOHR;
    }
}
