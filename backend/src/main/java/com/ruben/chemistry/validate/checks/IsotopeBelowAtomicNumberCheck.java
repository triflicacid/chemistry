package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#ISOTOPE_BELOW_ATOMIC_NUMBER}: a mass number that counts fewer particles in
 * the nucleus than the element has protons.
 */
public final class IsotopeBelowAtomicNumberCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.ISOTOPE_BELOW_ATOMIC_NUMBER);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (Atom atom : molecule.atoms())
        {
            OptionalInt isotope = atom.isotope();
            // a wildcard has no element, and so no proton count to measure a mass number against
            if (isotope.isEmpty() || !Elements.isSymbol(atom.element()))
            {
                continue;
            }

            int protons = Elements.atomicNumberOf(atom.element());
            if (isotope.getAsInt() < protons)
            {
                findings.accept(Finding.atAtom(Rule.ISOTOPE_BELOW_ATOMIC_NUMBER, atom,
                    atom.element() + " has " + protons + " protons, so a mass number of " + isotope.getAsInt() + " is impossible"));
            }
        }
    }
}
