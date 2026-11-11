package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.query.Query;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormulaUnitTest
{
    private static final Query CARBON = query("C");
    private static final Query WATER = query("[OX2H2]");

    @Test
    void matches_a_pattern_against_a_units_structure()
    {
        FormulaUnit ethanol = new FormulaUnit(ethanol(), 1);
        FormulaUnit water = new FormulaUnit(water(), 5);

        assertThat(ethanol.matches(CARBON)).isTrue();
        assertThat(water.matches(CARBON)).isFalse();
        assertThat(water.matches(WATER)).isTrue();
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

    /**
     * @param smarts the pattern to parse
     * @return the query that pattern denotes
     */
    private static Query query(String smarts)
    {
        return Query.fromSmarts(smarts);
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
}
