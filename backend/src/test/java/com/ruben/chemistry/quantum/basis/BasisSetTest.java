package com.ruben.chemistry.quantum.basis;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.quantum.Units;
import com.ruben.chemistry.quantum.integrals.OneElectronIntegrals;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.quantum.linalg.SymmetricEigen;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BasisSetTest
{
    private static Structure3D water()
    {
        return Embedder.embed(Molecule.fromSmiles("O"));
    }

    @Test
    void should_give_water_twelve_functions__eight_on_oxygen_and_two_on_each_hydrogen()
    {
        Structure3D water = water();

        BasisSet basis = BasisSet.build(water, 0, BasisLibrary.cep31g());

        assertThat(basis.size()).isEqualTo(12);
        for (Structure3D.Site site : water.sites())
        {
            long count = basis.siteOfFunction().stream().filter(index -> index == site.index()).count();
            assertThat(count).as(site.element() + " " + site.index()).isEqualTo(site.element().equals("O") ? 8 : 2);
        }
    }

    @Test
    void should_centre_every_function_on_its_atom_in_bohr()
    {
        Structure3D water = water();
        BasisSet basis = BasisSet.build(water, 0, BasisLibrary.cep31g());

        for (int f = 0; f < basis.size(); f++)
        {
            Vec3 angstrom = water.conformers().get(0).positions().get(basis.siteOfFunction().get(f));
            Vec3 centre = basis.functions().get(f).centre();
            assertThat(centre.x()).isCloseTo(Units.toBohr(angstrom.x()), within(1e-12));
            assertThat(centre.y()).isCloseTo(Units.toBohr(angstrom.y()), within(1e-12));
            assertThat(centre.z()).isCloseTo(Units.toBohr(angstrom.z()), within(1e-12));
        }
    }

    @Test
    void should_give_water_a_well_conditioned_overlap_matrix_with_unit_diagonal()
    {
        BasisSet basis = BasisSet.build(water(), 0, BasisLibrary.cep31g());

        Matrix overlap = OneElectronIntegrals.overlapMatrix(basis.functions());

        assertThat(overlap.rows()).isEqualTo(12);
        assertThat(overlap.isSymmetric(1e-13)).isTrue();
        for (int i = 0; i < 12; i++)
        {
            assertThat(overlap.get(i, i)).isCloseTo(1.0, within(1e-12));
        }
        assertThat(SymmetricEigen.of(overlap).values()[0]).isGreaterThan(1e-3);
    }

    @Test
    void should_reject_an_element_the_library_does_not_have()
    {
        BasisLibrary hydrogenOnly = BasisLibrary.parse("""
                H     0
                S    1   1.00
                      1.0              1.0
                ****
                """);

        assertThatThrownBy(() -> BasisSet.build(water(), 0, hydrogenOnly)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("O");
    }
}
