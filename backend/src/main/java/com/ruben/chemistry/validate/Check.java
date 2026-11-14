package com.ruben.chemistry.validate;

import com.ruben.chemistry.core.Molecule;

import java.util.Set;
import java.util.function.Consumer;

/**
 * One examination of a molecule, reporting the {@link Rule}s it is responsible for.
 */
public interface Check
{
    /**
     * @return every rule this check can report
     */
    Set<Rule> rules();

    /**
     * Examines a molecule.
     *
     * @param molecule the molecule to examine
     * @param findings receives each finding, in the order the check makes them; a check reports
     *     only the rules it declares in {@link #rules()}
     */
    void check(Molecule molecule, Consumer<Finding> findings);
}
