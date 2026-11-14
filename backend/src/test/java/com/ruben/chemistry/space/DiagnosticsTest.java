package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.PiSystem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsTest
{
    private static Diagnostics of(String smiles)
    {
        Molecule molecule = Molecule.fromSmiles(smiles);
        Structure3D structure = Embedder.embed(molecule, Embedder.Options.DEFAULT.withSeed(1));
        return Diagnostics.of(molecule, structure, 0);
    }

    @Test
    void should_measure_every_bond_and_the_angles_at_each_atom()
    {
        Diagnostics ethane = of("CC");
        assertEquals(7, ethane.lengths().size());
        // each carbon has four neighbours, so six angles
        assertEquals(12, ethane.angles().size());
        assertEquals(8, ethane.atomStrain().size());
    }

    @Test
    void should_find_little_deviation_in_a_well_embedded_molecule()
    {
        Diagnostics ethane = of("CC");
        assertTrue(ethane.lengths().stream().allMatch(length -> length.deviation() < 0.15));
        assertTrue(ethane.clashes().isEmpty());
        // ethane spins freely, so only a nearly eclipsed torsion would be wrong
        assertTrue(ethane.torsions().stream().allMatch(torsion -> torsion.deviation() < 45));
    }

    @Test
    void should_count_lone_pairs_from_the_electrons()
    {
        assertEquals(2, of("O").lonePairs().size());
        assertEquals(1, of("N").lonePairs().size());
        assertEquals(0, of("[NH4+]").lonePairs().size());
    }

    @Test
    void should_point_lone_lobes_away_from_the_neighbours()
    {
        Molecule water = Molecule.fromSmiles("O");
        Structure3D structure = Embedder.embed(water, Embedder.Options.DEFAULT.withSeed(1));
        Diagnostics diagnostics = Diagnostics.of(water, structure, 0);
        Vec3 oxygen = structure.conformers().get(0).positions().get(0);
        Vec3 hydrogens = structure.conformers().get(0).positions().get(1).minus(oxygen)
            .plus(structure.conformers().get(0).positions().get(2).minus(oxygen));
        for (Diagnostics.LonePair pair : diagnostics.lonePairs())
        {
            assertTrue(pair.tip().minus(oxygen).dot(hydrogens) < 0);
        }
    }

    @Test
    void should_stand_a_pi_orbital_on_every_atom_of_a_system()
    {
        Diagnostics benzene = of("c1ccccc1");
        assertEquals(1, benzene.conjugations().size());

        Diagnostics.Conjugation system = benzene.conjugations().getFirst();
        assertEquals(6, system.orbitals().size());
        assertEquals(6, system.piElectrons());
        assertEquals(PiSystem.Aromaticity.AROMATIC, system.aromaticity());

        // naphthalene's two rings are one system, so its orbitals run over all ten atoms
        assertEquals(10, of("c1ccc2ccccc2c1").conjugations().getFirst().orbitals().size());
        assertTrue(of("CCO").conjugations().isEmpty());
    }

    @Test
    void should_lay_a_cloud_over_every_bond_of_a_system()
    {
        Molecule benzene = Molecule.fromSmiles("c1ccccc1");
        Structure3D structure = Embedder.embed(benzene, Embedder.Options.DEFAULT.withSeed(1));
        List<Vec3> at = structure.conformers().get(0).positions();
        Diagnostics.Conjugation system = Diagnostics.of(benzene, structure, 0).conjugations().getFirst();

        assertEquals(6, system.clouds().size());
        Vec3 normal = at.get(1).minus(at.get(0)).cross(at.get(2).minus(at.get(0))).normalised();
        for (Diagnostics.PiCloud cloud : system.clouds())
        {
            Vec3 middle = at.get(cloud.first()).plus(at.get(cloud.second())).times(0.5);
            // the two lozenges lie either side of the bond, as far one way as the other
            assertTrue(cloud.above().minus(middle).dot(normal) * cloud.below().minus(middle).dot(normal) < 0);
            assertEquals(cloud.above().distanceTo(middle), cloud.below().distanceTo(middle), 1e-9);
            // and square over the bond, not off to one side of it
            assertEquals(0, cloud.above().minus(middle).dot(at.get(cloud.second()).minus(at.get(cloud.first()))), 1e-6);
        }
    }

    @Test
    void should_point_every_pi_lobe_across_the_plane_of_its_system()
    {
        Molecule benzene = Molecule.fromSmiles("c1ccccc1");
        Structure3D structure = Embedder.embed(benzene, Embedder.Options.DEFAULT.withSeed(1));
        List<Vec3> at = structure.conformers().get(0).positions();
        Diagnostics diagnostics = Diagnostics.of(benzene, structure, 0);

        Vec3 normal = at.get(1).minus(at.get(0)).cross(at.get(2).minus(at.get(0))).normalised();
        double first = 0;
        for (Diagnostics.PiOrbital orbital : diagnostics.conjugations().getFirst().orbitals())
        {
            Vec3 axis = orbital.above().minus(orbital.below()).normalised();
            // the lobes stand either side of the ring, so the axis between them is the ring's normal
            assertEquals(1, Math.abs(axis.dot(normal)), 0.05);

            // which side is above is arbitrary, but it is the same side for the whole system
            double side = orbital.above().minus(at.get(orbital.node())).dot(normal);
            first = first == 0 ? side : first;
            assertTrue(side * first > 0);
        }
    }

    @Test
    void should_count_what_each_atom_lends_its_system()
    {
        // pyrrole's nitrogen lends the pair that brings the system to six; each carbon lends one
        Diagnostics.Conjugation pyrrole = of("c1cc[nH]c1").conjugations().getFirst();
        assertEquals(6, pyrrole.piElectrons());
        assertEquals(List.of(1, 1, 1, 2, 1), pyrrole.orbitals().stream().map(Diagnostics.PiOrbital::electrons).toList());
    }

    @Test
    void should_name_the_totals_in_the_report()
    {
        assertTrue(of("CCO").report(1).startsWith("Strain "));
        assertTrue(of("CCO").report(0).contains("bond"));
    }

    @Test
    void should_reject_a_structure_of_another_molecule()
    {
        Structure3D structure = Embedder.embed(Molecule.fromSmiles("CC"));
        assertThrows(IllegalArgumentException.class, () -> Diagnostics.of(Molecule.fromSmiles("CCO"), structure, 0));
    }
}
