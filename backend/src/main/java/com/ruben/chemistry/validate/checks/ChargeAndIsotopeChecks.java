package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#CHARGE_UNCOMPENSATED} and {@link Rule#ISOTOPE_HYDROGEN_IMPLAUSIBLE}:
 * a molecule whose charges do not cancel, and a hydrogen labelled heavier than hydrogen comes.
 */
public final class ChargeAndIsotopeChecks implements Check
{
    /** Tritium's mass number, which is the heaviest a hydrogen is found at. */
    private static final int HEAVIEST_HYDROGEN = 3;

    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.CHARGE_UNCOMPENSATED, Rule.ISOTOPE_HYDROGEN_IMPLAUSIBLE);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        int charge = molecule.atoms().stream().mapToInt(Atom::charge).sum();
        if (charge != 0)
        {
            findings.accept(Finding.atMolecule(Rule.CHARGE_UNCOMPENSATED,
                "The molecule carries a net charge of " + (charge > 0 ? "+" + charge : charge)));
        }

        for (Atom atom : molecule.atoms())
        {
            for (int massNumber : atom.hydrogenIsotopes().keySet())
            {
                if (massNumber > HEAVIEST_HYDROGEN)
                {
                    findings.accept(Finding.atAtom(Rule.ISOTOPE_HYDROGEN_IMPLAUSIBLE, atom,
                        "A hydrogen of mass number " + massNumber + " is heavier than tritium"));
                }
            }
        }
    }
}
