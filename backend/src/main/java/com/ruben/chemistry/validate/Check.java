package com.ruben.chemistry.validate;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.Notation;

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
     * @param findings receives each finding, in the order the check makes them
     */
    void check(Molecule molecule, Consumer<Finding> findings);

    /**
     * Examines a molecule together with the notation it was written as.
     *
     * @param molecule the molecule to examine
     * @param written how the molecule was written
     * @param findings receives each finding, in the order the check makes them
     */
    default void check(Molecule molecule, Notation written, Consumer<Finding> findings)
    {
        check(molecule, findings);
    }
}
