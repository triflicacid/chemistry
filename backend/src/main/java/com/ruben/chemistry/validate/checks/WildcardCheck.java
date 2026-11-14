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
 * Reports what a wildcard atom is doing in a structure: {@link Rule#ELEMENT_WILDCARD_IN_MOLECULE},
 * and then {@link Rule#ISOTOPE_ON_WILDCARD}, {@link Rule#CHARGE_ON_WILDCARD} and
 * {@link Rule#HYDROGENS_ON_WILDCARD} for anything written on it.
 */
public final class WildcardCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(
                Rule.ELEMENT_WILDCARD_IN_MOLECULE,
                Rule.ISOTOPE_ON_WILDCARD,
                Rule.CHARGE_ON_WILDCARD,
                Rule.HYDROGENS_ON_WILDCARD);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (Atom atom : molecule.atoms())
        {
            if (Elements.isSymbol(atom.element()))
            {
                continue;
            }

            findings.accept(Finding.atAtom(Rule.ELEMENT_WILDCARD_IN_MOLECULE, atom,
                "'" + atom.element() + "' stands for any element, which a structure cannot"));
            if (atom.isotope().isPresent())
            {
                findings.accept(Finding.atAtom(Rule.ISOTOPE_ON_WILDCARD, atom,
                    "A mass number of " + atom.isotope().getAsInt() + " weighs a nucleus the wildcard does not name"));
            }
            if (atom.charge() != 0)
            {
                findings.accept(Finding.atAtom(Rule.CHARGE_ON_WILDCARD, atom,
                    "A charge counts electrons the wildcard does not have"));
            }
            if (atom.hydrogenCount().isPresent())
            {
                findings.accept(Finding.atAtom(Rule.HYDROGENS_ON_WILDCARD, atom,
                    atom.hydrogenCount().getAsInt() + " hydrogens fill a valence the wildcard does not have"));
            }
        }
    }
}
