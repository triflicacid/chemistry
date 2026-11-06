package com.ruben.chemistry.smiles;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.CanonicalRanker;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.NeighbourReference;
import com.ruben.chemistry.core.Ranker;
import com.ruben.chemistry.core.Ranking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final Map<Atom, Bond> arrivingBondOf = new IdentityHashMap<>();
    private final Map<Bond, Atom> openingAtomOfRingBond = new IdentityHashMap<>();
    private final Map<Bond, Character> directionalSymbolOf = new IdentityHashMap<>();
    private int nextRingNumber = 1;
    private boolean renderChirality = true;
    private boolean renderDoubleBondStereo = true;
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

    public boolean isRenderDoubleBondStereo()
    {
        return renderDoubleBondStereo;
    }

    public SmilesWriter setRenderDoubleBondStereo(boolean renderDoubleBondStereo)
    {
        this.renderDoubleBondStereo = renderDoubleBondStereo;
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

        molecule.atoms().forEach(atom -> ringBondsOfAtom.put(atom, new ArrayList<>()));

        // First pass: walk the graph purely to assign ring numbers
        for (Atom atom : rootAtoms())
        {
            if (!visited.contains(atom))
            {
                discoverRingClosures(atom, null);
            }
        }

        if (renderDoubleBondStereo)
        {
            assignDirectionalSymbols();
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
            return molecule.atoms();
        }
        return ranking.atomsByAscendingRank();
    }

    /**
     * @param atom the atom whose incident bonds are required
     * @return {@code atom}'s incident bonds, in the order they should be considered as branches
     */
    private List<Bond> orderedBondsOf(Atom atom)
    {
        List<Bond> bonds = new ArrayList<>(molecule.bondsOf(atom));
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
        if (arrivingBond != null)
        {
            arrivingBondOf.put(atom, arrivingBond);
        }

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
                    // neighbour was visited (and so written) first, so it's the ring-opening atom
                    openingAtomOfRingBond.put(bond, neighbour);
                }
            }
            else
            {
                discoverRingClosures(neighbour, bond);
            }
        }
    }

    /**
     * Assigns a {@code '/'} or {@code '\'} character to each bond chosen as a stereo
     * double bond's reference substituent (see {@link #referenceBondFor}), for every declared
     * {@link DoubleBondStereo} in the molecule.
     *
     * @throws IllegalStateException if two declared double bond configurations cannot both be
     *     satisfied by a single character on a reference bond they share
     */
    private void assignDirectionalSymbols()
    {
        for (Bond bond : molecule.bonds())
        {
            Optional<DoubleBondStereo> declared = molecule.doubleBondStereoOf(bond);
            if (declared.isEmpty())
            {
                continue;
            }

            Bond firstReference = referenceBondFor(bond, bond.first());
            Bond secondReference = referenceBondFor(bond, bond.second());
            if (firstReference == null || secondReference == null)
            {
                // one endpoint has no explicit substituent to carry a direction symbol
                continue;
            }

            boolean sameSide = molecule.doubleBondStereoOf(bond, firstReference, secondReference).orElseThrow() == DoubleBondStereo.SAME_SIDE;
            reconcile(bond.first(), firstReference, bond.second(), secondReference, sameSide);
        }
    }

    /**
     * Finds the bond to carry a direction symbol for one side of a stereo double bond.
     *
     * @param doubleBond the stereo double bond being resolved
     * @param atom one endpoint of {@code doubleBond}
     * @return the chosen reference bond, or {@code null} if {@code atom} has none available
     */
    private Bond referenceBondFor(Bond doubleBond, Atom atom)
    {
        // first non-ring bond, or use a ring bond if no alternative found
        Bond ringFallback = null;
        for (Bond candidate : orderedBondsOf(atom))
        {
            if (candidate == doubleBond)
            {
                continue;
            }
            if (!ringNumberOfBond.containsKey(candidate))
            {
                return candidate;
            }
            if (ringFallback == null)
            {
                ringFallback = candidate;
            }
        }
        return ringFallback;
    }

    /**
     * Assigns or checks the characters of two reference bonds so that, traced outward from
     * their respective stereocentres, they are equal if {@code sameSide}, or differ otherwise.
     *
     * @throws IllegalStateException if both references are already assigned and don't satisfy {@code sameSide}
     */
    private void reconcile(Atom firstAtom, Bond firstReference, Atom secondAtom, Bond secondReference, boolean sameSide)
    {
        // neither reference has been assigned by an earlier double bond, so start the chain off arbitrarily
        if (!directionalSymbolOf.containsKey(firstReference) && !directionalSymbolOf.containsKey(secondReference))
        {
            directionalSymbolOf.put(firstReference, '/');
        }
        // firstReference is still unassigned, so derive it from secondReference
        if (!directionalSymbolOf.containsKey(firstReference))
        {
            char secondOutward = reorient(secondReference, secondAtom, directionalSymbolOf.get(secondReference));
            char firstOutward = sameSide ? secondOutward : flip(secondOutward);
            directionalSymbolOf.put(firstReference, reorient(firstReference, firstAtom, firstOutward));
        }
        // firstReference was already assigned (by this double bond or an earlier one) but secondReference wasn't, so derive it from firstReference
        else if (!directionalSymbolOf.containsKey(secondReference))
        {
            char firstOutward = reorient(firstReference, firstAtom, directionalSymbolOf.get(firstReference));
            char secondOutward = sameSide ? firstOutward : flip(firstOutward);
            directionalSymbolOf.put(secondReference, reorient(secondReference, secondAtom, secondOutward));
        }

        // both references are now assigned, so verify they actually satisfy sameSide
        char firstOutward = reorient(firstReference, firstAtom, directionalSymbolOf.get(firstReference));
        char secondOutward = reorient(secondReference, secondAtom, directionalSymbolOf.get(secondReference));
        if ((firstOutward == secondOutward) != sameSide)
        {
            throw new IllegalStateException("Bond " + firstReference + " and bond " + secondReference
                + " cannot both carry a direction symbol satisfying every declared double bond configuration they are a reference for");
        }
    }

    /**
     * Converts a reference bond's character between its written form and its "outward" form,
     * as traced starting from {@code atom} (the stereocentre) toward its substituent,
     * regardless of which of the bond's two atoms is actually written first.
     *
     * @param reference the reference bond
     * @param atom the endpoint of {@code reference} whose perspective {@code symbol} is relative to
     * @param symbol the character to reorient
     * @return the reoriented character
     */
    private char reorient(Bond reference, Atom atom, char symbol)
    {
        return isWrittenFirst(reference, atom) ? symbol : flip(symbol);
    }

    /**
     * Determines whether {@code atom} is written first for {@code reference}'s occurrence.
     *
     * @param reference the bond to check
     * @param atom one of {@code reference}'s two endpoints
     * @return {@code true} if {@code atom} is written first
     */
    private boolean isWrittenFirst(Bond reference, Atom atom)
    {
        Atom openingAtom = openingAtomOfRingBond.get(reference);
        if (openingAtom != null)
        {
            return openingAtom == atom;
        }
        return arrivingBondOf.get(atom) != reference;
    }

    /**
     * @param symbol a direction symbol, {@code '/'} or {@code '\'}
     * @return the other direction symbol
     */
    private static char flip(char symbol)
    {
        return symbol == '/' ? '\\' : '/';
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

        List<NeighbourReference> writtenOrder = new ArrayList<>();
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
        return ringBondsOfAtom.get(atom).stream()
            .map(bond ->
            {
                int ringNumber = ringNumberOfBond.get(bond);
                String digits = ringNumber >= 10 ? "%" + ringNumber : String.valueOf(ringNumber);
                return ringBondSymbolPrefix(bond, atom) + digits;
            })
            .toList();
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
        Character directional = directionalSymbolOf.get(bond);
        if (directional != null)
        {
            return String.valueOf(directional);
        }
        BondOrder defaultOrder = BondOrder.inferredBetween(bond.first(), bond.second());
        return bond.order() == defaultOrder ? "" : String.valueOf(bond.order().symbol());
    }

    /**
     * Like {@link #bondSymbolPrefix}, but for a ring-closure bond specifically: prints the
     * flipped character at the ring-closing occurrence, so a chain of alternating
     * {@code '/'}/{@code '\'} characters keeps alternating across the ring closure instead of
     * resetting: e.g. {@code C/1=C/C=C\C=C/C=C\1}.
     *
     * @param bond the ring-closure bond to be written
     * @param atom the endpoint currently being written: the ring-opening or ring-closing atom
     * @return the bond's symbol at {@code atom}'s occurrence, or an empty if its order is the default
     */
    private String ringBondSymbolPrefix(Bond bond, Atom atom)
    {
        Character directional = directionalSymbolOf.get(bond);
        if (directional == null)
        {
            return bondSymbolPrefix(bond);
        }
        boolean isOpeningOccurrence = openingAtomOfRingBond.get(bond) == atom;
        return String.valueOf(isOpeningOccurrence ? directional : flip(directional));
    }
}
