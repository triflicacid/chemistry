package com.ruben.chemistry.quantum.basis;

import java.util.List;

/**
 * A semilocal effective core potential, which stands in for the innermost electrons of an atom.
 *
 * @param coreElectrons how many electrons the potential replaces
 * @param maxAngularMomentum the highest angular momentum {@code L} the potential has a channel for
 * @param local the local channel, with angular momentum {@code L}
 * @param projectors the semilocal channels for angular momentum {@code 0} up to {@code L - 1}, in that order
 */
public record EffectiveCorePotential(int coreElectrons, int maxAngularMomentum, EcpChannel local, List<EcpChannel> projectors)
{
    public EffectiveCorePotential
    {
        if (coreElectrons <= 0)
        {
            throw new IllegalArgumentException("a core potential replaces at least one electron, was " + coreElectrons);
        }
        if (maxAngularMomentum < 0 || local.angularMomentum() != maxAngularMomentum)
        {
            throw new IllegalArgumentException("local channel must have angular momentum " + maxAngularMomentum);
        }
        projectors = List.copyOf(projectors);
        if (projectors.size() != maxAngularMomentum)
        {
            throw new IllegalArgumentException("expected " + maxAngularMomentum + " projector channels, found " + projectors.size());
        }
        for (int l = 0; l < projectors.size(); l++)
        {
            if (projectors.get(l).angularMomentum() != l)
            {
                throw new IllegalArgumentException("projector " + l + " has angular momentum " + projectors.get(l).angularMomentum());
            }
        }
    }
}
