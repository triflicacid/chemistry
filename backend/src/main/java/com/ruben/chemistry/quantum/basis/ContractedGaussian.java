package com.ruben.chemistry.quantum.basis;

import com.ruben.chemistry.space.Vec3;

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
