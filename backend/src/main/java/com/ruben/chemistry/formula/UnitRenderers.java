package com.ruben.chemistry.formula;

import com.ruben.chemistry.query.MoleculeQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Ways of choosing the {@link FormulaSettings} a {@link FormulaUnit} of a fragmented formula is
 * written by, for {@link FormulaSettings.Fragmented#withRenderers(Function)}.
 */
public final class UnitRenderers
{
    private UnitRenderers()
    {
    }

    /**
     * @param settings the settings to write every unit with
     * @return a choice writing every unit alike
     */
    public static Function<FormulaUnit, FormulaSettings> alike(FormulaSettings settings)
    {
        return unit -> settings;
    }

    /**
     * Begins a chain of rules tried in the order they are given.
     *
     * @param query the pattern a unit must match for {@code settings} to be chosen
     * @param settings the settings to write a matching unit with
     * @return the rules so far, to be closed off by {@link Rules#otherwise}
     */
    public static Rules when(MoleculeQuery query, FormulaSettings settings)
    {
        return new Rules().add(query, settings);
    }

    /**
     * A chain of pattern rules, each tried in turn until one matches the unit being written.
     */
    public static final class Rules
    {
        private final List<MoleculeQuery> queries = new ArrayList<>();
        private final List<FormulaSettings> choices = new ArrayList<>();

        private Rules()
        {
        }

        /**
         * @param query the pattern a unit must match for {@code settings} to be chosen
         * @param settings the settings to write a matching unit with
         * @return these rules, with the new rule tried after those already given
         */
        public Rules when(MoleculeQuery query, FormulaSettings settings)
        {
            return add(query, settings);
        }

        /**
         * Closes off the chain.
         *
         * @param fallback the settings to write a unit matching no rule with
         * @return a choice made by the rules given, falling back to {@code fallback}
         */
        public Function<FormulaUnit, FormulaSettings> otherwise(FormulaSettings fallback)
        {
            Objects.requireNonNull(fallback, "fallback");
            List<MoleculeQuery> queries = List.copyOf(this.queries);
            List<FormulaSettings> choices = List.copyOf(this.choices);
            return unit ->
            {
                for (int index = 0; index < queries.size(); index++)
                {
                    if (unit.matches(queries.get(index)))
                    {
                        return choices.get(index);
                    }
                }
                return fallback;
            };
        }

        private Rules add(MoleculeQuery query, FormulaSettings settings)
        {
            queries.add(Objects.requireNonNull(query, "query"));
            choices.add(Objects.requireNonNull(settings, "settings"));
            return this;
        }
    }
}
