package com.ruben.chemistry.core;

import java.util.List;
import java.util.OptionalInt;

/**
 * An atom within a {@link Molecule}.
 *
 * @param element the canonical element symbol, e.g. {@code "C"}, {@code "Cl"}, or {@code "*"} for a wildcard atom
 * @param aromatic whether this atom is aromatic
 * @param charge the net formal charge, {@code 0} if unspecified
 * @param isotope the isotope mass number, or empty if unspecified
 * @param hydrogenCount the number of attached hydrogens, or empty if implicit
 * @param atomClass the atom class number, or empty if unspecified
 */
public record Atom(
    String element,
    boolean aromatic,
    int charge,
    OptionalInt isotope,
    OptionalInt hydrogenCount,
    OptionalInt atomClass)
{
    /**
     * Creates a non-aromatic atom with no charge, isotope, or atom class, and an implicit hydrogen count.
     *
     * @param element the canonical element symbol
     * @return the built atom
     */
    public static Atom organic(String element)
    {
        return organic(element, false);
    }

    /**
     * Creates an atom with no charge, isotope, or atom class, and an implicit hydrogen count.
     *
     * @param element the canonical element symbol
     * @param aromatic whether this atom is aromatic
     * @return the built atom
     */
    public static Atom organic(String element, boolean aromatic)
    {
        return new Atom(element, aromatic, 0, OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty());
    }

    @Override
    public String toString()
    {
        return toString(List.of());
    }

    /**
     * Renders this atom as a SMILES token, followed by the given ring-closure tokens.
     *
     * @param ringClosureTokens the pre-formatted ring-closure tokens to append, e.g. {@code "1"} or {@code "%12"}
     */
    public String toString(List<String> ringClosureTokens)
    {
        String symbol = element.equals("*") ? element : aromatic ? element.toLowerCase() : element;

        StringBuilder text = new StringBuilder();
        if (charge == 0 && isotope.isEmpty() && hydrogenCount.isEmpty() && atomClass.isEmpty())
        {
            text.append(symbol);
        }
        else
        {
            text.append('[');
            isotope.ifPresent(text::append);
            text.append(symbol);
            hydrogenCount.ifPresent(count ->
            {
                if (count > 0)
                {
                    text.append('H');
                    if (count > 1)
                    {
                        text.append(count);
                    }
                }
            });
            if (charge != 0)
            {
                text.append(charge > 0 ? '+' : '-');
                if (Math.abs(charge) > 1)
                {
                    text.append(Math.abs(charge));
                }
            }
            atomClass.ifPresent(atomClassNumber -> text.append(':').append(atomClassNumber));
            text.append(']');
        }

        for (String ringClosureToken : ringClosureTokens)
        {
            text.append(ringClosureToken);
        }
        return text.toString();
    }
}
