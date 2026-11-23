package com.ruben.chemistry.quantum.basis;

/**
 * One Gaussian term of an effective core potential channel
 * {@code coefficient * r^(power - 2) * exp(-exponent r^2)} as a function of the distance {@code r} from the nucleus.
 *
 * @param power the integer {@code n} so the radial factor is {@code r^(n - 2)}
 * @param exponent the exponent of the Gaussian, in inverse square bohr
 * @param coefficient the coefficient of the term, in hartree
 */
public record EcpTerm(int power, double exponent, double coefficient)
{
    public EcpTerm
    {
        if (!(exponent > 0.0) || !Double.isFinite(exponent))
        {
            throw new IllegalArgumentException("exponent must be positive and finite, was " + exponent);
        }
        if (!Double.isFinite(coefficient))
        {
            throw new IllegalArgumentException("coefficient must be finite, was " + coefficient);
        }
    }
}
