package com.ruben.chemistry.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Additional rendering options in addition to a {@link Depiction}.
 */
public final class RenderStyle
{
    /** The colours chosen for particular elements, overriding {@link ElementColours}. */
    private final Map<String, Colour> colours;

    private final double bondLength;
    private final double margin;
    private final Colour background;
    private final AtomShape atomShape;
    private final boolean isotopes;
    private final boolean charges;
    private final String fontFamily;
    private final double fontSize;
    private final double superscriptScale;
    private final double labelPadding;
    private final double atomRadius;
    private final double hydrogenRadius;
    private final Colour atomOutline;
    private final double atomOutlineWidth;
    private final Colour bondColour;
    private final boolean bondGradient;
    private final double bondWidth;
    private final double bondGap;
    private final double wedgeWidth;
    private final double hashSpacing;

    /**
     * @param builder the builder holding the settings to take
     */
    private RenderStyle(Builder builder)
    {
        this.colours = Map.copyOf(builder.colours);
        this.bondLength = builder.bondLength;
        this.margin = builder.margin;
        this.background = builder.background;
        this.atomShape = builder.atomShape;
        this.isotopes = builder.isotopes;
        this.charges = builder.charges;
        this.fontFamily = builder.fontFamily;
        this.fontSize = builder.fontSize;
        this.superscriptScale = builder.superscriptScale;
        this.labelPadding = builder.labelPadding;
        this.atomRadius = builder.atomRadius;
        this.hydrogenRadius = builder.hydrogenRadius;
        this.atomOutline = builder.atomOutline;
        this.atomOutlineWidth = builder.atomOutlineWidth;
        this.bondColour = builder.bondColour;
        this.bondGradient = builder.bondGradient;
        this.bondWidth = builder.bondWidth;
        this.bondGap = builder.bondGap;
        this.wedgeWidth = builder.wedgeWidth;
        this.hashSpacing = builder.hashSpacing;
    }

    /**
     * @return a structural diagram on white, with element symbols written in their own colours
     */
    public static RenderStyle defaults()
    {
        return builder().build();
    }

    /**
     * @return a builder holding the default settings
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * @return a builder holding this style's settings, for changing one or two of them
     */
    public Builder toBuilder()
    {
        Builder builder = new Builder()
            .bondLength(bondLength)
            .margin(margin)
            .background(background)
            .atomShape(atomShape)
            .isotopes(isotopes)
            .charges(charges)
            .fontFamily(fontFamily)
            .fontSize(fontSize)
            .superscriptScale(superscriptScale)
            .labelPadding(labelPadding)
            .atomRadius(atomRadius)
            .hydrogenRadius(hydrogenRadius)
            .atomOutline(atomOutline)
            .atomOutlineWidth(atomOutlineWidth)
            .bondColour(bondColour)
            .bondGradient(bondGradient)
            .bondWidth(bondWidth)
            .bondGap(bondGap)
            .wedgeWidth(wedgeWidth)
            .hashSpacing(hashSpacing);
        colours.forEach(builder::colour);
        return builder;
    }

    /**
     * @param element a canonical element symbol
     * @return the colour it is drawn in: the one this style chose for it, or the one
     *     {@link ElementColours} gives it
     */
    public Colour colourOf(String element)
    {
        Colour chosen = colours.get(element);
        return chosen != null ? chosen : ElementColours.of(element);
    }

    /**
     * @param element a canonical element symbol
     * @return how big a circle that element is drawn as, hydrogen being drawn smaller than the rest
     */
    public double radiusOf(String element)
    {
        return element.equals("H") ? hydrogenRadius : atomRadius;
    }

    /**
     * @return how many pixels long one bond is, which is what scales the whole drawing
     */
    public double bondLength()
    {
        return bondLength;
    }

    /**
     * @return how many pixels of empty space surround the drawing
     */
    public double margin()
    {
        return margin;
    }

    /**
     * @return the colour behind the drawing, or empty to leave it transparent
     */
    public Optional<Colour> background()
    {
        return Optional.ofNullable(background);
    }

    /**
     * @return how an atom itself is drawn
     */
    public AtomShape atomShape()
    {
        return atomShape;
    }

    /**
     * @return whether an atom's mass number is written above and before its symbol; a
     *     {@link AtomShape#CIRCLE} carries no writing, so never shows one
     */
    public boolean isotopes()
    {
        return isotopes && atomShape == AtomShape.TEXT;
    }

    /**
     * @return whether an atom's charge is written above and after its symbol; a
     *     {@link AtomShape#CIRCLE} carries no writing, so never shows one
     */
    public boolean charges()
    {
        return charges && atomShape == AtomShape.TEXT;
    }

    /**
     * @return the font an atom's symbol is written in
     */
    public String fontFamily()
    {
        return fontFamily;
    }

    /**
     * @return how many pixels tall an atom's symbol is written
     */
    public double fontSize()
    {
        return fontSize;
    }

    /**
     * @return how much of {@link #fontSize()} a superscript is written at
     */
    public double superscriptScale()
    {
        return superscriptScale;
    }

    /**
     * @return how many pixels of clear space a bond leaves around a written symbol
     */
    public double labelPadding()
    {
        return labelPadding;
    }

    /**
     * @return how many pixels across an atom other than hydrogen is drawn as a circle
     */
    public double atomRadius()
    {
        return atomRadius;
    }

    /**
     * @return how many pixels across a hydrogen is drawn as a circle, which is smaller than the rest
     */
    public double hydrogenRadius()
    {
        return hydrogenRadius;
    }

    /**
     * @return the colour outlining an atom, which is what keeps a white hydrogen visible
     */
    public Colour atomOutline()
    {
        return atomOutline;
    }

    /**
     * @return how many pixels thick that outline is
     */
    public double atomOutlineWidth()
    {
        return atomOutlineWidth;
    }

    /**
     * @return the colour a bond is drawn in
     */
    public Colour bondColour()
    {
        return bondColour;
    }

    /**
     * @return whether a bond is drawn running from the colour of the atom at one end to the colour
     *     of the atom at the other, rather than in {@link #bondColour()} throughout
     */
    public boolean bondGradient()
    {
        return bondGradient;
    }

    /**
     * @return how many pixels thick a bond line is
     */
    public double bondWidth()
    {
        return bondWidth;
    }

    /**
     * @return how many pixels apart the lines of a bond of more than one order are drawn
     */
    public double bondGap()
    {
        return bondGap;
    }

    /**
     * @return how many pixels across the wide end of a wedge or hashed bond is
     */
    public double wedgeWidth()
    {
        return wedgeWidth;
    }

    /**
     * @return how many pixels apart the marks of a hashed bond are
     */
    public double hashSpacing()
    {
        return hashSpacing;
    }

    /**
     * Collects the settings for a {@link RenderStyle}, starting from the defaults.
     */
    public static final class Builder
    {
        private final Map<String, Colour> colours = new LinkedHashMap<>();

        private double bondLength = 50;
        private double margin = 12;
        private Colour background = Colour.WHITE;
        private AtomShape atomShape = AtomShape.TEXT;
        private boolean isotopes = true;
        private boolean charges = true;
        private String fontFamily = "Helvetica, Arial, sans-serif";
        private double fontSize = 18;
        private double superscriptScale = 0.6;
        private double labelPadding = 3;
        private double atomRadius = 14;
        private double hydrogenRadius = 9;
        private Colour atomOutline = Colour.of("#222222");
        private double atomOutlineWidth = 1;
        private Colour bondColour = Colour.of("#222222");
        private boolean bondGradient = false;
        private double bondWidth = 2;
        private double bondGap = 6;
        private double wedgeWidth = 10;
        private double hashSpacing = 4;

        private Builder()
        {
        }

        /**
         * @param bondLength how many pixels long one bond is, which is what scales the whole drawing
         * @return this builder, for chaining
         */
        public Builder bondLength(double bondLength)
        {
            this.bondLength = requirePositive(bondLength, "bondLength");
            return this;
        }

        /**
         * @param margin how many pixels of empty space surround the drawing
         * @return this builder, for chaining
         */
        public Builder margin(double margin)
        {
            this.margin = requireAtLeastZero(margin, "margin");
            return this;
        }

        /**
         * @param background the colour behind the drawing, or {@code null} to leave it transparent
         * @return this builder, for chaining
         */
        public Builder background(Colour background)
        {
            this.background = background;
            return this;
        }

        /**
         * @param atomShape how an atom itself is drawn
         * @return this builder, for chaining
         */
        public Builder atomShape(AtomShape atomShape)
        {
            this.atomShape = Objects.requireNonNull(atomShape, "atomShape");
            return this;
        }

        /**
         * @param isotopes whether an atom's mass number is written above and before its symbol
         * @return this builder, for chaining
         */
        public Builder isotopes(boolean isotopes)
        {
            this.isotopes = isotopes;
            return this;
        }

        /**
         * @param charges whether an atom's charge is written above and after its symbol
         * @return this builder, for chaining
         */
        public Builder charges(boolean charges)
        {
            this.charges = charges;
            return this;
        }

        /**
         * @param fontFamily the font an atom's symbol is written in
         * @return this builder, for chaining
         */
        public Builder fontFamily(String fontFamily)
        {
            this.fontFamily = Objects.requireNonNull(fontFamily, "fontFamily");
            return this;
        }

        /**
         * @param fontSize how many pixels tall an atom's symbol is written
         * @return this builder, for chaining
         */
        public Builder fontSize(double fontSize)
        {
            this.fontSize = requirePositive(fontSize, "fontSize");
            return this;
        }

        /**
         * @param superscriptScale how much of the font size a superscript is written at
         * @return this builder, for chaining
         */
        public Builder superscriptScale(double superscriptScale)
        {
            this.superscriptScale = requirePositive(superscriptScale, "superscriptScale");
            return this;
        }

        /**
         * @param labelPadding how many pixels of clear space a bond leaves around a written symbol
         * @return this builder, for chaining
         */
        public Builder labelPadding(double labelPadding)
        {
            this.labelPadding = requireAtLeastZero(labelPadding, "labelPadding");
            return this;
        }

        /**
         * @param atomRadius how many pixels across an atom other than hydrogen is drawn as a circle
         * @return this builder, for chaining
         */
        public Builder atomRadius(double atomRadius)
        {
            this.atomRadius = requirePositive(atomRadius, "atomRadius");
            return this;
        }

        /**
         * @param hydrogenRadius how many pixels across a hydrogen is drawn as a circle
         * @return this builder, for chaining
         */
        public Builder hydrogenRadius(double hydrogenRadius)
        {
            this.hydrogenRadius = requirePositive(hydrogenRadius, "hydrogenRadius");
            return this;
        }

        /**
         * @param atomOutline the colour outlining an atom
         * @return this builder, for chaining
         */
        public Builder atomOutline(Colour atomOutline)
        {
            this.atomOutline = Objects.requireNonNull(atomOutline, "atomOutline");
            return this;
        }

        /**
         * @param atomOutlineWidth how many pixels thick that outline is
         * @return this builder, for chaining
         */
        public Builder atomOutlineWidth(double atomOutlineWidth)
        {
            this.atomOutlineWidth = requireAtLeastZero(atomOutlineWidth, "atomOutlineWidth");
            return this;
        }

        /**
         * @param bondColour the colour a bond is drawn in
         * @return this builder, for chaining
         */
        public Builder bondColour(Colour bondColour)
        {
            this.bondColour = Objects.requireNonNull(bondColour, "bondColour");
            return this;
        }

        /**
         * @param bondGradient whether a bond takes the colours of the atoms at its ends, holding
         *     each for a moment before turning quickly into a blend of the two across its middle
         * @return this builder, for chaining
         */
        public Builder bondGradient(boolean bondGradient)
        {
            this.bondGradient = bondGradient;
            return this;
        }

        /**
         * @param bondWidth how many pixels thick a bond line is
         * @return this builder, for chaining
         */
        public Builder bondWidth(double bondWidth)
        {
            this.bondWidth = requirePositive(bondWidth, "bondWidth");
            return this;
        }

        /**
         * @param bondGap how many pixels apart the lines of a bond of more than one order are drawn
         * @return this builder, for chaining
         */
        public Builder bondGap(double bondGap)
        {
            this.bondGap = requirePositive(bondGap, "bondGap");
            return this;
        }

        /**
         * @param wedgeWidth how many pixels across the wide end of a wedge or hashed bond is
         * @return this builder, for chaining
         */
        public Builder wedgeWidth(double wedgeWidth)
        {
            this.wedgeWidth = requirePositive(wedgeWidth, "wedgeWidth");
            return this;
        }

        /**
         * @param hashSpacing how many pixels apart the marks of a hashed bond are
         * @return this builder, for chaining
         */
        public Builder hashSpacing(double hashSpacing)
        {
            this.hashSpacing = requirePositive(hashSpacing, "hashSpacing");
            return this;
        }

        /**
         * Chooses the colour one element is drawn in, overriding {@link ElementColours}.
         *
         * @param element a canonical element symbol
         * @param colour the colour to draw it in
         * @return this builder, for chaining
         */
        public Builder colour(String element, Colour colour)
        {
            colours.put(
                Objects.requireNonNull(element, "element"),
                Objects.requireNonNull(colour, "colour"));
            return this;
        }

        /**
         * @param colours the colour to draw each element in, replacing any chosen already
         * @return this builder, for chaining
         */
        public Builder colours(Map<String, Colour> colours)
        {
            this.colours.clear();
            this.colours.putAll(colours);
            return this;
        }

        /**
         * @return a style with the settings chosen so far
         */
        public RenderStyle build()
        {
            return new RenderStyle(this);
        }

        /**
         * @param value the measurement to check
         * @param name the setting's name, for the error message
         * @return {@code value}
         * @throws IllegalArgumentException if {@code value} is not above zero
         */
        private static double requirePositive(double value, String name)
        {
            if (!(value > 0) || !Double.isFinite(value))
            {
                throw new IllegalArgumentException(name + " must be above zero, but was " + value);
            }
            return value;
        }

        /**
         * @param value the measurement to check
         * @param name the setting's name, for the error message
         * @return {@code value}
         * @throws IllegalArgumentException if {@code value} is below zero
         */
        private static double requireAtLeastZero(double value, String name)
        {
            if (!(value >= 0) || !Double.isFinite(value))
            {
                throw new IllegalArgumentException(name + " cannot be below zero, but was " + value);
            }
            return value;
        }
    }
}
