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

class UhfMullikenChargesTest
{
    private static final BasisLibrary LIBRARY = BasisLibrary.cep31g();

    private static Structure3D structure(String smiles)
    {
        return Embedder.embed(Molecule.fromSmiles(smiles));
    }

    private static double sum(double[] values)
    {
        double total = 0.0;
        for (double value : values)
        {
            total += value;
        }
        return total;
    }

    @Test
    void should_report_the_mulliken_convention_and_its_name()
    {
        UhfMullikenCharges model = new UhfMullikenCharges(LIBRARY, 2);

        assertThat(model.convention()).isEqualTo(ChargeConvention.MULLIKEN);
        assertThat(model.name()).isEqualTo("uhf-mulliken");
        assertThat(model.calculate(structure("[H]"), 0).convention()).isEqualTo(ChargeConvention.MULLIKEN);
    }

    @Test
    void should_give_triplet_oxygen_no_charge_and_one_unpaired_electron_on_each_atom()
    {
        UhfMullikenCharges.Calculation oxygen = new UhfMullikenCharges(LIBRARY, 3).run(structure("O=O"), 0);

        assertThat(oxygen.charges()[0]).isCloseTo(0.0, within(1e-8));
        assertThat(oxygen.charges()[1]).isCloseTo(0.0, within(1e-8));
        assertThat(oxygen.spinPopulations()[0]).isCloseTo(1.0, within(1e-8));
        assertThat(oxygen.spinPopulations()[1]).isCloseTo(1.0, within(1e-8));
        assertThat(oxygen.result().alphaElectrons() - oxygen.result().betaElectrons()).isEqualTo(2);
        assertThat(oxygen.result().spinSquared()).isBetween(2.0, 2.1);
    }

    @Test
    void should_not_end_above_the_restricted_open_shell_energy_for_triplet_oxygen()
    {
        double unrestricted = new UhfMullikenCharges(LIBRARY, 3).run(structure("O=O"), 0).result().totalEnergy();
        double restricted = new RohfMullikenCharges(LIBRARY, 3).run(structure("O=O"), 0).result().totalEnergy();

        // starting the unrestricted iteration from the core Hamiltonian instead ends 0.3 hartree higher, on an excited solution
        assertThat(unrestricted).isLessThan(restricted);
    }

    @Test
    void should_give_a_closed_shell_molecule_the_charges_of_the_restricted_model()
    {
        Structure3D water = structure("O");

        PartialCharges unrestricted = new UhfMullikenCharges(LIBRARY, 1).calculate(water, 0);
        PartialCharges restricted = new RhfMullikenCharges(LIBRARY).calculate(water, 0);

        for (int site = 0; site < 3; site++)
        {
            assertThat(unrestricted.bySite().get(site)).isCloseTo(restricted.bySite().get(site), within(1e-7));
        }
    }

    @Test
    void should_make_the_spin_populations_of_the_methyl_radical_sum_to_one_unpaired_electron()
    {
        Structure3D methyl = structure("[CH3]");
        UhfMullikenCharges.Calculation radical = new UhfMullikenCharges(LIBRARY, 2).run(methyl, 0);

        int carbon = methyl.sites().stream().filter(site -> site.element().equals("C")).findFirst().orElseThrow().index();
        assertThat(sum(radical.spinPopulations())).isCloseTo(1.0, within(1e-8));
        assertThat(sum(radical.charges())).isCloseTo(0.0, within(1e-8));
        assertThat(radical.spinPopulations()[carbon]).isGreaterThan(0.8);
        for (int site = 0; site < radical.spinPopulations().length; site++)
        {
            if (site != carbon)
            {
                // spin polarisation puts a little opposite spin on the hydrogens
                assertThat(radical.spinPopulations()[site]).isNegative();
            }
        }
    }

    @Test
    void should_give_a_cation_a_total_charge_of_one()
    {
        UhfMullikenCharges.Calculation cation = new UhfMullikenCharges(LIBRARY, 2).run(structure("[O+]"), 0);

        assertThat(sum(cation.charges())).isCloseTo(1.0, within(1e-8));
    }

    @Test
    void should_reject_a_multiplicity_the_electron_count_cannot_give()
    {
        Structure3D water = structure("O");

        assertThatThrownBy(() -> new UhfMullikenCharges(LIBRARY, 2).calculate(water, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UhfMullikenCharges(LIBRARY, 11).calculate(water, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UhfMullikenCharges(LIBRARY, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
