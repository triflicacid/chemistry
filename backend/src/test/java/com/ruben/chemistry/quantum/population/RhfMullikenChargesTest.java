package com.ruben.chemistry.quantum.population;

import com.ruben.chemistry.charges.ChargeConvention;
import com.ruben.chemistry.charges.PartialCharges;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Structure3D;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RhfMullikenChargesTest
{
    private static final RhfMullikenCharges MODEL = new RhfMullikenCharges(BasisLibrary.cep31g());

    private static Structure3D structure(String smiles)
    {
        return Embedder.embed(Molecule.fromSmiles(smiles));
    }

    @Test
    void should_report_the_mulliken_convention()
    {
        assertThat(MODEL.convention()).isEqualTo(ChargeConvention.MULLIKEN);
        assertThat(MODEL.calculate(structure("[H][H]"), 0).convention()).isEqualTo(ChargeConvention.MULLIKEN);
    }

    @Test
    void should_make_water_polar_with_equal_hydrogens_and_a_total_of_zero()
    {
        Structure3D water = structure("O");

        PartialCharges charges = MODEL.calculate(water, 0);

        int oxygen = indexOf(water, "O");
        assertThat(charges.bySite()).hasSize(3);
        assertThat(charges.total()).isCloseTo(0.0, within(1e-8));
        assertThat(charges.bySite().get(oxygen)).isNegative();
        for (int site = 0; site < 3; site++)
        {
            if (site != oxygen)
            {
                assertThat(charges.bySite().get(site)).isPositive();
            }
        }
    }

    @Test
    void should_give_an_ammonium_ion_a_total_charge_of_one()
    {
        PartialCharges charges = MODEL.calculate(structure("[NH4+]"), 0);

        assertThat(charges.total()).isCloseTo(1.0, within(1e-8));
    }

    @Test
    void should_converge_for_triflic_acid_whose_core_hamiltonian_guess_never_settles()
    {
        Structure3D acid = structure("OS(=O)(=O)C(F)(F)F");

        PartialCharges charges = MODEL.calculate(acid, 0);

        assertThat(charges.total()).isCloseTo(0.0, within(1e-6));
        assertThat(charges.bySite().get(indexOf(acid, "S"))).isPositive();
        assertThat(charges.bySite().get(indexOf(acid, "F"))).isNegative();
    }

    @Test
    void should_make_methane_nearly_apolar_compared_with_water()
    {
        PartialCharges methane = MODEL.calculate(structure("C"), 0);
        PartialCharges water = MODEL.calculate(structure("O"), 0);

        double methaneSpread = methane.bySite().stream().mapToDouble(Math::abs).max().orElseThrow();
        double waterSpread = water.bySite().stream().mapToDouble(Math::abs).max().orElseThrow();
        assertThat(methaneSpread).isLessThan(waterSpread);
    }

    @Test
    void should_reject_an_open_shell_molecule_and_an_unsupported_element()
    {
        assertThatThrownBy(() -> MODEL.calculate(structure("[CH3]"), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MODEL.calculate(structure("[Uuo]"), 0)).isInstanceOf(RuntimeException.class);
    }

    private static int indexOf(Structure3D structure, String element)
    {
        return structure.sites().stream().filter(site -> site.element().equals(element)).findFirst().orElseThrow().index();
    }
}
