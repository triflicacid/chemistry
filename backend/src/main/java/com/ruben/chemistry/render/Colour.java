package com.ruben.chemistry.render;

/**
 * A colour, in RGB.
 *
 * @param red how much red the colour has, from 0 to 255
 * @param green how much green the colour has, from 0 to 255
 * @param blue how much blue the colour has, from 0 to 255
 */
public record Colour(int red, int green, int blue)
{
    /** The largest value any one channel can take. */
    private static final int FULL = 255;

    /** How many characters a hex colour has, not counting its leading hash. */
    private static final int HEX_DIGITS = 6;

    /** Black, which is what an element with no colour of its own is drawn in. */
    public static final Colour BLACK = new Colour(0, 0, 0);

    /** White. */
    public static final Colour WHITE = new Colour(FULL, FULL, FULL);

    /**
     * @throws IllegalArgumentException if any channel falls outside 0 to 255
     */
    public Colour
    {
        requireChannel(red, "red");
        requireChannel(green, "green");
        requireChannel(blue, "blue");
    }

    /**
     * @param hex a colour as six hex digits, with or without a leading {@code #}, e.g. {@code "#FF0D0D"}
     * @return the colour it names
     * @throws IllegalArgumentException if {@code hex} is not six hex digits
     */
    public static Colour of(String hex)
    {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (digits.length() != HEX_DIGITS)
        {
            throw new IllegalArgumentException("A hex colour needs " + HEX_DIGITS + " digits, but '" + hex + "' has " + digits.length());
        }

        try
        {
            int packed = Integer.parseInt(digits, 16);
            return new Colour((packed >> 16) & FULL, (packed >> 8) & FULL, packed & FULL);
        }
        catch (NumberFormatException notHex)
        {
            throw new IllegalArgumentException("'" + hex + "' is not a hex colour", notHex);
        }
    }

    /**
     * Mixes this colour with another, channel by channel.
     *
     * @param other the colour to mix in
     * @param fraction how much of {@code other} to take, from 0 for all of this colour to 1 for
     *     all of the other
     * @return the colour that far between the two
     * @throws NullPointerException if {@code other} is {@code null}
     * @throws IllegalArgumentException if {@code fraction} falls outside 0 to 1
     */
    public Colour mix(Colour other, double fraction)
    {
        if (!(fraction >= 0) || !(fraction <= 1))
        {
            throw new IllegalArgumentException("A mix must be between 0 and 1, but was " + fraction);
        }
        return new Colour(
            channelBetween(red, other.red, fraction),
            channelBetween(green, other.green, fraction),
            channelBetween(blue, other.blue, fraction));
    }

    /**
     * @return this colour as six hex digits behind a hash, e.g. {@code "#ff0d0d"}
     */
    public String hex()
    {
        return String.format("#%02x%02x%02x", red, green, blue);
    }

    @Override
    public String toString()
    {
        return hex();
    }

    /**
     * @param from one colour's channel
     * @param to the other colour's channel
     * @param fraction how much of {@code to} to take
     * @return the channel that far between the two
     */
    private static int channelBetween(int from, int to, double fraction)
    {
        return (int) Math.round(from + (to - from) * fraction);
    }

    /**
     * @param value a channel's value
     * @param name the channel's name, for the error message
     * @throws IllegalArgumentException if {@code value} falls outside 0 to 255
     */
    private static void requireChannel(int value, String name)
    {
        if (value < 0 || value > FULL)
        {
            throw new IllegalArgumentException("A colour's " + name + " must be between 0 and " + FULL + ", but was " + value);
        }
    }
}
