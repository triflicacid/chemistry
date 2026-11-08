package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.CanonicalRanker;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.Ranker;
import com.ruben.chemistry.core.Ranking;
import com.ruben.chemistry.util.IdentitySet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Serialises a {@link Molecule} to a condensed structural formula, e.g., {@code CH3CH2OH} for ethanol,
 * {@code CH2%1CH2CH2%1} for cyclopropane.
 * If enabled ({@link #setCollapseRepeats}), collapse repeats down: a run along a chain, e.g., hexane is
 * {@code CH3(CH2)4CH3}, and interchangeable branches at one atom, e.g., tetrachloromethane is {@code CCl4}.
 * <p>
 * By default, the output is not canonical: it depends on the order atoms and bonds were added to the
 * molecule, so the same molecule built differently can produce a different (but equally valid)
 * formula. Supplying a {@link Ranker} via {@link #setRanker} instead drives atom and branch ordering
 * from its {@link Ranking}, giving deterministic output across differently-built but structurally
 * equivalent molecules.
 */
public final class CondensedFormulaWriter
{
    private static final String WILDCARD = "*";

    /** The shortest run of interchangeable groups collapsed into a repeat group unless told otherwise. */
    private static final int DEFAULT_MINIMUM_REPEAT = 2;

    private final Molecule molecule;

    /** The atoms reached so far by the traversal in progress; cleared between the two passes. */
    private final Set<Atom> visited = IdentitySet.of();

    /** The ring-closure number given to each bond written as a pair of markers rather than traversed. */
    private final Map<Bond, Integer> ringNumberOfBond = new IdentityHashMap<>();

    /** The ring-closure bonds to write markers for at each atom, in the order they were found. */
    private final Map<Atom, List<Bond>> ringBondsOfAtom = new IdentityHashMap<>();

    private int nextRingNumber = 1;
    private boolean renderBondSymbols = true;
    private boolean unicode;
    private boolean collapseRepeats = true;
    private int minimumRepeat = DEFAULT_MINIMUM_REPEAT;
    private MolecularFormula.ElementOrder elementOrder = MolecularFormula.ElementOrder.CONVENTIONAL;
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
     * @return {@code true} if hydrogen counts and charges are written as subscripts and superscripts
     */
    public boolean isUnicode()
    {
        return unicode;
    }

    /**
     * @param unicode whether to atom counts and charges in sub/superscript.
     * @return this writer, for chaining
     */
    public CondensedFormulaWriter setUnicode(boolean unicode)
    {
        this.unicode = unicode;
        return this;
    }

    /**
     * @return the order deciding whether a leading group's hydrogens come before its symbol
     */
    public MolecularFormula.ElementOrder getElementOrder()
    {
        return elementOrder;
    }

    /**
     * Chooses the element order a group with nothing before it in its fragment is written in.
     *
     * @param elementOrder the order to write a leading group in
     * @return this writer, for chaining
     */
    public CondensedFormulaWriter setElementOrder(MolecularFormula.ElementOrder elementOrder)
    {
        this.elementOrder = elementOrder;
        return this;
    }

    /**
     * @return {@code true} if a run of interchangeable groups is written as a repeat group
     */
    public boolean isCollapseRepeats()
    {
        return collapseRepeats;
    }

    /**
     * Chooses whether runs of interchangeable groups are collapsed: hexane as {@code CH3(CH2)4CH3} or as
     * {@code CH3CH2CH2CH2CH2CH3}, dichloromethane as {@code CH2Cl2} or as {@code CH2(Cl)Cl}.
     *
     * @param collapseRepeats whether to collapse runs of interchangeable groups
     * @return this writer, for chaining
     */
    public CondensedFormulaWriter setCollapseRepeats(boolean collapseRepeats)
    {
        this.collapseRepeats = collapseRepeats;
        return this;
    }

    /**
     * @return the shortest run of interchangeable groups written as a repeat group
     */
    public int getMinimumRepeat()
    {
        return minimumRepeat;
    }

    /**
     * Sets how long a run of interchangeable groups must be to be collapsed.
     * <p>
     * A run must also save characters to be worth collapsing, so at the default of two butane stays {@code CH3CH2CH2CH3}
     * as {@code CH3(CH2)2CH3} is no shorter, while hexane becomes {@code CH3(CH2)4CH3}.
     *
     * @param minimumRepeat the shortest run to collapse, at least two
     * @return this writer, for chaining
     * @throws IllegalArgumentException if {@code minimumRepeat} is below two
     */
    public CondensedFormulaWriter setMinimumRepeat(int minimumRepeat)
    {
        if (minimumRepeat < 2)
        {
            throw new IllegalArgumentException("a repeat group stands for at least two groups: " + minimumRepeat);
        }
        this.minimumRepeat = minimumRepeat;
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
                text.append(renderChain(trace(atom, null), true));
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
     * Depth-first traversal that records the structure reached from an atom, without writing anything.
     *
     * @param atom the atom at the root of the subtree
     * @param arrivingBond the bond traversed to reach {@code atom}, or {@code null} for a root atom
     * @return {@code atom} and everything reached through it
     */
    private Node trace(Atom atom, Bond arrivingBond)
    {
        visited.add(atom);

        List<Node> children = new ArrayList<>();
        for (Bond bond : orderedBondsOf(atom))
        {
            // ring bonds are already represented by their markers, so they lead nowhere new
            if (bond != arrivingBond && !ringNumberOfBond.containsKey(bond))
            {
                children.add(trace(bond.getOther(atom), bond));
            }
        }
        return new Node(atom, arrivingBond, ringBondsOfAtom.get(atom), children);
    }

    /**
     * Writes a chain, taking one branch of each atom as the chain's continuation and hanging the rest
     * off it, then collapsing the runs of interchangeable segments that result.
     *
     * @param start the atom the chain starts at
     * @param leadsFragment whether nothing is written before {@code start} within its fragment
     * @return the text of the chain and everything hanging off it
     */
    private String renderChain(Node start, boolean leadsFragment)
    {
        List<Segment> segments = new ArrayList<>();
        Node node = start;

        while (node != null)
        {
            List<BranchRun> branches = groupBranches(node.children());

            // a branch standing on its own can carry the chain onwards; one in a run stays with its group
            int continuation = -1;
            for (int i = 0; i < branches.size(); i++)
            {
                if (branches.get(i).count() == 1)
                {
                    continuation = i;
                }
            }
            Node next = continuation < 0 ? null : branches.remove(continuation).branch();

            boolean leading = leadsFragment && segments.isEmpty();
            boolean branchesFirst = leading && next != null;
            segments.add(new Segment(node, branches, leading,
                renderSegment(node, branches, leading, branchesFirst)));
            node = next;
        }
        return joinSegments(segments);
    }

    /**
     * Gathers an atom's branches into runs of interchangeable ones, keeping them in the order they
     * were traced.
     *
     * @param children the branches hanging off one atom
     * @return the runs, each holding one branch and how many of it there are
     */
    private List<BranchRun> groupBranches(List<Node> children)
    {
        List<BranchRun> runs = new ArrayList<>();
        if (!collapseRepeats)
        {
            children.forEach(child -> runs.add(new BranchRun(child, 1)));
            return runs;
        }

        boolean[] grouped = new boolean[children.size()];
        for (int i = 0; i < children.size(); i++)
        {
            if (grouped[i])
            {
                continue;
            }

            int count = 1;
            // a match further along still joins this run, so interchangeable branches need not be adjacent
            for (int j = i + 1; j < children.size(); j++)
            {
                if (!grouped[j] && interchangeable(children.get(i), children.get(j)))
                {
                    grouped[j] = true;
                    count++;
                }
            }
            runs.add(new BranchRun(children.get(i), count));
        }
        return runs;
    }

    /**
     * Writes one position along a chain: the atom there, its ring markers, and its branches.
     *
     * @param node the atom at this position
     * @param branches the branches hanging off it, the chain's continuation already removed
     * @param leading whether nothing is written before {@code node} within its fragment
     * @param branchesFirst whether a collapsed branch group is written ahead of the atom
     * @return the position's text
     */
    private String renderSegment(Node node, List<BranchRun> branches, boolean leading, boolean branchesFirst)
    {
        StringBuilder text = new StringBuilder();
        text.append(arrivingSymbol(node));

        // a collapsed group reads better ahead of its atom when the chain carries on past it, as in (CH3)2CHOH
        if (branchesFirst)
        {
            branches.stream().filter(this::isCollapsed).forEach(run -> text.append(renderBranch(run)));
        }

        text.append(groupOf(node.atom(), leading));
        for (Bond ringBond : node.ringBonds())
        {
            text.append(bondSymbol(ringBond)).append('%').append(ringNumberOfBond.get(ringBond));
        }

        for (BranchRun run : branches)
        {
            if (!branchesFirst || !isCollapsed(run))
            {
                text.append(renderBranch(run));
            }
        }
        return text.toString();
    }

    /**
     * @param run the run to test
     * @return {@code true} if {@code run} is written once with a count rather than branch by branch
     */
    private boolean isCollapsed(BranchRun run)
    {
        return collapseRepeats && run.count() >= minimumRepeat;
    }

    /**
     * Writes a run of interchangeable branches.
     *
     * @param run the run to write
     * @return the run's text, e.g. {@code (CH3)2}, {@code Cl4}, or a lone {@code (OH)}
     */
    private String renderBranch(BranchRun run)
    {
        String branch = renderChain(run.branch(), false);
        if (!isCollapsed(run))
        {
            // nothing resumes through a branch, so each one is bracketed in turn
            return ("(" + branch + ")").repeat(run.count());
        }
        if (takesCountDirectly(run.branch()))
        {
            return branch + countText(run.count());
        }
        return "(" + branch + ")" + countText(run.count());
    }

    /**
     * @param branch the branch to test
     * @return {@code true} if a count can follow {@code branch} with no brackets to separate them
     */
    private boolean takesCountDirectly(Node branch)
    {
        // only a bare element symbol leaves the count unambiguous: CCl4, but (CH3)4 and (CH)4 both need brackets
        return branch.children().isEmpty()
            && branch.ringBonds().isEmpty()
            && branch.atom().charge() == 0
            && molecule.totalHydrogenCount(branch.atom()) == 0
            && arrivingSymbol(branch).isEmpty();
    }

    /**
     * Joins a chain's segments, writing each run of interchangeable ones as a repeat group.
     *
     * @param segments the chain's segments, in the order they were walked
     * @return the chain's text
     */
    private String joinSegments(List<Segment> segments)
    {
        StringBuilder text = new StringBuilder();

        int index = 0;
        while (index < segments.size())
        {
            int period = collapseRepeats ? repeatPeriod(segments, index) : 0;
            if (period == 0)
            {
                text.append(segments.get(index).text());
                index++;
                continue;
            }

            int count = repeatCount(segments, index, period);
            text.append('(');
            for (int i = 0; i < period; i++)
            {
                text.append(segments.get(index + i).text());
            }
            text.append(')').append(countText(count));
            index += period * count;
        }
        return text.toString();
    }

    /**
     * Looks for a repeating motif starting at one segment, preferring the shortest motif that pays
     * for the brackets it costs.
     *
     * @param segments the chain's segments
     * @param from the segment the motif would start at
     * @return the motif's length in segments, or zero if nothing worth collapsing starts here
     */
    private int repeatPeriod(List<Segment> segments, int from)
    {
        // a repeat group opening with a bond symbol would read as a branch off the group before it
        if (!arrivingSymbol(segments.get(from).node()).isEmpty())
        {
            return 0;
        }

        for (int period = 1; from + 2 * period <= segments.size(); period++)
        {
            int count = repeatCount(segments, from, period);
            if (count < minimumRepeat)
            {
                continue;
            }

            int motif = 0;
            for (int i = 0; i < period; i++)
            {
                motif += segments.get(from + i).text().length();
            }

            // collapsing has to earn its brackets, which is why CH2CH2 is left alone
            if (motif + 2 + String.valueOf(count).length() < motif * count)
            {
                return period;
            }
        }
        return 0;
    }

    /**
     * @param segments the chain's segments
     * @param from the segment the motif starts at
     * @param period the motif's length in segments
     * @return how many times the motif repeats from {@code from}, at least one
     */
    private int repeatCount(List<Segment> segments, int from, int period)
    {
        int count = 1;
        while (from + (count + 1) * period <= segments.size())
        {
            int next = from + count * period;
            boolean matches = true;
            for (int i = 0; i < period && matches; i++)
            {
                matches = sameSegment(segments.get(from + i), segments.get(next + i));
            }

            if (!matches)
            {
                break;
            }
            count++;
        }
        return count;
    }

    /**
     * @param first the first segment
     * @param second the second segment
     * @return {@code true} if both segments write the same text, their continuations aside
     */
    private boolean sameSegment(Segment first, Segment second)
    {
        if (first.leading() != second.leading()
            || !rendersAlike(first.node(), second.node())
            || first.branches().size() != second.branches().size())
        {
            return false;
        }

        for (int i = 0; i < first.branches().size(); i++)
        {
            BranchRun ours = first.branches().get(i);
            BranchRun theirs = second.branches().get(i);
            if (ours.count() != theirs.count() || !interchangeable(ours.branch(), theirs.branch()))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @param first the first atom's node
     * @param second the second atom's node
     * @return {@code true} if both atoms write the same group, whatever hangs off them
     */
    private boolean rendersAlike(Node first, Node second)
    {
        // ring markers are numbered one at a time, and an aromatic ring is delocalised rather than repeating
        if (!first.ringBonds().isEmpty() || !second.ringBonds().isEmpty()
            || first.atom().aromatic() || second.atom().aromatic())
        {
            return false;
        }

        // comparing symbols rather than orders lets two bonds match when neither of them is written
        return first.atom().element().equals(second.atom().element())
            && first.atom().charge() == second.atom().charge()
            && molecule.totalHydrogenCount(first.atom()) == molecule.totalHydrogenCount(second.atom())
            && arrivingSymbol(first).equals(arrivingSymbol(second));
    }

    /**
     * @param first the first subtree's root
     * @param second the second subtree's root
     * @return {@code true} if both subtrees write the same text, so one can stand for the other
     */
    private boolean interchangeable(Node first, Node second)
    {
        if (!rendersAlike(first, second) || first.children().size() != second.children().size())
        {
            return false;
        }

        for (int i = 0; i < first.children().size(); i++)
        {
            if (!interchangeable(first.children().get(i), second.children().get(i)))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @param node the atom whose arriving bond is required
     * @return the symbol of the bond reached {@code node} by, or an empty string for a root atom
     */
    private String arrivingSymbol(Node node)
    {
        return node.arrivingBond() == null ? "" : bondSymbol(node.arrivingBond());
    }

    /**
     * @param count how many times a group repeats
     * @return {@code count} as it follows a repeat group
     */
    private String countText(int count)
    {
        return unicode ? Unicode.subscript(count) : String.valueOf(count);
    }

    /**
     * One atom and everything reached through it, as the traversal found it.
     *
     * @param atom the atom at this node
     * @param arrivingBond the bond traversed to reach it, or {@code null} for a root atom
     * @param ringBonds the ring-closure bonds to write markers for at this atom
     * @param children the subtrees hanging off it, in the order they were traced
     */
    private record Node(Atom atom, Bond arrivingBond, List<Bond> ringBonds, List<Node> children)
    {
    }

    /**
     * A run of interchangeable branches at one atom, written once with a count.
     *
     * @param branch the branch standing for the whole run
     * @param count how many of it there are, at least one
     */
    private record BranchRun(Node branch, int count)
    {
    }

    /**
     * One position along a chain.
     *
     * @param node the atom at this position
     * @param branches the branches hanging off it, the chain's continuation removed
     * @param leading whether nothing is written before it within its fragment
     * @param text the position's text
     */
    private record Segment(Node node, List<BranchRun> branches, boolean leading, String text)
    {
    }

    /**
     * Writes a single atom as a group: its element symbol, its hydrogen count, and its charge.
     *
     * @param atom the atom to write
     * @param leading whether nothing is written before {@code atom} within its fragment, which lets
     *     its hydrogens come first
     * @return {@code atom}'s group, e.g. {@code CH3}, {@code NH4+}, or a leading {@code H2O}
     */
    private String groupOf(Atom atom, boolean leading)
    {
        StringBuilder group = new StringBuilder();
        int hydrogens = molecule.totalHydrogenCount(atom);

        if (leading && hydrogensComeFirst(atom.element()))
        {
            appendHydrogens(group, hydrogens);
            group.append(atom.element());
        }
        else
        {
            group.append(atom.element());
            appendHydrogens(group, hydrogens);
        }

        int charge = atom.charge();
        if (charge != 0)
        {
            // conventionally 2- rather than -2
            int magnitude = Math.abs(charge);
            if (magnitude > 1)
            {
                group.append(unicode ? Unicode.superscript(magnitude) : String.valueOf(magnitude));
            }
            if (unicode)
            {
                group.append(charge > 0 ? Unicode.PLUS : Unicode.MINUS);
            }
            else
            {
                group.append(charge > 0 ? '+' : '-');
            }
        }
        return group.toString();
    }

    /**
     * @param element the group's element symbol
     * @return {@code true} if hydrogen is written first
     */
    private boolean hydrogensComeFirst(String element)
    {
        // a wildcard stands for no element, so there is no ordering to appeal to
        if (element.equals(WILDCARD))
        {
            return false;
        }
        if (elementOrder == MolecularFormula.ElementOrder.HILL)
        {
            // Hill promotes carbon above hydrogen and leaves every other element to the alphabet
            return !element.equals("C") && "H".compareTo(element) < 0;
        }
        return Elements.iupacOrderOf("H") < Elements.iupacOrderOf(element);
    }

    /**
     * Appends a group's hydrogen count, if it has any, as {@code H} and a number above one.
     *
     * @param group the group being built, appended to in place
     * @param hydrogens the number of hydrogens attached
     */
    private void appendHydrogens(StringBuilder group, int hydrogens)
    {
        if (hydrogens > 0)
        {
            group.append('H');
            if (hydrogens > 1)
            {
                group.append(unicode ? Unicode.subscript(hydrogens) : String.valueOf(hydrogens));
            }
        }
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
