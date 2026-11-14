package com.ruben.chemistry.cli;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.query.Match;
import com.ruben.chemistry.query.Query;
import com.ruben.chemistry.query.SubstructureMatcher;
import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.match.MatchStyle;

import java.util.List;
import java.util.Map;

/**
 * The options shared by the commands that draw a SMARTS pattern's matches.
 */
final class MatchOptions
{
    /** How several matches are shown. */
    enum View
    {
        /** One drawing of the molecule per match, side by side. */
        EACH,

        /** One drawing, with every match highlighted in a colour of its own. */
        MERGED
    }

    /** A molecule and the matches of a pattern in it. */
    record Found(Molecule molecule, List<Match> matches)
    {
    }

    static final CliOption<View> VIEW = CliOption.choice(
            "view",
            "whether each match gets its own drawing of the molecule, or all are highlighted on one",
            Map.of("each", View.EACH, "merged", View.MERGED),
            () -> View.EACH
    ).withDefaultText("each");

    static final CliOption<Boolean> ALL_MATCHES = CliOption.flag(
            "all-matches",
            "whether matches covering the same atoms in another order count as matches of their own",
            false
    );

    static final CliOption<Integer> MAX_MATCHES = CliOption.parsed(
            "max-matches",
            "n",
            "the most matches to draw",
            input -> {
                try
                {
                    int count = Integer.parseInt(input);
                    if (count < 1)
                    {
                        throw new IllegalArgumentException("--max-matches must be at least 1, but was " + count);
                    }
                    return count;
                }
                catch (NumberFormatException invalid)
                {
                    throw new IllegalArgumentException("--max-matches must be a whole number, but was '" + input + "'");
                }
            },
            () -> 10
    ).withDefaultText("10");

    static final CliOption<Double> DIM = CliOption.number(
            "dim",
            "0-1",
            "how much everything outside the match is faded, from 0 (not at all) to 1 (invisible)",
            0
    );

    static final CliOption<Boolean> LABELS = CliOption.flag(
            "labels",
            "whether matched atoms and the bonds between them are numbered",
            false
    );

    static final CliOption<Boolean> RECOLOUR = CliOption.flag(
            "recolour",
            "whether matched atoms are filled with the colour of their match",
            false
    );

    static final CliOption<Colour> MATCH_COLOUR = CliOption.parsed(
            "match-colour",
            "colour",
            "the colour to highlight every match in, instead of a colour each",
            Colour::of,
            null
    );

    static final List<CliOption<?>> ALL = List.of(VIEW, ALL_MATCHES, MAX_MATCHES, DIM, LABELS, RECOLOUR, MATCH_COLOUR);

    private MatchOptions()
    {
    }

    /**
     * @param options the parsed options, whose subjects are a SMILES string and a SMARTS pattern
     * @return the molecule, and the matches to draw, capped at {@code --max-matches}
     * @throws IllegalArgumentException if the subjects are not a SMILES string and a pattern, or the pattern matches nothing
     */
    static Found find(CliOptions options)
    {
        List<String> subjects = options.subjects();
        if (subjects.size() != 2)
        {
            throw new IllegalArgumentException("Expected a SMILES string and a SMARTS pattern, but got " + subjects.size() + " arguments");
        }

        Molecule molecule = Molecule.fromSmiles(subjects.get(0));
        Query query = Query.fromSmarts(subjects.get(1));

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);
        List<Match> found = options.get(ALL_MATCHES) ? matcher.findMatches(query) : matcher.findDistinctMatches(query);
        if (found.isEmpty())
        {
            throw new IllegalArgumentException("'" + subjects.get(1) + "' does not match '" + subjects.get(0) + "'");
        }

        List<Match> matches = found.subList(0, Math.min(found.size(), options.get(MAX_MATCHES)));
        if (matches.size() < found.size())
        {
            System.err.println("Drawing " + matches.size() + " of " + found.size() + " matches; see --max-matches");
        }
        return new Found(molecule, matches);
    }

    /**
     * @param options the parsed options
     * @return how the matches are highlighted
     */
    static MatchStyle style(CliOptions options)
    {
        MatchStyle style = MatchStyle.defaults()
                .withDimRest(options.get(DIM))
                .withLabels(options.get(LABELS))
                .withRecolour(options.get(RECOLOUR));
        if (options.has(MATCH_COLOUR.name()))
        {
            style = style.withColour(options.get(MATCH_COLOUR));
        }
        return style;
    }
}
