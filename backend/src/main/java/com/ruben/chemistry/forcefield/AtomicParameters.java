package com.ruben.chemistry.forcefield;

import java.util.Objects;

import static com.ruben.chemistry.forcefield.Validation.finite;

/**
 * The fitted Gaussian expansion for one element.
 *
 * @param element the element symbol
 * @param atomicNumber the atomic number
 * @param effectiveNuclearCharge the nuclear charge the electrons see, less any core removed by the ECP
 * @param coefficients the Gaussian coefficients
 * @param exponents the Gaussian exponents, one per coefficient
 */
public record AtomicParameters(String element, int atomicNumber, double effectiveNuclearCharge, double[] coefficients, double[] exponents)
{
    public AtomicParameters
    {
        Objects.requireNonNull(element);
        coefficients = coefficients.clone();
        exponents = exponents.clone();
        if (coefficients.length == 0 || coefficients.length != exponents.length)
        {
            throw new IllegalArgumentException("invalid gaussian parameter arrays");
        }
        finite(effectiveNuclearCharge, "effective nuclear charge");
        for (double value : coefficients)
        {
            finite(value, "gaussian coefficient");
        }
        for (double value : exponents)
        {
            if (!(value > 0.0) || !Double.isFinite(value))
            {
                throw new IllegalArgumentException("gaussian exponents must be positive");
            }
        }
    }

    /**
     * @return a defensive copy of the Gaussian coefficients
     */
    @Override
    public double[] coefficients()
    {
        return coefficients.clone();
    }

    /**
     * @return a defensive copy of the Gaussian exponents
     */
    @Override
    public double[] exponents()
    {
        return exponents.clone();
    }
}
