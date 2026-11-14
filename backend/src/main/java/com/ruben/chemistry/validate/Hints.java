package com.ruben.chemistry.validate;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * What to change about a molecule a rule has faulted.
 */
public final class Hints
{
    private static final Map<Rule, String> BY_RULE = hints();

    private Hints()
    {
    }

    /**
     * @param rule a rule
     * @return what to change about a molecule it faulted, or empty where it asks for no change
     */
    public static Optional<String> forRule(Rule rule)
    {
        return Optional.ofNullable(BY_RULE.get(rule));
    }

    /**
     * @return each rule's hint, naming what to change and leaving the fault itself to the message
     */
    private static Map<Rule, String> hints()
    {
        Map<Rule, String> hints = new EnumMap<>(Rule.class);

        hints.put(Rule.STRUCTURE_EMPTY, "Add an atom");
        hints.put(Rule.STRUCTURE_LONE_HYDROGEN, "Bond it to something, or write [H-] or [H+] for an ion");

        hints.put(Rule.ISOTOPE_BELOW_ATOMIC_NUMBER, "Correct the mass number, as [13C], or drop it");
        hints.put(Rule.ISOTOPE_HYDROGEN_IMPLAUSIBLE, "Write a mass number of 1, 2 or 3");
        hints.put(Rule.ELEMENT_WILDCARD_IN_MOLECULE, "Name the element. A wildcard belongs in a query, not a structure");
        hints.put(Rule.ISOTOPE_ON_WILDCARD, "Drop the mass number, or name the element it weighs");
        hints.put(Rule.CHARGE_ON_WILDCARD, "Drop the charge, or name the element that carries it");
        hints.put(Rule.HYDROGENS_ON_WILDCARD, "Drop the hydrogen count, or name the element that holds them");

        hints.put(Rule.VALENCE_EXCEEDED, "Remove a bond, lower a bond order, drop a hydrogen, or write the charge that allows it");
        hints.put(Rule.VALENCE_CHARGE_DISALLOWS, "Write the charge the message names, as [NH4+] rather than [NH4]");
        hints.put(Rule.CHARGE_BEYOND_ELECTRON_COUNT, "Lower the charge, or check its sign");
        hints.put(Rule.CHARGE_IMPLAUSIBLE, "Lower the charge");

        hints.put(Rule.AROMATIC_BOND_NOT_IN_RING, "Close the ring, or write the bond as single or double");
        hints.put(Rule.AROMATIC_ATOM_NOT_IN_RING, "Close the ring, or write the symbol uppercase");
        hints.put(Rule.AROMATIC_SYSTEM_NOT_KEKULIZABLE,
            "Give a ring nitrogen its hydrogen as [nH], add a charge, or write the ring as single and double bonds");
        hints.put(Rule.AROMATIC_RING_MIXED_NOTATION, "Write the ring all lowercase, or all single and double bonds");

        hints.put(Rule.RING_CUMULATED_DOUBLE_BONDS, "Move one of the double bonds, or make the ring nine atoms or more");
        hints.put(Rule.RING_TRIPLE_BOND_IN_SMALL_RING, "Write the bond as double or single, or make the ring eight atoms or more");

        hints.put(Rule.STEREO_CENTRE_TOO_FEW_NEIGHBOURS, "Remove the @ or @@, or add the substituents it is missing");
        hints.put(Rule.STEREO_CENTRE_NOT_A_CENTRE, "Remove the @ or @@");
        hints.put(Rule.STEREO_DOUBLE_BOND_IN_SMALL_RING, "Turn one / into \\, or make the ring eight atoms or more");
        hints.put(Rule.STEREO_DOUBLE_BOND_NOT_STEREOGENIC, "Delete the / and \\, or give that end two substituents that differ");
        hints.put(Rule.STEREO_DIRECTION_NOT_ON_DOUBLE_BOND, "Delete the / or \\, or make the neighbouring bond double");

        return Map.copyOf(hints);
    }
}
