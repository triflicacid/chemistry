package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#AROMATIC_ATOM_NOT_IN_RING}: an aromatic atom with no ring to be aromatic in.
 */
public final class AromaticAtomNotInRingCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.AROMATIC_ATOM_NOT_IN_RING);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (Atom atom : molecule.atoms())
        {
            if (atom.aromatic() && molecule.ringCount(atom) == 0)
            {
                findings.accept(Finding.atAtom(Rule.AROMATIC_ATOM_NOT_IN_RING, atom,
                    "An aromatic " + atom.element() + " lies on no ring"));
            }
        }
    }
}
