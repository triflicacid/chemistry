package com.ruben.chemistry.forcefield;

import com.ruben.chemistry.charges.ChargeConvention;

public enum ModelType
{
    FULL("AtomsCoeffs_FullE.txt", ChargeConvention.MULLIKEN),
    ECP("AtomsCoeffs_ECP.txt", ChargeConvention.MULLIKEN);

    private final String resource;
    private final ChargeConvention chargeConvention;

    ModelType(String resource, ChargeConvention chargeConvention)
    {
        this.resource = resource;
        this.chargeConvention = chargeConvention;
    }

    public ChargeConvention chargeConvention()
    {
        return chargeConvention;
    }

    String resource()
    {
        return resource;
    }
}
