package com.ruben.chemistry.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FragmentArrangementTest
{
    @Test
    void row_puts_every_part_on_the_one_row()
    {
        FragmentArrangement row = FragmentArrangement.parse("row");

        for (int index = 0; index < 5; index++)
        {
            assertThat(row.rowOf(index)).isZero();
            assertThat(row.columnOf(index)).isEqualTo(index);
        }
    }

    @Test
    void column_puts_every_part_in_the_one_column()
    {
        FragmentArrangement column = FragmentArrangement.parse("column");

        for (int index = 0; index < 5; index++)
        {
            assertThat(column.columnOf(index)).isZero();
            assertThat(column.rowOf(index)).isEqualTo(index);
        }
    }

    @Test
    void a_row_limit_fills_each_row_before_starting_the_next()
    {
        FragmentArrangement wrapped = FragmentArrangement.parse("row:2");

        assertThat(wrapped.rowOf(0)).isZero();
        assertThat(wrapped.columnOf(1)).isEqualTo(1);
        assertThat(wrapped.rowOf(2)).isEqualTo(1);
        assertThat(wrapped.columnOf(2)).isZero();
        assertThat(wrapped.rowOf(4)).isEqualTo(2);
    }

    @Test
    void a_column_limit_fills_each_column_before_starting_the_next()
    {
        FragmentArrangement wrapped = FragmentArrangement.parse("column:2");

        assertThat(wrapped.columnOf(0)).isZero();
        assertThat(wrapped.rowOf(1)).isEqualTo(1);
        assertThat(wrapped.columnOf(2)).isEqualTo(1);
        assertThat(wrapped.rowOf(2)).isZero();
        assertThat(wrapped.columnOf(4)).isEqualTo(2);
    }

    @Test
    void has_no_limit_on_how_many_parts_it_takes()
    {
        assertThat(FragmentArrangement.parse("row:3").rowOf(1000)).isEqualTo(333);
    }

    @Test
    void ignores_case_and_surrounding_space()
    {
        assertThat(FragmentArrangement.parse("  Column:4 ")).isEqualTo(new FragmentArrangement(true, 4));
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
        assertThatThrownBy(() -> new FragmentArrangement(false, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
