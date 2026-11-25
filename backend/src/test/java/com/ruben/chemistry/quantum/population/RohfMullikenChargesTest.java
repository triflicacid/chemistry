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

class RohfMullikenChargesTest
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
        RohfMullikenCharges model = new RohfMullikenCharges(LIBRARY, 2);

        assertThat(model.convention()).isEqualTo(ChargeConvention.MULLIKEN);
        assertThat(model.name()).isEqualTo("rohf-mulliken");
        assertThat(model.calculate(structure("[H]"), 0).convention()).isEqualTo(ChargeConvention.MULLIKEN);
    }

    @Test
    void should_give_triplet_oxygen_no_charge_and_one_unpaired_electron_on_each_atom_in_a_pure_triplet()
    {
        RohfMullikenCharges.Calculation oxygen = new RohfMullikenCharges(LIBRARY, 3).run(structure("O=O"), 0);

        assertThat(oxygen.charges()[0]).isCloseTo(0.0, within(1e-8));
        assertThat(oxygen.charges()[1]).isCloseTo(0.0, within(1e-8));
        assertThat(oxygen.spinPopulations()[0]).isCloseTo(1.0, within(1e-8));
        assertThat(oxygen.spinPopulations()[1]).isCloseTo(1.0, within(1e-8));
        assertThat(oxygen.result().spinSquared()).isCloseTo(2.0, within(1e-8));
    }

    @Test
    void should_give_a_closed_shell_molecule_the_charges_of_the_restricted_model()
    {
        Structure3D water = structure("O");

        PartialCharges open = new RohfMullikenCharges(LIBRARY, 1).calculate(water, 0);
        PartialCharges closed = new RhfMullikenCharges(LIBRARY).calculate(water, 0);

        for (int site = 0; site < 3; site++)
        {
            assertThat(open.bySite().get(site)).isCloseTo(closed.bySite().get(site), within(1e-7));
        }
    }

    @Test
    void should_make_the_spin_populations_of_the_methyl_radical_sum_to_one_and_keep_the_spin_off_the_hydrogens()
    {
        Structure3D methyl = structure("[CH3]");
        RohfMullikenCharges.Calculation radical = new RohfMullikenCharges(LIBRARY, 2).run(methyl, 0);
        UhfMullikenCharges.Calculation unrestricted = new UhfMullikenCharges(LIBRARY, 2).run(methyl, 0);

        int carbon = methyl.sites().stream().filter(site -> site.element().equals("C")).findFirst().orElseThrow().index();
        assertThat(sum(radical.spinPopulations())).isCloseTo(1.0, within(1e-8));
        assertThat(radical.result().spinSquared()).isCloseTo(0.75, within(1e-8));
        for (int site = 0; site < radical.spinPopulations().length; site++)
        {
            if (site != carbon)
            {
                // the restricted orbitals cannot polarise the paired electrons, which the unrestricted ones do
                assertThat(radical.spinPopulations()[site]).isGreaterThan(-1e-8);
                assertThat(unrestricted.spinPopulations()[site]).isNegative();
            }
        }
    }

    @Test
    void should_never_lie_below_the_unrestricted_energy()
    {
        for (String[] entry : new String[][] {{"[CH3]", "2"}, {"[OH]", "2"}, {"[CH2]", "3"}, {"O=O", "3"}})
        {
            Structure3D structure = structure(entry[0]);
            int multiplicity = Integer.parseInt(entry[1]);

            double restricted = new RohfMullikenCharges(LIBRARY, multiplicity).run(structure, 0).result().totalEnergy();
            double unrestricted = new UhfMullikenCharges(LIBRARY, multiplicity).run(structure, 0).result().totalEnergy();

            assertThat(restricted).as(entry[0]).isGreaterThan(unrestricted - 1e-9);
        }
    }

    @Test
    void should_converge_nitric_oxide_with_the_default_settings()
    {
        RohfMullikenCharges.Calculation nitricOxide = new RohfMullikenCharges(LIBRARY, 2).run(structure("[N]=O"), 0);

        assertThat(nitricOxide.result().converged()).isTrue();
        assertThat(sum(nitricOxide.spinPopulations())).isCloseTo(1.0, within(1e-8));
    }

    @Test
    void should_reject_a_multiplicity_the_electron_count_cannot_give()
    {
        Structure3D water = structure("O");

        assertThatThrownBy(() -> new RohfMullikenCharges(LIBRARY, 2).calculate(water, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RohfMullikenCharges(LIBRARY, 11).calculate(water, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RohfMullikenCharges(LIBRARY, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
