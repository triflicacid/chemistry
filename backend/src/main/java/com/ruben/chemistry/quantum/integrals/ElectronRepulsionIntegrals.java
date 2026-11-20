package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.quantum.basis.ContractedGaussian;

import java.util.ArrayList;
import java.util.List;

/**
 * Coulomb repulsion between two electrons, each in a product of two Cartesian Gaussian basis functions, for any
 * angular momentum. Lengths are in bohr and energies in hartree.
 */
public final class ElectronRepulsionIntegrals
{
    private ElectronRepulsionIntegrals()
    {
    }

    /**
     * The primitive products of two basis functions, with the weight each one has in the contracted product.
     */
    private record ContractedPair(List<HermitePair> pairs, double[] weights)
    {
        static ContractedPair of(ContractedGaussian a, ContractedGaussian b)
        {
            List<HermitePair> pairs = new ArrayList<>();
            double[] weights = new double[a.size() * b.size()];
            for (int i = 0; i < a.size(); i++)
            {
                for (int j = 0; j < b.size(); j++)
                {
                    weights[pairs.size()] = a.coefficient(i) * b.coefficient(j);
                    pairs.add(HermitePair.of(a, i, b, j));
                }
            }
            return new ContractedPair(pairs, weights);
        }
    }

    /**
     * @param a the first function of the first density
     * @param b the second function of the first density
     * @param c the first function of the second density
     * @param d the second function of the second density
     * @return the repulsion between the density {@code a b} and the density {@code c d}, written {@code (ab|cd)}
     */
    public static double electronRepulsion(ContractedGaussian a, ContractedGaussian b, ContractedGaussian c, ContractedGaussian d)
    {
        return contract(ContractedPair.of(a, b), ContractedPair.of(c, d));
    }

    /**
     * @param basis the basis functions
     * @return every electron-repulsion integral over the basis
     */
    public static ElectronRepulsionTensor tensor(List<ContractedGaussian> basis)
    {
        int n = basis.size();
        double[] values = new double[n * n * n * n];

        // a pair (i, j) with i >= j has one index
        List<int[]> indices = new ArrayList<>();
        List<ContractedPair> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            for (int j = 0; j <= i; j++)
            {
                indices.add(new int[] {i, j});
                pairs.add(ContractedPair.of(basis.get(i), basis.get(j)));
            }
        }

        for (int first = 0; first < pairs.size(); first++)
        {
            for (int second = 0; second <= first; second++)
            {
                double value = contract(pairs.get(first), pairs.get(second));
                int[] ij = indices.get(first);
                int[] kl = indices.get(second);
                store(values, n, ij[0], ij[1], kl[0], kl[1], value);
            }
        }
        return new ElectronRepulsionTensor(n, values);
    }

    /**
     * Writes the value at all eight index orders that are equal by symmetry.
     */
    private static void store(double[] values, int n, int i, int j, int k, int l, double value)
    {
        int[][] orders = {{i, j, k, l}, {j, i, k, l}, {i, j, l, k}, {j, i, l, k}, {k, l, i, j}, {l, k, i, j}, {k, l, j, i}, {l, k, j, i}};
        for (int[] order : orders)
        {
            values[((order[0] * n + order[1]) * n + order[2]) * n + order[3]] = value;
        }
    }

    private static double contract(ContractedPair left, ContractedPair right)
    {
        double sum = 0.0;
        for (int m = 0; m < left.pairs().size(); m++)
        {
            for (int k = 0; k < right.pairs().size(); k++)
            {
                sum += left.weights()[m] * right.weights()[k] * primitive(left.pairs().get(m), right.pairs().get(k));
            }
        }
        return sum;
    }

    /**
     * @return the repulsion between two Hermite-expanded primitive products
     */
    private static double primitive(HermitePair left, HermitePair right)
    {
        double p = left.exponent();
        double q = right.exponent();
        // the two densities interact like point charges with this reduced exponent
        double reduced = p * q / (p + q);
        double[][][] coulomb = HermiteCoulomb.integrals(
                left.order() + right.order(),
                reduced,
                left.centre()[0] - right.centre()[0],
                left.centre()[1] - right.centre()[1],
                left.centre()[2] - right.centre()[2]);

        double[][] e = left.expansion();
        double[][] f = right.expansion();
        double sum = 0.0;
        for (int t = 0; t < e[0].length; t++)
        {
            for (int u = 0; u < e[1].length; u++)
            {
                for (int v = 0; v < e[2].length; v++)
                {
                    double inner = 0.0;
                    for (int tau = 0; tau < f[0].length; tau++)
                    {
                        for (int nu = 0; nu < f[1].length; nu++)
                        {
                            for (int phi = 0; phi < f[2].length; phi++)
                            {
                                // Hermite functions on the second centre are differentiated the opposite way round
                                double sign = (tau + nu + phi) % 2 == 0 ? 1.0 : -1.0;
                                inner += sign * f[0][tau] * f[1][nu] * f[2][phi] * coulomb[t + tau][u + nu][v + phi];
                            }
                        }
                    }
                    sum += e[0][t] * e[1][u] * e[2][v] * inner;
                }
            }
        }
        return 2.0 * Math.pow(Math.PI, 2.5) / (p * q * Math.sqrt(p + q)) * sum;
    }
}
