package com.ruben.chemistry.quantum.basis;

import java.util.List;

/**
 * One angular-momentum channel of an effective core potential: a sum of radial Gaussian terms.
 * <p>
 * The channel with the highest angular momentum {@code L} is the local part, which acts on every electron.
 * Each other channel {@code l < L} is a semilocal correction, {@code U_l - U_L}, that acts only on the part of an
 * electron's wavefunction with angular momentum {@code l}.
 *
 * @param angularMomentum the angular momentum {@code l} the channel corrects, or {@code L} for the local part
 * @param terms the Gaussian terms
 */
public record EcpChannel(int angularMomentum, List<EcpTerm> terms)
{
    public EcpChannel
    {
        if (angularMomentum < 0)
        {
            throw new IllegalArgumentException("angular momentum must not be negative, was " + angularMomentum);
        }
        if (terms.isEmpty())
        {
            throw new IllegalArgumentException("a channel needs at least one term");
        }
        terms = List.copyOf(terms);
    }
}
