package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#AROMATIC_BOND_NOT_IN_RING}: an aromatic bond with no ring to be aromatic in.
 */
public final class AromaticBondNotInRingCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.AROMATIC_BOND_NOT_IN_RING);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        Set<Bond> bridges = molecule.bridges();
        for (Bond bond : molecule.bonds())
        {
            if (bond.order() == BondOrder.AROMATIC && bridges.contains(bond))
            {
                findings.accept(Finding.atBond(Rule.AROMATIC_BOND_NOT_IN_RING, bond,
                    "An aromatic bond joins " + bond.first().element() + " to " + bond.second().element() + ", but it lies on no ring"));
            }
        }
    }
}
