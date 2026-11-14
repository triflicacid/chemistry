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
 * Reports {@link Rule#ELECTRONS_ODD}: a molecule whose electrons do not pair up, as a radical's
 * do not, and as a d-block ions do not either.
 * <p>
 * The count is a parity, so it says nothing about which atom carries the unpaired electron, and it
 * has nothing to say about a species whose unpaired electrons come to an even number.
 */
public final class OddElectronCountCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.ELECTRONS_ODD);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        int electrons = 0;
        for (Atom atom : molecule.atoms())
        {
            // a wildcard stands for an atom of unknown element, so the molecule's electrons cannot be counted at all
            if (!Elements.isSymbol(atom.element()))
            {
                return;
            }
            electrons += Elements.atomicNumberOf(atom.element()) + molecule.totalHydrogenCount(atom) - atom.charge();
        }

        if (electrons % 2 != 0)
        {
            findings.accept(Finding.atMolecule(Rule.ELECTRONS_ODD,
                "The molecule has " + electrons + " electrons, an odd number, so one of them is unpaired"));
        }
    }
}
