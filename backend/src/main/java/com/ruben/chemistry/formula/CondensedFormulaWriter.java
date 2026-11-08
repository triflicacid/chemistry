package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.CanonicalRanker;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.Ranker;
import com.ruben.chemistry.core.Ranking;
import com.ruben.chemistry.util.IdentitySet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serialises a {@link Molecule} to a condensed structural formula, e.g., {@code CH3CH2OH} for ethanol,
 * {@code CH2%1CH2CH2%1} for cyclopropane.
 * <p>
 * By default, the output is not canonical: it depends on the order atoms and bonds were added to the
 * molecule, so the same molecule built differently can produce a different (but equally valid)
 * formula. Supplying a {@link Ranker} via {@link #setRanker} instead drives atom and branch ordering
 * from its {@link Ranking}, giving deterministic output across differently-built but structurally
 * equivalent molecules.
 */
public final class CondensedFormulaWriter
{
    private final Molecule molecule;

    /** The atoms reached so far by the traversal in progress; cleared between the two passes. */
    private final Set<Atom> visited = IdentitySet.of();

    /** The ring-closure number given to each bond written as a pair of markers rather than traversed. */
    private final Map<Bond, Integer> ringNumberOfBond = new IdentityHashMap<>();

    /** The ring-closure bonds to write markers for at each atom, in the order they were found. */
    private final Map<Atom, List<Bond>> ringBondsOfAtom = new IdentityHashMap<>();

    private int nextRingNumber = 1;
    private boolean renderBondSymbols = true;
    private Ranker ranker;
    private Ranking ranking;

    /**
     * @param molecule the molecule to write
     */
    public CondensedFormulaWriter(Molecule molecule)
    {
        this.molecule = molecule;
    }

    /**
     * @return {@code true} if {@code =}, {@code #}, and {@code $} are written for multiple bonds
     */
    public boolean isRenderBondSymbols()
    {
        return renderBondSymbols;
    }

    /**
     * Chooses whether multiple bonds are marked: ethene as {@code CH2=CH2} or as {@code CH2CH2}.
     *
     * @param renderBondSymbols whether to write a symbol for a bond above single order
     * @return this writer, for chaining
     */
    public CondensedFormulaWriter setRenderBondSymbols(boolean renderBondSymbols)
    {
        this.renderBondSymbols = renderBondSymbols;
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
     * Supplies a ranker to drive deterministic atom and branch ordering.
     *
     * @param ranker the ranker to use, or {@code null} to fall back to insertion order
     * @return this writer, for chaining
     */
    public CondensedFormulaWriter setRanker(Ranker ranker)
    {
        this.ranker = ranker;
        return this;
    }

    /**
     * Writes a molecule as a condensed structural formula, in insertion order.
     *
     * @param molecule the molecule to write
     * @return the formula, with disconnected fragments separated by {@code .}
     */
    public static String write(Molecule molecule)
    {
        return new CondensedFormulaWriter(molecule).write();
    }

    /**
     * Writes a molecule as a condensed structural formula, with deterministic atom and branch
     * ordering driven by a {@link CanonicalRanker}.
     *
     * @param molecule the molecule to write
     * @return the formula, with disconnected fragments separated by {@code .}
     */
    public static String writeCanonical(Molecule molecule)
    {
        return new CondensedFormulaWriter(molecule).setRanker(new CanonicalRanker()).write();
    }

    /**
     * Writes the molecule as a condensed structural formula.
     *
     * @return the formula, with disconnected fragments separated by {@code .}
     */
    public String write()
    {
        ranking = ranker == null ? null : ranker.rank(molecule);

        visited.clear();
        ringNumberOfBond.clear();
        nextRingNumber = 1;
        molecule.atoms().forEach(atom -> ringBondsOfAtom.put(atom, new ArrayList<>()));

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
            // an unvisited atom here belongs to a fragment nothing written so far reaches
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
     * @return {@code atom}'s incident bonds
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
     * Depth-first traversal that assigns ring numbers to back-edge bonds (bonds that reach an already-visited atom.)
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
     * Depth-first traversal that emits the formula.
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
            text.append(bondSymbol(arrivingBond));
        }

        // ring bonds are already represented by their markers, so don't consider them as branches
        List<Bond> branches = new ArrayList<>();
        for (Bond bond : orderedBondsOf(atom))
        {
            if (bond != arrivingBond && !ringNumberOfBond.containsKey(bond))
            {
                branches.add(bond);
            }
        }

        text.append(groupOf(atom));
        for (Bond ringBond : ringBondsOfAtom.get(atom))
        {
            text.append(bondSymbol(ringBond)).append('%').append(ringNumberOfBond.get(ringBond));
        }

        // all but the last branch are wrapped in brackets so the chain can resume from this atom afterwards
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
     * Writes a single atom as a group: its element symbol, its hydrogen count, and its charge.
     *
     * @param atom the atom to write
     * @return {@code atom}'s group, e.g. {@code CH3} or {@code NH4+}
     */
    private String groupOf(Atom atom)
    {
        StringBuilder group = new StringBuilder(atom.element());

        int hydrogens = molecule.totalHydrogenCount(atom);
        if (hydrogens > 0)
        {
            group.append('H');
            if (hydrogens > 1)
            {
                group.append(hydrogens);
            }
        }

        int charge = atom.charge();
        if (charge != 0)
        {
            // conventionally 2- rather than -2
            if (Math.abs(charge) > 1)
            {
                group.append(Math.abs(charge));
            }
            group.append(charge > 0 ? '+' : '-');
        }
        return group.toString();
    }

    /**
     * @param bond the bond to be written
     * @return the bond's symbol, or an empty string if we don't need to render it
     */
    private String bondSymbol(Bond bond)
    {
        // a single bond is the default between two groups, and an aromatic one has no order to mark
        if (!renderBondSymbols || bond.order() == BondOrder.SINGLE || bond.order() == BondOrder.AROMATIC)
        {
            return "";
        }
        return String.valueOf(bond.order().symbol());
    }
}
