package com.ruben.chemistry.server.match;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.query.Match;
import com.ruben.chemistry.query.MatchLabels;
import com.ruben.chemistry.query.Query;
import com.ruben.chemistry.query.SubstructureMatcher;
import com.ruben.chemistry.render.match.MatchStyle;
import com.ruben.chemistry.server.molecule.MoleculeException;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds where SMARTS patterns match molecules.
 */
public final class MoleculeMatcher
{
    /**
     * @param molecule the molecule to search
     * @param smarts the pattern to find
     * @param options how to find and show the matches
     * @return the matches, none if the pattern does not match
     * @throws MoleculeException if the pattern or an option is invalid
     */
    public MatchResult find(Molecule molecule, String smarts, MatchOptions options)
    {
        MatchOptionsMapper.Matching matching = MatchOptionsMapper.map(options);

        Query query;
        try
        {
            query = Query.fromSmarts(smarts);
        }
        catch (InvalidNotationException invalid)
        {
            throw new MoleculeException("INVALID_SMARTS", invalid.getMessage());
        }

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);
        List<Match> found = matching.allMatches() ? matcher.findMatches(query) : matcher.findDistinctMatches(query);
        return new MatchResult(matching, found.size(), found.subList(0, Math.min(found.size(), matching.maxMatches())));
    }

    /**
     * @param molecule the molecule that was searched
     * @param result the matches in {@code molecule}
     * @return where the pattern matches in a 3D structure
     */
    public Match3dData describe3d(Molecule molecule, MatchResult result)
    {
        MatchStyle style = result.matching().style();

        Map<Atom, Integer> atomIndices = new IdentityHashMap<>();
        for (int i = 0; i < molecule.atoms().size(); i++)
        {
            atomIndices.put(molecule.atoms().get(i), i);
        }
        Map<Bond, Integer> bondIndices = new IdentityHashMap<>();
        for (int i = 0; i < molecule.bonds().size(); i++)
        {
            bondIndices.put(molecule.bonds().get(i), i);
        }

        List<Match3dData.Highlight> highlights = new ArrayList<>();
        for (int i = 0; i < result.shown().size(); i++)
        {
            Match each = result.shown().get(i);
            Set<Atom> atoms = each.atoms();
            List<Integer> bonds = new ArrayList<>();
            for (Bond bond : molecule.bonds())
            {
                if (atoms.contains(bond.first()) && atoms.contains(bond.second()))
                {
                    bonds.add(bondIndices.get(bond));
                }
            }

            List<Match3dData.Label> atomLabels = new ArrayList<>();
            List<Match3dData.Label> bondLabels = new ArrayList<>();
            if (style.labels())
            {
                MatchLabels labels = MatchLabels.of(each, molecule);
                labels.atoms().forEach((atom, text) -> atomLabels.add(new Match3dData.Label(atomIndices.get(atom), text)));
                labels.bonds().forEach((bond, text) -> bondLabels.add(new Match3dData.Label(bondIndices.get(bond), text)));
            }
            highlights.add(new Match3dData.Highlight(
                    style.colourOfMatch(i).hex(),
                    atoms.stream().map(atomIndices::get).sorted().toList(),
                    bonds,
                    atomLabels,
                    bondLabels));
        }
        return new Match3dData(
                result.matching().merged(),
                result.total(),
                highlights.size(),
                style.dimRest(),
                style.recolour(),
                style.labels(),
                highlights);
    }
}
