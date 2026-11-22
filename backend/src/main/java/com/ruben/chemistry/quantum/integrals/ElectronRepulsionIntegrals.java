package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.quantum.basis.ContractedGaussian;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Coulomb repulsion between two electrons, each in a product of two Cartesian Gaussian basis functions, for any
 * angular momentum. Lengths are in bohr and energies in hartree.
 */
public final class ElectronRepulsionIntegrals
{
    /** An integral whose Schwarz bound is below this, in hartree, is taken to be zero. */
    static final double SCREENING_THRESHOLD = 1e-12;

    /** A product of two primitives whose weighted Hermite expansion is smaller than this everywhere is left out. */
    private static final double NEGLIGIBLE_PRODUCT = 1e-15;

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
                    HermitePair pair = HermitePair.of(a, i, b, j);
                    double weight = a.coefficient(i) * b.coefficient(j);
                    // a product of primitives far apart or very unlike each other is nothing next to the rest
                    if (Math.abs(weight) * pair.magnitude() >= NEGLIGIBLE_PRODUCT)
                    {
                        weights[pairs.size()] = weight;
                        pairs.add(pair);
                    }
                }
            }
            return new ContractedPair(pairs, Arrays.copyOf(weights, pairs.size()));
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
     * Computed on every available core, leaving out the integrals that Schwarz screening shows cannot reach
     * {@link #SCREENING_THRESHOLD}.
     *
     * @param basis the basis functions
     * @return every electron-repulsion integral over the basis
     */
    public static ElectronRepulsionTensor tensor(List<ContractedGaussian> basis)
    {
        int n = basis.size();
        double[] values = new double[ElectronRepulsionTensor.length(n)];

        // the pair (i, j) with i >= j is at position i (i + 1) / 2 + j, which is the order they are added in
        List<ContractedPair> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            for (int j = 0; j <= i; j++)
            {
                pairs.add(ContractedPair.of(basis.get(i), basis.get(j)));
            }
        }

        // (ab|cd) cannot be larger than sqrt((ab|ab) (cd|cd)), so a quartet whose bound is negligible is left at zero
        double[] bounds = new double[pairs.size()];
        IntStream.range(0, pairs.size()).parallel()
                .forEach(pair -> bounds[pair] = Math.sqrt(Math.max(0.0, contract(pairs.get(pair), pairs.get(pair)))));

        // each quartet has a slot of its own, so no two tasks write the same one
        IntStream.range(0, pairs.size()).parallel().forEach(first ->
        {
            for (int second = 0; second <= first; second++)
            {
                if (bounds[first] * bounds[second] < SCREENING_THRESHOLD)
                {
                    continue;
                }

                values[ElectronRepulsionTensor.slot(first, second)] = contract(pairs.get(first), pairs.get(second));
            }
        });
        return new ElectronRepulsionTensor(n, values);
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
        int size = left.order() + right.order() + 1;
        double[] coulomb = HermiteCoulomb.flat(
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
                                inner += sign * f[0][tau] * f[1][nu] * f[2][phi] * coulomb[((t + tau) * size + u + nu) * size + v + phi];
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
