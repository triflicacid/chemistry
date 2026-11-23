package com.ruben.chemistry.quantum.integrals;

import com.ruben.chemistry.quantum.basis.ContractedGaussian;

/**
 * The product of one unnormalised Cartesian primitive from each of two basis functions, written as a sum of Hermite Gaussians.
 *
 * @param exponent the total exponent {@code p}, the sum of the two primitives' exponents
 * @param centre the x, y and z of the point the Hermite Gaussians sit on, in bohr
 * @param expansion for each axis, the coefficients {@code E_t} for {@code t} from 0 to the sum of the two powers on that axis
 * @param order the sum of the two functions' total angular momenta, the largest {@code t + u + v} that occurs
 */
record HermitePair(double exponent, double[] centre, double[][] expansion, int order)
{
    /**
     * @param a the function the first primitive belongs to
     * @param i the index of the first primitive within {@code a}
     * @param b the function the second primitive belongs to
     * @param j the index of the second primitive within {@code b}
     * @return the Hermite expansion of the product of {@code a_i} and {@code b_j}
     */
    static HermitePair of(ContractedGaussian a, int i, ContractedGaussian b, int j)
    {
        double exponentA = a.exponent(i);
        double exponentB = b.exponent(j);
        double p = exponentA + exponentB;
        int[] powersA = {a.angular().lx(), a.angular().ly(), a.angular().lz()};
        int[] powersB = {b.angular().lx(), b.angular().ly(), b.angular().lz()};
        double[] centreA = {a.centre().x(), a.centre().y(), a.centre().z()};
        double[] centreB = {b.centre().x(), b.centre().y(), b.centre().z()};

        double[] centre = new double[3];
        double[][] expansion = new double[3][];
        int order = 0;
        for (int axis = 0; axis < 3; axis++)
        {
            // the product of two Gaussians is centred at the exponent-weighted mean of their centres
            centre[axis] = (exponentA * centreA[axis] + exponentB * centreB[axis]) / p;
            int top = powersA[axis] + powersB[axis];
            order += top;
            expansion[axis] = new double[top + 1];
            for (int t = 0; t <= top; t++)
            {
                expansion[axis][t] = HermiteExpansion.coefficient(powersA[axis], powersB[axis], t, centreA[axis] - centreB[axis], exponentA, exponentB);
            }
        }
        return new HermitePair(p, centre, expansion, order);
    }
}
