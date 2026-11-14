package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#ELECTRONS_ODD}: a species whose electrons do not pair up, as a radical's do
 * not, and as a d-block ion's do not either.
 * <p>
 * The count is a parity, so it says nothing about which atom carries the unpaired electron, and it
 * has nothing to say about a fragment whose unpaired electrons come to an even number.
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
        for (Molecule fragment : molecule.fragments())
        {
            OptionalInt electrons = electronsOf(fragment);
            if (electrons.isPresent() && electrons.getAsInt() % 2 != 0)
            {
                findings.accept(new Finding(Rule.ELECTRONS_ODD,
                    "'" + SmilesWriter.write(fragment) + "' has " + electrons.getAsInt() + " electrons, an odd number, so one of them is unpaired",
                    fragment.atoms(),
                    fragment.bonds()));
            }
        }
    }

    /**
     * @param fragment one connected fragment of a molecule
     * @return how many electrons it holds, or empty if it holds an atom whose element is not named
     */
    private static OptionalInt electronsOf(Molecule fragment)
    {
        int electrons = 0;
        for (Atom atom : fragment.atoms())
        {
            // a wildcard stands for an atom of unknown element, so this fragment's electrons cannot be counted at all
            if (!Elements.isSymbol(atom.element()))
            {
                return OptionalInt.empty();
            }
            electrons += Elements.atomicNumberOf(atom.element()) + fragment.totalHydrogenCount(atom) - atom.charge();
        }
        return OptionalInt.of(electrons);
    }
}
