package com.ruben.chemistry.core;

import java.util.Optional;

/**
 * A molecule's stereochemical declarations, expressed using atom indices.
 * <p>
 * A stereochemical descriptor is meaningful only relative to a particular
 * ordering of the atoms/substituents it describes. Each declaration
 * therefore records both the stereochemical descriptor and the ordering
 * against which it was specified.
 * <p>
 * When a declaration needs to be compared using a different ordering, its
 * descriptor must be re-expressed against that ordering. These conversions
 * are provided by {@link Tetrahedral#parityAgainst} and{@link DoubleBond#configurationAgainst}.
 */
public sealed interface StereoDeclaration
{
    /** Stands in for an implicit hydrogen, which has no atom index of its own. */
    int IMPLICIT_HYDROGEN = -1;

    /**
     * A tetrahedral stereochemical declaration.
     * <p>
     * The parity describes the arrangement of the centre's four ligands
     * relative to the order in which they appear in {@code ligands}.
     *
     * @param centre the atom index of the stereocentre
     * @param parity the stereochemical parity relative to {@code ligands}
     * @param ligands the four ligand atom indices, in the order against which
     *     {@code parity} is defined; an implicit hydrogen is represented by
     *     {@link #IMPLICIT_HYDROGEN}
     */
    record Tetrahedral(int centre, Chirality parity, int... ligands) implements StereoDeclaration
    {
        public Tetrahedral
        {
            ligands = ligands.clone();
        }

        /**
         * Expresses this declaration's parity relative to the ligands ordered by ascending colour.
         * <p>
         * The parity is flipped when the change from the declared ligand order
         * to ascending colour order requires an odd number of transpositions.
         * An implicit hydrogen is ordered before every other ligand.
         * <p>
         * If two ligands have the same colour, their relative order cannot be
         * established, so the resulting parity is undefined.
         *
         * @param colourOf the colour assigned to each atom, indexed by atom index
         * @return the parity relative to ascending colour order, or empty if two
         *     ligands have the same colour
         */
        public Optional<Chirality> parityAgainst(int[] colourOf)
        {
            int inversions = 0;
            for (int first = 0; first < ligands.length; first++)
            {
                for (int second = first + 1; second < ligands.length; second++)
                {
                    int byColour = Integer.compare(colourAt(ligands[first], colourOf), colourAt(ligands[second], colourOf));
                    // two ligands of one colour leave their order undecided
                    if (byColour == 0)
                    {
                        return Optional.empty();
                    }
                    if (byColour > 0)
                    {
                        inversions++;
                    }
                }
            }
            // an even number of swaps reaches the ascending order without disturbing the parity
            return Optional.of(inversions % 2 == 0 ? parity : parity.flip());
        }

        /**
         * @param ligand one of a centre's ligands
         * @param colourOf each atom's colour, by atom index
         * @return where the ligand falls in its centre's ordering: its colour, or, for an implicit
         *     hydrogen, ahead of every colour
         */
        private static int colourAt(int ligand, int[] colourOf)
        {
            return ligand == IMPLICIT_HYDROGEN ? IMPLICIT_HYDROGEN : colourOf[ligand];
        }
    }

    /**
     * A stereochemical declaration for a double bond.
     *
     * @param firstAtom the atom index of one end of the double bond
     * @param secondAtom the atom index of the other end of the double bond
     * @param configuration the configuration relative to the two reference substituents
     * @param firstReference the reference substituent atom index at {@code firstAtom}
     * @param secondReference the reference substituent atom index at {@code secondAtom}
     */
    record DoubleBond(
        int firstAtom,
        int secondAtom,
        DoubleBondStereo configuration,
        int firstReference,
        int secondReference) implements StereoDeclaration
    {
        /**
         * Expresses this configuration relative to another pair of reference substituents.
         * <p>
         * Changing the reference at one end reverses the configuration.
         * Changing the references at both ends, or neither, preserves it.
         *
         * @param first the reference substituent at {@link #firstAtom}
         * @param second the reference substituent at {@link #secondAtom}
         * @return the configuration relative to the supplied references
         */
        public DoubleBondStereo configurationAgainst(int first, int second)
        {
            // measuring from the other substituent at one end swaps the sides, and doing so at both swaps them back
            boolean flipped = (first != firstReference) ^ (second != secondReference);
            return flipped ? configuration.flip() : configuration;
        }

        /**
         * @param atom one atom's index
         * @param neighbour the other atom's index
         * @return a key identifying the pair, whichever way round the two are given
         */
        public static long pairKey(int atom, int neighbour)
        {
            return ((long) Math.min(atom, neighbour) << Integer.SIZE) | Math.max(atom, neighbour);
        }
    }
}
