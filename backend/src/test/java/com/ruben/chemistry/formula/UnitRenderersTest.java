package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.query.Query;
import com.ruben.chemistry.smarts.SmartsParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class UnitRenderersTest
{
    private static final Query CARBON = query("C");
    private static final Query WATER = query("[OX2H2]");

    private static final FormulaUnit ETHANOL = new FormulaUnit(ethanol(), 1);
    private static final FormulaUnit WATER_UNIT = new FormulaUnit(water(), 5);

    @Nested
    class Alike
    {
        @Test
        void chooses_the_same_settings_for_every_unit()
        {
            FormulaSettings settings = FormulaSettings.condensed();
            Function<FormulaUnit, FormulaSettings> renderers = UnitRenderers.alike(settings);

            assertThat(renderers.apply(ETHANOL)).isSameAs(settings);
            assertThat(renderers.apply(WATER_UNIT)).isSameAs(settings);
        }
    }

    @Nested
    class Rules
    {
        @Test
        void tries_each_rule_in_the_order_it_was_given()
        {
            FormulaSettings organic = FormulaSettings.condensed();
            FormulaSettings aqueous = FormulaSettings.molecular();
            FormulaSettings fallback = FormulaSettings.molecular().withEmpirical(true);

            Function<FormulaUnit, FormulaSettings> renderers = UnitRenderers.when(CARBON, organic)
                .when(WATER, aqueous)
                .otherwise(fallback);

            assertThat(renderers.apply(ETHANOL)).isSameAs(organic);
            assertThat(renderers.apply(WATER_UNIT)).isSameAs(aqueous);
            assertThat(renderers.apply(new FormulaUnit(ammonia(), 1))).isSameAs(fallback);
        }

        @Test
        void keeps_the_rules_a_choice_was_made_from_as_they_were()
        {
            UnitRenderers.Rules rules = UnitRenderers.when(CARBON, FormulaSettings.condensed());
            FormulaSettings fallback = FormulaSettings.molecular();
            Function<FormulaUnit, FormulaSettings> renderers = rules.otherwise(fallback);

            // adding to the rules afterwards leaves the choice already made from them alone
            rules.when(WATER, FormulaSettings.condensed());

            assertThat(renderers.apply(WATER_UNIT)).isSameAs(fallback);
        }
    }

    @Nested
    class Matching
    {
        @Test
        void matches_a_pattern_against_a_units_structure()
        {
            assertThat(ETHANOL.matches(CARBON)).isTrue();
            assertThat(WATER_UNIT.matches(CARBON)).isFalse();
            assertThat(WATER_UNIT.matches(WATER)).isTrue();
        }

        @Test
        void matches_a_pattern_against_any_fragment_of_a_combined_unit()
        {
            Molecule hydrate = new Molecule.Builder()
                .addAtom(Atom.builder("Na").charge(1).hydrogenCount(0).build())
                .addAtom(Atom.organic("O"))
                .build();

            assertThat(new FormulaUnit(hydrate, 1).matches(WATER)).isTrue();
        }
    }

    @Nested
    class Settings
    {
        @Test
        void writes_each_unit_by_the_pattern_it_matches()
        {
            Atom methyl = Atom.organic("C");
            Atom methylene = Atom.organic("C");
            Atom hydroxyl = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(methyl, methylene, hydroxyl, Atom.organic("O"))
                .addBond(methyl, methylene, BondOrder.SINGLE)
                .addBond(methylene, hydroxyl, BondOrder.SINGLE)
                .build();

            String written = FormulaSettings.fragmented()
                .withGrouping(FragmentGrouping.COMPONENTS)
                .withRenderers(UnitRenderers
                        .when(CARBON, FormulaSettings.condensed())
                        .otherwise(FormulaSettings.molecular()))
                .write(molecule);

            assertThat(written).isEqualTo("CH3CH2OH.H2O");
        }
    }

    /**
     * @param smarts the pattern to parse
     * @return the query that pattern denotes
     */
    private static Query query(String smarts)
    {
        return new SmartsParser(smarts).parseQuery();
    }

    /**
     * @return {@code CCO}
     */
    private static Molecule ethanol()
    {
        Atom methyl = Atom.organic("C");
        Atom methylene = Atom.organic("C");
        Atom hydroxyl = Atom.organic("O");
        return new Molecule.Builder()
            .addAtoms(methyl, methylene, hydroxyl)
            .addBond(methyl, methylene, BondOrder.SINGLE)
            .addBond(methylene, hydroxyl, BondOrder.SINGLE)
            .build();
    }

    /**
     * @return {@code O}
     */
    private static Molecule water()
    {
        return new Molecule.Builder().addAtom(Atom.organic("O")).build();
    }

    /**
     * @return {@code N}
     */
    private static Molecule ammonia()
    {
        return new Molecule.Builder().addAtom(Atom.organic("N")).build();
    }
}
