package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.query.Query;
import com.ruben.chemistry.query.SubstructureMatcher;

/**
 * One of the units written either side of a {@code .} in a fragmented formula, such as the
 * {@code 5H2O} of {@code CuSO4.5H2O}.
 *
 * @param structure the unit's structure
 * @param count how many of the unit there are, at least one
 */
public record FormulaUnit(Molecule structure, int count)
{
    public FormulaUnit
    {
        if (count < 1)
        {
            throw new IllegalArgumentException("a unit stands for at least one fragment: " + count);
        }
    }

    /**
     * @return the formula of one of this unit, a combined unit's fragments counted together
     */
    public MolecularFormula formula()
    {
        return MolecularFormula.of(structure);
    }

    /**
     * @return whether this unit's structure carries a net formal charge, as a lone ion does
     */
    public boolean isCharged()
    {
        return formula().charge() != 0;
    }

    /**
     * @param query the pattern to search this unit's structure for
     * @return whether {@code query} matches some substructure of this unit's structure
     */
    public boolean matches(Query query)
    {
        return new SubstructureMatcher(structure).matches(query);
    }

    /**
     * Writes the unit, prefixed by its count where there is more than one of it.
     *
     * @param settings how to write the unit's structure
     * @return the unit as text, e.g. {@code 5H2O}
     */
    public String render(FormulaSettings settings)
    {
        return (count > 1 ? String.valueOf(count) : "") + settings.write(structure);
    }

    /**
     * @return the unit as a plain-text molecular formula, e.g. {@code 5H2O}
     */
    @Override
    public String toString()
    {
        return render(FormulaSettings.molecular());
    }
}
