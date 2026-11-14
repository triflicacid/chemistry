package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.ValenceTable;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports what an atom's valence is against what its element and charge allow:
 * {@link Rule#VALENCE_EXCEEDED}, {@link Rule#VALENCE_CHARGE_DISALLOWS} and {@link Rule#VALENCE_UNCHECKED}.
 */
public final class ValenceCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.VALENCE_EXCEEDED, Rule.VALENCE_CHARGE_DISALLOWS, Rule.VALENCE_UNCHECKED);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (Atom atom : molecule.atoms())
        {
            if (!ValenceTable.isKnownFor(atom.element()))
            {
                findings.accept(Finding.atAtom(Rule.VALENCE_UNCHECKED, atom,
                    "No valences are known for " + atom.element() + ", so its own were not checked"));
                continue;
            }

            OptionalInt largest = ValenceTable.largestAllowedFor(atom.element(), atom.charge());
            // a charge the element cannot carry leaves it no valences to be measured against
            if (largest.isEmpty())
            {
                continue;
            }

            int valence = leastValenceOf(molecule, atom);
            if (valence <= largest.getAsInt())
            {
                continue;
            }

            OptionalInt accounted = ValenceTable.nearestChargeReaching(atom.element(), valence, atom.charge());
            findings.accept(accounted.isPresent()
                ? Finding.atAtom(Rule.VALENCE_CHARGE_DISALLOWS, atom, describe(atom) + " reaches a valence of " + valence + ", which it does only with a charge of " + signed(accounted.getAsInt()))
                : Finding.atAtom(Rule.VALENCE_EXCEEDED, atom, describe(atom) + " reaches a valence of " + valence + ", beyond the " + largest.getAsInt() + " it allows"));
        }
    }

    /**
     * @param molecule the molecule the atom belongs to
     * @param atom an atom of it
     * @return the valence it holds at least
     */
    private static int leastValenceOf(Molecule molecule, Atom atom)
    {
        int bonded = 0;
        for (Bond bond : molecule.bondsOf(atom))
        {
            bonded += switch (bond.order())
            {
                case SINGLE, AROMATIC -> 1;
                case DOUBLE -> 2;
                case TRIPLE -> 3;
                case QUADRUPLE -> 4;
            };
        }
        return bonded + molecule.totalHydrogenCount(atom);
    }

    /**
     * @param atom an atom
     * @return the atom's element and charge in words, e.g. {@code "N"} or {@code "N+2"}
     */
    private static String describe(Atom atom)
    {
        return atom.charge() == 0 ? atom.element() : atom.element() + signed(atom.charge());
    }

    /**
     * @param charge a formal charge
     * @return the charge with its sign, e.g. {@code "+1"}
     */
    private static String signed(int charge)
    {
        return charge > 0 ? "+" + charge : String.valueOf(charge);
    }
}
