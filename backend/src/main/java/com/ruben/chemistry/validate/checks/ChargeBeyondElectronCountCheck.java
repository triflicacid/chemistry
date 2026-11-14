package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#CHARGE_BEYOND_ELECTRON_COUNT}: a positive charge larger than the element's electron count.
 */
public final class ChargeBeyondElectronCountCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.CHARGE_BEYOND_ELECTRON_COUNT);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (Atom atom : molecule.atoms())
        {
            // a wildcard has no element, and so no electrons to count a charge against
            if (atom.charge() <= 0 || !Elements.isSymbol(atom.element()))
            {
                continue;
            }

            int electrons = Elements.atomicNumberOf(atom.element());
            if (atom.charge() > electrons)
            {
                findings.accept(Finding.atAtom(Rule.CHARGE_BEYOND_ELECTRON_COUNT, atom,
                    atom.element() + " has " + electrons + " electrons, so a charge of +" + atom.charge() + " is impossible"));
            }
        }
    }
}
