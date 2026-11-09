package com.ruben.chemistry.formula;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.canonical.Ranker;
import com.ruben.chemistry.core.Molecule;

/**
 * Configuration for writing a molecular formula.
 * {@link FormulaStyle} is included in this, but this interface offers more options such as condensed formulae.
 */
public sealed interface FormulaSettings
{
    /**
     * @return the style chosen for this rendering option.
     */
    FormulaStyle style();

    /**
     * @return these settings, with {@code style} replaced
     */
    FormulaSettings withStyle(FormulaStyle style);

    /**
     * Writes a molecule's formula as these settings describe.
     *
     * @param molecule the molecule to write
     * @return the formula as text
     */
    String write(Molecule molecule);

    /**
     * @return settings writing a molecular formula, e.g. {@code C2H6O}, with everything left at its default
     */
    static Molecular molecular()
    {
        return new Molecular(FormulaStyle.PLAIN, false);
    }

    /**
     * @return settings writing a condensed structural formula, e.g. {@code CH3CH2OH}, with everything left at its default
     */
    static Condensed condensed()
    {
        return new Condensed(FormulaStyle.PLAIN, true, true, Condensed.DEFAULT_MINIMUM_REPEAT, null);
    }

    /**
     * The choices made when writing a {@link MolecularFormula}.
     *
     * @param style the text-level choices shared by every kind of formula
     * @param empirical whether to reduce the counts to their simplest whole-number ratio, e.g. {@code CH2O} rather than glucose's {@code C6H12O6}
     */
    record Molecular(FormulaStyle style, boolean empirical) implements FormulaSettings
    {
        @Override
        public Molecular withStyle(FormulaStyle style)
        {
            return new Molecular(style, empirical);
        }

        @Override
        public String write(Molecule molecule)
        {
            MolecularFormula formula = MolecularFormula.of(molecule);
            return (empirical ? formula.empirical() : formula).render(style);
        }

        /**
         * @param empirical whether to reduce the counts to their simplest whole-number ratio
         * @return these settings, with {@code empirical} replaced
         */
        public Molecular withEmpirical(boolean empirical)
        {
            return new Molecular(style, empirical);
        }
    }

    /**
     * The choices made when writing a condensed structural formula.
     *
     * @param style the text-level choices shared by every kind of formula
     * @param renderBondSymbols whether to write a symbol for a bond above single order, so ethene is
     *     {@code CH2=CH2} rather than {@code CH2CH2}
     * @param collapseRepeats whether to collapse runs of interchangeable groups, so hexane is
     *     {@code CH3(CH2)4CH3} rather than {@code CH3CH2CH2CH2CH2CH3}
     * @param minimumRepeat the shortest run of interchangeable groups to collapse, at least two,
     *     ignored unless {@code collapseRepeats}
     * @param ranker the ranker driving deterministic atom and branch ordering, or {@code null} to
     *     follow the order the molecule was built in
     */
    record Condensed(
        FormulaStyle style,
        boolean renderBondSymbols,
        boolean collapseRepeats,
        int minimumRepeat,
        Ranker ranker) implements FormulaSettings
    {
        /** The shortest run of interchangeable groups collapsed unless told otherwise. */
        public static final int DEFAULT_MINIMUM_REPEAT = 2;

        public Condensed
        {
            if (minimumRepeat < 2)
            {
                throw new IllegalArgumentException("a repeat group stands for at least two groups: " + minimumRepeat);
            }
        }

        @Override
        public Condensed withStyle(FormulaStyle style)
        {
            return new Condensed(style, renderBondSymbols, collapseRepeats, minimumRepeat, ranker);
        }

        @Override
        public String write(Molecule molecule)
        {
            return new CondensedFormulaWriter(molecule)
                .setUnicode(style.unicode())
                .setElementOrder(style.elementOrder())
                .setIsotopeStyle(style.isotopeStyle())
                .setRenderBondSymbols(renderBondSymbols)
                .setCollapseRepeats(collapseRepeats)
                .setMinimumRepeat(minimumRepeat)
                .setRanker(ranker)
                .write();
        }

        /**
         * @param renderBondSymbols whether to write a symbol for a bond above single order
         * @return these settings, with {@code renderBondSymbols} replaced
         */
        public Condensed withRenderBondSymbols(boolean renderBondSymbols)
        {
            return new Condensed(style, renderBondSymbols, collapseRepeats, minimumRepeat, ranker);
        }

        /**
         * @param collapseRepeats whether to collapse runs of interchangeable groups
         * @return these settings, with {@code collapseRepeats} replaced
         */
        public Condensed withCollapseRepeats(boolean collapseRepeats)
        {
            return new Condensed(style, renderBondSymbols, collapseRepeats, minimumRepeat, ranker);
        }

        /**
         * A run must also save characters to be worth collapsing, so at the default of two butane
         * stays {@code CH3CH2CH2CH3} as {@code CH3(CH2)2CH3} is no shorter, while hexane becomes
         * {@code CH3(CH2)4CH3}.
         *
         * @param minimumRepeat the shortest run to collapse, at least two
         * @return these settings, with {@code minimumRepeat} replaced
         * @throws IllegalArgumentException if {@code minimumRepeat} is below two
         */
        public Condensed withMinimumRepeat(int minimumRepeat)
        {
            return new Condensed(style, renderBondSymbols, collapseRepeats, minimumRepeat, ranker);
        }

        /**
         * @param ranker the ranker to order atoms and branches by, or {@code null} to follow build order
         * @return these settings, with {@code ranker} replaced
         */
        public Condensed withRanker(Ranker ranker)
        {
            return new Condensed(style, renderBondSymbols, collapseRepeats, minimumRepeat, ranker);
        }

        /**
         * Orders atoms and branches by a {@link CanonicalRanker}.
         *
         * @return these settings, ranked canonically
         */
        public Condensed canonical()
        {
            return withRanker(new CanonicalRanker());
        }
    }
}
