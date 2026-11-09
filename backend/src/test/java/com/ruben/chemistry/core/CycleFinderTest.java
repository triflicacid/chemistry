package com.ruben.chemistry.core;

import com.ruben.chemistry.core.CycleFinder.Cycle;
import com.ruben.chemistry.core.CycleFinder.Cycles;
import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CycleFinderTest
{
    private static final int[][] CUBANE_EDGES = {
        {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private static Molecule parse(String smiles)
    {
        return new SmilesParser(smiles).parse();
    }

    private static List<Integer> sizes(List<Cycle> cycles)
    {
        return cycles.stream().map(cycle -> cycle.atoms().size()).sorted().toList();
    }

    private static long sharedAtoms(Cycle first, Cycle second)
    {
        return first.atoms().stream().filter(second.atoms()::contains).count();
    }

    /**
     * Builds cubane with its bonds added starting {@code shift} places along, and optionally with every atom relabelled.
     */
    private static Molecule cubane(Atom[] atoms, int shift, boolean relabelled)
    {
        Molecule.Builder builder = new Molecule.Builder();
        for (Atom atom : atoms)
        {
            builder.addAtom(atom);
        }
        for (int e = 0; e < CUBANE_EDGES.length; e++)
        {
            int[] edge = CUBANE_EDGES[(e + shift) % CUBANE_EDGES.length];
            int first = relabelled ? 7 - edge[0] : edge[0];
            int second = relabelled ? 7 - edge[1] : edge[1];
            builder.addBond(atoms[first], atoms[second], BondOrder.SINGLE);
        }
        return builder.build();
    }

    private static Atom[] carbons(int count)
    {
        Atom[] atoms = new Atom[count];
        for (int i = 0; i < count; i++)
        {
            atoms[i] = Atom.organic("C");
        }
        return atoms;
    }

    /**
     * @return each relevant cycle as the set of the positions in {@code atoms} of its atoms
     */
    private static Set<Set<Integer>> cyclesByPosition(Cycles cycles, Atom[] atoms)
    {
        Set<Set<Integer>> result = new HashSet<>();
        for (Cycle cycle : cycles.relevant())
        {
            Set<Integer> positions = new HashSet<>();
            for (int i = 0; i < atoms.length; i++)
            {
                if (cycle.atoms().contains(atoms[i]))
                {
                    positions.add(i);
                }
            }
            result.add(positions);
        }
        return result;
    }

    @Nested
    class Acyclic
    {
        @ParameterizedTest
        @ValueSource(strings = {"C", "CCO", "CC(C)(C)C"})
        void has_no_cycles(String smiles)
        {
            Cycles cycles = CycleFinder.analyse(parse(smiles));

            assertThat(cycles.relevant()).isEmpty();
            assertThat(cycles.basis()).isEmpty();
        }

        @Test
        void substituents_are_not_part_of_the_ring()
        {
            Molecule toluene = parse("Cc1ccccc1");

            Cycles cycles = CycleFinder.analyse(toluene);

            assertThat(cycles.relevant()).hasSize(1);
            Cycle ring = cycles.relevant().getFirst();
            assertThat(ring.atoms()).hasSize(6);
            assertThat(ring.bonds()).hasSize(6);
            assertThat(ring.atoms()).doesNotContain(toluene.atoms().get(0));
        }

        @Test
        void a_bond_joining_two_rings_is_in_neither()
        {
            Molecule biphenyl = parse("c1ccc(cc1)c1ccccc1");

            Cycles cycles = CycleFinder.analyse(biphenyl);

            assertThat(sizes(cycles.relevant())).containsExactly(6, 6);
            long bondsOutsideRings = biphenyl.bonds().stream()
                .filter(bond -> cycles.relevant().stream().noneMatch(cycle -> cycle.bonds().contains(bond)))
                .count();
            assertThat(bondsOutsideRings).isEqualTo(1);
            assertThat(sharedAtoms(cycles.relevant().get(0), cycles.relevant().get(1))).isZero();
        }
    }

    @Nested
    class SingleRings
    {
        @ParameterizedTest
        @CsvSource({"C1CC1,3", "C1CCC1,4", "C1CCCC1,5", "c1ccccc1,6", "C1CCCCCCC1,8"})
        void is_one_cycle_of_the_ring_size(String smiles, int size)
        {
            Cycles cycles = CycleFinder.analyse(parse(smiles));

            assertThat(sizes(cycles.relevant())).containsExactly(size);
            assertThat(sizes(cycles.basis())).containsExactly(size);
        }
    }

    @Nested
    class FusedAndBridgedSystems
    {
        @Test
        void fused_rings_share_two_atoms()
        {
            Cycles cycles = CycleFinder.analyse(parse("c1ccc2ccccc2c1"));

            assertThat(sizes(cycles.relevant())).containsExactly(6, 6);
            assertThat(sizes(cycles.basis())).containsExactly(6, 6);
            assertThat(sharedAtoms(cycles.relevant().get(0), cycles.relevant().get(1))).isEqualTo(2);
        }

        @Test
        void a_fused_system_has_no_cycle_around_its_perimeter()
        {
            // naphthalene's outer ten-membered cycle is the XOR of its two rings, so it is not a ring of its own
            Cycles cycles = CycleFinder.analyse(parse("c1ccc2ccccc2c1"));

            assertThat(sizes(cycles.relevant())).doesNotContain(10);
            assertThat(cycles.relevant()).hasSize(2);
        }

        @Test
        void only_fusion_atoms_belong_to_two_cycles()
        {
            Molecule naphthalene = parse("c1ccc2ccccc2c1");
            Cycles cycles = CycleFinder.analyse(naphthalene);

            List<Long> membership = naphthalene.atoms().stream()
                .map(atom -> cycles.relevant().stream().filter(cycle -> cycle.atoms().contains(atom)).count())
                .sorted()
                .toList();

            assertThat(membership).containsExactly(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 2L, 2L);
        }

        @Test
        void a_linear_fused_system_has_one_cycle_per_ring()
        {
            // anthracene: the middle ring shares two atoms with each outer ring, which share none with each other
            Cycles cycles = CycleFinder.analyse(parse("c1ccc2cc3ccccc3cc2c1"));

            assertThat(sizes(cycles.relevant())).containsExactly(6, 6, 6);
            assertThat(sizes(cycles.basis())).containsExactly(6, 6, 6);
            List<Long> sharing = List.of(
                sharedAtoms(cycles.relevant().get(0), cycles.relevant().get(1)),
                sharedAtoms(cycles.relevant().get(0), cycles.relevant().get(2)),
                sharedAtoms(cycles.relevant().get(1), cycles.relevant().get(2)));
            assertThat(sharing).containsExactlyInAnyOrder(0L, 2L, 2L);
        }

        @Test
        void spiro_rings_share_one_atom()
        {
            Cycles cycles = CycleFinder.analyse(parse("C1CCC12CCC2"));

            assertThat(sizes(cycles.relevant())).containsExactly(4, 4);
            assertThat(sharedAtoms(cycles.relevant().get(0), cycles.relevant().get(1))).isEqualTo(1);
        }

        @Test
        void a_cycle_made_of_two_shorter_ones_is_not_relevant()
        {
            // norbornane: the six-membered cycle around the outside is the XOR of the two five-membered ones
            Cycles cycles = CycleFinder.analyse(parse("C1CC2CCC1C2"));

            assertThat(sizes(cycles.relevant())).containsExactly(5, 5);
            assertThat(sizes(cycles.basis())).containsExactly(5, 5);
        }

        @Test
        void disconnected_fragments_are_each_searched()
        {
            Cycles cycles = CycleFinder.analyse(parse("c1ccccc1.C1CC1"));

            assertThat(sizes(cycles.relevant())).containsExactly(3, 6);
            assertThat(sizes(cycles.basis())).containsExactly(3, 6);
        }
    }

    @Nested
    class SymmetricCages
    {
        @Test
        void cubane_has_six_relevant_faces_but_a_basis_of_five()
        {
            Cycles cycles = CycleFinder.analyse(cubane(carbons(8), 0, false));

            assertThat(sizes(cycles.relevant())).containsExactly(4, 4, 4, 4, 4, 4);
            assertThat(sizes(cycles.basis())).containsExactly(4, 4, 4, 4, 4);
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void relevant_cycles_do_not_depend_on_bond_or_atom_order(boolean relabelled)
        {
            Atom[] reference = carbons(8);
            Set<Set<Integer>> expected = cyclesByPosition(CycleFinder.analyse(cubane(reference, 0, false)), reference);

            for (int shift = 0; shift < CUBANE_EDGES.length; shift++)
            {
                Atom[] atoms = carbons(8);
                Cycles cycles = CycleFinder.analyse(cubane(atoms, shift, relabelled));

                assertThat(cyclesByPosition(cycles, atoms)).isEqualTo(expected);
            }
        }
    }

    @Nested
    class Invariants
    {
        private static final String[] SAMPLES = {
            "C1CC1", "c1ccc2ccccc2c1", "C1CC2CCC1C2", "c1ccc(cc1)c1ccccc1", "C1CCC12CCC2", "Cc1ccccc1"};

        @Test
        void the_basis_size_is_the_circuit_rank()
        {
            for (String smiles : SAMPLES)
            {
                Molecule molecule = parse(smiles);

                // every sample is a single connected molecule
                int circuitRank = molecule.bonds().size() - molecule.atomCount() + 1;
                assertThat(CycleFinder.analyse(molecule).basis()).as(smiles).hasSize(circuitRank);
            }
        }

        @Test
        void the_basis_is_made_of_relevant_cycles()
        {
            for (String smiles : SAMPLES)
            {
                Cycles cycles = CycleFinder.analyse(parse(smiles));

                assertThat(cycles.relevant()).as(smiles).containsAll(cycles.basis());
            }
        }

        @Test
        void every_cycle_is_simple()
        {
            for (String smiles : SAMPLES)
            {
                Molecule molecule = parse(smiles);
                for (Cycle cycle : CycleFinder.analyse(molecule).relevant())
                {
                    assertThat(cycle.bonds()).as(smiles).hasSameSizeAs(cycle.atoms());
                    for (Atom atom : cycle.atoms())
                    {
                        long cycleBondsAtAtom = molecule.bondsOf(atom).stream().filter(cycle.bonds()::contains).count();
                        assertThat(cycleBondsAtAtom).as(smiles).isEqualTo(2);
                    }
                }
            }
        }
    }

    @Nested
    class ToMolecule
    {
        @Test
        void contains_just_the_cycles_atoms_and_bonds()
        {
            Molecule toluene = parse("Cc1ccccc1");
            Cycle ring = CycleFinder.analyse(toluene).relevant().getFirst();

            Molecule ringMolecule = CycleFinder.toMolecule(ring);

            assertThat(ringMolecule.atomCount()).isEqualTo(6);
            assertThat(ringMolecule.bonds()).hasSize(6);
            assertThat(ringMolecule.containsAtom(toluene.atoms().getFirst())).isFalse();
        }

        @Test
        void reuses_the_original_atoms()
        {
            Molecule cyclopropane = parse("C1CC1");
            Cycle ring = CycleFinder.analyse(cyclopropane).relevant().getFirst();

            Molecule ringMolecule = CycleFinder.toMolecule(ring);

            assertThat(cyclopropane.atoms()).allMatch(ringMolecule::containsAtom);
        }
    }
}
