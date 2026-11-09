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
    /** Four ligands a quarter turn apart, the first of which carries the stereo bond. */
    private static final List<Point> CROSS = List.of(
        Point.polar(0, 1),
        Point.polar(Math.PI / 2, 1),
        Point.polar(Math.PI, 1),
        Point.polar(3 * Math.PI / 2, 1));

    /**
     * @param style how the bond to the first ligand leaves the plane of the page
     * @return the depth of each of the four ligands of {@link #CROSS}
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
            List<Point> three = CROSS.subList(0, 3);

            assertThatThrownBy(() -> Parity.signedVolume(Point.ORIGIN, three, new double[3]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs 4 ligands");
        }

        @Test
        void measures_a_proper_tetrahedron_from_its_first_ligand()
        {
            // the first ligand points straight out of the page, so sits on top of the centre, and
            // the other three run anticlockwise around the plane a third of the way below it
            List<Point> ligands = List.of(
                Point.ORIGIN,
                Point.polar(0, 1),
                Point.polar(Math.toRadians(120), 1),
                Point.polar(Math.toRadians(240), 1));
            double[] depths = {1, -1 / 3.0, -1 / 3.0, -1 / 3.0};

            assertThat(Parity.signedVolume(Point.ORIGIN, ligands, depths)).isCloseTo(-Math.sqrt(12), within(1e-9));
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
            List<Point> ligands = List.of(
                Point.ORIGIN,
                Point.polar(0, 1),
                Point.polar(Math.toRadians(120), 1),
                Point.polar(Math.toRadians(240), 1));
            double[] depths = {1, -1 / 3.0, -1 / 3.0, -1 / 3.0};

            assertThat(Parity.read(Point.ORIGIN, ligands, depths)).contains(Chirality.ANTICLOCKWISE);
        }

        @Test
        void reads_a_wedge_to_the_first_of_four_quartered_ligands_as_anticlockwise()
        {
            assertThat(Parity.read(Point.ORIGIN, CROSS, depthsOf(BondStyle.WEDGE))).contains(Chirality.ANTICLOCKWISE);
        }

        @Test
        void reads_the_same_arrangement_hashed_as_the_opposite_parity()
        {
            assertThat(Parity.read(Point.ORIGIN, CROSS, depthsOf(BondStyle.HASH))).contains(Chirality.CLOCKWISE);
        }

        @Test
        void swaps_parity_when_two_ligands_swap_places()
        {
            List<Point> swapped = List.of(CROSS.get(0), CROSS.get(2), CROSS.get(1), CROSS.get(3));

            assertThat(Parity.read(Point.ORIGIN, CROSS, depthsOf(BondStyle.WEDGE))).contains(Chirality.ANTICLOCKWISE);
            assertThat(Parity.read(Point.ORIGIN, swapped, depthsOf(BondStyle.WEDGE))).contains(Chirality.CLOCKWISE);
        }
    }
}
