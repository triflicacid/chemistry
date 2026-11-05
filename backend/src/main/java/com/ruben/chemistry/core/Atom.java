package com.ruben.chemistry.core;

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
        String symbol = element.equals("*") ? element : aromatic ? element.toLowerCase() : element;

        if (charge == 0 && isotope.isEmpty() && hydrogenCount.isEmpty() && atomClass.isEmpty())
        {
            return symbol;
        }

        StringBuilder text = new StringBuilder("[");
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
        return text.toString();
    }
}
