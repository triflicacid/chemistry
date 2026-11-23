package com.ruben.chemistry.charges;

import com.ruben.chemistry.space.Structure3D;

/**
 * Assigns a partial charge to every site of a {@link Structure3D}.
 */
public interface PartialChargeModel
{
    /**
     * @return a short name for the model
     */
    String name();

    /**
     * @return what the charges this model assigns mean
     */
    ChargeConvention convention();

    /**
     * @param structure the molecule in three dimensions
     * @param conformer the index of the shape to assign charges for
     * @return a charge for every site
     * @throws IllegalArgumentException if the model cannot assign charges to this structure
     */
    PartialCharges calculate(Structure3D structure, int conformer);
}
