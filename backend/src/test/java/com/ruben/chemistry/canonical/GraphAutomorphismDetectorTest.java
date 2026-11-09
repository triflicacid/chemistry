package com.ruben.chemistry.canonical;

import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.canonical.GraphAutomorphismDetector.Graph;
import com.ruben.chemistry.core.StereoDeclaration.DoubleBond;
import com.ruben.chemistry.core.StereoDeclaration.Tetrahedral;
import com.ruben.chemistry.core.StereoDeclaration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static com.ruben.chemistry.core.Chirality.ANTICLOCKWISE;
import static com.ruben.chemistry.core.Chirality.CLOCKWISE;
import static com.ruben.chemistry.core.DoubleBondStereo.OPPOSITE_SIDE;
import static com.ruben.chemistry.core.DoubleBondStereo.SAME_SIDE;
import static com.ruben.chemistry.canonical.GraphAutomorphismDetector.Graph.IMPLICIT_HYDROGEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphAutomorphismDetectorTest
{
    private static final int SINGLE = 1;
    private static final int DOUBLE = 2;

    @Test
    void maps_every_atom_to_itself_under_the_identity()
    {
        assertThat(findAutomorphism(butane(), 0, 0)).containsExactly(0, 1, 2, 3);
    }

    @Test
    void finds_an_automorphism_between_atoms_related_by_symmetry()
    {
        assertThat(findAutomorphism(butane(), 0, 3)).containsExactly(3, 2, 1, 0);
    }

    @Test
    void returns_an_empty_optional_when_no_automorphism_exists()
    {
        TestGraph graph = new TestGraph(0, 1, 1, 0)
                .bond(0, 1, SINGLE)
                .bond(1, 2, SINGLE)
                .bond(2, 3, DOUBLE);

        Optional<int[]> mapping = GraphAutomorphismDetector.findAutomorphism(
                graph,
                graph.atomClasses(),
                0,
                3);

        assertThat(mapping).isEmpty();
    }

    @Test
    void does_not_map_atoms_when_corresponding_bond_classes_differ()
    {
        TestGraph graph = new TestGraph(0, 1, 1, 0)
                .bond(0, 1, SINGLE)
                .bond(1, 2, SINGLE)
                .bond(2, 3, DOUBLE);

        assertThat(areEquivalent(graph, 0, 3)).isFalse();
    }

    @Test
    void treats_stereocentres_of_matching_parity_as_equivalent()
    {
        TestGraph graph = twoStereocentres(ANTICLOCKWISE, ANTICLOCKWISE);

        assertThat(areEquivalent(graph, 0, 1)).isTrue();
    }

    @Test
    void treats_stereocentres_of_opposite_parity_as_inequivalent()
    {
        TestGraph graph = twoStereocentres(ANTICLOCKWISE, CLOCKWISE);

        assertThat(areEquivalent(graph, 0, 1)).isFalse();
    }

    @Test
    void treats_stereocentres_as_equivalent_when_neighbour_order_and_parity_are_both_reversed()
    {
        TestGraph graph = twoStereocentres(ANTICLOCKWISE, ANTICLOCKWISE);

        graph.stereochemistry().removeIf(
                element -> element instanceof Tetrahedral tetrahedral
                        && tetrahedral.centre() == 1);

        graph.declare(new Tetrahedral(
                1,
                CLOCKWISE,
                IMPLICIT_HYDROGEN,
                0,
                5,
                4));

        assertThat(areEquivalent(graph, 0, 1)).isTrue();
    }

    @Test
    void treats_stereocentres_of_opposite_parity_as_equivalent_when_no_parity_is_declared()
    {
        TestGraph graph = twoStereocentres(ANTICLOCKWISE, CLOCKWISE);
        graph.stereochemistry().clear();

        assertThat(areEquivalent(graph, 0, 1)).isTrue();
    }

    @Test
    void treats_a_stereocentre_as_equivalent_to_itself()
    {
        TestGraph graph = twoStereocentres(ANTICLOCKWISE, CLOCKWISE);

        assertThat(areEquivalent(graph, 0, 0)).isTrue();
    }

    @Test
    void keeps_an_atom_with_no_declared_parity_from_mapping_onto_a_stereocentre()
    {
        TestGraph graph = twoStereocentres(ANTICLOCKWISE, ANTICLOCKWISE);
        graph.stereochemistry().removeIf(
                element -> ((Tetrahedral) element).centre() == 1);

        assertThat(areEquivalent(graph, 0, 1)).isFalse();
    }

    @Test
    void refines_tetrahedral_ligand_colours_using_multiple_neighbourhood_rounds()
    {
        // C(CC)(CCC)C(CC)CCC
        TestGraph graph = new TestGraph(
                0, 0,
                1, 1, 1, 1,
                2, 2, 2, 2,
                3, 3)
                .bond(0, 1, SINGLE)
                .bond(0, 2, SINGLE)
                .bond(0, 3, SINGLE)
                .bond(1, 4, SINGLE)
                .bond(1, 5, SINGLE)
                .bond(2, 6, SINGLE)
                .bond(3, 7, SINGLE)
                .bond(4, 8, SINGLE)
                .bond(5, 9, SINGLE)
                .bond(7, 10, SINGLE)
                .bond(9, 11, SINGLE)
                .declare(new Tetrahedral(
                        0,
                        ANTICLOCKWISE,
                        IMPLICIT_HYDROGEN,
                        1,
                        2,
                        3))
                .declare(new Tetrahedral(
                        1,
                        ANTICLOCKWISE,
                        IMPLICIT_HYDROGEN,
                        0,
                        4,
                        5));

        assertThat(areEquivalent(graph, 0, 1)).isTrue();
    }

    @Test
    void rejects_a_tetrahedral_centre_with_repeated_neighbours()
    {
        TestGraph graph = butane()
                .declare(new Tetrahedral(
                        1,
                        CLOCKWISE,
                        0,
                        2,
                        2,
                        IMPLICIT_HYDROGEN));

        assertThatThrownBy(() -> areEquivalent(graph, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_tetrahedral_centre_listed_as_its_own_neighbour()
    {
        TestGraph graph = butane()
                .declare(new Tetrahedral(
                        1,
                        CLOCKWISE,
                        0,
                        1,
                        2,
                        IMPLICIT_HYDROGEN));

        assertThatThrownBy(() -> areEquivalent(graph, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_tetrahedral_neighbour_not_bonded_to_its_centre()
    {
        TestGraph graph = butane()
                .declare(new Tetrahedral(
                        1,
                        CLOCKWISE,
                        0,
                        2,
                        3,
                        IMPLICIT_HYDROGEN));

        assertThatThrownBy(() -> areEquivalent(graph, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void treats_double_bonds_of_matching_configuration_as_equivalent()
    {
        TestGraph graph = hexadiene(SAME_SIDE, SAME_SIDE);

        assertThat(areEquivalent(graph, 0, 5)).isTrue();
    }

    @Test
    void treats_double_bonds_of_opposite_configuration_as_inequivalent()
    {
        TestGraph graph = hexadiene(SAME_SIDE, OPPOSITE_SIDE);

        assertThat(areEquivalent(graph, 0, 5)).isFalse();
    }

    @Test
    void treats_double_bonds_of_opposite_configuration_as_equivalent_when_none_is_declared()
    {
        TestGraph graph = hexadiene(SAME_SIDE, OPPOSITE_SIDE);
        graph.stereochemistry().clear();

        assertThat(areEquivalent(graph, 0, 5)).isTrue();
    }

    @Test
    void preserves_a_double_bond_configuration_across_its_own_symmetry()
    {
        TestGraph graph = new TestGraph(0, 1, 1, 0)
                .bond(0, 1, SINGLE)
                .bond(1, 2, DOUBLE)
                .bond(2, 3, SINGLE)
                .declare(new DoubleBond(1, 2, SAME_SIDE, 0, 3));

        assertThat(findAutomorphism(graph, 0, 3)).containsExactly(3, 2, 1, 0);
    }

    @Test
    void rejects_a_double_bond_with_identical_endpoints()
    {
        TestGraph graph = butane()
                .declare(new DoubleBond(1, 1, SAME_SIDE, 0, 2));

        assertThatThrownBy(() -> areEquivalent(graph, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_double_bond_whose_endpoints_are_not_bonded()
    {
        TestGraph graph = butane()
                .declare(new DoubleBond(0, 2, SAME_SIDE, 1, 3));

        assertThatThrownBy(() -> areEquivalent(graph, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_double_bond_reference_not_bonded_to_its_endpoint()
    {
        TestGraph graph = new TestGraph(0, 0, 0, 0)
                .bond(0, 1, DOUBLE)
                .bond(0, 2, SINGLE)
                .bond(1, 3, SINGLE)
                .declare(new DoubleBond(0, 1, SAME_SIDE, 3, 2));

        assertThatThrownBy(() -> areEquivalent(graph, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_stereochemistry_naming_an_atom_outside_the_graph()
    {
        TestGraph graph = butane()
                .declare(new Tetrahedral(4, CLOCKWISE, 0, 1, 2, 3));

        assertThatThrownBy(() -> areEquivalent(graph, 0, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the graph");
    }

    @Test
    void rejects_a_tetrahedral_centre_without_exactly_four_neighbours()
    {
        TestGraph graph = butane()
                .declare(new Tetrahedral(
                        1,
                        CLOCKWISE,
                        0,
                        2,
                        IMPLICIT_HYDROGEN));

        assertThatThrownBy(() -> areEquivalent(graph, 0, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have exactly four neighbours");
    }

    @Test
    void rejects_two_parities_declared_at_the_same_stereocentre()
    {
        TestGraph graph = isobutane()
                .declare(new Tetrahedral(
                        0,
                        CLOCKWISE,
                        IMPLICIT_HYDROGEN,
                        1,
                        2,
                        3))
                .declare(new Tetrahedral(
                        0,
                        ANTICLOCKWISE,
                        IMPLICIT_HYDROGEN,
                        1,
                        2,
                        3));

        assertThatThrownBy(() -> areEquivalent(graph, 1, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declares more than one parity");
    }

    @Test
    void rejects_two_configurations_declared_on_the_same_double_bond()
    {
        TestGraph graph = butene()
                .declare(new DoubleBond(0, 1, SAME_SIDE, 2, 3))
                .declare(new DoubleBond(0, 1, OPPOSITE_SIDE, 2, 3));

        assertThatThrownBy(() -> areEquivalent(graph, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declares more than one configuration");
    }

    @Test
    void returns_a_mapping_that_is_itself_an_automorphism()
    {
        // whatever is returned gets used as a permutation, not just as a yes, so it must be valid
        for (TestGraph graph : List.of(butane(), isobutane(), benzene(), cubane(), butene(), twoStereocentres(ANTICLOCKWISE, ANTICLOCKWISE)))
        {
            for (int fromAtom = 0; fromAtom < graph.atomCount(); fromAtom++)
            {
                for (int toAtom = 0; toAtom < graph.atomCount(); toAtom++)
                {
                    GraphAutomorphismDetector.findAutomorphism(graph, graph.atomClasses(), fromAtom, toAtom)
                            .ifPresent(mapping -> assertIsAutomorphism(graph, mapping));
                }
            }
        }
    }

    @Test
    void returns_a_mapping_that_sends_the_requested_atom_to_the_requested_one()
    {
        TestGraph graph = cubane();

        for (int toAtom = 0; toAtom < graph.atomCount(); toAtom++)
        {
            assertThat(findAutomorphism(graph, 0, toAtom)[0])
                    .describedAs("image of atom 0 when asked for %d", toAtom)
                    .isEqualTo(toAtom);
        }
    }

    @Test
    void finds_an_automorphism_between_every_pair_of_atoms_of_a_symmetric_cage()
    {
        TestGraph graph = cubane();

        for (int fromAtom = 0; fromAtom < graph.atomCount(); fromAtom++)
        {
            for (int toAtom = 0; toAtom < graph.atomCount(); toAtom++)
            {
                assertThat(areEquivalent(graph, fromAtom, toAtom))
                        .describedAs("atoms %d and %d of a cube", fromAtom, toAtom)
                        .isTrue();
            }
        }
    }

    @Test
    void maps_an_atom_onto_a_matching_atom_of_a_separate_component()
    {
        TestGraph graph = new TestGraph(0, 1, 0, 1)
                .bond(0, 1, SINGLE)
                .bond(2, 3, SINGLE);

        assertThat(areEquivalent(graph, 0, 2)).isTrue();
        assertIsAutomorphism(graph, findAutomorphism(graph, 0, 2));
    }

    @Test
    void does_not_map_an_atom_onto_an_atom_of_a_differently_shaped_component()
    {
        // one component is a bonded pair, the other a lone atom
        TestGraph graph = new TestGraph(0, 0, 0)
                .bond(0, 1, SINGLE);

        assertThat(areEquivalent(graph, 0, 2)).isFalse();
    }

    @Test
    void maps_between_atoms_of_a_graph_with_no_bonds_at_all()
    {
        TestGraph graph = new TestGraph(0, 0, 0);

        assertThat(areEquivalent(graph, 0, 2)).isTrue();
        assertIsAutomorphism(graph, findAutomorphism(graph, 0, 2));
    }

    @Test
    void treats_atoms_carrying_matching_self_loops_as_equivalent()
    {
        TestGraph graph = butane()
                .bond(0, 0, DOUBLE)
                .bond(3, 3, DOUBLE);

        assertThat(areEquivalent(graph, 0, 3)).isTrue();
    }

    @Test
    void does_not_map_an_atom_with_a_self_loop_onto_one_without()
    {
        TestGraph graph = butane()
                .bond(0, 0, DOUBLE);

        assertThat(areEquivalent(graph, 0, 3)).isFalse();
    }

    @Test
    void maps_a_lone_atom_onto_itself()
    {
        TestGraph graph = new TestGraph(0);

        assertThat(findAutomorphism(graph, 0, 0)).containsExactly(0);
    }

    @Test
    void rejects_a_class_for_every_atom_not_being_supplied()
    {
        TestGraph graph = butane();

        assertThatThrownBy(() -> GraphAutomorphismDetector.findAutomorphism(graph, new int[]{ 0, 1 }, 0, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one atom class for every atom");
    }

    @Test
    void rejects_an_atom_outside_the_graph()
    {
        TestGraph graph = butane();

        assertThatThrownBy(() -> areEquivalent(graph, 4, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> areEquivalent(graph, 0, 4)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> areEquivalent(graph, -1, 0)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void rejects_an_atom_of_an_empty_graph()
    {
        TestGraph graph = new TestGraph();

        assertThatThrownBy(() -> areEquivalent(graph, 0, 0)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void preserves_a_tetrahedral_parity_and_a_double_bond_configuration_together()
    {
        assertThat(areEquivalent(stereocentresAcrossADoubleBond(ANTICLOCKWISE, ANTICLOCKWISE), 0, 5)).isTrue();
    }

    @Test
    void does_not_map_across_a_double_bond_when_the_tetrahedral_parities_disagree()
    {
        assertThat(areEquivalent(stereocentresAcrossADoubleBond(ANTICLOCKWISE, CLOCKWISE), 0, 5)).isFalse();
    }

    @Test
    void preserves_a_double_bond_configuration_declared_against_implicit_hydrogens()
    {
        TestGraph graph = butene()
                .declare(new DoubleBond(0, 1, SAME_SIDE, IMPLICIT_HYDROGEN, IMPLICIT_HYDROGEN));

        assertThat(areEquivalent(graph, 0, 1)).isTrue();
    }

    @Test
    void maps_the_ligands_of_a_stereocentre_whose_ligands_cannot_be_told_apart()
    {
        // four interchangeable ligands leave the parity ambiguous during refinement, so only the
        // search can settle it: swapping two would invert the centre, but rotating three does not
        TestGraph graph = new TestGraph(0, 1, 1, 1, 1)
                .bond(0, 1, SINGLE)
                .bond(0, 2, SINGLE)
                .bond(0, 3, SINGLE)
                .bond(0, 4, SINGLE)
                .declare(new Tetrahedral(0, CLOCKWISE, 1, 2, 3, 4));

        assertThat(areEquivalent(graph, 1, 2)).isTrue();
        assertIsAutomorphism(graph, findAutomorphism(graph, 1, 2));
    }

    /**
     * Asserts that a mapping really is an automorphism: a permutation of the atoms that leaves every
     * atom class and every bond class, present or absent, exactly as it found it.
     *
     * @param graph the graph the mapping was found for
     * @param mapping the destination atom of each atom
     */
    private static void assertIsAutomorphism(TestGraph graph, int[] mapping)
    {
        int size = graph.atomCount();
        assertThat(mapping).describedAs("mapping covers every atom").hasSize(size);
        assertThat(IntStream.of(mapping).boxed().distinct().count())
                .describedAs("mapping %s is one-to-one", Arrays.toString(mapping))
                .isEqualTo(size);

        for (int atom = 0; atom < size; atom++)
        {
            assertThat(graph.atomClasses()[mapping[atom]])
                    .describedAs("class of atom %d maps onto the class of %d", atom, mapping[atom])
                    .isEqualTo(graph.atomClasses()[atom]);
        }

        for (int first = 0; first < size; first++)
        {
            for (int second = 0; second < size; second++)
            {
                assertThat(graph.bondClass(mapping[first], mapping[second]))
                        .describedAs("bond %d-%d maps onto bond %d-%d", first, second, mapping[first], mapping[second])
                        .isEqualTo(graph.bondClass(first, second));
            }
        }
    }

    private static TestGraph butane()
    {
        return new TestGraph(0, 1, 1, 0)
                .bond(0, 1, SINGLE)
                .bond(1, 2, SINGLE)
                .bond(2, 3, SINGLE);
    }

    private static TestGraph isobutane()
    {
        return new TestGraph(0, 1, 1, 1)
                .bond(0, 1, SINGLE)
                .bond(0, 2, SINGLE)
                .bond(0, 3, SINGLE);
    }

    private static TestGraph butene()
    {
        return new TestGraph(0, 0, 1, 1)
                .bond(0, 1, DOUBLE)
                .bond(0, 2, SINGLE)
                .bond(1, 3, SINGLE);
    }

    private static TestGraph benzene()
    {
        return new TestGraph(0, 0, 0, 0, 0, 0)
                .bond(0, 1, SINGLE)
                .bond(1, 2, SINGLE)
                .bond(2, 3, SINGLE)
                .bond(3, 4, SINGLE)
                .bond(4, 5, SINGLE)
                .bond(5, 0, SINGLE);
    }

    private static TestGraph cubane()
    {
        return new TestGraph(0, 0, 0, 0, 0, 0, 0, 0)
                .bond(0, 1, SINGLE)
                .bond(1, 2, SINGLE)
                .bond(2, 3, SINGLE)
                .bond(3, 0, SINGLE)
                .bond(4, 5, SINGLE)
                .bond(5, 6, SINGLE)
                .bond(6, 7, SINGLE)
                .bond(7, 4, SINGLE)
                .bond(0, 4, SINGLE)
                .bond(1, 5, SINGLE)
                .bond(2, 6, SINGLE)
                .bond(3, 7, SINGLE);
    }

    private static TestGraph stereocentresAcrossADoubleBond(Chirality first, Chirality second)
    {
        return new TestGraph(0, 1, 2, 3, 3, 0, 1, 2)
                .bond(0, 1, SINGLE)
                .bond(0, 2, SINGLE)
                .bond(0, 3, SINGLE)
                .bond(3, 4, DOUBLE)
                .bond(4, 5, SINGLE)
                .bond(5, 6, SINGLE)
                .bond(5, 7, SINGLE)
                .declare(new Tetrahedral(0, first, IMPLICIT_HYDROGEN, 1, 2, 3))
                .declare(new Tetrahedral(5, second, IMPLICIT_HYDROGEN, 6, 7, 4))
                .declare(new DoubleBond(3, 4, SAME_SIDE, 0, 5));
    }

    private static TestGraph twoStereocentres(Chirality first, Chirality second)
    {
        // CC(C)C(C)C
        return new TestGraph(0, 0, 1, 2, 1, 2)
                .bond(0, 1, SINGLE)
                .bond(0, 2, SINGLE)
                .bond(0, 3, SINGLE)
                .bond(1, 4, SINGLE)
                .bond(1, 5, SINGLE)
                .declare(new Tetrahedral(0, first, IMPLICIT_HYDROGEN, 1, 2, 3))
                .declare(new Tetrahedral(1, second, IMPLICIT_HYDROGEN, 0, 4, 5));
    }

    private static TestGraph hexadiene(DoubleBondStereo first, DoubleBondStereo second)
    {
        // CC=CC=CC
        return new TestGraph(0, 1, 2, 2, 1, 0)
                .bond(0, 1, SINGLE)
                .bond(1, 2, DOUBLE)
                .bond(2, 3, SINGLE)
                .bond(3, 4, DOUBLE)
                .bond(4, 5, SINGLE)
                .declare(new DoubleBond(1, 2, first, 0, 3))
                .declare(new DoubleBond(3, 4, second, 2, 5));
    }

    private static int[] findAutomorphism(TestGraph graph, int fromAtom, int toAtom)
    {
        Optional<int[]> mapping = GraphAutomorphismDetector.findAutomorphism(
                graph,
                graph.atomClasses(),
                fromAtom,
                toAtom);

        return mapping.orElseThrow(() -> new AssertionError("No automorphism found"));
    }

    private static boolean areEquivalent(TestGraph graph, int firstAtom, int secondAtom)
    {
        return GraphAutomorphismDetector.areEquivalent(
                graph,
                graph.atomClasses(),
                firstAtom,
                secondAtom);
    }

    private static final class TestGraph implements Graph
    {
        private final int[] atomClasses;
        private final int[][] bondClasses;
        private final List<StereoDeclaration> stereochemistry = new ArrayList<>();

        TestGraph(int... atomClasses)
        {
            this.atomClasses = atomClasses;
            this.bondClasses = new int[atomClasses.length][atomClasses.length];
        }

        TestGraph bond(int firstAtom, int secondAtom, int bondClass)
        {
            bondClasses[firstAtom][secondAtom] = bondClass;
            bondClasses[secondAtom][firstAtom] = bondClass;

            return this;
        }

        TestGraph declare(StereoDeclaration element)
        {
            stereochemistry.add(element);

            return this;
        }

        int[] atomClasses()
        {
            return atomClasses;
        }

        @Override
        public int atomCount()
        {
            return bondClasses.length;
        }

        @Override
        public int bondClass(int firstAtom, int secondAtom)
        {
            return bondClasses[firstAtom][secondAtom];
        }

        @Override
        public List<StereoDeclaration> stereochemistry()
        {
            return stereochemistry;
        }
    }
}