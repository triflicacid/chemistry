package com.ruben.chemistry.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How the disconnected parts of a molecule are set out on the page.
 */
public sealed interface FragmentArrangement
{
    /** Every part on one row. */
    Lines ROW = new Lines(false, 0);

    /**
     * Reads {@code row}, {@code column}, {@code row:<n>} (a new row after every n parts),
     * {@code column:<n>} (a new column after every n parts), or {@code grid:<template>}.
     *
     * @param text the arrangement
     * @return what it describes
     * @throws IllegalArgumentException if the text is not understood
     * @see #parseGrid(String)
     */
    static FragmentArrangement parse(String text)
    {
        String value = text.strip();
        if (value.regionMatches(true, 0, "grid:", 0, 5))
        {
            return parseGrid(value.substring(5).toLowerCase(Locale.ROOT));
        }
        return parseLines(value);
    }

    /**
     * @param text the arrangement
     * @return what it describes
     * @throws IllegalArgumentException if the text is not understood
     */
    static Lines parseLines(String text)
    {
        Matcher line = Pattern.compile("(row|column)(?::(\\d+))?").matcher(text.strip().toLowerCase(Locale.ROOT));
        if (!line.matches())
        {
            throw new IllegalArgumentException("'" + text + "' is not row, column, row:<n>, column:<n> or grid:<template>");
        }

        int count = 0;
        if (line.group(2) != null)
        {
            count = Integer.parseInt(line.group(2));
            if (count < 1)
            {
                throw new IllegalArgumentException("'" + text + "' must allow at least one part per line");
            }
        }
        return new Lines(line.group(1).equals("column"), count);
    }

    /**
     * Reads a grid template. Rows are separated by {@code /}, and the cells of a row by spaces. A cell is
     * a run of {@code x}, which takes the next part in order, or a run of {@code .}, which is left empty.
     * The length of the run is the cell's share of the row, so {@code x xx x} is one row where the middle
     * part gets twice the width of each of the others. Every row is stretched to the same width.
     *
     * @param template the template, without its {@code grid:} prefix
     * @return what it describes
     * @throws IllegalArgumentException if the template is not understood
     */
    private static WeightedGrid parseGrid(String template)
    {
        List<List<WeightedCell>> rows = new ArrayList<>();
        for (String row : template.split("/", -1))
        {
            if (row.isBlank())
            {
                throw new IllegalArgumentException("A grid cannot have an empty row: '" + template + "'");
            }

            List<WeightedCell> cells = new ArrayList<>();
            for (String token : row.strip().split("\\s+"))
            {
                if (!token.matches("x+|\\.+"))
                {
                    throw new IllegalArgumentException("'" + token + "' is not a run of x or . in a grid");
                }
                cells.add(new WeightedCell(token.length(), token.charAt(0) == 'x'));
            }
            rows.add(cells);
        }

        if (rows.getLast().stream().noneMatch(WeightedCell::filled))
        {
            throw new IllegalArgumentException("The last row of a grid must hold an x, since it repeats for any parts left over");
        }
        return new WeightedGrid(rows);
    }

    /**
     * Where a part sits in the grid of parts.
     *
     * @param row the first row it covers
     * @param column the first column it covers
     * @param rowSpan how many rows it covers
     * @param columnSpan how many columns it covers
     */
    record Cell(int row, int column, int rowSpan, int columnSpan)
    {
    }

    /**
     * Parts in order along a row or a column.
     *
     * @param columnMajor whether parts run down a column and then start a new column, rather than
     *     along a row and then starting a new row
     * @param perLine how many parts a line holds before the next is begun, or {@code 0} for no limit
     */
    record Lines(boolean columnMajor, int perLine) implements FragmentArrangement
    {
        public Lines
        {
            if (perLine < 0)
            {
                throw new IllegalArgumentException("A line cannot hold " + perLine + " parts");
            }
        }

        /**
         * @param index a part's position in order
         * @return where in the grid that part sits
         */
        public Cell cellOf(int index)
        {
            int along = perLine == 0 ? index : index % perLine;
            int across = perLine == 0 ? 0 : index / perLine;
            return columnMajor ? new Cell(along, across, 1, 1) : new Cell(across, along, 1, 1);
        }
    }

    /**
     * A cell of a {@link WeightedGrid}'s template.
     *
     * @param weight the cell's share of its row
     * @param filled whether a part goes in it, rather than it being left empty
     */
    record WeightedCell(int weight, boolean filled)
    {
    }

    /**
     * Where a part sits in a {@link WeightedGrid}.
     *
     * @param row the row it is in
     * @param index how many cells, empty ones included, come before it in its row
     * @param cells how many cells its row has
     * @param before the weight of the cells before it in its row
     * @param weight its own weight
     * @param total the weight of its whole row
     */
    record Slot(int row, int index, int cells, int before, int weight, int total)
    {
    }

    /**
     * A grid whose parts fill the template's {@code x} cells in order. Each row's width is shared out
     * by weight, and rows are stretched to match each other rather than lining up in columns.
     *
     * @param rows the template's rows
     */
    record WeightedGrid(List<List<WeightedCell>> rows) implements FragmentArrangement
    {
        public WeightedGrid
        {
            rows = rows.stream().map(List::copyOf).toList();
        }

        /**
         * @param index a part's position in order
         * @return where it sits; parts past the template repeat its last row below
         */
        public Slot slotOf(int index)
        {
            int remaining = index;
            for (int row = 0; row < rows.size(); row++)
            {
                int filled = (int) rows.get(row).stream().filter(WeightedCell::filled).count();
                if (remaining < filled)
                {
                    return slotIn(row, remaining);
                }
                remaining -= filled;
            }

            int perRow = (int) rows.getLast().stream().filter(WeightedCell::filled).count();
            return slotIn(rows.size() - 1, remaining % perRow, rows.size() + remaining / perRow);
        }

        private Slot slotIn(int template, int nth)
        {
            return slotIn(template, nth, template);
        }

        /**
         * @param template the template row to follow
         * @param nth which of its filled cells, from 0
         * @param row the row to place the part in
         */
        private Slot slotIn(int template, int nth, int row)
        {
            List<WeightedCell> cells = rows.get(template);
            int total = cells.stream().mapToInt(WeightedCell::weight).sum();
            int before = 0;
            for (int index = 0; index < cells.size(); index++)
            {
                WeightedCell cell = cells.get(index);
                if (cell.filled() && nth-- == 0)
                {
                    return new Slot(row, index, cells.size(), before, cell.weight(), total);
                }
                before += cell.weight();
            }
            throw new IllegalStateException("No filled cell " + nth + " in row " + template);
        }
    }
}
