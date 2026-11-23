package com.ruben.chemistry.quantum.integrals;

/**
 * Every electron-repulsion integral {@code (ij|kl)} over a basis: the repulsion between
 * the charge density {@code i(1) j(1)} and the density {@code k(2) l(2)}.
 */
public final class ElectronRepulsionTensor
{
    private final int size;
    private final double[] values;

    /**
     * @param size the number of basis functions
     * @param values the {@code size^4} integrals, with the index of {@code l} running fastest
     */
    ElectronRepulsionTensor(int size, double[] values)
    {
        this.size = size;
        this.values = values;
    }

    /**
     * @return the number of basis functions
     */
    public int size()
    {
        return size;
    }

    /**
     * @param i the first function of the first density
     * @param j the second function of the first density
     * @param k the first function of the second density
     * @param l the second function of the second density
     * @return the repulsion {@code (ij|kl)}, in hartree
     */
    public double get(int i, int j, int k, int l)
    {
        return values[((i * size + j) * size + k) * size + l];
    }
}
