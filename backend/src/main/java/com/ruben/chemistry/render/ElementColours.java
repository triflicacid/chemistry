package com.ruben.chemistry.render;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The colour each element is drawn in.
 */
public final class ElementColours
{
    /** What an element with no colour of its own is drawn in. */
    public static final Colour DEFAULT = Colour.BLACK;

    private static final Map<String, Colour> BY_ELEMENT = buildColours();

    private ElementColours()
    {
    }

    /**
     * @param element a canonical element symbol, e.g. {@code "C"} or {@code "Cl"}
     * @return the colour that element is drawn in, or {@link #DEFAULT} if it has none of its own
     */
    public static Colour of(String element)
    {
        return BY_ELEMENT.getOrDefault(element, DEFAULT);
    }

    /**
     * @param element a canonical element symbol
     * @return whether that element has a colour of its own
     */
    public static boolean isColoured(String element)
    {
        return BY_ELEMENT.containsKey(element);
    }

    /**
     * @return every element that has a colour of its own, against that colour, unmodifiable
     */
    public static Map<String, Colour> all()
    {
        return BY_ELEMENT;
    }

    /**
     * @return the colouring, by element symbol
     */
    private static Map<String, Colour> buildColours()
    {
        Map<String, Colour> colours = new LinkedHashMap<>();
        colours.put("H", Colour.of("#ffffff"));
        colours.put("He", Colour.of("#d9ffff"));
        colours.put("Li", Colour.of("#cc80ff"));
        colours.put("Be", Colour.of("#c2ff00"));
        colours.put("B", Colour.of("#ffb5b5"));
        colours.put("C", Colour.of("#000000"));
        colours.put("N", Colour.of("#3050f8"));
        colours.put("O", Colour.of("#ff0d0d"));
        colours.put("F", Colour.of("#b300ff"));
        colours.put("Ne", Colour.of("#b3e3f5"));
        colours.put("Na", Colour.of("#ab5cf2"));
        colours.put("Mg", Colour.of("#8aff00"));
        colours.put("Al", Colour.of("#bfa6a6"));
        colours.put("Si", Colour.of("#f0c8a0"));
        colours.put("P", Colour.of("#ff8000"));
        colours.put("S", Colour.of("#ffff30"));
        colours.put("Cl", Colour.of("#1ff01f"));
        colours.put("Ar", Colour.of("#80d1e3"));
        colours.put("K", Colour.of("#8f40d4"));
        colours.put("Ca", Colour.of("#3dff00"));
        colours.put("Ti", Colour.of("#bfc2c7"));
        colours.put("Cr", Colour.of("#8a99c7"));
        colours.put("Mn", Colour.of("#9c7ac7"));
        colours.put("Fe", Colour.of("#e06633"));
        colours.put("Co", Colour.of("#f090a6"));
        colours.put("Ni", Colour.of("#50d050"));
        colours.put("Cu", Colour.of("#c88033"));
        colours.put("Zn", Colour.of("#7d80b0"));
        colours.put("Br", Colour.of("#a62929"));
        colours.put("Ag", Colour.of("#c0c0c0"));
        colours.put("Sn", Colour.of("#668080"));
        colours.put("I", Colour.of("#940094"));
        colours.put("Ba", Colour.of("#00c900"));
        colours.put("Pt", Colour.of("#d0d0e0"));
        colours.put("Au", Colour.of("#ffd123"));
        colours.put("Hg", Colour.of("#b8b8d0"));
        colours.put("Pb", Colour.of("#575961"));
        colours.put("U", Colour.of("#008fff"));
        return Collections.unmodifiableMap(colours);
    }
}
