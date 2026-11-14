package com.ruben.chemistry.server.match;

import com.ruben.chemistry.query.Match;

import java.util.List;

/**
 * The matches of a SMARTS pattern in a molecule.
 *
 * @param matching how the matches were found and are to be shown
 * @param total how many matches the pattern has
 * @param shown the matches to show, the first of them
 */
public record MatchResult(MatchOptionsMapper.Matching matching, int total, List<Match> shown)
{
}
