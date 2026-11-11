package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.util.IdentityMap;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Short, deterministic labels for the atoms and bonds a {@link Match} covers.
 * <p>
 * Atoms are labelled by element symbol and a running count per element (Cl1, Cl2, C1, ...);
 * bonds by "#" and a running count (#1, #2, ...).
 *
 * @param atoms the label of each matched atom
 * @param bonds the label of each bond between matched atoms
 */
public record MatchLabels(Map<Atom, String> atoms, Map<Bond, String> bonds)
{
    public MatchLabels
    {
        atoms = IdentityMap.copyOf(atoms);
        bonds = IdentityMap.copyOf(bonds);
    }

    /**
     * @param match a match against {@code molecule}
     * @param molecule the molecule {@code match} was found in
     * @return labels for the covered atoms and bonds
     */
    public static MatchLabels of(Match match, Molecule molecule)
    {
        Set<Atom> matched = match.atoms();

        Map<Atom, String> atomLabels = new IdentityHashMap<>();
        Map<String, Integer> perElement = new HashMap<>();
        for (Atom atom : molecule.atoms())
        {
            if (matched.contains(atom))
            {
                atomLabels.put(atom, atom.element() + perElement.merge(atom.element(), 1, Integer::sum));
            }
        }

        Map<Bond, String> bondLabels = new IdentityHashMap<>();
        int count = 0;
        for (Bond bond : molecule.bonds())
        {
            if (matched.contains(bond.first()) && matched.contains(bond.second()))
            {
                bondLabels.put(bond, "#" + ++count);
            }
        }

        return new MatchLabels(atomLabels, bondLabels);
    }
}
