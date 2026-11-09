package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class FragmentedFormulaTest
{
    /** Finds every fragment alike, so all of them gather into one unit. */
    private static final Comparator<Molecule> ALIKE = (first, second) -> 0;

    /** Tells every fragment apart, so none of them gather. */
    private static final Comparator<Molecule> DISTINCT = (first, second) -> 1;

    /** Tells fragments apart by basic formula (good enough for testing.) */
    private static final Comparator<Molecule> BY_FORMULA = Comparator.comparing(molecule -> MolecularFormula.of(molecule).toString());

    @Nested
    class Grouping
    {
        @Test
        void counts_the_whole_molecule_as_one_unit_when_not_grouping()
        {
            FragmentedFormula formula = FragmentedFormula.of(copperSulfatePentahydrate(), FragmentGrouping.NONE, BY_FORMULA);

            assertThat(formula.unitCount()).isEqualTo(1);
            assertThat(formula).hasToString("CuH10SO9");
        }

        @Test
        void writes_each_ion_separately_when_grouping_by_component()
        {
            FragmentedFormula formula = FragmentedFormula.of(copperSulfatePentahydrate(), FragmentGrouping.COMPONENTS, BY_FORMULA);

            assertThat(formula).hasToString("Cu2+.SO42-.5H2O");
        }

        @Test
        void combines_the_ions_into_a_salt_when_grouping_into_neutral_units()
        {
            FragmentedFormula formula = FragmentedFormula.of(copperSulfatePentahydrate(), FragmentGrouping.NEUTRAL_UNITS, BY_FORMULA);

            assertThat(formula).hasToString("CuSO4.5H2O");
            assertThat(formula.units()).extracting(FormulaUnit::count).containsExactly(1, 5);
        }

        @Test
        void combines_more_than_two_ions_into_one_unit()
        {
            // [Na+].[Na+].[O-]S(=O)(=O)[O-]
            Molecule.Builder builder = new Molecule.Builder()
                .addAtom(Atom.builder("Na").charge(1).hydrogenCount(0).build())
                .addAtom(Atom.builder("Na").charge(1).hydrogenCount(0).build());
            addSulfate(builder);

            assertThat(FragmentedFormula.of(builder.build(), FragmentGrouping.NEUTRAL_UNITS, BY_FORMULA))
                .hasToString("Na2SO4");
        }

        @Test
        void leaves_a_unit_charged_when_the_ions_do_not_cancel()
        {
            Molecule.Builder builder = new Molecule.Builder();
            addSulfate(builder);
            builder.addAtom(Atom.organic("O"));

            assertThat(FragmentedFormula.of(builder.build(), FragmentGrouping.NEUTRAL_UNITS, BY_FORMULA))
                .hasToString("SO42-.H2O");
        }

        @Test
        void writes_a_connected_molecule_as_a_single_unit()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, o)
                .addBond(c, o, BondOrder.SINGLE)
                .build();

            assertThat(FragmentedFormula.of(molecule, FragmentGrouping.NEUTRAL_UNITS, BY_FORMULA))
                .hasToString(MolecularFormula.of(molecule).toString());
        }

        @Test
        void writes_nothing_for_a_molecule_with_no_atoms()
        {
            FragmentedFormula formula = FragmentedFormula.of(new Molecule.Builder().build(), FragmentGrouping.COMPONENTS, BY_FORMULA);

            assertThat(formula.unitCount()).isZero();
            assertThat(formula).hasToString("");
        }
    }

    @Nested
    class GatheringFragments
    {
        @Test
        void counts_fragments_the_comparator_finds_alike_as_one_unit()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtoms(Atom.organic("O"), Atom.organic("O"), Atom.organic("O"))
                .build();

            assertThat(FragmentedFormula.of(molecule, FragmentGrouping.COMPONENTS, ALIKE)).hasToString("3H2O");
        }

        @Test
        void keeps_fragments_the_comparator_tells_apart_as_separate_units()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtoms(Atom.organic("O"), Atom.organic("O"), Atom.organic("O"))
                .build();

            assertThat(FragmentedFormula.of(molecule, FragmentGrouping.COMPONENTS, DISTINCT))
                .hasToString("H2O.H2O.H2O");
        }

        @Test
        void gathers_a_fragment_with_those_the_comparator_matched_it_to()
        {
            // O.C.O.O: the waters gather, the methane stands on its own
            Molecule molecule = new Molecule.Builder()
                .addAtoms(Atom.organic("O"), Atom.organic("C"), Atom.organic("O"), Atom.organic("O"))
                .build();

            assertThat(FragmentedFormula.of(molecule, FragmentGrouping.COMPONENTS, BY_FORMULA).units())
                .extracting(FormulaUnit::count)
                .containsExactly(3, 1);
        }

        @Test
        void orders_units_by_where_their_first_fragment_appears()
        {
            // O.C.O: the water's unit comes first, as its first fragment does
            Molecule molecule = new Molecule.Builder()
                .addAtoms(Atom.organic("O"), Atom.organic("C"), Atom.organic("O"))
                .build();

            assertThat(FragmentedFormula.of(molecule, FragmentGrouping.COMPONENTS, BY_FORMULA))
                .hasToString("2H2O.CH4");
        }
    }

    @Nested
    class Units
    {
        @Test
        void keeps_the_structure_each_unit_was_gathered_from()
        {
            FragmentedFormula formula = FragmentedFormula.of(copperSulfatePentahydrate(), FragmentGrouping.NEUTRAL_UNITS, BY_FORMULA);

            // the salt's two ions are kept as one disconnected structure, the water as one fragment
            assertThat(formula.units().getFirst().structure().fragmentCount()).isEqualTo(2); // Cu and SO4
            assertThat(formula.units().getLast().structure().fragmentCount()).isEqualTo(1); // H2O
        }

        @Test
        void counts_a_combined_units_ions_together()
        {
            FragmentedFormula formula = FragmentedFormula.of(copperSulfatePentahydrate(), FragmentGrouping.NEUTRAL_UNITS, BY_FORMULA);

            FormulaUnit salt = formula.units().getFirst();

            assertThat(salt.formula()).hasToString("CuSO4");
            assertThat(salt.isCharged()).isFalse();
            assertThat(salt.structure().atomCount()).isEqualTo(6);
        }

        @Test
        void reports_a_lone_ion_as_charged()
        {
            FragmentedFormula formula = FragmentedFormula.of(copperSulfatePentahydrate(), FragmentGrouping.COMPONENTS, BY_FORMULA);

            assertThat(formula.units().getFirst().isCharged()).isTrue();
        }

        @Test
        void writes_a_single_unit_without_a_count()
        {
            Molecule water = new Molecule.Builder().addAtom(Atom.organic("O")).build();

            assertThat(new FormulaUnit(water, 1)).hasToString("H2O");
        }
    }

    @Nested
    class Rendering
    {
        @Test
        void separates_units_with_a_middle_dot_in_unicode()
        {
            FragmentedFormula formula = FragmentedFormula.of(copperSulfatePentahydrate(), FragmentGrouping.NEUTRAL_UNITS, BY_FORMULA);

            assertThat(formula.toUnicode()).isEqualTo("CuSO₄·5H₂O");
        }

        @Test
        void writes_a_unit_count_full_size_even_in_unicode()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtoms(Atom.organic("O"), Atom.organic("O"))
                .build();

            assertThat(FragmentedFormula.of(molecule, FragmentGrouping.COMPONENTS, BY_FORMULA).toUnicode())
                .isEqualTo("2H₂O");
        }

        @Test
        void writes_each_unit_with_the_settings_chosen_for_it()
        {
            // CCO.O: the ethanol condensed, its water left molecular
            FragmentedFormula formula = FragmentedFormula.of(hydratedEthanol(), FragmentGrouping.COMPONENTS, BY_FORMULA);

            String written = formula.render(FormulaStyle.PLAIN, FragmentedFormulaTest::condenseAllButWater);

            assertThat(written).isEqualTo("CH3CH2OH.H2O");
        }

        @Test
        void makes_every_unit_agree_on_whether_to_write_in_unicode()
        {
            FragmentedFormula formula = FragmentedFormula.of(hydratedEthanol(), FragmentGrouping.COMPONENTS, BY_FORMULA);

            // the units' own settings are plain, but the formula as a whole is not
            String written = formula.render(FormulaStyle.UNICODE, FragmentedFormulaTest::condenseAllButWater);

            assertThat(written).isEqualTo("CH₃CH₂OH·H₂O");
        }

        @Test
        void keeps_a_units_own_choices_other_than_unicode()
        {
            // [2H]C([2H])([2H])[2H].O, the deuterated methane named by alias
            Molecule molecule = new Molecule.Builder()
                .addAtoms(Atom.builder("C").hydrogenIsotope(2, 4).build(), Atom.organic("O"))
                .build();

            String written = FragmentedFormula.of(molecule, FragmentGrouping.COMPONENTS, BY_FORMULA).render(
                FormulaStyle.PLAIN,
                unit -> FormulaSettings.molecular()
                    .withStyle(FormulaStyle.PLAIN.withIsotopeStyle(IsotopeStyle.HYDROGEN_ALIASES)));

            assertThat(written).isEqualTo("CD4.H2O");
        }

        @Test
        void reduces_a_unit_rather_than_the_whole_when_its_settings_are_empirical()
        {
            // C=C.C=C: each unit reduces to CH2, and the pair stays one unit counted twice
            Molecule.Builder builder = new Molecule.Builder();
            for (int fragment = 0; fragment < 2; fragment++)
            {
                Atom first = Atom.organic("C");
                Atom second = Atom.organic("C");
                builder.addAtoms(first, second).addBond(first, second, BondOrder.DOUBLE);
            }

            FragmentedFormula formula = FragmentedFormula.of(builder.build(), FragmentGrouping.COMPONENTS, BY_FORMULA);

            assertThat(formula).hasToString("2C2H4");
            assertThat(formula.render(FormulaStyle.PLAIN, unit -> FormulaSettings.molecular().withEmpirical(true)))
                .isEqualTo("2CH2");
        }
    }

    /**
     * @param unit the unit to write
     * @return settings writing the unit condensed, unless it is a lone water
     */
    private static FormulaSettings condenseAllButWater(FormulaUnit unit)
    {
        return unit.structure().atomCount() > 1 ? FormulaSettings.condensed() : FormulaSettings.molecular();
    }

    /**
     * @return {@code CCO.O}
     */
    private static Molecule hydratedEthanol()
    {
        Atom methyl = Atom.organic("C");
        Atom methylene = Atom.organic("C");
        Atom hydroxyl = Atom.organic("O");
        return new Molecule.Builder()
            .addAtoms(methyl, methylene, hydroxyl, Atom.organic("O"))
            .addBond(methyl, methylene, BondOrder.SINGLE)
            .addBond(methylene, hydroxyl, BondOrder.SINGLE)
            .build();
    }

    /**
     * @return {@code [Cu+2].[O-]S(=O)(=O)[O-].O.O.O.O.O}
     */
    private static Molecule copperSulfatePentahydrate()
    {
        Molecule.Builder builder = new Molecule.Builder()
            .addAtom(Atom.builder("Cu").charge(2).hydrogenCount(0).build());
        addSulfate(builder);
        for (int water = 0; water < 5; water++)
        {
            builder.addAtom(Atom.organic("O"));
        }
        return builder.build();
    }

    /**
     * Adds {@code [O-]S(=O)(=O)[O-]} to a builder.
     *
     * @param builder the builder to add the sulfate ion to
     */
    private static void addSulfate(Molecule.Builder builder)
    {
        Atom sulfur = Atom.organic("S");
        Atom firstAnionic = Atom.builder("O").charge(-1).hydrogenCount(0).build();
        Atom secondAnionic = Atom.builder("O").charge(-1).hydrogenCount(0).build();
        Atom firstDouble = Atom.organic("O");
        Atom secondDouble = Atom.organic("O");
        builder.addAtoms(sulfur, firstAnionic, secondAnionic, firstDouble, secondDouble)
            .addBond(sulfur, firstAnionic, BondOrder.SINGLE)
            .addBond(sulfur, secondAnionic, BondOrder.SINGLE)
            .addBond(sulfur, firstDouble, BondOrder.DOUBLE)
            .addBond(sulfur, secondDouble, BondOrder.DOUBLE);
    }
}
