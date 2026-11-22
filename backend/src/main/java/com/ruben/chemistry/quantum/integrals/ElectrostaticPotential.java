package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.linalg.Matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * The electrostatic potential of a molecule.
 * <p>
 * Each pair of basis functions, weighted by the density between them, is a Gaussian cloud of charge that is a sum of Hermite Gaussians, and
 * how much it contributes at a point is the same Hermite Coulomb integral that the nuclear attraction of the calculation uses.
 */
public final class ElectrostaticPotential
{
    /** A cloud of charge's share is left out if it is below this, which is far below anything that could be seen. */
    private static final double NEGLIGIBLE = 1.0e-13;

    /** How close to a nucleus a point may be before the potential there is taken to be undefined, in bohr. */
    private static final double AT_NUCLEUS = 1.0e-6;

    /**
     * One cloud of charge: a Hermite Gaussian expansion of the product of two primitives, weighted by the density between their functions.
     *
     * @param exponent the total exponent of the pair
     * @param centre where the Gaussians sit, in bohr
     * @param order the largest sum of the three indices that occurs
     * @param coefficients the weight of each Hermite Gaussian, with the factor that turns the Hermite Coulomb integral into the integral over the cloud,
     *     at {@code (t * size + u) * size + v} where {@code size} is {@code order + 1}
     */
    private record Cloud(double exponent, double[] centre, int order, double[] coefficients)
    {
    }

    private final List<Cloud> clouds = new ArrayList<>();
    private final List<PointCharge> nuclei;

    /**
     * @param basis the basis functions, placed on the molecule, in bohr
     * @param density the density matrix of all the electrons over those functions
     * @param nuclei the nuclei as the point charges the electrons see, in bohr
     * @throws IllegalArgumentException if the density matrix is not as large as the basis
     */
    public ElectrostaticPotential(List<ContractedGaussian> basis, Matrix density, List<PointCharge> nuclei)
    {
        if (density.rows() != basis.size() || density.columns() != basis.size())
        {
            throw new IllegalArgumentException("a density matrix of " + density.rows() + " by " + density.columns() + " does not fit " + basis.size() + " basis functions");
        }

        this.nuclei = List.copyOf(nuclei);
        for (int a = 0; a < basis.size(); a++)
        {
            for (int b = a; b < basis.size(); b++)
            {
                // the pair is counted once in each order, so off the diagonal it counts twice
                double weight = density.get(a, b) * (a == b ? 1.0 : 2.0);
                add(basis.get(a), basis.get(b), weight);
            }
        }
    }

    /**
     * @param a one basis function
     * @param b another
     * @param weight how much electron density there is between them
     */
    private void add(ContractedGaussian a, ContractedGaussian b, double weight)
    {
        for (int i = 0; i < a.size(); i++)
        {
            for (int j = 0; j < b.size(); j++)
            {
                double share = weight * a.coefficient(i) * b.coefficient(j);
                HermitePair pair = HermitePair.of(a, i, b, j);
                double factor = share * 2.0 * Math.PI / pair.exponent();
                if (Math.abs(factor) * pair.magnitude() < NEGLIGIBLE)
                {
                    continue;
                }

                double[][] expansion = pair.expansion();
                int size = pair.order() + 1;
                double[] coefficients = new double[size * size * size];
                for (int t = 0; t < expansion[0].length; t++)
                {
                    for (int u = 0; u < expansion[1].length; u++)
                    {
                        for (int v = 0; v < expansion[2].length; v++)
                        {
                            coefficients[(t * size + u) * size + v] = factor * expansion[0][t] * expansion[1][u] * expansion[2][v];
                        }
                    }
                }
                clouds.add(new Cloud(pair.exponent(), pair.centre(), pair.order(), coefficients));
            }
        }
    }

    /**
     * @return how many clouds of charge a point's potential is summed over
     */
    public int clouds()
    {
        return clouds.size();
    }

    /**
     * @param x the x coordinate of a point, in bohr
     * @param y the y coordinate of the point, in bohr
     * @param z the z coordinate of the point, in bohr
     * @return the potential there, in hartree per elementary charge, which is positive where a positive charge would be repelled; undefined
     *     at a nucleus, where it is infinite
     */
    public double at(double x, double y, double z)
    {
        double electrons = 0.0;
        for (Cloud cloud : clouds)
        {
            double[] r = HermiteCoulomb.flat(cloud.order(), cloud.exponent(), cloud.centre()[0] - x, cloud.centre()[1] - y, cloud.centre()[2] - z);
            double[] coefficients = cloud.coefficients();
            int size = cloud.order() + 1;
            for (int t = 0; t < size; t++)
            {
                for (int u = 0; u + t < size; u++)
                {
                    for (int v = 0; v + u + t < size; v++)
                    {
                        int i = (t * size + u) * size + v;
                        electrons += coefficients[i] * r[i];
                    }
                }
            }
        }

        double nuclear = 0.0;
        for (PointCharge nucleus : nuclei)
        {
            double distance = Math.sqrt(Math.pow(nucleus.position().x() - x, 2) + Math.pow(nucleus.position().y() - y, 2) + Math.pow(nucleus.position().z() - z, 2));
            if (distance > AT_NUCLEUS)
            {
                nuclear += nucleus.charge() / distance;
            }
        }
        return nuclear - electrons;
    }
}
