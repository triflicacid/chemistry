package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Chirality;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ParityTest
{
    private static final int LIGANDS = Parity.LIGANDS;
    private static final double BOND_LENGTH = 1;
    private static final double QUARTER_TURN = Math.PI / 2;
    private static final double THIRD_TURN = 2 * Math.PI / 3;
    private static final double TETRAHEDRON_DEPTH = -1.0 / 3;
    private static final double EXPECTED_VOLUME = -Math.sqrt(12);
    private static final double TOLERANCE = 1e-9;

    /** Four ligands a quarter turn apart, with the first carrying the stereo bond */
    private static final List<Point> CROSS = List.of(
            Point.polar(0, BOND_LENGTH),
            Point.polar(QUARTER_TURN, BOND_LENGTH),
            Point.polar(2 * QUARTER_TURN, BOND_LENGTH),
            Point.polar(3 * QUARTER_TURN, BOND_LENGTH));

    /** A tetrahedron viewed from its first ligand, with the other three below the page */
    private static final List<Point> TETRAHEDRON = List.of(
            Point.ORIGIN,
            Point.polar(0, BOND_LENGTH),
            Point.polar(THIRD_TURN, BOND_LENGTH),
            Point.polar(2 * THIRD_TURN, BOND_LENGTH));

    private static final double[] TETRAHEDRON_DEPTHS = {
            BOND_LENGTH, TETRAHEDRON_DEPTH, TETRAHEDRON_DEPTH, TETRAHEDRON_DEPTH};

    /**
     * @param style how the bond to the first ligand leaves the page
     * @return the depths of the four ligands in {@link #CROSS}
     */
    private static double[] depthsOf(BondStyle style)
    {
        return new double[] {Parity.depthOf(style), 0, 0, 0};
    }

    @Nested
    class DepthOf
    {
        @Test
        void leaves_a_plain_bond_in_the_plane_of_the_page()
        {
            assertThat(Parity.depthOf(BondStyle.PLAIN)).isZero();
        }

        @Test
        void carries_a_wedge_out_of_the_page_and_a_hash_into_it()
        {
            assertThat(Parity.depthOf(BondStyle.WEDGE)).isEqualTo(Parity.STEREO_DEPTH);
            assertThat(Parity.depthOf(BondStyle.HASH)).isEqualTo(-Parity.STEREO_DEPTH);
        }
    }

    @Nested
    class SignedVolume
    {
        @Test
        void rejects_an_arrangement_that_is_not_tetrahedral()
        {
            List<Point> three = CROSS.subList(0, LIGANDS - 1);

            assertThatThrownBy(() -> Parity.signedVolume(Point.ORIGIN, three, new double[LIGANDS - 1]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("needs " + LIGANDS + " ligands");
        }

        @Test
        void measures_a_proper_tetrahedron_from_its_first_ligand()
        {
            assertThat(Parity.signedVolume(Point.ORIGIN, TETRAHEDRON, TETRAHEDRON_DEPTHS))
                    .isCloseTo(EXPECTED_VOLUME, within(TOLERANCE));
        }

        @Test
        void is_flat_when_no_bond_leaves_the_page()
        {
            assertThat(Parity.signedVolume(Point.ORIGIN, CROSS, depthsOf(BondStyle.PLAIN))).isZero();
        }
    }

    @Nested
    class Read
    {
        @Test
        void reads_nothing_from_an_arrangement_lying_flat_on_the_page()
        {
            assertThat(Parity.read(Point.ORIGIN, CROSS, depthsOf(BondStyle.PLAIN))).isEmpty();
        }

        @Test
        void reads_a_tetrahedron_wound_anticlockwise_from_its_first_ligand()
        {
            assertThat(Parity.read(Point.ORIGIN, TETRAHEDRON, TETRAHEDRON_DEPTHS))
                    .contains(Chirality.ANTICLOCKWISE);
        }

        @Test
        void reads_a_wedge_to_the_first_of_four_quartered_ligands_as_anticlockwise()
        {
            assertThat(Parity.read(Point.ORIGIN, CROSS, depthsOf(BondStyle.WEDGE)))
                    .contains(Chirality.ANTICLOCKWISE);
        }

        @Test
        void reads_the_same_arrangement_hashed_as_the_opposite_parity()
        {
            assertThat(Parity.read(Point.ORIGIN, CROSS, depthsOf(BondStyle.HASH)))
                    .contains(Chirality.CLOCKWISE);
        }

        @Test
        void swaps_parity_when_two_ligands_swap_places()
        {
            List<Point> swapped = List.of(CROSS.get(0), CROSS.get(2), CROSS.get(1), CROSS.get(3));

            assertThat(Parity.read(Point.ORIGIN, CROSS, depthsOf(BondStyle.WEDGE)))
                    .contains(Chirality.ANTICLOCKWISE);
            assertThat(Parity.read(Point.ORIGIN, swapped, depthsOf(BondStyle.WEDGE)))
                    .contains(Chirality.CLOCKWISE);
        }
    }
}