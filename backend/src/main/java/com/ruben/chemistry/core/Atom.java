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

    /**
     * @param element the canonical element symbol
     * @return a new builder for an atom with no charge, isotope, hydrogen count, or atom class
     */
    public static Builder builder(String element)
    {
        return new Builder(element);
    }

    @Override
    public String toString()
    {
        return toString(List.of(), null);
    }

    /**
     * Renders this atom as a SMILES token, with an optional chirality marker rendered
     * immediately after the element symbol, followed by the given ring-closure tokens.
     *
     * @param ringClosureTokens the pre-formatted ring-closure tokens to append, e.g. {@code "1"} or {@code "%12"}
     * @param chirality the chirality to render as a marker, or {@code null} if not a stereocentre
     * @return this atom's SMILES representation
     */
    public String toString(List<String> ringClosureTokens, Chirality chirality)
    {
        // aromaticity is written by lower-casing the symbol, but the wildcard has no case to change
        String symbol = element.equals("*") ? element : aromatic ? element.toLowerCase() : element;

        StringBuilder text = new StringBuilder();
        if (charge == 0 && isotope.isEmpty() && hydrogenCount.isEmpty() && atomClass.isEmpty() && chirality == null)
        {
            text.append(symbol);
        }
        else
        {
            // bracket form: order matters, as per SMILES spec
            text.append('[');
            isotope.ifPresent(text::append);
            text.append(symbol);
            if (chirality != null)
            {
                text.append(chirality.symbol());
            }
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

        ringClosureTokens.forEach(text::append);
        return text.toString();
    }

    public static final class Builder
    {
        private final String element;
        private boolean aromatic;
        private int charge;
        private OptionalInt isotope = OptionalInt.empty();
        private OptionalInt hydrogenCount = OptionalInt.empty();
        private OptionalInt atomClass = OptionalInt.empty();

        private Builder(String element)
        {
            this.element = element;
        }

        /**
         * @param aromatic whether the atom is aromatic
         * @return this builder, for chaining
         */
        public Builder aromatic(boolean aromatic)
        {
            this.aromatic = aromatic;
            return this;
        }

        /**
         * @param charge the net formal charge
         * @return this builder, for chaining
         */
        public Builder charge(int charge)
        {
            this.charge = charge;
            return this;
        }

        /**
         * @param isotope the isotope mass number
         * @return this builder, for chaining
         */
        public Builder isotope(int isotope)
        {
            this.isotope = OptionalInt.of(isotope);
            return this;
        }

        /**
         * @param isotope the isotope mass number, or empty to leave it unspecified
         * @return this builder, for chaining
         */
        public Builder isotope(OptionalInt isotope)
        {
            this.isotope = isotope;
            return this;
        }

        /**
         * @param hydrogenCount the number of attached hydrogens
         * @return this builder, for chaining
         */
        public Builder hydrogenCount(int hydrogenCount)
        {
            this.hydrogenCount = OptionalInt.of(hydrogenCount);
            return this;
        }

        /**
         * @param hydrogenCount the number of attached hydrogens, or empty to leave them implicit
         * @return this builder, for chaining
         */
        public Builder hydrogenCount(OptionalInt hydrogenCount)
        {
            this.hydrogenCount = hydrogenCount;
            return this;
        }

        /**
         * @param atomClass the atom class number
         * @return this builder, for chaining
         */
        public Builder atomClass(int atomClass)
        {
            this.atomClass = OptionalInt.of(atomClass);
            return this;
        }

        /**
         * @param atomClass the atom class number, or empty to leave it unspecified
         * @return this builder, for chaining
         */
        public Builder atomClass(OptionalInt atomClass)
        {
            this.atomClass = atomClass;
            return this;
        }

        /**
         * @return an atom with the element and properties set so far
         */
        public Atom build()
        {
            return new Atom(element, aromatic, charge, isotope, hydrogenCount, atomClass);
        }
    }
}
