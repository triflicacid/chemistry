package com.ruben.chemistry.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AngleFanTest
{
    private static final double TOLERANCE = 1e-9;

    /**
     * @param radians an angle in radians
     * @return the same direction in degrees, from zero up to but not including a whole turn
     */
    private static double degrees(double radians)
    {
        return Math.toDegrees(AngleFan.normalise(radians));
    }

    /**
     * @param directions the directions to check, in radians
     * @param expected the degrees each of them should point in, in order
     */
    private static void assertDegrees(List<Double> directions, double... expected)
    {
        assertThat(directions).hasSize(expected.length);
        for (int slot = 0; slot < expected.length; slot++)
        {
            assertThat(degrees(directions.get(slot))).isCloseTo(expected[slot], within(TOLERANCE));
        }
    }

    @Nested
    class EvenlyAround
    {
        @Test
        void returns_nothing_when_there_is_nothing_to_place()
        {
            assertThat(AngleFan.evenlyAround(0, 0)).isEmpty();
        }

        @Test
        void puts_a_lone_direction_at_the_start()
        {
            assertThat(AngleFan.evenlyAround(Math.PI, 1)).containsExactly(Math.PI);
        }

        @Test
        void spreads_four_directions_a_quarter_turn_apart()
        {
            double[] directions = AngleFan.evenlyAround(0, 4);

            assertThat(directions).hasSize(4);
            for (int slot = 0; slot < 4; slot++)
            {
                assertThat(degrees(directions[slot])).isCloseTo(90.0 * slot, within(TOLERANCE));
            }
        }

        @Test
        void measures_the_spread_from_the_start()
        {
            double[] directions = AngleFan.evenlyAround(Math.toRadians(-30), 4);

            assertThat(degrees(directions[0])).isCloseTo(330, within(TOLERANCE));
            assertThat(degrees(directions[1])).isCloseTo(60, within(TOLERANCE));
        }
    }

    @Nested
    class FillGaps
    {
        @Test
        void returns_nothing_when_nothing_more_is_needed()
        {
            assertThat(AngleFan.fillGaps(List.of(0.0), 0)).isEmpty();
        }

        @Test
        void rejects_filling_gaps_between_no_directions_at_all()
        {
            assertThatThrownBy(() -> AngleFan.fillGaps(List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already fixed");
        }

        @Test
        void spreads_three_directions_evenly_around_a_single_fixed_one()
        {
            assertDegrees(AngleFan.fillGaps(List.of(Math.PI), 3), 270, 0, 90);
        }

        @Test
        void halves_each_of_two_equal_gaps_before_quartering_either()
        {
            assertDegrees(AngleFan.fillGaps(List.of(0.0, Math.PI), 2), 90, 270);
        }

        @Test
        void fills_the_roomier_gap_first()
        {
            // a quarter turn one side of the fixed directions and three quarters the other
            assertDegrees(AngleFan.fillGaps(List.of(0.0, Math.PI / 2), 1), 225);
        }

        @Test
        void keeps_every_direction_clear_of_the_ones_already_fixed()
        {
            List<Double> fixed = List.of(0.0, Math.toRadians(45));

            for (double direction : AngleFan.fillGaps(fixed, 5))
            {
                for (double edge : fixed)
                {
                    assertThat(degrees(direction)).isNotCloseTo(degrees(edge), within(1e-6));
                }
            }
        }
    }

    @Nested
    class Normalise
    {
        @Test
        void brings_a_negative_angle_back_into_a_single_turn()
        {
            assertThat(degrees(Math.toRadians(-90))).isCloseTo(270, within(TOLERANCE));
        }

        @Test
        void brings_an_angle_beyond_a_whole_turn_back_into_one()
        {
            assertThat(degrees(Math.toRadians(450))).isCloseTo(90, within(TOLERANCE));
        }
    }

    @Nested
    class Mirror
    {
        @Test
        void leaves_the_axis_itself_alone()
        {
            double axis = Math.toRadians(37);

            assertThat(degrees(AngleFan.mirror(axis, axis))).isCloseTo(37, within(TOLERANCE));
        }

        @Test
        void leaves_the_direction_opposite_the_axis_alone()
        {
            double axis = Math.toRadians(37);

            assertThat(degrees(AngleFan.mirror(axis + Math.PI, axis))).isCloseTo(217, within(TOLERANCE));
        }

        @Test
        void swaps_a_direction_over_to_the_other_side()
        {
            assertThat(degrees(AngleFan.mirror(Math.toRadians(90), 0))).isCloseTo(270, within(TOLERANCE));
        }
    }
}
