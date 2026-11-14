package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.PiSystem;
import com.ruben.chemistry.core.RingAnalysis;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#AROMATIC_SYSTEM_NOT_KEKULIZABLE} and {@link Rule#AROMATIC_SYSTEM_ANTIAROMATIC}:
 * structure written with aromatic bonds that will not stand as an aromatic system.
 */
public final class SystemAromaticityCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.AROMATIC_SYSTEM_NOT_KEKULIZABLE, Rule.AROMATIC_SYSTEM_ANTIAROMATIC);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (PiSystem system : molecule.piSystems())
        {
            if (!isWrittenAromatic(system.bonds()))
            {
                continue;
            }

            if (!system.isKekulisable())
            {
                findings.accept(new Finding(Rule.AROMATIC_SYSTEM_NOT_KEKULIZABLE, describe(system), system.atoms(), system.bonds()));
            }
            else if (system.aromaticity() == PiSystem.Aromaticity.ANTIAROMATIC)
            {
                findings.accept(new Finding(Rule.AROMATIC_SYSTEM_ANTIAROMATIC,
                    "A π system of " + system.atoms().size() + " atoms is written as aromatic, but the "
                        + system.rimElectrons().orElse(system.piElectrons())
                        + " π electrons around its rim make it antiaromatic",
                    system.atoms(), system.bonds()));
            }
        }

        for (Molecule ring : molecule.rings())
        {
            RingAnalysis analysis = RingAnalysis.of(molecule, ring.atoms());
            // a ring whose atoms cannot all join a system is in none of them, so no system above has spoken for it
            if (analysis.notation() == RingAnalysis.Notation.AROMATIC && analysis.system().isEmpty())
            {
                findings.accept(new Finding(Rule.AROMATIC_SYSTEM_NOT_KEKULIZABLE,
                    "A ring of " + ring.atomCount() + " atoms is written as aromatic, but its atoms cannot all lie in one π system",
                    ring.atoms(), List.copyOf(ring.bonds())));
            }
        }
    }

    /**
     * @param bonds a system's bonds
     * @return whether every one of them is written as an aromatic bond, so claims to be aromatic
     */
    private static boolean isWrittenAromatic(List<Bond> bonds)
    {
        return !bonds.isEmpty() && bonds.stream().allMatch(bond -> bond.order() == BondOrder.AROMATIC);
    }

    /**
     * @param system a system with no assignment
     * @return the finding in words, saying which of the two reasons it is
     */
    private static String describe(PiSystem system)
    {
        String subject = "A π system of " + system.atoms().size() + " atoms is written as aromatic, but ";
        // an odd count cannot pair however the bonds fall, which makes the structure a radical
        if (system.piElectrons() % 2 != 0)
        {
            return subject + "its " + system.piElectrons() + " π electrons are an odd number, so no assignment of single and double bonds can pair them";
        }
        return subject + "no assignment of single and double bonds satisfies it";
    }
}
