package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.ValenceTable;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#CHARGE_IMPLAUSIBLE}: a charge that would take an atom's outer shell past
 * either end, leaving it either no electrons to bond with or more than the shell holds.
 */
public final class ImplausibleChargeCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.CHARGE_IMPLAUSIBLE);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (Atom atom : molecule.atoms())
        {
            if (atom.charge() == 0 || !ValenceTable.isKnownFor(atom.element()))
            {
                continue;
            }

            if (ValenceTable.allowedFor(atom.element(), atom.charge()).isEmpty())
            {
                findings.accept(Finding.atAtom(Rule.CHARGE_IMPLAUSIBLE, atom,
                    "A charge of " + (atom.charge() > 0 ? "+" + atom.charge() : atom.charge()) + " is past what " + atom.element() + "'s outer shell can carry"));
            }
        }
    }
}
