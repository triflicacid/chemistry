package com.ruben.chemistry.quantum.basis;

import com.ruben.chemistry.space.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A basis function: a fixed combination of Cartesian Gaussians sharing one centre and one set of powers of
 * x, y and z, scaled so that its overlap with itself is one.
 */
public final class ContractedGaussian
{
    private final Vec3 centre;
    private final AngularMomentum angular;
    private final double[] exponents;
    private final double[] coefficients;

    /**
     * @param centre the position of the function, in bohr
     * @param angular the powers of x, y and z
     * @param primitives the terms, each weighted by the coefficient tabulated for a normalised primitive
     * @throws IllegalArgumentException if there are no primitives, or their combination has no norm
     */
    public ContractedGaussian(Vec3 centre, AngularMomentum angular, List<GaussianPrimitive> primitives)
    {
        this.centre = Objects.requireNonNull(centre);
        this.angular = Objects.requireNonNull(angular);
        if (primitives.isEmpty())
        {
            throw new IllegalArgumentException("a contracted Gaussian needs at least one primitive");
        }
        int count = primitives.size();
        exponents = new double[count];
        coefficients = new double[count];
        for (int i = 0; i < count; i++)
        {
            GaussianPrimitive primitive = primitives.get(i);
            exponents[i] = primitive.exponent();
            coefficients[i] = primitive.coefficient() * primitiveNormalisation(primitive.exponent(), angular);
        }

        double selfOverlap = 0.0;
        for (int i = 0; i < count; i++)
        {
            for (int j = 0; j < count; j++)
            {
                selfOverlap += coefficients[i] * coefficients[j] * sameCentreOverlap(exponents[i], exponents[j], angular);
            }
        }
        if (!(selfOverlap > 0.0) || !Double.isFinite(selfOverlap))
        {
            throw new IllegalArgumentException("primitives combine to a function with no norm");
        }
        double scale = 1.0 / Math.sqrt(selfOverlap);
        for (int i = 0; i < count; i++)
        {
            coefficients[i] *= scale;
        }
    }

    /**
     * @return the position of the function, in bohr
     */
    public Vec3 centre()
    {
        return centre;
    }

    /**
     * @return the powers of x, y and z
     */
    public AngularMomentum angular()
    {
        return angular;
    }

    /**
     * @return the number of primitives
     */
    public int size()
    {
        return exponents.length;
    }

    /**
     * @param index the primitive's index
     * @return its exponent
     */
    public double exponent(int index)
    {
        return exponents[index];
    }

    /**
     * @param index the primitive's index
     * @return the weight of the unnormalised Cartesian Gaussian {@code x^lx y^ly z^lz exp(-a r^2)}
     */
    public double coefficient(int index)
    {
        return coefficients[index];
    }

    /**
     * @param x the x coordinate of a point, in bohr
     * @param y the y coordinate of the point, in bohr
     * @param z the z coordinate of the point, in bohr
     * @return the value of the function at the point
     */
    public double valueAt(double x, double y, double z)
    {
        double dx = x - centre.x();
        double dy = y - centre.y();
        double dz = z - centre.z();
        double radialSquared = dx * dx + dy * dy + dz * dz;
        double sum = 0.0;
        for (int i = 0; i < exponents.length; i++)
        {
            sum += coefficients[i] * Math.exp(-exponents[i] * radialSquared);
        }
        return sum * Math.pow(dx, angular.lx()) * Math.pow(dy, angular.ly()) * Math.pow(dz, angular.lz());
    }

    /** How many numbers {@link #derivativesAt} fills in: the value, three first derivatives and six second derivatives. */
    public static final int DERIVATIVES = 10;

    /**
     * Finds the value of the function at a point together with its first and second derivatives.
     *
     * @param x the x coordinate of a point, in bohr
     * @param y the y coordinate of the point, in bohr
     * @param z the z coordinate of the point, in bohr
     * @param into receives, in this order: the value, then the derivatives with respect to x, y and z, then those with respect to xx, yy, zz, xy, xz and yz
     * @throws IllegalArgumentException if {@code into} has fewer than {@link #DERIVATIVES} places
     */
    public void derivativesAt(double x, double y, double z, double[] into)
    {
        if (into.length < DERIVATIVES)
        {
            throw new IllegalArgumentException("expected room for " + DERIVATIVES + " numbers, but got " + into.length);
        }

        double dx = x - centre.x();
        double dy = y - centre.y();
        double dz = z - centre.z();
        double[] fx = new double[3];
        double[] fy = new double[3];
        double[] fz = new double[3];
        Arrays.fill(into, 0, DERIVATIVES, 0.0);
        for (int i = 0; i < exponents.length; i++)
        {
            double a = exponents[i];
            along(dx, angular.lx(), a, fx);
            along(dy, angular.ly(), a, fy);
            along(dz, angular.lz(), a, fz);
            double c = coefficients[i];
            into[0] += c * fx[0] * fy[0] * fz[0];
            into[1] += c * fx[1] * fy[0] * fz[0];
            into[2] += c * fx[0] * fy[1] * fz[0];
            into[3] += c * fx[0] * fy[0] * fz[1];
            into[4] += c * fx[2] * fy[0] * fz[0];
            into[5] += c * fx[0] * fy[2] * fz[0];
            into[6] += c * fx[0] * fy[0] * fz[2];
            into[7] += c * fx[1] * fy[1] * fz[0];
            into[8] += c * fx[1] * fy[0] * fz[1];
            into[9] += c * fx[0] * fy[1] * fz[1];
        }
    }

    /**
     * Finds one coordinate's part of a primitive: {@code d^l exp(-a d^2)} and its first two derivatives.
     *
     * @param d the distance from the centre along the axis, in bohr
     * @param l the power of the distance
     * @param a the exponent
     * @param into receives the value, the first derivative and the second
     */
    private static void along(double d, int l, double a, double[] into)
    {
        double decay = Math.exp(-a * d * d);
        into[0] = power(d, l) * decay;
        into[1] = (l * power(d, l - 1) - 2.0 * a * power(d, l + 1)) * decay;
        into[2] = (l * (l - 1) * power(d, l - 2) - 2.0 * a * (2 * l + 1) * power(d, l) + 4.0 * a * a * power(d, l + 2)) * decay;
    }

    /**
     * @param base a number
     * @param exponent a whole number, which is negative only where the term it belongs to is multiplied by zero
     * @return the base to that power, or zero for a negative power
     */
    private static double power(double base, int exponent)
    {
        return exponent < 0 ? 0.0 : Math.pow(base, exponent);
    }

    /**
     * @param exponent the exponent of a primitive
     * @param angular its powers of x, y and z
     * @return the factor that makes the primitive {@code x^lx y^ly z^lz exp(-a r^2)} have unit self-overlap
     */
    private static double primitiveNormalisation(double exponent, AngularMomentum angular)
    {
        return 1.0 / Math.sqrt(sameCentreOverlap(exponent, exponent, angular));
    }

    /**
     * @param a the exponent of one primitive
     * @param b the exponent of the other, on the same centre with the same powers
     * @param angular the powers of x, y and z of both
     * @return the overlap of the two unnormalised primitives
     */
    private static double sameCentreOverlap(double a, double b, AngularMomentum angular)
    {
        double p = a + b;
        double overlap = Math.pow(Math.PI / p, 1.5);
        overlap *= oddDoubleFactorial(angular.lx()) / Math.pow(2.0 * p, angular.lx());
        overlap *= oddDoubleFactorial(angular.ly()) / Math.pow(2.0 * p, angular.ly());
        overlap *= oddDoubleFactorial(angular.lz()) / Math.pow(2.0 * p, angular.lz());
        return overlap;
    }

    /**
     * @param l a non-negative integer
     * @return {@code (2l - 1)!!}, which is one for {@code l = 0}
     */
    private static double oddDoubleFactorial(int l)
    {
        double product = 1.0;
        for (int k = 2 * l - 1; k > 1; k -= 2)
        {
            product *= k;
        }
        return product;
    }
}
