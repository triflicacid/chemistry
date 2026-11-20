package com.ruben.chemistry.charges;

import com.ruben.chemistry.space.Structure3D;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Uses a primary charge model where it covers every element of a structure, and otherwise gives every site its
 * formal charge, so the total still matches the molecule's charge.
 *
 * <p>The choice is made for the whole structure, never site by site, so charges from two different methods are
 * not mixed in one molecule. Fallback charges are labelled {@link ChargeConvention#FORMAL}, never as the
 * primary model's convention, so callers can tell the result is not a real calculation.
 */
public final class FallbackCharges implements PartialChargeModel
{
    private final PartialChargeModel primary;
    private final Set<String> supportedElements;

    /**
     * @param primary the model to use when it supports the whole structure
     * @param supportedElements the element symbols {@code primary} can assign charges to
     */
    public FallbackCharges(PartialChargeModel primary, Set<String> supportedElements)
    {
        this.primary = Objects.requireNonNull(primary);
        this.supportedElements = Set.copyOf(supportedElements);
    }

    @Override
    public String name()
    {
        return primary.name() + "-or-neutral";
    }

    /**
     * @return the convention of the primary model; the convention of a particular result is
     *     {@link PartialCharges#convention()}, which is {@link ChargeConvention#FORMAL} after a fallback
     */
    @Override
    public ChargeConvention convention()
    {
        return primary.convention();
    }

    /**
     * @param structure the molecule in three dimensions
     * @return whether the primary model covers every element of {@code structure}
     */
    public boolean isSupported(Structure3D structure)
    {
        return structure.sites().stream().allMatch(site -> supportedElements.contains(site.element()));
    }

    @Override
    public PartialCharges calculate(Structure3D structure, int conformer)
    {
        if (isSupported(structure))
        {
            return primary.calculate(structure, conformer);
        }
        // TODO: replace the neutral fallback with Gasteiger or EEM charges for the elements they cover
        List<Double> formal = structure.sites().stream().map(site -> (double) site.charge()).toList();
        return new PartialCharges(ChargeConvention.FORMAL, formal);
    }
}
