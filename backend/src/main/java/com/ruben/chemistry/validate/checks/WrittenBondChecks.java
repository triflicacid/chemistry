package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.Notation;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Reports what a bond's written symbol says that the bond itself does not:
 * {@link Rule#STEREO_DIRECTION_NOT_ON_DOUBLE_BOND} and {@link Rule#NOTATION_REDUNDANT_BOND_ORDER}.
 */
public final class WrittenBondChecks implements Check
{
    /** The symbols that place a double bond's substituent on one side or the other. */
    private static final Set<String> DIRECTIONS = Set.of("/", "\\");

    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.STEREO_DIRECTION_NOT_ON_DOUBLE_BOND, Rule.NOTATION_REDUNDANT_BOND_ORDER);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        // a molecule that was never written has no symbols to measure it against
    }

    @Override
    public void check(Molecule molecule, Notation written, Consumer<Finding> findings)
    {
        for (Bond bond : molecule.bonds())
        {
            for (String symbol : written.symbolsOf(bond))
            {
                // a ring closure writing only its digits leaves no symbol at all
                if (!symbol.isEmpty())
                {
                    checkSymbol(molecule, bond, symbol, findings);
                }
            }
        }
    }

    /**
     * @param molecule the molecule being checked
     * @param bond a bond of it
     * @param symbol the symbol one end of the bond was written with
     * @param findings where to report what is found
     */
    private static void checkSymbol(Molecule molecule, Bond bond, String symbol, Consumer<Finding> findings)
    {
        if (DIRECTIONS.contains(symbol))
        {
            // a direction is written on the single bond beside a double one, so it is that neighbour it needs
            if (!flanksADoubleBond(molecule, bond))
            {
                findings.accept(Finding.atBond(Rule.STEREO_DIRECTION_NOT_ON_DOUBLE_BOND, bond,
                    "'" + symbol + "' places a substituent to one side of a double bond, and this bond is beside none"));
            }
        }
        else if (symbol.equals(impliedSymbol(bond)))
        {
            findings.accept(Finding.atBond(Rule.NOTATION_REDUNDANT_BOND_ORDER, bond,
                "'" + symbol + "' is the bond that would have been read here anyway"));
        }
    }

    /**
     * @param molecule the molecule being checked
     * @param bond a bond of it
     * @return whether either end of the bond also holds a double bond, which a direction describes
     */
    private static boolean flanksADoubleBond(Molecule molecule, Bond bond)
    {
        return Stream.of(bond.first(), bond.second())
            .flatMap(end -> molecule.bondsOf(end).stream())
            .anyMatch(other -> other != bond && other.order() == BondOrder.DOUBLE);
    }

    /**
     * @param bond a bond of the molecule being checked
     * @return the symbol that writing nothing would have meant here
     */
    private static String impliedSymbol(Bond bond)
    {
        // two aromatic atoms side by side imply the aromatic bond, which is why a single bond between them must say so
        return bond.first().aromatic() && bond.second().aromatic() ? ":" : "-";
    }
}
