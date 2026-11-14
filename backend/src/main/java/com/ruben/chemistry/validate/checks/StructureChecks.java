package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#STRUCTURE_DISCONNECTED} and {@link Rule#STRUCTURE_LONE_HYDROGEN}: what the
 * molecule is made of taken as a whole, rather than anything about one atom's chemistry.
 */
public final class StructureChecks implements Check
{
    /** Hydrogen's symbol, which is an ordinary element when written as an atom of its own. */
    private static final String HYDROGEN = "H";

    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.STRUCTURE_DISCONNECTED, Rule.STRUCTURE_LONE_HYDROGEN);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        int fragments = molecule.fragmentCount();
        if (fragments > 1)
        {
            findings.accept(Finding.atMolecule(Rule.STRUCTURE_DISCONNECTED, "The molecule is in " + fragments + " separate pieces"));
        }

        for (Atom atom : molecule.atoms())
        {
            if (atom.element().equals(HYDROGEN) && atom.charge() == 0 && molecule.bondsOf(atom).isEmpty())
            {
                findings.accept(Finding.atAtom(Rule.STRUCTURE_LONE_HYDROGEN, atom, "A hydrogen is written as an atom of its own with nothing bonded to it"));
            }
        }
    }
}
