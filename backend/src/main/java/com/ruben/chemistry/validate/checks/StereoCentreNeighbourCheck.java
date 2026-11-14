package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#STEREO_CENTRE_TOO_FEW_NEIGHBOURS}: a chirality marker on an atom without the substituents to arrange.
 */
public final class StereoCentreNeighbourCheck implements Check
{
    /** The substituents a tetrahedral centre needs before its two arrangements differ. */
    private static final int MINIMUM_SUBSTITUENTS = 3;

    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.STEREO_CENTRE_TOO_FEW_NEIGHBOURS);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (Atom atom : molecule.atoms())
        {
            if (molecule.chiralityOf(atom).isEmpty())
            {
                continue;
            }

            int substituents = molecule.bondsOf(atom).size() + molecule.totalHydrogenCount(atom);
            if (substituents < MINIMUM_SUBSTITUENTS)
            {
                findings.accept(Finding.atAtom(Rule.STEREO_CENTRE_TOO_FEW_NEIGHBOURS, atom,
                    atom.element() + " is marked as a stereocentre but has " + substituents
                        + (substituents == 1 ? " substituent" : " substituents") + ", too few to arrange"));
            }
        }
    }
}
