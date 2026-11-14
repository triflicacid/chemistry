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

    /** More than one connected fragment, e.g. {@code CCO.O}. */
    STRUCTURE_DISCONNECTED("structure.disconnected", Severity.INFO, Category.STRUCTURE, false),

    /** An uncharged hydrogen written as a heavy atom with no bonds, e.g. {@code [H]}. */
    STRUCTURE_LONE_HYDROGEN("structure.lone-hydrogen", Severity.WARNING, Category.STRUCTURE),

    /** A mass number below the element's proton count, e.g. {@code [1C]}. */
    ISOTOPE_BELOW_ATOMIC_NUMBER("isotope.below-atomic-number", Severity.ERROR, Category.ELEMENT),

    /** A query wildcard used where a real structure is meant, e.g. {@code *CO}. */
    ELEMENT_WILDCARD_IN_MOLECULE("element.wildcard-in-molecule", Severity.WARNING, Category.ELEMENT),

    /** A mass number on a wildcard, which has no element to weigh, e.g. {@code [13*]}. */
    ISOTOPE_ON_WILDCARD("isotope.on-wildcard", Severity.WARNING, Category.ELEMENT),

    /** A hydrogen isotope label beyond tritium, e.g. {@code [C]([4H])}. */
    ISOTOPE_HYDROGEN_IMPLAUSIBLE("isotope.hydrogen-implausible", Severity.WARNING, Category.ELEMENT),

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

    /** A charge on a wildcard, which has no electrons to lose, e.g. {@code [*+]}. */
    CHARGE_ON_WILDCARD("charge.on-wildcard", Severity.WARNING, Category.CHARGE),

    /** A hydrogen count on a wildcard, which has no valence to fill, e.g. {@code [*H3]}. */
    HYDROGENS_ON_WILDCARD("hydrogens.on-wildcard", Severity.WARNING, Category.VALENCE),

    /** The molecule carries a net charge, as a free ion legitimately does. */
    CHARGE_UNCOMPENSATED("charge.uncompensated", Severity.INFO, Category.CHARGE, false),

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

    /** One ring written part aromatic, part alternating. */
    AROMATIC_RING_MIXED_NOTATION("aromatic.ring.mixed-notation", Severity.WARNING, Category.AROMATICITY),

    /** A ring atom carrying two double bonds in a ring too small to hold them, e.g. {@code C1=C=CC=C1}. */
    RING_CUMULATED_DOUBLE_BONDS("ring.cumulated-double-bonds", Severity.ERROR, Category.RING),

    /** A triple bond in a ring of six atoms or fewer, e.g. {@code C1CC#CC1}. */
    RING_TRIPLE_BOND_IN_SMALL_RING("ring.triple-bond-in-small-ring", Severity.ERROR, Category.RING),

    /** A triple bond in a ring of seven atoms, which is a real transient rather than an impossibility. */
    RING_TRIPLE_BOND_STRAINED("ring.triple-bond-strained", Severity.WARNING, Category.RING),

    /** A three- or four-membered ring: real, but strained. */
    RING_STRAINED("ring.strained", Severity.INFO, Category.RING),

    /** A chirality marker on an atom with fewer than three neighbours. */
    STEREO_CENTRE_TOO_FEW_NEIGHBOURS("stereo.centre.too-few-neighbours", Severity.ERROR, Category.STEREOCHEMISTRY),

    /** A chirality marker on an atom whose substituents are not all different, so it says nothing, e.g. {@code [C@](C)(C)(C)O}. */
    STEREO_CENTRE_NOT_A_CENTRE("stereo.centre.not-a-centre", Severity.WARNING, Category.STEREOCHEMISTRY),

    /** An unmarked stereocentre, so the notation stands for two structures without choosing, e.g. {@code CC(N)O}. */
    STEREO_CENTRE_MISSING("stereo.centre.missing", Severity.INFO, Category.STEREOCHEMISTRY, false),

    /** An unmarked stereogenic double bond, so the notation stands for two structures without choosing, e.g. {@code CC=CC}. */
    STEREO_DOUBLE_BOND_MISSING("stereo.double-bond.missing", Severity.INFO, Category.STEREOCHEMISTRY, false),

    /** A declared configuration a ring is too small to hold, e.g. a trans double bond in cyclohexene. */
    STEREO_DOUBLE_BOND_IN_SMALL_RING("stereo.double-bond.in-small-ring", Severity.WARNING, Category.STEREOCHEMISTRY),

    /** A declared configuration on a double bond with two alike substituents at one end, e.g. {@code C/C(C)=C/C}. */
    STEREO_DOUBLE_BOND_NOT_STEREOGENIC("stereo.double-bond.not-stereogenic", Severity.WARNING, Category.STEREOCHEMISTRY);

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
