package com.ruben.chemistry.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FragmentArrangementTest
{
    private static int rowOf(FragmentArrangement arrangement, int index)
    {
        return ((FragmentArrangement.Linear) arrangement).positionOf(index).row();
    }

    private static int columnOf(FragmentArrangement arrangement, int index)
    {
        return ((FragmentArrangement.Linear) arrangement).positionOf(index).column();
    }

    private static FragmentArrangement.GridSlot slot(String grid, int index)
    {
        return ((FragmentArrangement.Grid) FragmentArrangement.parse(grid)).slotOf(index);
    }

    @Test
    void grid_cells_are_runs_of_x_whose_length_is_their_share_of_the_row()
    {
        // one row of three cells, with relative widths 1, 2 and 1
        assertThat(slot("grid:x xx x", 0)).isEqualTo(new FragmentArrangement.GridSlot(0, 0, 3, 0, 1, 4));
        assertThat(slot("grid:x xx x", 1)).isEqualTo(new FragmentArrangement.GridSlot(0, 1, 3, 1, 2, 4));
        assertThat(slot("grid:x xx x", 2)).isEqualTo(new FragmentArrangement.GridSlot(0, 2, 3, 3, 1, 4));
    }

    @Test
    void grid_rows_are_separated_by_a_slash_and_each_has_its_own_total()
    {
        assertThat(slot("grid:x x x / x xx x", 3)).isEqualTo(new FragmentArrangement.GridSlot(1, 0, 3, 0, 1, 4));
        assertThat(slot("grid:x x x / x xx x", 4)).isEqualTo(new FragmentArrangement.GridSlot(1, 1, 3, 1, 2, 4));
        assertThat(slot("grid:x x x / x xx x", 0).total()).isEqualTo(3);
    }

    @Test
    void grid_repeats_its_last_row_for_parts_left_over()
    {
        assertThat(slot("grid:x x / x", 2).row()).isEqualTo(1);
        assertThat(slot("grid:x x / x", 3).row()).isEqualTo(2);
        assertThat(slot("grid:x x / x", 100).row()).isEqualTo(99);
        assertThat(slot("grid:x x / x x", 5)).isEqualTo(new FragmentArrangement.GridSlot(2, 1, 2, 1, 1, 2));
    }

    @Test
    void grid_skips_empty_cells_but_keeps_their_room()
    {
        assertThat(slot("GRID:. x / x x", 0)).isEqualTo(new FragmentArrangement.GridSlot(0, 1, 2, 1, 1, 2));
        assertThat(slot("grid:.. x", 0)).isEqualTo(new FragmentArrangement.GridSlot(0, 1, 2, 2, 1, 3));
    }

    @Test
    void rejects_bad_grids()
    {
        assertThatThrownBy(() -> FragmentArrangement.parse("grid:x 1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FragmentArrangement.parse("grid:xy")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FragmentArrangement.parse("grid:x.")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FragmentArrangement.parse("grid:. .")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FragmentArrangement.parse("grid:x / .")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FragmentArrangement.parse("grid:x / / x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FragmentArrangement.parse("grid:")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void row_puts_every_part_on_the_one_row()
    {
        FragmentArrangement row = FragmentArrangement.parse("row");

        for (int index = 0; index < 5; index++)
        {
            assertThat(rowOf(row, index)).isZero();
            assertThat(columnOf(row, index)).isEqualTo(index);
        }
    }

    @Test
    void column_puts_every_part_in_the_one_column()
    {
        FragmentArrangement column = FragmentArrangement.parse("column");

        for (int index = 0; index < 5; index++)
        {
            assertThat(columnOf(column, index)).isZero();
            assertThat(rowOf(column, index)).isEqualTo(index);
        }
    }

    @Test
    void a_row_limit_fills_each_row_before_starting_the_next()
    {
        FragmentArrangement wrapped = FragmentArrangement.parse("row:2");

        assertThat(rowOf(wrapped, 0)).isZero();
        assertThat(columnOf(wrapped, 1)).isEqualTo(1);
        assertThat(rowOf(wrapped, 2)).isEqualTo(1);
        assertThat(columnOf(wrapped, 2)).isZero();
        assertThat(rowOf(wrapped, 4)).isEqualTo(2);
    }

    @Test
    void a_column_limit_fills_each_column_before_starting_the_next()
    {
        FragmentArrangement wrapped = FragmentArrangement.parse("column:2");

        assertThat(columnOf(wrapped, 0)).isZero();
        assertThat(rowOf(wrapped, 1)).isEqualTo(1);
        assertThat(columnOf(wrapped, 2)).isEqualTo(1);
        assertThat(rowOf(wrapped, 2)).isZero();
        assertThat(columnOf(wrapped, 4)).isEqualTo(2);
    }

    @Test
    void has_no_limit_on_how_many_parts_it_takes()
    {
        assertThat(rowOf(FragmentArrangement.parse("row:3"), 1000)).isEqualTo(333);
    }

    @Test
    void ignores_case_and_surrounding_space()
    {
        assertThat(FragmentArrangement.parse("  Column:4 "))
                .isEqualTo(new FragmentArrangement.Linear(true, 4));
    }

    @Test
    void defaults_to_a_row()
    {
        assertThat(FragmentArrangement.ROW).isEqualTo(FragmentArrangement.parse("row"));
    }

    @Test
    void rejects_what_it_does_not_understand()
    {
        assertThatThrownBy(() -> FragmentArrangement.parse("diagonal")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FragmentArrangement.parse("3x2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FragmentArrangement.parse("row:0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FragmentArrangement.Linear(false, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}