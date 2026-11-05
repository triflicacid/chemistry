package com.ruben.chemistry.smiles;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.CanonicalRanker;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.Ranker;
import com.ruben.chemistry.core.Ranking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serialises a {@link Molecule} to a SMILES string.
 * <p>
 * By default, the output is not canonical: it depends on the order atoms and bonds were added
 * to the molecule, so the same molecule built differently can produce a different (but equally
 * valid) SMILES string. Supplying a {@link Ranker} via {@link #setRanker} instead drives atom and
 * branch ordering from its {@link Ranking}, giving deterministic output across differently-built
 * but structurally equivalent molecules.
 */
public final class SmilesWriter
{
    private final Molecule molecule;
    private final Set<Atom> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Bond, Integer> ringNumberOfBond = new IdentityHashMap<>();
    private final Map<Atom, List<Bond>> ringBondsOfAtom = new IdentityHashMap<>();
    private int nextRingNumber = 1;
    private boolean renderChirality = true;
    private Ranker ranker;
    private Ranking ranking;

    public SmilesWriter(Molecule molecule)
    {
        this.molecule = molecule;
    }

    public boolean isRenderChirality()
    {
        return renderChirality;
    }

    public SmilesWriter setRenderChirality(boolean renderChirality)
    {
        this.renderChirality = renderChirality;
        return this;
    }

    /**
     * @return the ranker driving atom and branch order, or {@code null} if using insertion order
     */
    public Ranker getRanker()
    {
        return ranker;
    }

    /**
     * Supplies a ranker to drive deterministic atom and branch ordering, instead of the default
     * insertion order. {@link CanonicalRanker} is the usual choice.
     *
     * @param ranker the ranker to use, or {@code null} to fall back to insertion order
     * @return this writer, for chaining
     */
    public SmilesWriter setRanker(Ranker ranker)
    {
        this.ranker = ranker;
        return this;
    }

    /**
     * Writes a molecule as a SMILES string, in insertion order.
     *
     * @param molecule the molecule to write
     * @return the SMILES string, with disconnected fragments separated by {@code .}
     */
    public static String write(Molecule molecule)
    {
        return new SmilesWriter(molecule).write();
    }

    /**
     * Writes a molecule as a SMILES string, with deterministic atom and branch ordering
     * driven by a {@link CanonicalRanker}.
     *
     * @param molecule the molecule to write
     * @return the SMILES string, with disconnected fragments separated by {@code .}
     */
    public static String writeCanonical(Molecule molecule)
    {
        return new SmilesWriter(molecule).setRanker(new CanonicalRanker()).write();
    }

    /**
     * Writes the molecule as a SMILES string.
     *
     * @return the SMILES string, with disconnected fragments separated by {@code .}
     */
    public String write()
    {
        ranking = ranker == null ? null : ranker.rank(molecule);

        for (Atom atom : molecule.atoms())
        {
            ringBondsOfAtom.put(atom, new ArrayList<>());
        }

        // First pass: walk the graph purely to assign ring numbers
        for (Atom atom : rootAtoms())
        {
            if (!visited.contains(atom))
            {
                discoverRingClosures(atom, null);
            }
        }

        // Second pass: reuse the atom-visiting order above to emit text
        visited.clear();

        StringBuilder text = new StringBuilder();
        for (Atom atom : rootAtoms())
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
     * @return the molecule's atoms, in the order fragments should be visited and rooted
     */
    private List<Atom> rootAtoms()
    {
        if (ranking == null)
        {
            List<Atom> atoms = new ArrayList<>();
            for (Atom atom : molecule.atoms())
            {
                atoms.add(atom);
            }
            return atoms;
        }
        return ranking.atomsByAscendingRank();
    }

    /**
     * @param atom the atom whose incident bonds are required
     * @return {@code atom}'s incident bonds, in the order they should be considered as branches
     */
    private List<Bond> orderedBondsOf(Atom atom)
    {
        List<Bond> bonds = new ArrayList<>();
        for (Bond bond : molecule.bondsOf(atom))
        {
            bonds.add(bond);
        }
        if (ranking != null)
        {
            // stable sort: ties fall back to the original bondsOf order
            bonds.sort(Comparator.comparingInt(bond -> ranking.rankOf(bond.getOther(atom))));
        }
        return bonds;
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

        for (Bond bond : orderedBondsOf(atom))
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

        // Ring bonds are already represented by numbers, so don't consider them as branches
        List<Bond> branches = new ArrayList<>();
        for (Bond bond : orderedBondsOf(atom))
        {
            if (bond == arrivingBond || ringNumberOfBond.containsKey(bond))
            {
                continue;
            }
            branches.add(bond);
        }

        text.append(atom.toString(ringClosureTokens(atom), resolveChirality(atom, arrivingBond, branches)));

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
     * Resolves the chirality to render for an atom, if it is a stereocentre.
     *
     * <p>{@link Molecule} stores chirality relative to a fixed neighbour order
     * which may differ from the order neighbours are actually written in here, so this
     * reprojects the declared chirality onto the written order via {@link Molecule#chiralityOf(Atom, List)}.
     *
     * @param atom the atom about to be written
     * @param arrivingBond the bond traversed to reach {@code atom}, or {@code null} for a root atom
     * @param branches the bonds to be written as this atom's branches, in emission order
     * @return the chirality to render, or {@code null} if {@code atom} is not a stereocentre or rendering is disabled
     */
    private Chirality resolveChirality(Atom atom, Bond arrivingBond, List<Bond> branches)
    {
        if (!renderChirality)
        {
            return null;
        }

        List<Object> writtenOrder = new ArrayList<>();
        // start from our arrival
        if (arrivingBond != null)
        {
            writtenOrder.add(arrivingBond);
        }
        // then any implicit hydrogens
        if (atom.hydrogenCount().orElse(0) > 0)
        {
            writtenOrder.add(Molecule.IMPLICIT_HYDROGEN);
        }
        // then any ring bonds
        writtenOrder.addAll(ringBondsOfAtom.get(atom));
        // then any branches
        writtenOrder.addAll(branches);

        return molecule.chiralityOf(atom, writtenOrder).orElse(null);
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
