package com.ruben.chemistry.render.match;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.query.Match;
import com.ruben.chemistry.query.MatchLabels;
import com.ruben.chemistry.query.QueryAtom;
import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.annotate.Annotations;
import com.ruben.chemistry.render.annotate.AtomLook;
import com.ruben.chemistry.render.annotate.BondLook;
import com.ruben.chemistry.render.annotate.Halo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns {@link Match}es into {@link Annotations}.
 */
public final class MatchAnnotator
{
    private MatchAnnotator()
    {
    }

    /**
     * @param match a match against {@code molecule}
     * @param molecule the molecule {@code match} was found in
     * @param style how to highlight it
     * @return annotations highlighting the match
     */
    public static Annotations annotate(Match match, Molecule molecule, MatchStyle style)
    {
        return annotate(List.of(match), molecule, style);
    }

    /**
     * @param matches matches against {@code molecule}, each given its own palette colour
     * @param molecule the molecule the matches were found in
     * @param style how to highlight them
     * @return the highlights of every match
     */
    public static Annotations annotate(List<Match> matches, Molecule molecule, MatchStyle style)
    {
        Annotations.Builder base = new Annotations.Builder();
        if (style.dimRest() > 0)
        {
            base.defaultAtom(AtomLook.NONE.withOpacity(1 - style.dimRest()));
            base.defaultBond(BondLook.NONE.withOpacity(1 - style.dimRest()));
        }

        Annotations result = base.build();
        for (int index = 0; index < matches.size(); index++)
        {
            result = result.merge(annotateOne(matches.get(index), molecule, style, style.colourOfMatch(index)));
        }
        return result;
    }

    private static Annotations annotateOne(Match match, Molecule molecule, MatchStyle style, Colour colour)
    {
        Annotations.Builder builder = new Annotations.Builder();
        Set<Atom> atoms = match.atoms();

        // matched things are undimmed explicitly, as the defaults dim everything else
        AtomLook atomLook = AtomLook.NONE;
        BondLook bondLook = BondLook.NONE;
        if (style.dimRest() > 0)
        {
            atomLook = atomLook.withOpacity(1);
            bondLook = bondLook.withOpacity(1);
        }
        if (style.halo())
        {
            atomLook = atomLook.withHalo(Halo.of(colour));
            bondLook = bondLook.withHalo(Halo.of(colour));
        }
        if (style.recolour())
        {
            atomLook = atomLook.withFill(colour);
        }

        for (Atom atom : atoms)
        {
            builder.atom(atom, atomLook);
        }
        for (Bond bond : molecule.bonds())
        {
            if (atoms.contains(bond.first()) && atoms.contains(bond.second()))
            {
                builder.bond(bond, bondLook);
            }
        }

        if (style.labels())
        {
            MatchLabels automatic = MatchLabels.of(match, molecule);
            automatic.atoms().forEach(builder::label);
            automatic.bonds().forEach(builder::label);
        }

        return builder.build();
    }
}
