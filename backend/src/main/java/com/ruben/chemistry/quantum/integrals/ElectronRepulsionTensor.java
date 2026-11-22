package com.ruben.chemistry.quantum.integrals;

/**
 * Every electron-repulsion integral {@code (ij|kl)} over a basis: the repulsion between
 * the charge density {@code i(1) j(1)} and the density {@code k(2) l(2)}.
 * <p>
 * The integral is the same when {@code i} and {@code j} swap, when {@code k} and {@code l} swap, and when the two
 * densities swap, so only one of each eight is kept.
 */
public final class ElectronRepulsionTensor
{
    private final int size;
    private final double[] values;

    /**
     * @param size the number of basis functions
     * @param values the integrals that differ by symmetry, laid out as {@link #length(int)} and {@link #slot} say
     */
    ElectronRepulsionTensor(int size, double[] values)
    {
        this.size = size;
        this.values = values;
    }

    /**
     * @param size the number of basis functions
     * @return how many values a tensor over that many functions keeps
     * @throws IllegalArgumentException if there are too many to hold
     */
    static int length(int size)
    {
        long pairs = (long) size * (size + 1) / 2;
        long length = pairs * (pairs + 1) / 2;
        if (length > Integer.MAX_VALUE)
        {
            throw new IllegalArgumentException(size + " basis functions have too many electron-repulsion integrals to hold");
        }
        return (int) length;
    }

    /**
     * @param first the position of the first density's pair of functions among the pairs, where the pair {@code (i, j)}
     *     with {@code i >= j} is at {@code i (i + 1) / 2 + j}
     * @param second the position of the second density's pair, which is no greater than {@code first}
     * @return where the integral between the two densities is kept
     */
    static int slot(int first, int second)
    {
        return first * (first + 1) / 2 + second;
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
        int first = i >= j ? i * (i + 1) / 2 + j : j * (j + 1) / 2 + i;
        int second = k >= l ? k * (k + 1) / 2 + l : l * (l + 1) / 2 + k;
        return values[first >= second ? slot(first, second) : slot(second, first)];
    }
}
