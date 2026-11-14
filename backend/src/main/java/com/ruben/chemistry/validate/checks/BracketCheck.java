package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.Valences;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#NOTATION_UNNECESSARY_BRACKETS}:
 * an atom written in brackets that a bare element symbol would have written just as well.
 */
public final class BracketCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.NOTATION_UNNECESSARY_BRACKETS);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (Atom atom : molecule.atoms())
        {
            if (isBracketed(atom) && !needsBrackets(molecule, atom)
                && atom.hydrogenCount().getAsInt() == molecule.hydrogensToFillValence(atom))
            {
                findings.accept(Finding.atAtom(Rule.NOTATION_UNNECESSARY_BRACKETS, atom,
                    "'" + bare(atom) + "' on its own says what the brackets say"));
            }
        }
    }

    /**
     * @param atom an atom
     * @return whether it was written in brackets rather than as a bare element symbol
     */
    private static boolean isBracketed(Atom atom)
    {
        // brackets are the only place a hydrogen count can be written, and outside them it is never written at all
        return atom.hydrogenCount().isPresent();
    }

    /**
     * @param molecule the molecule the atom belongs to
     * @param atom an atom of it written in brackets
     * @return whether anything other than its hydrogen count keeps it in them
     */
    private static boolean needsBrackets(Molecule molecule, Atom atom)
    {
        return !Valences.areKnownFor(atom.element())
            || atom.isotope().isPresent()
            || atom.charge() != 0
            || atom.atomClass().isPresent()
            || !atom.hydrogenIsotopes().isEmpty()
            || molecule.chiralityOf(atom).isPresent();
    }

    /**
     * @param atom an atom the organic subset can write bare
     * @return the symbol that would write it, lowercase where it is aromatic
     */
    private static String bare(Atom atom)
    {
        return atom.aromatic() ? atom.element().toLowerCase(Locale.ROOT) : atom.element();
    }
}
