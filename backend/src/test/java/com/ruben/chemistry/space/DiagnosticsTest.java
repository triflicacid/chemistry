package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Test;

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
