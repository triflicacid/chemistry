package com.ruben.chemistry.smiles;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serialises a {@link Molecule} to a SMILES string.
 * The output is not canonical, and depends on the order atoms and bonds were added to the molecule, so the
 * same molecule built differently can produce a different (but equally valid) SMILES string.
 * <p>
 * TODO: Take a canonicalisation option/strategy so callers can request a
 *     canonical atom ordering (e.g. Morgan/invariant-rank based) for deterministic,
 *     comparable output across differently-constructed but equivalent molecules.
 */
public final class SmilesWriter
{
    private final Molecule molecule;
    private final Set<Atom> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Bond, Integer> ringNumberOfBond = new IdentityHashMap<>();
    private final Map<Atom, List<Bond>> ringBondsOfAtom = new IdentityHashMap<>();
    private int nextRingNumber = 1;

    public SmilesWriter(Molecule molecule)
    {
        this.molecule = molecule;
    }

    /**
     * Writes a molecule as a SMILES string.
     *
     * @param molecule the molecule to write
     * @return the SMILES string, with disconnected fragments separated by {@code .}
     */
    public static String write(Molecule molecule)
    {
        return new SmilesWriter(molecule).write();
    }

    /**
     * Writes the molecule as a SMILES string.
     *
     * @return the SMILES string, with disconnected fragments separated by {@code .}
     */
    public String write()
    {
        for (Atom atom : molecule.atoms())
        {
            ringBondsOfAtom.put(atom, new ArrayList<>());
        }

        // First pass: walk the graph purely to assign ring numbers
        for (Atom atom : molecule.atoms())
        {
            if (!visited.contains(atom))
            {
                discoverRingClosures(atom, null);
            }
        }

        // Second pass: reuse the atom-visiting order above to emit text
        visited.clear();

        StringBuilder text = new StringBuilder();
        for (Atom atom : molecule.atoms())
        {
            if (!visited.contains(atom))
            {
                if (!text.isEmpty())
                {
                    text.append('.');
                }
                writeAtom(atom, null, text);
            }
        }
        return text.toString();
    }

    /**
     * Depth-first traversal that assigns ring numbers to back-edge bonds: bonds that
     * reach an already-visited atom, i.e. bonds not part of the spanning tree rooted
     * at the first atom visited in each fragment.
     *
     * @param atom the atom currently being visited
     * @param arrivingBond the bond traversed to reach {@code atom}, or {@code null} for a root atom
     */
    private void discoverRingClosures(Atom atom, Bond arrivingBond)
    {
        visited.add(atom);

        for (Bond bond : molecule.bondsOf(atom))
        {
            if (bond == arrivingBond)
            {
                continue;
            }

            Atom neighbour = bond.getOther(atom);
            if (visited.contains(neighbour))
            {
                // neighbour was reached earlier by a different path, so this bond closes a ring
                if (!ringNumberOfBond.containsKey(bond))
                {
                    ringNumberOfBond.put(bond, nextRingNumber++);
                    ringBondsOfAtom.get(atom).add(bond);
                    ringBondsOfAtom.get(neighbour).add(bond);
                }
            }
            else
            {
                discoverRingClosures(neighbour, bond);
            }
        }
    }

    /**
     * Depth-first traversal that emits SMILES text, following the same atom-visiting
     * order as {@link #discoverRingClosures}.
     *
     * @param atom the atom currently being visited
     * @param arrivingBond the bond traversed to reach {@code atom}, or {@code null} for a root atom
     * @param text the text produced so far, appended to in place
     */
    private void writeAtom(Atom atom, Bond arrivingBond, StringBuilder text)
    {
        visited.add(atom);

        if (arrivingBond != null)
        {
            text.append(bondSymbolPrefix(arrivingBond));
        }
        text.append(atom.toString(ringClosureTokens(atom)));

        // Ring bonds are already represented by numbers, so don't consider them
        List<Bond> branches = new ArrayList<>();
        for (Bond bond : molecule.bondsOf(atom))
        {
            if (bond == arrivingBond || ringNumberOfBond.containsKey(bond))
            {
                continue;
            }
            branches.add(bond);
        }

        // All but the last branch are wrapped in brackets so the chain can resume from this atom afterwards
        for (int i = 0; i < branches.size(); i++)
        {
            Bond bond = branches.get(i);
            Atom neighbour = bond.getOther(atom);
            boolean isLastBranch = i == branches.size() - 1;

            if (isLastBranch)
            {
                writeAtom(neighbour, bond, text);
            }
            else
            {
                text.append('(');
                writeAtom(neighbour, bond, text);
                text.append(')');
            }
        }
    }

    /**
     * Builds the ring-closure tokens to append after an atom's own token.
     *
     * @param atom the atom whose ring-closure tokens are required
     * @return the pre-formatted ring-closure tokens, in no particular but stable order
     */
    private List<String> ringClosureTokens(Atom atom)
    {
        List<String> tokens = new ArrayList<>();
        for (Bond bond : ringBondsOfAtom.get(atom))
        {
            int ringNumber = ringNumberOfBond.get(bond);
            String digits = ringNumber >= 10 ? "%" + ringNumber : String.valueOf(ringNumber);
            tokens.add(bondSymbolPrefix(bond) + digits);
        }
        return tokens;
    }

    /**
     * Determines the bond symbol to prefix a bond's written form with, if its order
     * would not already be inferred by default.
     *
     * @param bond the bond to be written
     * @return the bond's symbol, or an empty if its order is the default inferred between its two atoms
     */
    private String bondSymbolPrefix(Bond bond)
    {
        BondOrder defaultOrder = BondOrder.inferredBetween(bond.first(), bond.second());
        return bond.order() == defaultOrder ? "" : String.valueOf(bond.order().symbol());
    }
}
