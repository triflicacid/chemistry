package com.ruben.chemistry.formula;

/**
 * Unicode utilities - plus, minus, and superscript/subscript numbers.
 */
final class Unicode
{
    /** Subscript digits 0 to 9. */
    private static final String SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉";

    /** Superscript digits 0 to 9. */
    private static final String SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    /** Superscript plus. */
    static final char PLUS = '⁺';

    /** Superscript minus. */
    static final char MINUS = '⁻';

    private Unicode()
    {
    }

    /**
     * @param number the number to render, zero or above
     * @return {@code number}'s digits as subscripts
     */
    static String subscript(int number)
    {
        return render(number, SUBSCRIPTS);
    }

    /**
     * @param number the number to render, zero or above
     * @return {@code number}'s digits as superscripts
     */
    static String superscript(int number)
    {
        return render(number, SUPERSCRIPTS);
    }

    /**
     * @param number the number to render, zero or above
     * @param digits the ten digit characters to render it with, in value order
     * @return {@code number}'s digits, each swapped for its counterpart in {@code digits}
     */
    private static String render(int number, String digits)
    {
        StringBuilder text = new StringBuilder();
        for (char digit : String.valueOf(number).toCharArray())
        {
            text.append(digits.charAt(digit - '0'));
        }
        return text.toString();
    }
}
