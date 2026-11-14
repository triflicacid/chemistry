package com.ruben.chemistry.server.molecule;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.util.Span;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.MoleculeValidator;
import com.ruben.chemistry.validate.Severity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What checking a molecule found, with each finding placed in the SMILES it was read from.
 *
 * @param errors how many findings cannot be ignored, i.e. how many are {@code error}
 * @param warnings how many are {@code warning}
 * @param infos how many are {@code info}
 * @param findings what was found, worst first
 */
public record ValidationReport(int errors, int warnings, int infos, List<FindingInfo> findings)
{
    /**
     * One thing the checks found.
     *
     * @param rule the rule's stable identifier, e.g. {@code aromatic.system.not-kekulizable}
     * @param severity {@code error}, {@code warning} or {@code info}
     * @param category what part of the molecule it is about, e.g. {@code aromaticity}
     * @param message the finding in words
     * @param atoms the indices of the atoms it is about, in {@link MoleculeMetadata#atoms()}
     * @param bonds the indices of the bonds it is about, in {@link MoleculeMetadata#bonds()}
     * @param spans where those atoms and bonds are written in {@link MoleculeMetadata#smiles()}
     */
    public record FindingInfo(String rule, String severity, String category, String message, List<Integer> atoms, List<Integer> bonds, List<Span> spans)
    {
    }

    /**
     * Checks a molecule and places what was found in its notation.
     *
     * @param molecule the molecule to check
     * @param validator the checks to run
     * @param source where the molecule's atoms and bonds are written in the SMILES it came from
     * @return the report
     */
    public static ValidationReport of(Molecule molecule, MoleculeValidator validator, MoleculeMetadata.Notation source)
    {
        Map<Atom, Integer> indexOfAtom = new IdentityHashMap<>();
        List<Atom> atoms = molecule.atoms();
        for (int index = 0; index < atoms.size(); index++)
        {
            indexOfAtom.put(atoms.get(index), index);
        }
        Map<Bond, Integer> indexOfBond = new IdentityHashMap<>();
        List<Bond> bonds = molecule.bonds();
        for (int index = 0; index < bonds.size(); index++)
        {
            indexOfBond.put(bonds.get(index), index);
        }

        List<FindingInfo> reported = validator.validate(molecule).stream()
            .map(finding -> describe(finding, indexOfAtom, indexOfBond, source))
            .toList();
        return new ValidationReport(
            count(reported, Severity.ERROR),
            count(reported, Severity.WARNING),
            count(reported, Severity.INFO),
            reported);
    }

    /**
     * @param findings the findings reported
     * @param severity a severity
     * @return how many of them carry it
     */
    private static int count(List<FindingInfo> findings, Severity severity)
    {
        String name = name(severity);
        return (int) findings.stream().filter(finding -> finding.severity().equals(name)).count();
    }

    /**
     * @param finding what was found
     * @param indexOfAtom each atom's index in the molecule
     * @param indexOfBond each bond's index in the molecule
     * @param source where the molecule's atoms and bonds are written
     * @return the finding as a client sees it
     */
    private static FindingInfo describe(Finding finding, Map<Atom, Integer> indexOfAtom, Map<Bond, Integer> indexOfBond, MoleculeMetadata.Notation source)
    {
        List<Span> spans = new ArrayList<>();
        finding.atoms().stream().map(source.atomSpans()::get).filter(Objects::nonNull).forEach(spans::add);
        finding.bonds().stream()
            .map(bond -> source.bondSpans().getOrDefault(bond, List.of()))
            .forEach(spans::addAll);

        return new FindingInfo(
            finding.rule().id(),
            name(finding.severity()),
            name(finding.category()),
            finding.message(),
            finding.atoms().stream().map(indexOfAtom::get).filter(Objects::nonNull).toList(),
            finding.bonds().stream().map(indexOfBond::get).filter(Objects::nonNull).toList(),
            List.copyOf(spans));
    }

    /**
     * @param value an enum value
     * @return its name in lower case, as the client spells it
     */
    private static String name(Enum<?> value)
    {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
