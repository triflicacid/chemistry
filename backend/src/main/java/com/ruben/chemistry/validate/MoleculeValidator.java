package com.ruben.chemistry.validate;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.validate.checks.AromaticAtomNotInRingCheck;
import com.ruben.chemistry.validate.checks.AromaticBondNotInRingCheck;
import com.ruben.chemistry.validate.checks.ChargeBeyondElectronCountCheck;
import com.ruben.chemistry.validate.checks.EmptyStructureCheck;
import com.ruben.chemistry.validate.checks.ImplausibleChargeCheck;
import com.ruben.chemistry.validate.checks.IsotopeBelowAtomicNumberCheck;
import com.ruben.chemistry.validate.checks.OddElectronCountCheck;
import com.ruben.chemistry.validate.checks.StereoCentreNeighbourCheck;
import com.ruben.chemistry.validate.checks.SystemAromaticityCheck;
import com.ruben.chemistry.validate.checks.ValenceCheck;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a molecule that was built permissively.
 */
public final class MoleculeValidator
{
    /** Every check there is, in the order their findings are reported for one position. */
    private static final List<Check> CHECKS = List.of(
        new EmptyStructureCheck(),
        new IsotopeBelowAtomicNumberCheck(),
        new ValenceCheck(),
        new ChargeBeyondElectronCountCheck(),
        new ImplausibleChargeCheck(),
        new OddElectronCountCheck(),
        new AromaticBondNotInRingCheck(),
        new AromaticAtomNotInRingCheck(),
        new SystemAromaticityCheck(),
        new StereoCentreNeighbourCheck());

    private final ValidationOptions options;

    /**
     * Creates a validator reporting the rules that are on unless asked otherwise.
     */
    public MoleculeValidator()
    {
        this(ValidationOptions.defaults());
    }

    /**
     * @param options which rules to report
     */
    public MoleculeValidator(ValidationOptions options)
    {
        this.options = options;
    }

    /**
     * Checks a molecule against every rule this validator reports.
     *
     * @param molecule the molecule to check
     * @return what was found, worst first, unmodifiable and empty if nothing was found
     */
    public List<Finding> validate(Molecule molecule)
    {
        List<Finding> findings = new ArrayList<>();
        for (Check check : CHECKS)
        {
            if (options.reportsAnyOf(check.rules()))
            {
                check.check(molecule, finding -> collect(finding, findings));
            }
        }
        findings.sort(orderWithin(molecule));
        return List.copyOf(findings);
    }

    /**
     * Checks a molecule for anything that would stop a caller using it.
     *
     * @param molecule the molecule to check
     * @return whether any finding is an {@link Severity#ERROR}
     */
    public boolean hasErrors(Molecule molecule)
    {
        return validate(molecule).stream().anyMatch(finding -> finding.severity() == Severity.ERROR);
    }

    /**
     * @param finding a finding a check made
     * @param findings the findings collected so far
     */
    private void collect(Finding finding, List<Finding> findings)
    {
        if (options.reports(finding.rule()))
        {
            findings.add(finding);
        }
    }

    /**
     * Orders findings by severity, then by the first atom each is about, then by rule.
     *
     * @param molecule the molecule the findings are about
     * @return the comparator, which depends on {@code molecule}'s atom order
     */
    private static Comparator<Finding> orderWithin(Molecule molecule)
    {
        Map<Atom, Integer> positionOf = new IdentityHashMap<>();
        List<Atom> atoms = molecule.atoms();
        for (int position = 0; position < atoms.size(); position++)
        {
            positionOf.put(atoms.get(position), position);
        }
        return Comparator.comparing(Finding::severity)
            .thenComparingInt(finding -> firstPosition(finding, positionOf))
            .thenComparing(Finding::rule);
    }

    /**
     * @param finding a finding
     * @param positionOf each atom's position in the molecule
     * @return where the first atom the finding is about sits, or -1
     */
    private static int firstPosition(Finding finding, Map<Atom, Integer> positionOf)
    {
        return finding.atoms().stream()
            .mapToInt(atom -> positionOf.getOrDefault(atom, -1))
            .min()
            .orElse(-1);
    }
}
