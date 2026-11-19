package com.ruben.chemistry.server.studio;

/**
 * Generates unique names for atoms and bonds.
 */
final class Names
{
    private int atoms;
    private int bonds;

    /**
     * @param atoms how many atom names have been given already
     * @param bonds how many bond names have been given already
     */
    Names(int atoms, int bonds)
    {
        this.atoms = atoms;
        this.bonds = bonds;
    }

    /**
     * @return a name no atom has had
     */
    String nextAtom()
    {
        return "a" + (++atoms);
    }

    /**
     * @return a name no bond has had
     */
    String nextBond()
    {
        return "b" + (++bonds);
    }
}
