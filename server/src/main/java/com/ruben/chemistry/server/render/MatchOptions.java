package com.ruben.chemistry.server.render;

/**
 * The options a client may set on how a SMARTS pattern's matches are shown. A {@code null} component means "use the default".
 * The matching schema is {@link OptionSchema#optionsMatch()}.
 *
 * @param view {@code each} for a drawing per match, or {@code merged} for one drawing with every match highlighted
 * @param allMatches whether matches covering the same atoms in another order count as matches of their own
 * @param maxMatches the most matches to draw
 * @param dim how much everything outside a match is faded, from 0 to 1
 * @param labels whether matched atoms and the bonds between them are numbered
 * @param recolour whether matched atoms are filled with the colour of their match
 * @param matchColour a hex colour to highlight every match in, or blank for a colour each
 */
public record MatchOptions(
        String view,
        Boolean allMatches,
        Integer maxMatches,
        Double dim,
        Boolean labels,
        Boolean recolour,
        String matchColour)
{
    /**
     * @return options that change nothing
     */
    public static MatchOptions defaults()
    {
        return new MatchOptions(null, null, null, null, null, null, null);
    }
}
