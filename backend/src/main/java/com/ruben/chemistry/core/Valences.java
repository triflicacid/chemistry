package com.ruben.chemistry.core;

import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * The valences each element is written with when its hydrogens are left implicit.
 * <p>
 * Only the SMILES organic subset has these defaults, as other elements must declare hydrogen count explicitly.
 */
public final class Valences
{
    /** The default valences of the SMILES organic subset elements, each ascending. */
    private static final Map<String, int[]> ORGANIC_SUBSET = Map.of(
        "B", new int[] {3},
        "C", new int[] {4},
        "N", new int[] {3, 5},
        "O", new int[] {2},
        "P", new int[] {3, 5},
        "S", new int[] {2, 4, 6},
        "F", new int[] {1},
        "Cl", new int[] {1},
        "Br", new int[] {1},
        "I", new int[] {1});

    private Valences()
    {
    }

    /**
     * @param element an element symbol
     * @return whether valences are known for the element
     */
    public static boolean areKnownFor(String element)
    {
        return ORGANIC_SUBSET.containsKey(element);
    }

    /**
     * @param element an element symbol
     * @return the largest valence the element is written with, or empty if none are known for it
     */
    public static OptionalInt largestOf(String element)
    {
        int[] valences = ORGANIC_SUBSET.get(element);
        return valences == null ? OptionalInt.empty() : OptionalInt.of(valences[valences.length - 1]);
    }

    /**
     * Picks the valence an atom is taken to have, which is the smallest one that accommodates the
     * bonds it already carries.
     *
     * @param element an element symbol
     * @param required the valence the atom's bonds spend between them
     * @return the valence to fill, or empty if no valences are known for the element
     */
    public static OptionalInt toFill(String element, int required)
    {
        int[] valences = ORGANIC_SUBSET.get(element);
        if (valences == null)
        {
            return OptionalInt.empty();
        }
        // escalate to a higher valence only when required
        return OptionalInt.of(IntStream.of(valences)
            .filter(valence -> valence >= required)
            .findFirst()
            .orElse(valences[valences.length - 1]));
    }
}
