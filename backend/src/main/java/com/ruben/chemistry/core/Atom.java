package com.ruben.chemistry.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * An atom within a {@link Molecule}.
 *
 * @param element the canonical element symbol, e.g. {@code "C"}, {@code "Cl"}, or {@code "*"} for a wildcard atom
 * @param aromatic whether this atom is aromatic
 * @param charge the net formal charge, {@code 0} if unspecified
 * @param isotope the isotope mass number, or empty if unspecified
 * @param hydrogenCount the number of attached hydrogens, or empty if implicit
 * @param hydrogenIsotopes how many attached hydrogens carry each mass number above one, unmodifiable and ascending
 * @param atomClass the atom class number, or empty if unspecified
 */
public record Atom(
    String element,
    boolean aromatic,
    int charge,
    OptionalInt isotope,
    OptionalInt hydrogenCount,
    Map<Integer, Integer> hydrogenIsotopes,
    OptionalInt atomClass)
{
    /**
     * Normalises the hydrogen isotope labels and checks they fit within a declared hydrogen count.
     */
    public Atom
    {
        hydrogenIsotopes = normaliseHydrogenIsotopes(hydrogenIsotopes);

        int labelled = hydrogenIsotopes.values().stream().mapToInt(Integer::intValue).sum();
        if (hydrogenCount.isPresent() && labelled > hydrogenCount.getAsInt())
        {
            throw new IllegalArgumentException("Atom has more labelled hydrogens (" + labelled + ") than hydrogens (" + hydrogenCount.getAsInt() + ")");
        }
    }

    /**
     * @param labels how many attached hydrogens carry each mass number
     * @return the same labels, protium dropped, as an unmodifiable ascending map
     * @throws IllegalArgumentException if a mass number is below one or a count is negative
     */
    private static Map<Integer, Integer> normaliseHydrogenIsotopes(Map<Integer, Integer> labels)
    {
        if (labels.isEmpty())
        {
            return Map.of();
        }

        Map<Integer, Integer> normalised = new TreeMap<>();
        labels.forEach((massNumber, count) ->
        {
            if (massNumber < 1)
            {
                throw new IllegalArgumentException("Hydrogen mass number must be at least 1, but was " + massNumber);
            }
            if (count < 0)
            {
                throw new IllegalArgumentException("Hydrogen isotope count cannot be negative, but was " + count);
            }
            // protium is what an unlabelled hydrogen already means, so labelling it says nothing
            if (count > 0 && massNumber > 1)
            {
                normalised.put(massNumber, count);
            }
        });
        return Collections.unmodifiableMap(normalised);
    }

    /**
     * @return how many of this atom's attached hydrogens carry a mass number above one
     */
    public int labelledHydrogenCount()
    {
        return hydrogenIsotopes.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Creates a non-aromatic atom with no charge, isotope, or atom class, and an implicit hydrogen count.
     *
     * @param element the canonical element symbol
     * @return the built atom
     */
    public static Atom organic(String element)
    {
        return new Builder(element).build();
    }

    /**
     * Creates an aromatic atom with no charge, isotope, or atom class, and an implicit hydrogen count.
     *
     * @param element the canonical element symbol
     * @return the built atom
     */
    public static Atom aromatic(String element)
    {
        return new Builder(element).aromatic(true).build();
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
        private final Map<Integer, Integer> hydrogenIsotopes = new TreeMap<>();
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
         * Labels some of this atom's attached hydrogens as a heavier nuclide, e.g. deuterium.
         * Protium is what an unlabelled hydrogen already means, so a mass number of one is ignored.
         *
         * @param massNumber the hydrogen isotope's mass number, e.g. {@code 2} for deuterium
         * @param count how many attached hydrogens carry it, replacing any count already given for {@code massNumber}
         * @return this builder, for chaining
         */
        public Builder hydrogenIsotope(int massNumber, int count)
        {
            hydrogenIsotopes.put(massNumber, count);
            return this;
        }

        /**
         * @param hydrogenIsotopes how many attached hydrogens carry each mass number, replacing any labels already given
         * @return this builder, for chaining
         */
        public Builder hydrogenIsotopes(Map<Integer, Integer> hydrogenIsotopes)
        {
            this.hydrogenIsotopes.clear();
            this.hydrogenIsotopes.putAll(hydrogenIsotopes);
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
            return new Atom(element, aromatic, charge, isotope, hydrogenCount, hydrogenIsotopes, atomClass);
        }
    }
}
