package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.quantum.population.RhfMullikenCharges;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PlaneSymmetryTest
{
    private static final BasisLibrary LIBRARY = BasisLibrary.cep31g();

    private static Structure3D structure(String smiles)
    {
        return Embedder.embed(Molecule.fromSmiles(smiles));
    }

    private static List<Vec3> positions(Structure3D structure)
    {
        return structure.sites().stream().map(site -> structure.conformers().getFirst().positions().get(site.index())).toList();
    }

    @Test
    void should_find_the_plane_of_benzene_and_that_its_atoms_lie_in_it()
    {
        PlaneSymmetry.Plane plane = PlaneSymmetry.fit(positions(structure("c1ccccc1")));

        assertThat(plane).isNotNull();
        assertThat(plane.flatness()).isLessThan(0.05);
        assertThat(plane.flat()).isTrue();
        assertThat(plane.normal().length()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void should_not_call_ethane_flat()
    {
        PlaneSymmetry.Plane plane = PlaneSymmetry.fit(positions(structure("CC")));

        assertThat(plane).isNotNull();
        assertThat(plane.flat()).isFalse();
    }

    @Test
    void should_find_no_plane_for_atoms_on_a_line_or_fewer_than_three()
    {
        assertThat(PlaneSymmetry.fit(List.of(new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(2, 0, 0)))).isNull();
        assertThat(PlaneSymmetry.fit(List.of(new Vec3(0, 0, 0), new Vec3(1, 0, 0)))).isNull();
    }

    @Test
    void should_find_the_pi_orbitals_of_benzene_to_be_pi_and_the_rest_sigma()
    {
        Structure3D benzene = structure("c1ccccc1");
        MolecularOrbitals orbitals = MolecularOrbitals.of(new RhfMullikenCharges(LIBRARY).run(benzene, 0));
        MolecularOrbitals.Channel channel = orbitals.channels().getFirst();
        Vec3 normal = PlaneSymmetry.fit(positions(benzene)).normal();

        double[] pi = new double[channel.energies().length];
        for (int orbital = 0; orbital < pi.length; orbital++)
        {
            pi[orbital] = PlaneSymmetry.piShare(orbitals, channel, orbital, normal);
        }

        // the three filled pi orbitals are the highest filled one, the one with it, and one a good way below
        int homo = channel.homo();
        assertThat(pi[homo]).isGreaterThan(0.95);
        assertThat(pi[homo - 1]).isGreaterThan(0.95);
        long piFilled = java.util.stream.IntStream.rangeClosed(0, homo).filter(orbital -> pi[orbital] > 0.5).count();
        assertThat(piFilled).isEqualTo(3);
        // every other filled orbital is sigma
        assertThat(java.util.stream.IntStream.rangeClosed(0, homo).filter(orbital -> pi[orbital] <= 0.5).mapToDouble(orbital -> pi[orbital]).max().orElse(0.0)).isLessThan(0.05);
        // and six pi electrons fill three orbitals, so the pi shares of the filled orbitals add up to three
        assertThat(java.util.stream.IntStream.rangeClosed(0, homo).mapToDouble(orbital -> pi[orbital]).sum()).isCloseTo(3.0, within(0.1));
    }

    @Test
    void should_find_the_highest_filled_orbital_of_water_to_be_pi_and_the_lowest_to_be_sigma()
    {
        Structure3D water = structure("O");
        MolecularOrbitals orbitals = MolecularOrbitals.of(new RhfMullikenCharges(LIBRARY).run(water, 0));
        MolecularOrbitals.Channel channel = orbitals.channels().getFirst();
        Vec3 normal = PlaneSymmetry.fit(positions(water)).normal();

        assertThat(PlaneSymmetry.piShare(orbitals, channel, channel.homo(), normal)).isGreaterThan(0.9);
        assertThat(PlaneSymmetry.piShare(orbitals, channel, 0, normal)).isLessThan(0.05);
    }

    @Test
    void should_keep_every_pi_share_between_zero_and_one()
    {
        Structure3D benzene = structure("c1ccccc1");
        MolecularOrbitals orbitals = MolecularOrbitals.of(new RhfMullikenCharges(LIBRARY).run(benzene, 0));
        MolecularOrbitals.Channel channel = orbitals.channels().getFirst();
        Vec3 normal = PlaneSymmetry.fit(positions(benzene)).normal();

        for (int orbital = 0; orbital < channel.energies().length; orbital++)
        {
            assertThat(PlaneSymmetry.piShare(orbitals, channel, orbital, normal)).isBetween(0.0, 1.0);
        }
    }

    @Test
    void should_refuse_an_orbital_the_set_does_not_have()
    {
        Structure3D water = structure("O");
        MolecularOrbitals orbitals = MolecularOrbitals.of(new RhfMullikenCharges(LIBRARY).run(water, 0));

        assertThatThrownBy(() -> PlaneSymmetry.piShare(orbitals, orbitals.channels().getFirst(), 99, new Vec3(0, 0, 1))).isInstanceOf(IllegalArgumentException.class);
    }
}
