package com.ruben.chemistry.charges;

import java.util.List;
import java.util.Objects;

/**
 * The partial charge of every site of a {@link com.ruben.chemistry.space.Structure3D}.
 *
 * @param convention what the charges mean
 * @param bySite the charge of each site, in site order
 */
public record PartialCharges(ChargeConvention convention, List<Double> bySite)
{
    public PartialCharges
    {
        Objects.requireNonNull(convention);
        bySite = List.copyOf(bySite);
        for (double charge : bySite)
        {
            if (!Double.isFinite(charge))
            {
                throw new IllegalArgumentException("partial charge must be finite, was " + charge);
            }
        }
    }

    /**
     * @return the sum of the charges
     */
    public double total()
    {
        return bySite.stream().mapToDouble(Double::doubleValue).sum();
    }
}
