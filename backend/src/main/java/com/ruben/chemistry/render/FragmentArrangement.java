
package com.ruben.chemistry.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How disconnected molecular fragments are arranged on the page
 */
public sealed interface FragmentArrangement
{
    /** Every fragment on one row */
    Linear ROW = new Linear(false, 0);

    /**
     * Reads {@code row}, {@code column}, {@code row:<n>}, {@code column:<n>} or {@code grid:<template>}
     *
     * @param text the arrangement
     * @return what it describes
     * @throws IllegalArgumentException if the text is not understood
     */
    static FragmentArrangement parse(String text)
    {
        String value = text.strip();

        if (value.regionMatches(true, 0, "grid:", 0, 5))
        {
            return parseGrid(value.substring(5).toLowerCase(Locale.ROOT));
        }

        return parseLinear(value);
    }

    /**
     * Reads a row or column arrangement
     *
     * @param text the arrangement
     * @return what it describes
     * @throws IllegalArgumentException if the text is not understood
     */
    static Linear parseLinear(String text)
    {
        Matcher line = Pattern.compile("(row|column)(?::(\\d+))?")
                .matcher(text.strip().toLowerCase(Locale.ROOT));

        if (!line.matches())
        {
            throw new IllegalArgumentException("'" + text + "' is not row, column, row:<n>, column:<n> or grid:<template>");
        }

        int perLine = 0;
        if (line.group(2) != null)
        {
            perLine = Integer.parseInt(line.group(2));
            if (perLine < 1)
            {
                throw new IllegalArgumentException("'" + text + "' must allow at least one fragment per line");
            }
        }

        return new Linear(line.group(1).equals("column"), perLine);
    }

    /**
     * Reads a grid template.
     * <p>
     * Rows are separated by {@code /}, and cells by spaces.
     * A run of {@code x} holds a fragment, while a run of {@code .} reserves empty
     * space. The length of each run determines its share of the row's width.
     *
     * @param template the template, without its {@code grid:} prefix
     * @return what it describes
     * @throws IllegalArgumentException if the template is not understood
     */
    private static Grid parseGrid(String template)
    {
        List<List<GridPosition>> rows = new ArrayList<>();

        for (String row : template.split("/", -1))
        {
            if (row.isBlank())
            {
                throw new IllegalArgumentException("A grid cannot have an empty row: '" + template + "'");
            }

            List<GridPosition> positions = new ArrayList<>();
            for (String token : row.strip().split("\\s+"))
            {
                if (!token.matches("x+|\\.+"))
                {
                    throw new IllegalArgumentException("'" + token + "' is not a run of x or . in a grid");
                }

                positions.add(new GridPosition(
                        token.length(),
                        token.charAt(0) == 'x'));
            }

            rows.add(positions);
        }

        // the final row must accept fragments left over from the template
        if (rows.getLast().stream().noneMatch(GridPosition::occupied))
        {
            throw new IllegalArgumentException("The last row of a grid must hold an x, since it repeats for any fragments left over");
        }

        return new Grid(rows);
    }

    /**
     * Fragments left exactly where the depiction puts them, as a view of them in space has them
     */
    record AsPlaced() implements FragmentArrangement
    {
    }

    /**
     * A fragment's position and size in a regular grid
     *
     * @param row the first row it occupies
     * @param column the first column it occupies
     * @param rowSpan how many rows it occupies
     * @param columnSpan how many columns it occupies
     */
    record Position(int row, int column, int rowSpan, int columnSpan)
    {
    }

    /**
     * Fragments arranged along rows or columns
     *
     * @param vertical whether fragments run down each column before starting the next column, rather than across each row
     * @param perLine how many fragments a row or column holds, or {@code 0} for no limit
     */
    record Linear(boolean vertical, int perLine) implements FragmentArrangement
    {
        public Linear
        {
            if (perLine < 0)
            {
                throw new IllegalArgumentException("A line cannot hold " + perLine + " fragments");
            }
        }

        /**
         * Finds a fragment's position in the arrangement
         *
         * @param index the fragment's position in order
         * @return where the fragment sits
         */
        public Position positionOf(int index)
        {
            int along = perLine == 0 ? index : index % perLine;
            int across = perLine == 0 ? 0 : index / perLine;

            return vertical
                    ? new Position(along, across, 1, 1)
                    : new Position(across, along, 1, 1);
        }
    }

    /**
     * A position in a grid template
     *
     * @param width the position's share of its row
     * @param occupied whether a fragment is placed here
     */
    record GridPosition(int width, boolean occupied)
    {
    }

    /**
     * A fragment's allocated space in a grid
     *
     * @param row the row it occupies
     * @param column the position it occupies within the row
     * @param columnSpan how many positions the row contains
     * @param before the combined width of the preceding positions
     * @param width the fragment's share of the row
     * @param total the combined width of the whole row
     */
    record GridSlot(int row, int column, int columnSpan, int before, int width, int total)
    {
    }

    /**
     * Fragments arranged in rows of variable-width positions. Each row shares
     * its width according to its positions, and rows are stretched to match.
     * The final row repeats for any fragments left over.
     *
     * @param rows the grid template
     */
    record Grid(List<List<GridPosition>> rows) implements FragmentArrangement
    {
        public Grid
        {
            rows = rows.stream().map(List::copyOf).toList();
        }

        /**
         * Finds the space allocated to a fragment
         *
         * @param index the fragment's position in order
         * @return its grid position
         */
        public GridSlot slotOf(int index)
        {
            int remaining = index;

            // find the template row containing this fragment
            for (int row = 0; row < rows.size(); row++)
            {
                int occupied = (int) rows.get(row).stream()
                        .filter(GridPosition::occupied)
                        .count();

                if (remaining < occupied)
                {
                    return slotIn(row, remaining);
                }

                remaining -= occupied;
            }

            // repeat the final row until every fragment has a position
            int perRow = (int) rows.getLast().stream()
                    .filter(GridPosition::occupied)
                    .count();

            return slotIn(
                    rows.size() - 1,
                    remaining % perRow,
                    rows.size() + remaining / perRow);
        }

        private GridSlot slotIn(int template, int nth)
        {
            return slotIn(template, nth, template);
        }

        /**
         * Finds an occupied position within a template row
         *
         * @param template the row defining the layout
         * @param nth which occupied position to use, starting at zero
         * @param row the actual row occupied by the fragment
         */
        private GridSlot slotIn(int template, int nth, int row)
        {
            List<GridPosition> positions = rows.get(template);
            int total = positions.stream().mapToInt(GridPosition::width).sum();
            int before = 0;

            for (int column = 0; column < positions.size(); column++)
            {
                GridPosition position = positions.get(column);

                if (position.occupied() && nth-- == 0)
                {
                    return new GridSlot(
                            row,
                            column,
                            positions.size(),
                            before,
                            position.width(),
                            total);
                }

                // empty positions also contribute to the layout width
                before += position.width();
            }

            throw new IllegalStateException("No occupied position " + nth + " in template row " + template);
        }
    }
}
