package com.ruben.chemistry.render.svg;

import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.annotate.AtomLook;
import com.ruben.chemistry.render.annotate.BondLook;

/**
 * Applies {@link AtomLook}s and {@link BondLook}s to what is drawn.
 */
public final class Looks
{
    /** The weights of red, green and blue in how bright a colour looks. */
    private static final double RED_WEIGHT = 0.2126;
    private static final double GREEN_WEIGHT = 0.7152;
    private static final double BLUE_WEIGHT = 0.0722;

    private Looks()
    {
    }

    /**
     * @param look how the atom is restyled
     * @param natural the atom's fill colour before it is restyled
     * @return the look's fill colour desaturated as the look asks
     */
    public static Colour fill(AtomLook look, Colour natural)
    {
        return saturate(look.fill().orElse(natural), look.saturation().orElse(1));
    }

    /**
     * @param look how the atom is restyled
     * @param natural the atom's outline colour before it is restyled
     * @return the look's outline colour desaturated as the look asks
     */
    public static Colour outline(AtomLook look, Colour natural)
    {
        return saturate(look.outline().orElse(natural), look.saturation().orElse(1));
    }

    /**
     * @param look how the bond is restyled
     * @param natural the bond's colour before it is restyled
     * @return the look's stroke colour
     */
    public static Colour stroke(BondLook look, Colour natural)
    {
        return look.colour().orElse(natural);
    }

    /**
     * @param element the element drawing an atom
     * @param look how the atom is restyled
     * @return {@code element}, with the look's opacity applied if it has one
     */
    public static Element apply(Element element, AtomLook look)
    {
        look.opacity().ifPresent(element::opacity);
        return element;
    }

    /**
     * @param element the element drawing a bond
     * @param look how the bond is restyled
     * @return {@code element}, with the look's opacity applied if it has one
     */
    public static Element apply(Element element, BondLook look)
    {
        look.opacity().ifPresent(element::opacity);
        return element;
    }

    /**
     * @param colour a colour
     * @param saturation 0 for its grey equivalent to 1 for unchanged
     * @return the colour that far from its grey equivalent
     */
    static Colour saturate(Colour colour, double saturation)
    {
        if (saturation >= 1)
        {
            return colour;
        }
        int level = (int) Math.round(RED_WEIGHT * colour.red() + GREEN_WEIGHT * colour.green() + BLUE_WEIGHT * colour.blue());
        return colour.mix(new Colour(level, level, level), 1 - saturation);
    }
}
