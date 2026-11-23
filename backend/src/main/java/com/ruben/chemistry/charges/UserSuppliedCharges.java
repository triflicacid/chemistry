package com.ruben.chemistry.charges;

import com.ruben.chemistry.space.Structure3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CA {@link PartialChargeModel} where the user supplies values.
 */
public final class UserSuppliedCharges implements PartialChargeModel
{
    private final ChargeConvention convention;
    private final Map<Integer, Double> bySite;

    /**
     * @param convention what the charges mean
     * @param bySite the charge of each listed site, by site index
     * @throws IllegalArgumentException if an index is negative
     */
    public UserSuppliedCharges(ChargeConvention convention, Map<Integer, Double> bySite)
    {
        this.convention = Objects.requireNonNull(convention);
        this.bySite = Map.copyOf(bySite);
        for (int index : this.bySite.keySet())
        {
            if (index < 0)
            {
                throw new IllegalArgumentException("Site index " + index + " is negative");
            }
        }
    }

    @Override
    public String name()
    {
        return "user-supplied";
    }

    @Override
    public ChargeConvention convention()
    {
        return convention;
    }

    @Override
    public PartialCharges calculate(Structure3D structure, int conformer)
    {
        int count = structure.sites().size();
        for (int index : bySite.keySet())
        {
            if (index >= count)
            {
                throw new IllegalArgumentException("Site index " + index + " is not in a structure of " + count + " sites");
            }
        }
        List<Double> charges = new ArrayList<>();
        for (int site = 0; site < count; site++)
        {
            charges.add(bySite.getOrDefault(site, 0.0));
        }
        return new PartialCharges(convention, charges);
    }
}
