package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.query.MoleculeQuery;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class UnitRenderersTest
{
    private static final Molecule ETHANOL_STRUCTURE = new Molecule.Builder().build();
    private static final Molecule WATER_STRUCTURE = new Molecule.Builder().build();
    private static final Molecule AMMONIA_STRUCTURE = new Molecule.Builder().build();

    private static final MoleculeQuery CARBON = m -> m == ETHANOL_STRUCTURE;
    private static final MoleculeQuery WATER = m -> m == WATER_STRUCTURE;

    private static final FormulaUnit ETHANOL = new FormulaUnit(ETHANOL_STRUCTURE, 1);
    private static final FormulaUnit WATER_UNIT = new FormulaUnit(WATER_STRUCTURE, 5);

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
            assertThat(renderers.apply(new FormulaUnit(AMMONIA_STRUCTURE, 1))).isSameAs(fallback);
        }

        @Test
        void returns_on_the_first_rule_that_matches_without_trying_the_rest()
        {
            MoleculeQuery alwaysMatches = candidate -> true;
            FormulaSettings first = FormulaSettings.condensed();
            FormulaSettings second = FormulaSettings.molecular();

            Function<FormulaUnit, FormulaSettings> renderers = UnitRenderers.when(alwaysMatches, first)
                .when(alwaysMatches, second)
                .otherwise(FormulaSettings.molecular().withEmpirical(true));

            assertThat(renderers.apply(WATER_UNIT)).isSameAs(first);
        }

        @Test
        void falls_back_when_no_rule_matches()
        {
            MoleculeQuery neverMatches = candidate -> false;
            FormulaSettings fallback = FormulaSettings.molecular();

            Function<FormulaUnit, FormulaSettings> renderers = UnitRenderers.when(neverMatches, FormulaSettings.condensed())
                .otherwise(fallback);

            assertThat(renderers.apply(WATER_UNIT)).isSameAs(fallback);
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
}
