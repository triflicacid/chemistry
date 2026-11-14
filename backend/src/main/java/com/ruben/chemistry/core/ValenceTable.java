package com.ruben.chemistry.core;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * The valences a main-group element can reach at a given charge.
 * <p>
 * {@link Valences} is about organic subset atoms only. This table serves all elements, but is derived.
 */
public final class ValenceTable
{
    /** The electrons a full outer shell holds, beyond the first period. */
    private static final int OCTET = 8;

    /** The electrons a full outer shell holds in the first period. */
    private static final int DUET = 2;

    /** The first period whose elements can take pairs beyond a full shell. */
    private static final int FIRST_EXPANDING_PERIOD = 3;

    /** The valence electrons an element needs before it can take pairs beyond a full shell. */
    private static final int EXPANDING_ELECTRONS = 4;

    /** How far either side of a written charge another one is looked for. */
    private static final int CHARGE_SEARCH = 4;

    private ValenceTable()
    {
    }

    /**
     * @param element an element symbol
     * @return whether valences are known for the element, which they are for the main group alone
     */
    public static boolean isKnownFor(String element)
    {
        return Elements.isSymbol(element) && Elements.valenceElectronsOf(Elements.atomicNumberOf(element)) >= 0;
    }

    /**
     * Finds every valence an atom of the element can reach at the charge.
     *
     * @param element an element symbol
     * @param charge a formal charge
     * @return the valences, ascending and unmodifiable
     */
    public static List<Integer> allowedFor(String element, int charge)
    {
        if (!isKnownFor(element))
        {
            return List.of();
        }

        int atomicNumber = Elements.atomicNumberOf(element);
        int ownElectrons = Elements.valenceElectronsOf(atomicNumber);
        int electrons = ownElectrons - charge;
        int shell = Elements.periodOf(atomicNumber) == 1 ? DUET : OCTET;
        // a charge cannot take electrons an atom has not got, nor give it more than its shell holds
        if (electrons < 0 || electrons > shell)
        {
            return List.of();
        }

        int bonded = Math.min(electrons, shell - electrons);
        if (!expandsBeyondShell(atomicNumber))
        {
            return List.of(bonded);
        }

        // each further pair beyond the shell is two more bonds, and a covalent bond costs the atom
        // one electron, so it can make no more bonds than it has electrons to spend
        List<Integer> valences = new ArrayList<>();
        for (int valence = bonded; valence <= electrons; valence += 2)
        {
            valences.add(valence);
        }
        return List.copyOf(valences);
    }

    /**
     * @param element an element symbol
     * @param charge a formal charge
     * @param valence a valence
     * @return whether an atom of the element can reach that valence at that charge
     */
    public static boolean allows(String element, int charge, int valence)
    {
        return allowedFor(element, charge).contains(valence);
    }

    /**
     * @param element an element symbol
     * @param charge a formal charge
     * @return the largest valence an atom of the element can reach at that charge
     */
    public static OptionalInt largestAllowedFor(String element, int charge)
    {
        List<Integer> allowed = allowedFor(element, charge);
        return allowed.isEmpty() ? OptionalInt.empty() : OptionalInt.of(allowed.getLast());
    }

    /**
     * @param element an element symbol
     * @param valence a valence
     * @param written the charge already written, which is not itself a candidate
     * @return the nearest charge at which the element reaches that valence, or empty if none does
     */
    public static OptionalInt nearestChargeReaching(String element, int valence, int written)
    {
        for (int distance = 1; distance <= CHARGE_SEARCH; distance++)
        {
            // a cation first, since leaving a charge off an onium is the commoner slip
            for (int charge : new int[] {written + distance, written - distance})
            {
                if (allows(element, charge, valence))
                {
                    return OptionalInt.of(charge);
                }
            }
        }
        return OptionalInt.empty();
    }

    /**
     * @param atomicNumber an element's atomic number
     * @return whether the element can take pairs beyond a full outer shell
     */
    private static boolean expandsBeyondShell(int atomicNumber)
    {
        return Elements.periodOf(atomicNumber) >= FIRST_EXPANDING_PERIOD
            && Elements.valenceElectronsOf(atomicNumber) >= EXPANDING_ELECTRONS;
    }
}
