package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#STRUCTURE_EMPTY}: a molecule with no atoms at all.
 */
public final class EmptyStructureCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.STRUCTURE_EMPTY);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        if (molecule.atomCount() == 0)
        {
            findings.accept(Finding.atMolecule(Rule.STRUCTURE_EMPTY, "The molecule has no atoms"));
        }
    }
}
