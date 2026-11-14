package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.RingAnalysis;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports {@link Rule#AROMATIC_RING_MIXED_NOTATION}: a ring written with aromatic bonds for some
 * of its bonds and alternating ones for the rest.
 */
public final class MixedNotationCheck implements Check
{
    @Override
    public Set<Rule> rules()
    {
        return Set.of(Rule.AROMATIC_RING_MIXED_NOTATION);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        for (Molecule ring : molecule.rings())
        {
            RingAnalysis analysis = molecule.ringAnalysisOf(ring);
            // only a ring of a pi system is writing its own bonds
            if (analysis.notation() == RingAnalysis.Notation.MIXED && analysis.system().isPresent())
            {
                findings.accept(new Finding(Rule.AROMATIC_RING_MIXED_NOTATION,
                    "A ring of " + ring.atomCount() + " atoms is written part aromatic and part alternating",
                    ring.atoms(), List.copyOf(ring.bonds())));
            }
        }
    }
}
