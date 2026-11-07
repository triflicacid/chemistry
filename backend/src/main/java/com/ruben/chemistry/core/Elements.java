package com.ruben.chemistry.core;

import java.util.HashMap;
import java.util.Map;

/**
 * The periodic table, as a lookup between element symbol and atomic number.
 */
public final class Elements
{
    /**
     * Every element symbol, in atomic number order.
     */
    private static final String[] ELEMENTS = {
        "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne",
        "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar", "K", "Ca",
        "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn",
        "Ga", "Ge", "As", "Se", "Br", "Kr", "Rb", "Sr", "Y", "Zr",
        "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn",
        "Sb", "Te", "I", "Xe", "Cs", "Ba", "La", "Ce", "Pr", "Nd",
        "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb",
        "Lu", "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg",
        "Tl", "Pb", "Bi", "Po", "At", "Rn", "Fr", "Ra", "Ac", "Th",
        "Pa", "U", "Np", "Pu", "Am", "Cm", "Bk", "Cf", "Es", "Fm",
        "Md", "No", "Lr", "Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds",
        "Rg", "Cn", "Nh", "Fl", "Mc", "Lv", "Ts", "Og"
    };

    private static final Map<String, Integer> ATOMIC_NUMBER_BY_SYMBOL = buildAtomicNumberLookup();

    private Elements()
    {
    }

    /**
     * @param atomicNumber an atomic number, from 1 (hydrogen) to 118 (oganesson)
     * @return the element symbol for that atomic number, e.g. {@code "C"} for {@code 6}
     * @throws IllegalArgumentException if {@code atomicNumber} has no known element
     */
    public static String symbolOf(int atomicNumber)
    {
        if (atomicNumber < 1 || atomicNumber > ELEMENTS.length)
        {
            throw new IllegalArgumentException("No known element has atomic number " + atomicNumber);
        }
        return ELEMENTS[atomicNumber - 1];
    }

    /**
     * @param symbol an element symbol, e.g. {@code "C"} or {@code "Cl"}
     * @return the atomic number for that symbol, e.g. {@code 6} for {@code "C"}
     * @throws IllegalArgumentException if {@code symbol} is not a standard periodic table symbol
     */
    public static int atomicNumberOf(String symbol)
    {
        Integer atomicNumber = ATOMIC_NUMBER_BY_SYMBOL.get(symbol);
        if (atomicNumber == null)
        {
            throw new IllegalArgumentException("Unknown element symbol: " + symbol);
        }
        return atomicNumber;
    }

    /**
     * @param symbol a candidate element symbol, e.g. {@code "C"} or {@code "Cl"}
     * @return {@code true} if {@code symbol} is a standard periodic table symbol
     */
    public static boolean isSymbol(String symbol)
    {
        return ATOMIC_NUMBER_BY_SYMBOL.containsKey(symbol);
    }

    /**
     * @return each symbol mapped to its atomic number
     */
    private static Map<String, Integer> buildAtomicNumberLookup()
    {
        Map<String, Integer> lookup = new HashMap<>();
        for (int i = 0; i < ELEMENTS.length; i++)
        {
            lookup.put(ELEMENTS[i], i + 1);
        }
        return Map.copyOf(lookup);
    }
}
