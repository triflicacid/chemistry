package com.ruben.chemistry.space;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesWriter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructureReaderTest
{
    private static final String[] STEREO_MOLECULES = {
        "N[C@@H](C)C(=O)O",
        "N[C@H](C)C(=O)O",
        "C/C=C/C",
        "C/C=C\\C",
        "C[C@H](O)[C@@H](O)C",
        "C[C@H](O)[C@H](O)C",
        "C/C=C/C=C/C",
        "C/C=C\\C=C/C",
        "CC(C)[C@@H]1CC[C@@H](C)C[C@H]1O",
        "F[C@](Cl)(Br)I",
        "F[C@@](Cl)(Br)I",
    };

    private static String canonical(Molecule molecule)
    {
        return new SmilesWriter(molecule).setRanker(new CanonicalRanker()).write();
    }

    private static String canonical(String smiles)
    {
        return canonical(Molecule.fromSmiles(smiles));
    }

    private static String read(Structure3D structure)
    {
        return canonical(StructureReader.read(structure, 0).molecule());
    }

    private static Structure3D withPositions(Structure3D structure, List<Vec3> positions)
    {
        return new Structure3D(structure.sites(), structure.connections(), List.of(new Structure3D.Conformer(0, positions)));
    }

    private static Structure3D noisy(Structure3D structure, double amplitude, Random random)
    {
        List<Vec3> positions = new ArrayList<>();
        for (Vec3 position : structure.conformers().getFirst().positions())
        {
            positions.add(position.plus(new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).times(amplitude)));
        }
        return withPositions(structure, positions);
    }

    private static Structure3D mirrored(Structure3D structure)
    {
        List<Vec3> positions = new ArrayList<>();
        for (Vec3 position : structure.conformers().getFirst().positions())
        {
            positions.add(new Vec3(-position.x(), position.y(), position.z()));
        }
        return withPositions(structure, positions);
    }

    /** A structure of atoms with no hydrogens, bonded as listed. */
    private static Structure3D heavy(String[] elements, int[][] bonds, BondOrder[] orders, List<Vec3> positions)
    {
        List<Structure3D.Site> sites = new ArrayList<>();
        for (int index = 0; index < elements.length; index++)
        {
            sites.add(new Structure3D.Site(index, elements[index], 0, index, false, 0));
        }
        List<Structure3D.Connection> connections = new ArrayList<>();
        for (int index = 0; index < bonds.length; index++)
        {
            connections.add(new Structure3D.Connection(bonds[index][0], bonds[index][1], orders[index], index));
        }
        return new Structure3D(sites, connections, List.of(new Structure3D.Conformer(0, positions)));
    }

    /** A carbon bonded to F, Cl, Br and I, with the ligands at the corners of a tetrahedron or, if flat, of a square. */
    private static Structure3D centre(boolean flat, boolean mirrored)
    {
        double side = mirrored ? -1 : 1;
        List<Vec3> positions = flat
            ? List.of(Vec3.ZERO, new Vec3(1.5, 0, 0), new Vec3(0, 1.5, 0), new Vec3(-1.5, 0, 0), new Vec3(0, -1.5, 0))
            : List.of(Vec3.ZERO, new Vec3(side, 1, 1), new Vec3(side, -1, -1), new Vec3(-side, 1, -1), new Vec3(-side, -1, 1));
        return heavy(new String[] {"C", "F", "Cl", "Br", "I"}, new int[][] {{0, 1}, {0, 2}, {0, 3}, {0, 4}},
            new BondOrder[] {BondOrder.SINGLE, BondOrder.SINGLE, BondOrder.SINGLE, BondOrder.SINGLE}, positions);
    }

    /** FC(Cl)=C(Br)I, with the bromine turned the given number of degrees from the fluorine about the double bond. */
    private static Structure3D alkene(double degrees)
    {
        double turn = Math.toRadians(degrees);
        double y = 1.1 * Math.cos(turn);
        double z = 1.1 * Math.sin(turn);
        List<Vec3> positions = List.of(Vec3.ZERO, new Vec3(1.34, 0, 0), new Vec3(-0.6, 1.1, 0), new Vec3(-0.6, -1.1, 0),
            new Vec3(1.94, y, z), new Vec3(1.94, -y, -z));
        return heavy(new String[] {"C", "C", "F", "Cl", "Br", "I"}, new int[][] {{0, 1}, {0, 2}, {0, 3}, {1, 4}, {1, 5}},
            new BondOrder[] {BondOrder.DOUBLE, BondOrder.SINGLE, BondOrder.SINGLE, BondOrder.SINGLE, BondOrder.SINGLE}, positions);
    }

    private static void assertReadsAsItself(String smiles)
    {
        Molecule original = Molecule.fromSmiles(smiles);
        StructureReader.Reading reading = StructureReader.read(Embedder.embed(original), 0);
        assertThat(reading.issues()).as(smiles).isEmpty();
        assertThat(canonical(reading.molecule())).as(smiles).isEqualTo(canonical(original));
    }

    @Test
    void embedded_molecules_with_stereochemistry_should_read_back_as_themselves()
    {
        for (String smiles : STEREO_MOLECULES)
        {
            assertReadsAsItself(smiles);
        }
    }

    @Test
    void embedded_molecules_without_stereochemistry_should_read_back_as_themselves()
    {
        for (String smiles : new String[] {"CCO", "c1ccccc1O", "C1=CCCCC1", "CC(C)C", "C#CC", "c1ccncc1", "CC(=O)O", "C=C", "CC=C(C)C"})
        {
            assertReadsAsItself(smiles);
        }
    }

    @Test
    void charges_isotopes_and_hydrogen_counts_should_survive_reading()
    {
        for (String smiles : new String[] {"C[N+](C)(C)C", "[13CH3]O", "[O-]C(=O)C", "[NH4+]", "[2H]C(Cl)(Cl)Cl"})
        {
            assertReadsAsItself(smiles);
        }
    }

    @Test
    void every_conformer_should_give_the_same_stereochemistry()
    {
        Molecule menthol = Molecule.fromSmiles("CC(C)[C@@H]1CC[C@@H](C)C[C@H]1O");
        Structure3D structure = Embedder.embed(menthol, Embedder.Options.DEFAULT.withConformers(6));
        assertThat(structure.conformers().size()).isGreaterThan(1);
        for (int conformer = 0; conformer < structure.conformers().size(); conformer++)
        {
            StructureReader.Reading reading = StructureReader.read(structure, conformer);
            assertThat(reading.issues()).isEmpty();
            assertThat(canonical(reading.molecule())).isEqualTo(canonical(menthol));
        }
    }

    @Test
    void different_seeds_should_give_the_same_stereochemistry()
    {
        Molecule molecule = Molecule.fromSmiles("C[C@H](O)[C@@H](O)C/C=C/C");
        for (long seed = 0; seed < 8; seed++)
        {
            StructureReader.Reading reading = StructureReader.read(Embedder.embed(molecule, Embedder.Options.DEFAULT.withSeed(seed)), 0);
            assertThat(reading.issues()).as("seed " + seed).isEmpty();
            assertThat(canonical(reading.molecule())).as("seed " + seed).isEqualTo(canonical(molecule));
        }
    }

    @Test
    void enantiomers_should_read_differently()
    {
        assertThat(read(Embedder.embed(Molecule.fromSmiles("N[C@H](C)C(=O)O"))))
            .isNotEqualTo(read(Embedder.embed(Molecule.fromSmiles("N[C@@H](C)C(=O)O"))));
    }

    @Test
    void geometric_isomers_should_read_differently()
    {
        assertThat(read(Embedder.embed(Molecule.fromSmiles("C/C=C/C"))))
            .isNotEqualTo(read(Embedder.embed(Molecule.fromSmiles("C/C=C\\C"))));
    }

    @Test
    void mirrored_centres_should_be_read_as_the_two_hands()
    {
        StructureReader.Reading one = StructureReader.read(centre(false, false), 0);
        StructureReader.Reading other = StructureReader.read(centre(false, true), 0);
        assertThat(one.issues()).isEmpty();
        assertThat(other.issues()).isEmpty();
        assertThat(List.of(canonical(one.molecule()), canonical(other.molecule())))
            .containsExactlyInAnyOrder(canonical("F[C@](Cl)(Br)I"), canonical("F[C@@](Cl)(Br)I"));
    }

    @Test
    void a_flat_centre_should_be_left_unspecified_and_reported()
    {
        StructureReader.Reading reading = StructureReader.read(centre(true, false), 0);
        assertThat(reading.issues()).hasSize(1);
        assertThat(reading.issues().getFirst().kind()).isEqualTo(StructureReader.Kind.FLAT_CENTRE);
        assertThat(reading.issues().getFirst().atom()).isZero();
        assertThat(canonical(reading.molecule())).isEqualTo(canonical("C(F)(Cl)(Br)I"));
    }

    @Test
    void a_nearly_flat_centre_should_be_left_unspecified()
    {
        Structure3D tetrahedral = centre(false, false);
        List<Vec3> positions = new ArrayList<>(tetrahedral.conformers().getFirst().positions());
        positions.replaceAll(position -> new Vec3(position.x() * 0.05, position.y(), position.z()));
        StructureReader.Reading reading = StructureReader.read(withPositions(tetrahedral, positions), 0);
        assertThat(reading.issues()).extracting(StructureReader.Issue::kind).containsExactly(StructureReader.Kind.FLAT_CENTRE);
    }

    @Test
    void the_handedness_threshold_should_decide_how_flat_a_centre_may_be()
    {
        Structure3D structure = centre(false, false);
        assertThat(StructureReader.read(structure, 0, new StructureReader.Options(0.2, 30)).issues()).isEmpty();
        assertThat(StructureReader.read(structure, 0, new StructureReader.Options(100, 30)).issues()).hasSize(1);
    }

    @Test
    void a_centre_with_two_alike_ligands_should_not_be_a_stereocentre()
    {
        assertReadsAsItself("CC(C)O");
    }

    @Test
    void ligands_alike_by_symmetry_deeper_in_the_molecule_should_not_be_told_apart()
    {
        assertReadsAsItself("CCC(CC)(O)C");
    }

    @Test
    void ligands_differing_only_far_away_should_be_told_apart()
    {
        assertReadsAsItself("C[C@H](CCCCC)CCCCO");
    }

    @Test
    void a_double_bond_should_be_read_as_same_side_or_opposite_side()
    {
        assertThat(StructureReader.read(alkene(0), 0).issues()).isEmpty();
        assertThat(StructureReader.read(alkene(180), 0).issues()).isEmpty();
        String same = read(alkene(0));
        String opposite = read(alkene(180));
        assertThat(same).isNotEqualTo(opposite);
        assertThat(same).isNotEqualTo(canonical("FC(Cl)=C(Br)I"));
        assertThat(opposite).isNotEqualTo(canonical("FC(Cl)=C(Br)I"));
    }

    @Test
    void a_double_bond_within_the_tolerance_of_planar_should_still_be_read()
    {
        assertThat(read(alkene(25))).isEqualTo(read(alkene(0)));
        assertThat(read(alkene(-25))).isEqualTo(read(alkene(0)));
        assertThat(read(alkene(155))).isEqualTo(read(alkene(180)));
    }

    @Test
    void a_twisted_double_bond_should_be_left_unspecified_and_reported()
    {
        StructureReader.Reading reading = StructureReader.read(alkene(90), 0);
        assertThat(reading.issues()).extracting(StructureReader.Issue::kind).containsExactly(StructureReader.Kind.TWISTED_DOUBLE_BOND);
        assertThat(reading.issues().getFirst().atom()).isZero();
        assertThat(canonical(reading.molecule())).isEqualTo(canonical("FC(Cl)=C(Br)I"));
    }

    @Test
    void the_torsion_tolerance_should_decide_how_twisted_a_double_bond_may_be()
    {
        Structure3D structure = alkene(40);
        assertThat(StructureReader.read(structure, 0, new StructureReader.Options(0.2, 30)).issues()).hasSize(1);
        assertThat(StructureReader.read(structure, 0, new StructureReader.Options(0.2, 45)).issues()).isEmpty();
    }

    @Test
    void a_double_bond_with_alike_substituents_should_not_be_a_stereo_bond()
    {
        assertReadsAsItself("CC(C)=CC");
        assertReadsAsItself("C=CC");
    }

    @Test
    void a_double_bond_in_a_ring_should_be_left_alone()
    {
        assertReadsAsItself("C1=CCCCCCC1");
    }

    @Test
    void the_read_molecule_should_have_the_atoms_and_bonds_of_the_structure()
    {
        Molecule original = Molecule.fromSmiles("N[C@@H](C)C(=O)O");
        Molecule read = StructureReader.read(Embedder.embed(original), 0).molecule();
        assertThat(read.atoms()).hasSameSizeAs(original.atoms());
        assertThat(read.bonds()).hasSameSizeAs(original.bonds());
        for (int index = 0; index < original.atoms().size(); index++)
        {
            assertThat(read.atoms().get(index).element()).isEqualTo(original.atoms().get(index).element());
        }
    }

    @Test
    void disconnected_molecules_should_each_keep_their_stereochemistry()
    {
        assertReadsAsItself("N[C@@H](C)C(=O)O.C/C=C/C");
        assertReadsAsItself("N[C@H](C)C(=O)O.N[C@@H](C)C(=O)O");
    }

    @Test
    void a_subset_should_read_only_the_fragments_holding_its_atoms()
    {
        Molecule original = Molecule.fromSmiles("N[C@@H](C)C(=O)O.C/C=C/C");
        Structure3D structure = Embedder.embed(original);

        assertThat(read(structure.restrictedTo(Set.of(0)))).isEqualTo(canonical("N[C@@H](C)C(=O)O"));
        assertThat(read(structure.restrictedTo(Set.of(original.atoms().size() - 1)))).isEqualTo(canonical("C/C=C/C"));
    }

    @Test
    void a_subset_of_every_atom_should_read_the_whole_structure()
    {
        Molecule original = Molecule.fromSmiles("N[C@@H](C)C(=O)O.C/C=C/C");
        Set<Integer> every = new HashSet<>();
        for (int atom = 0; atom < original.atoms().size(); atom++)
        {
            every.add(atom);
        }
        assertThat(read(Embedder.embed(original).restrictedTo(every))).isEqualTo(canonical(original));
    }

    @Test
    void a_subset_of_no_known_atom_should_be_refused()
    {
        Structure3D structure = Embedder.embed(Molecule.fromSmiles("CCO"));
        assertThatThrownBy(() -> structure.restrictedTo(Set.of(99))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_missing_conformer_should_be_refused()
    {
        Structure3D structure = Embedder.embed(Molecule.fromSmiles("CCO"));
        assertThatThrownBy(() -> StructureReader.read(structure, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructureReader.read(structure, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void small_noise_should_not_change_what_is_read()
    {
        Random random = new Random(7);
        for (String smiles : STEREO_MOLECULES)
        {
            Molecule original = Molecule.fromSmiles(smiles);
            Structure3D structure = Embedder.embed(original);
            for (int trial = 0; trial < 10; trial++)
            {
                StructureReader.Reading reading = StructureReader.read(noisy(structure, 0.05, random), 0);
                assertThat(reading.issues()).as(smiles).isEmpty();
                assertThat(canonical(reading.molecule())).as(smiles).isEqualTo(canonical(original));
            }
        }
    }

    @Test
    void large_noise_should_never_flip_stereochemistry()
    {
        Random random = new Random(11);
        for (String smiles : STEREO_MOLECULES)
        {
            Molecule original = Molecule.fromSmiles(smiles);
            Structure3D structure = Embedder.embed(original);
            for (double amplitude : new double[] {0.1, 0.2, 0.3})
            {
                for (int trial = 0; trial < 20; trial++)
                {
                    StructureReader.Reading reading = StructureReader.read(noisy(structure, amplitude, random), 0);
                    if (reading.issues().isEmpty())
                    {
                        assertThat(canonical(reading.molecule())).as(smiles + " at " + amplitude).isEqualTo(canonical(original));
                    }
                }
            }
        }
    }

    @Test
    void moving_and_scaling_a_structure_should_not_change_what_is_read()
    {
        Molecule original = Molecule.fromSmiles("N[C@@H](C)C(=O)O");
        Structure3D structure = Embedder.embed(original);
        List<Vec3> moved = new ArrayList<>();
        for (Vec3 position : structure.conformers().getFirst().positions())
        {
            moved.add(position.times(2.5).plus(new Vec3(10, -4, 7)));
        }
        assertThat(read(withPositions(structure, moved))).isEqualTo(canonical(original));
    }

    @Test
    void mirroring_a_structure_should_invert_its_centre()
    {
        Structure3D structure = Embedder.embed(Molecule.fromSmiles("N[C@@H](C)C(=O)O"));
        assertThat(read(mirrored(structure))).isEqualTo(canonical("N[C@H](C)C(=O)O"));
    }

    @Test
    void mirroring_a_structure_should_leave_its_double_bond_alone()
    {
        Structure3D structure = Embedder.embed(Molecule.fromSmiles("C/C=C/C"));
        assertThat(read(mirrored(structure))).isEqualTo(canonical("C/C=C/C"));
    }
}
