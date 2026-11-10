package com.ruben.chemistry.render;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How the disconnected parts of a molecule are set out on the page.
 *
 * @param columnMajor whether parts run down a column and then start a new column, rather than
 *     along a row and then starting a new row
 * @param perLine how many parts a line holds before the next is begun, or {@code 0} for no limit
 */
public record FragmentArrangement(boolean columnMajor, int perLine)
{
    /** Matches {@code row}, {@code column}, or either with a count, as in {@code row:4}. */
    private static final Pattern LINE = Pattern.compile("(row|column)(?::(\\d+))?");

    /** Every part on one row. */
    public static final FragmentArrangement ROW = new FragmentArrangement(false, 0);

    /**
     * @throws IllegalArgumentException if {@code perLine} is negative
     */
    public FragmentArrangement
    {
        if (perLine < 0)
        {
            throw new IllegalArgumentException("A line cannot hold " + perLine + " parts");
        }
    }

    /**
     * Reads {@code row}, {@code column}, {@code row:<n>} (a new row after every n parts),
     * or {@code column:<n>} (a new column after every n parts). There is no limit on how many parts
     * fit, since a full line is followed by another.
     *
     * @param text the arrangement
     * @return what it describes
     * @throws IllegalArgumentException if the text is not understood
     */
    public static FragmentArrangement parse(String text)
    {
        String value = text.strip().toLowerCase(Locale.ROOT);

        Matcher line = LINE.matcher(value);
        if (line.matches())
        {
            int count = line.group(2) == null ? 0 : Integer.parseInt(line.group(2));
            if (line.group(2) != null && count < 1)
            {
                throw new IllegalArgumentException("'" + text + "' must allow at least one part per line");
            }
            return new FragmentArrangement(line.group(1).equals("column"), count);
        }

        throw new IllegalArgumentException("'" + text + "' is not row, column, row:<n>, or column:<n>");
    }

    /**
     * @param index a part's position in order
     * @return which row the part is in
     */
    int rowOf(int index)
    {
        if (perLine == 0)
        {
            return columnMajor ? index : 0;
        }
        return columnMajor ? index % perLine : index / perLine;
    }

    /**
     * @param index a part's position in order
     * @return which column the part is in
     */
    int columnOf(int index)
    {
        if (perLine == 0)
        {
            return columnMajor ? 0 : index;
        }
        return columnMajor ? index / perLine : index % perLine;
    }
}
