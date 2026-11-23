package com.ruben.chemistry.charges;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Structure3D;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackChargesTest
{
    private static Structure3D water()
    {
        return Embedder.embed(Molecule.fromSmiles("O"));
    }

    private static PartialChargeModel primary()
    {
        return new UserSuppliedCharges(ChargeConvention.MULLIKEN, Map.of(0, -0.4));
    }

    @Test
    void should_use_the_primary_model_when_every_element_is_supported()
    {
        FallbackCharges model = new FallbackCharges(primary(), Set.of("O", "H"));

        PartialCharges charges = model.calculate(water(), 0);

        assertThat(charges.convention()).isEqualTo(ChargeConvention.MULLIKEN);
        assertThat(charges.bySite()).containsExactly(-0.4, 0.0, 0.0);
    }

    @Test
    void should_give_every_site_zero_and_label_it_formal_when_a_neutral_molecule_is_unsupported()
    {
        FallbackCharges model = new FallbackCharges(primary(), Set.of("H"));

        PartialCharges charges = model.calculate(water(), 0);

        assertThat(charges.convention()).isEqualTo(ChargeConvention.FORMAL);
        assertThat(charges.bySite()).containsExactly(0.0, 0.0, 0.0);
    }

    @Test
    void should_give_each_site_its_formal_charge_so_the_total_matches_an_ion()
    {
        Structure3D ammonium = Embedder.embed(Molecule.fromSmiles("[NH4+]"));
        FallbackCharges model = new FallbackCharges(primary(), Set.of());

        PartialCharges charges = model.calculate(ammonium, 0);

        assertThat(charges.convention()).isEqualTo(ChargeConvention.FORMAL);
        assertThat(charges.bySite()).containsExactly(1.0, 0.0, 0.0, 0.0, 0.0);
        assertThat(charges.total()).isEqualTo(1.0);
    }

    @Test
    void should_report_whether_a_structure_is_supported()
    {
        assertThat(new FallbackCharges(primary(), Set.of("O", "H")).isSupported(water())).isTrue();
        assertThat(new FallbackCharges(primary(), Set.of("H")).isSupported(water())).isFalse();
    }

    @Test
    void should_report_the_primary_convention_and_a_derived_name()
    {
        FallbackCharges model = new FallbackCharges(primary(), Set.of());

        assertThat(model.convention()).isEqualTo(ChargeConvention.MULLIKEN);
        assertThat(model.name()).isEqualTo("user-supplied-or-neutral");
    }
}
