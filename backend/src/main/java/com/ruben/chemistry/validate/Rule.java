package com.ruben.chemistry.validate;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Something a molecule can be checked for.
 */
public enum Rule
{
    /** A molecule with no atoms at all. */
    STRUCTURE_EMPTY("structure.empty", Severity.ERROR, Category.STRUCTURE),

    /** A mass number below the element's proton count, e.g. {@code [1C]}. */
    ISOTOPE_BELOW_ATOMIC_NUMBER("isotope.below-atomic-number", Severity.ERROR, Category.ELEMENT),

    /** Bonds and hydrogens beyond every valence the element and charge allow, e.g. {@code [CH5]}. */
    VALENCE_EXCEEDED("valence.exceeded", Severity.ERROR, Category.VALENCE),

    /** A valence the element reaches only at another charge, so a charge was likely left off, e.g. {@code [NH4]}. */
    VALENCE_CHARGE_DISALLOWS("valence.charge-disallows", Severity.WARNING, Category.VALENCE),

    /** No valences known for the element, so its own were not checked, e.g. {@code [Fe]}. */
    VALENCE_UNCHECKED("valence.unchecked", Severity.INFO, Category.VALENCE, false),

    /** A formal charge that strips an atom of more electrons than it has, e.g. {@code [C+7]}. */
    CHARGE_BEYOND_ELECTRON_COUNT("charge.beyond-electron-count", Severity.ERROR, Category.CHARGE),

    /** A formal charge past what the element's outer shell can carry, e.g. {@code [C+5]}. */
    CHARGE_IMPLAUSIBLE("charge.implausible", Severity.WARNING, Category.CHARGE),

    /** An odd electron count, i.e. a radical, e.g. {@code [CH3]}. */
    ELECTRONS_ODD("electrons.odd", Severity.INFO, Category.CHARGE),

    /** An aromatic bond that lies on no ring at all, e.g. {@code C:C}. */
    AROMATIC_BOND_NOT_IN_RING("aromatic.bond.not-in-ring", Severity.ERROR, Category.AROMATICITY),

    /** An aromatic atom that lies on no ring at all. */
    AROMATIC_ATOM_NOT_IN_RING("aromatic.atom.not-in-ring", Severity.ERROR, Category.AROMATICITY),

    /** A π system written aromatic that no assignment of single and double bonds can satisfy, e.g. {@code c1cccccc1}. */
    AROMATIC_SYSTEM_NOT_KEKULIZABLE("aromatic.system.not-kekulizable", Severity.ERROR, Category.AROMATICITY),

    /** A lone ring written aromatic whose electrons come to 4n, e.g. {@code c1ccc1}. */
    AROMATIC_SYSTEM_ANTIAROMATIC("aromatic.system.antiaromatic", Severity.WARNING, Category.AROMATICITY),

    /** A chirality marker on an atom with fewer than three neighbours. */
    STEREO_CENTRE_TOO_FEW_NEIGHBOURS("stereo.centre.too-few-neighbours", Severity.ERROR, Category.STEREOCHEMISTRY);

    private static final Map<String, Rule> BY_ID = Stream.of(values())
        .collect(Collectors.toUnmodifiableMap(Rule::id, Function.identity()));

    private final String id;

    private final Severity severity;

    private final Category category;

    private final boolean enabledByDefault;

    Rule(String id, Severity severity, Category category)
    {
        this(id, severity, category, true);
    }

    Rule(String id, Severity severity, Category category, boolean enabledByDefault)
    {
        this.id = id;
        this.severity = severity;
        this.category = category;
        this.enabledByDefault = enabledByDefault;
    }

    /**
     * @return this rule's identifier, e.g. {@code "aromatic.bond.not-in-ring"}
     */
    public String id()
    {
        return id;
    }

    /**
     * @return how much a finding of this rule ought to stop its caller
     */
    public Severity severity()
    {
        return severity;
    }

    /**
     * @return what part of a molecule this rule is about
     */
    public Category category()
    {
        return category;
    }

    /**
     * @return whether this rule runs unless a caller asks otherwise
     */
    public boolean isEnabledByDefault()
    {
        return enabledByDefault;
    }

    /**
     * @param id a rule identifier
     * @return the rule with that {@link #id()}, or empty if there is none
     */
    public static Optional<Rule> byId(String id)
    {
        return Optional.ofNullable(BY_ID.get(id));
    }
}
