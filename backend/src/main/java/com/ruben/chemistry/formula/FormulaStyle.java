package com.ruben.chemistry.formula;

/**
 * The text-level choices when rendering a molecular formula.
 *
 * @param unicode whether to write counts and charges as subscripts and superscripts rather than as plain digits
 * @param elementOrder the order to write the elements in
 * @param isotopeStyle how to name an isotopically labelled nuclide
 */
public record FormulaStyle(boolean unicode, ElementOrder elementOrder, IsotopeStyle isotopeStyle)
{
    /** Plain digits, conventional element order, every labelled nuclide written with its mass number. */
    public static final FormulaStyle PLAIN = new FormulaStyle(false, ElementOrder.CONVENTIONAL, IsotopeStyle.MASS_NUMBER);

    /** As {@link #PLAIN}, except that counts and charges are written in Unicode. */
    public static final FormulaStyle UNICODE = new FormulaStyle(true, ElementOrder.CONVENTIONAL, IsotopeStyle.MASS_NUMBER);

    /**
     * @param unicode whether to write counts and charges in Unicode
     * @return this style, with {@code unicode} replaced
     */
    public FormulaStyle withUnicode(boolean unicode)
    {
        return new FormulaStyle(unicode, elementOrder, isotopeStyle);
    }

    /**
     * @param elementOrder the order to write the elements in
     * @return this style, with {@code elementOrder} replaced
     */
    public FormulaStyle withElementOrder(ElementOrder elementOrder)
    {
        return new FormulaStyle(unicode, elementOrder, isotopeStyle);
    }

    /**
     * @param isotopeStyle how to name a labelled nuclide
     * @return this style, with {@code isotopeStyle} replaced
     */
    public FormulaStyle withIsotopeStyle(IsotopeStyle isotopeStyle)
    {
        return new FormulaStyle(unicode, elementOrder, isotopeStyle);
    }
}
