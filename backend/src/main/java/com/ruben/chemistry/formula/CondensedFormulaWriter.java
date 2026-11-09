package com.ruben.chemistry.formula;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.canonical.Ranker;
import com.ruben.chemistry.canonical.Ranking;
import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.util.IdentitySet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

    /** Stands for a bond with no symbol to write, and for a root atom's absent arriving bond. */
    private static final char NO_SYMBOL = '\0';

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
    private ElementOrder elementOrder = ElementOrder.CONVENTIONAL;
    private IsotopeStyle isotopeStyle = IsotopeStyle.MASS_NUMBER;
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
    public ElementOrder getElementOrder()
    {
        return elementOrder;
    }

    /**
     * Chooses the element order a group with nothing before it in its fragment is written in.
     *
     * @param elementOrder the order to write a leading group in
     * @return this writer, for chaining
     */
    public CondensedFormulaWriter setElementOrder(ElementOrder elementOrder)
    {
        this.elementOrder = elementOrder;
        return this;
    }

    /**
     * @return how a labelled nuclide is named
     */
    public IsotopeStyle getIsotopeStyle()
    {
        return isotopeStyle;
    }

    /**
     * @param isotopeStyle the style to name a labelled nuclide with
     * @return this writer, for chaining
     */
    public CondensedFormulaWriter setIsotopeStyle(IsotopeStyle isotopeStyle)
    {
        this.isotopeStyle = isotopeStyle;
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

        List<Atom> roots = rootAtoms();

        // First pass: walk the graph purely to assign ring numbers
        for (Atom atom : roots)
        {
            if (!visited.contains(atom))
            {
                discoverRingClosures(atom);
            }
        }

        // Second pass: reuse the atom-visiting order above to emit text
        visited.clear();

        StringBuilder text = new StringBuilder();
        for (Atom atom : roots)
        {
            // an unvisited atom here belongs to a fragment nothing written so far reaches
            if (!visited.contains(atom))
            {
                if (!text.isEmpty())
                {
                    text.append('.');
                }
                text.append(renderChain(trace(atom), true));
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
     * @return {@code atom}'s incident bonds, in rank order if this writer has a ranking
     */
    private List<Bond> orderedBondsOf(Atom atom)
    {
        List<Bond> bonds = molecule.bondsOf(atom);
        return ranking == null ? bonds : Ranking.bondsByAscendingRank(ranking, atom, bonds);
    }

    /**
     * Depth-first traversal that assigns ring numbers to back-edge bonds (bonds that reach an already-visited atom.)
     *
     * @param root the atom to traverse from
     */
    private void discoverRingClosures(Atom root)
    {
        Deque<Visit> stack = new ArrayDeque<>();
        visited.add(root);
        stack.push(new Visit(root, null, orderedBondsOf(root)));

        while (!stack.isEmpty())
        {
            Visit visit = stack.peek();
            Bond bond = visit.nextBond();
            if (bond == null)
            {
                stack.pop();
                continue;
            }
            if (bond == visit.arrivingBond())
            {
                continue;
            }

            Atom neighbour = bond.getOther(visit.atom());
            if (!visited.contains(neighbour))
            {
                visited.add(neighbour);
                stack.push(new Visit(neighbour, bond, orderedBondsOf(neighbour)));
                continue;
            }

            // neighbour was reached earlier by a different path, so this bond closes a ring
            if (!ringNumberOfBond.containsKey(bond))
            {
                ringNumberOfBond.put(bond, nextRingNumber++);
                ringBondsOfAtom.get(visit.atom()).add(bond);
                ringBondsOfAtom.get(neighbour).add(bond);
            }
        }
    }

    /**
     * Depth-first traversal that records the structure reached from an atom, without writing anything.
     *
     * @param root the atom at the root of the subtree
     * @return {@code root} and everything reached through it
     */
    private Node trace(Atom root)
    {
        Deque<Visit> stack = new ArrayDeque<>();
        visited.add(root);
        stack.push(new Visit(root, null, orderedBondsOf(root)));
        Node traced = null;

        while (!stack.isEmpty())
        {
            Visit visit = stack.peek();
            Bond bond = visit.nextBond();
            if (bond == null)
            {
                traced = nodeOf(visit);
                stack.pop();
                if (!stack.isEmpty())
                {
                    stack.peek().addChild(traced);
                }
                continue;
            }

            // ring bonds are already represented by their markers, so they lead nowhere new
            if (bond == visit.arrivingBond() || ringNumberOfBond.containsKey(bond))
            {
                continue;
            }

            Atom neighbour = bond.getOther(visit.atom());
            visited.add(neighbour);
            stack.push(new Visit(neighbour, bond, orderedBondsOf(neighbour)));
        }
        return traced;
    }

    /**
     * @param visit a fully traversed atom
     * @return {@code visit}'s atom and everything traced below it
     */
    private Node nodeOf(Visit visit)
    {
        List<Bond> ringBonds = ringBondsOfAtom.get(visit.atom());
        boolean matchable = !visit.atom().aromatic() && ringBonds.isEmpty();
        for (Node child : visit.children())
        {
            matchable &= child.matchable();
        }
        return new Node(visit.atom(), visit.arrivingBond(), ringBonds, visit.children(), matchable);
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
            segments.add(new Segment(node, branches, leading, matchable(node, branches),
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
        appendBondSymbol(text, arrivingSymbol(node));

        // a collapsed group reads better ahead of its atom when the chain carries on past it, as in (CH3)2CHOH
        if (branchesFirst)
        {
            branches.stream().filter(this::isCollapsed).forEach(run -> text.append(renderBranch(run)));
        }

        text.append(groupOf(node.atom(), leading));
        for (Bond ringBond : node.ringBonds())
        {
            appendBondSymbol(text, bondSymbol(ringBond));
            text.append('%').append(ringNumberOfBond.get(ringBond));
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
     * @param node the atom at one position along a chain
     * @param branches the branches hanging off it, the chain's continuation already removed
     * @return {@code true} if the position can stand for an interchangeable one
     */
    private boolean matchable(Node node, List<BranchRun> branches)
    {
        if (node.atom().aromatic() || !node.ringBonds().isEmpty())
        {
            return false;
        }

        for (BranchRun run : branches)
        {
            if (!run.branch().matchable())
            {
                return false;
            }
        }
        return true;
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
                && arrivingSymbol(branch) == NO_SYMBOL;
    }

    /**
     * Joins a chain's segments, writing each run of interchangeable ones as a repeat group.
     *
     * @param segments the chain's segments, in the order they were walked
     * @return the chain's text
     */
    private String joinSegments(List<Segment> segments)
    {
        // a chain too short to hold one repeat group has nothing to find, so indexing it would not pay
        RepeatIndex repeats = collapseRepeats && segments.size() >= 2 * minimumRepeat
            ? indexRepeats(segments)
            : null;

        StringBuilder text = new StringBuilder();
        int index = 0;
        while (index < segments.size())
        {
            int period = repeats == null ? 0 : repeatPeriod(repeats, index);
            if (period == 0)
            {
                text.append(segments.get(index).text());
                index++;
                continue;
            }

            int count = repeats.repeatCount(index, period);
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
     * Sorts a chain's segments into classes of interchangeable ones.
     *
     * @param segments the chain's segments, in the order they were walked
     * @return the chain indexed for repeat detection
     */
    private RepeatIndex indexRepeats(List<Segment> segments)
    {
        int size = segments.size();
        int[] classOf = new int[size];
        int[] representativeOf = new int[size];
        int[] nextClassOfSameText = new int[size];
        int[] textEnd = new int[size + 1];
        boolean[] canStartGroup = new boolean[size];
        Map<String, Integer> classByText = new HashMap<>();
        int classes = 0;

        for (int index = 0; index < size; index++)
        {
            Segment segment = segments.get(index);
            textEnd[index + 1] = textEnd[index] + segment.text().length();
            // a repeat group opening with a bond symbol would read as a branch off the group before it
            canStartGroup[index] = arrivingSymbol(segment.node()) == NO_SYMBOL;

            if (!segment.matchable())
            {
                representativeOf[classes] = index;
                classOf[index] = classes++;
                continue;
            }

            // interchangeable segments write the same text, so only the classes writing this text are worth comparing
            Integer written = classByText.get(segment.text());
            int first = written == null ? -1 : written;

            int match = -1;
            for (int candidate = first; candidate >= 0; candidate = nextClassOfSameText[candidate])
            {
                if (sameSegment(segments.get(representativeOf[candidate]), segment))
                {
                    match = candidate;
                    break;
                }
            }

            if (match < 0)
            {
                match = classes++;
                representativeOf[match] = index;
                nextClassOfSameText[match] = first;
                classByText.put(segment.text(), match);
            }
            classOf[index] = match;
        }
        return new RepeatIndex(classOf, nextOfEachClass(classOf, classes), textEnd, canStartGroup);
    }

    /**
     * @param classOf each segment's class
     * @param classes how many classes are in use
     * @return the next segment of the same class as each segment, or {@code -1} where there is none
     */
    private static int[] nextOfEachClass(int[] classOf, int classes)
    {
        int[] next = new int[classOf.length];
        int[] lastSeen = new int[classes];
        Arrays.fill(lastSeen, -1);

        for (int index = classOf.length - 1; index >= 0; index--)
        {
            next[index] = lastSeen[classOf[index]];
            lastSeen[classOf[index]] = index;
        }
        return next;
    }

    /**
     * Looks for a repeating motif starting at one segment, preferring the shortest motif that pays
     * for the brackets it costs.
     *
     * @param repeats the chain indexed for repeat detection
     * @param from the segment the motif would start at
     * @return the motif's length in segments, or zero if nothing worth collapsing starts here
     */
    private int repeatPeriod(RepeatIndex repeats, int from)
    {
        if (!repeats.canStartGroup(from))
        {
            return 0;
        }

        // the motif's second turn opens with a segment interchangeable with this one, so no other period can repeat
        for (int next = repeats.nextOfSameClass(from); next >= 0; next = repeats.nextOfSameClass(next))
        {
            int period = next - from;
            // periods only grow from here, so once the chain is too short for two turns nothing later fits either
            if (from + 2 * period > repeats.size())
            {
                return 0;
            }

            int count = repeats.repeatCount(from, period);
            if (count < minimumRepeat)
            {
                continue;
            }

            int motif = repeats.motifLength(from, period);
            // collapsing has to earn its brackets, which is why CH2CH2 is left alone
            if (motif + 2 + String.valueOf(count).length() < motif * count)
            {
                return period;
            }
        }
        return 0;
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
            && first.atom().isotope().equals(second.atom().isotope())
            && molecule.totalHydrogenCount(first.atom()) == molecule.totalHydrogenCount(second.atom())
            && first.atom().hydrogenIsotopes().equals(second.atom().hydrogenIsotopes())
            && arrivingSymbol(first) == arrivingSymbol(second);
    }

    /**
     * @param first the first subtree's root
     * @param second the second subtree's root
     * @return {@code true} if both subtrees write the same text, so one can stand for the other
     */
    private boolean interchangeable(Node first, Node second)
    {
        if (!first.matchable() || !second.matchable()
            || !rendersAlike(first, second) || first.children().size() != second.children().size())
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
     * @return the symbol of the bond {@code node} was reached by, or {@link #NO_SYMBOL} if there is none to write
     */
    private char arrivingSymbol(Node node)
    {
        return node.arrivingBond() == null ? NO_SYMBOL : bondSymbol(node.arrivingBond());
    }

    /**
     * Appends a bond's symbol, if it has one to write.
     *
     * @param text the text being built, appended to in place
     * @param symbol the symbol to write, or {@link #NO_SYMBOL} to write nothing
     */
    private static void appendBondSymbol(StringBuilder text, char symbol)
    {
        if (symbol != NO_SYMBOL)
        {
            text.append(symbol);
        }
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
     * @param matchable whether this subtree can stand for an identical one, which a ring marker or an aromatic atom anywhere within it rules out
     */
    private record Node(Atom atom, Bond arrivingBond, List<Bond> ringBonds, List<Node> children, boolean matchable)
    {
    }

    /**
     * A chain's segments, containing what repeat detection needs to know about each position.
     *
     * @param classOf each segment's class, two segments sharing a class exactly when they are interchangeable
     * @param nextOfSameClass the next segment of the same class as each segment, or {@code -1} where there is none
     * @param textEnd the combined length of the segments before each position, one entry longer than the chain
     * @param canStartGroup whether a repeat group may open at each segment
     */
    private record RepeatIndex(int[] classOf, int[] nextOfSameClass, int[] textEnd, boolean[] canStartGroup)
    {
        /**
         * @return how many segments the chain holds
         */
        int size()
        {
            return classOf.length;
        }

        /**
         * @param segment the segment to test
         * @return {@code true} if a repeat group may open at {@code segment}
         */
        boolean canStartGroup(int segment)
        {
            return canStartGroup[segment];
        }

        /**
         * @param segment the segment to look up
         * @return the next segment interchangeable with {@code segment}, or {@code -1} if there is none
         */
        int nextOfSameClass(int segment)
        {
            return nextOfSameClass[segment];
        }

        /**
         * @param from the segment the motif starts at
         * @param period the motif's length in segments
         * @return the combined length of the motif's text
         */
        int motifLength(int from, int period)
        {
            return textEnd[from + period] - textEnd[from];
        }

        /**
         * @param from the segment the motif starts at
         * @param period the motif's length in segments
         * @return how many times the motif repeats from {@code from}, at least one
         */
        int repeatCount(int from, int period)
        {
            int count = 1;
            while (from + (count + 1) * period <= size())
            {
                int next = from + count * period;
                boolean matches = true;
                for (int i = 0; i < period && matches; i++)
                {
                    matches = classOf[from + i] == classOf[next + i];
                }

                if (!matches)
                {
                    break;
                }
                count++;
            }
            return count;
        }
    }

    /**
     * One atom of a depth-first traversal, holding the bonds left to follow from it and the subtrees traced below it so far.
     */
    private static final class Visit
    {
        private final Atom atom;
        private final Bond arrivingBond;
        private final List<Bond> bonds;
        private List<Node> children;
        private int next;

        /**
         * @param atom the atom being traversed
         * @param arrivingBond the bond traversed to reach {@code atom}, or {@code null} for a root atom
         * @param bonds {@code atom}'s incident bonds, in the order they are to be followed
         */
        private Visit(Atom atom, Bond arrivingBond, List<Bond> bonds)
        {
            this.atom = atom;
            this.arrivingBond = arrivingBond;
            this.bonds = bonds;
        }

        /**
         * @return the atom being traversed
         */
        private Atom atom()
        {
            return atom;
        }

        /**
         * @return the bond traversed to reach this atom, or {@code null} for a root atom
         */
        private Bond arrivingBond()
        {
            return arrivingBond;
        }

        /**
         * @return the subtrees traced below this atom, in the order they were traced
         */
        private List<Node> children()
        {
            return children == null ? List.of() : children;
        }

        /**
         * @param child a subtree traced below this atom
         */
        private void addChild(Node child)
        {
            if (children == null)
            {
                children = new ArrayList<>();
            }
            children.add(child);
        }

        /**
         * @return the next bond to follow from this atom, or {@code null} once they are all followed
         */
        private Bond nextBond()
        {
            return next < bonds.size() ? bonds.get(next++) : null;
        }
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
     * @param matchable whether this position can stand for an interchangeable one, which a ring marker or an aromatic atom at it or in any of its branches rules out
     * @param text the position's text
     */
    private record Segment(Node node, List<BranchRun> branches, boolean leading, boolean matchable, String text)
    {
    }

    /**
     * Writes a single atom as a group: its element symbol, its hydrogen count, and its charge.
     *
     * @param atom the atom to write
     * @param leading whether nothing is written before {@code atom} within its fragment, which lets its hydrogens come first
     * @return {@code atom}'s group, e.g. {@code CH3}, {@code NH4+}, or a leading {@code H2O}
     */
    private String groupOf(Atom atom, boolean leading)
    {
        StringBuilder group = new StringBuilder();
        String symbol = Nuclide.render(Nuclide.symbolOf(atom.element(), atom.isotope().orElse(0)), unicode, isotopeStyle);

        if (leading && hydrogensComeFirst(atom.element()))
        {
            appendHydrogens(group, atom);
            group.append(symbol);
        }
        else
        {
            group.append(symbol);
            appendHydrogens(group, atom);
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
        if (elementOrder == ElementOrder.HILL)
        {
            // Hill promotes carbon above hydrogen and leaves every other element to the alphabet
            return !element.equals("C") && "H".compareTo(element) < 0;
        }
        return Elements.iupacOrderOf("H") < Elements.iupacOrderOf(element);
    }

    /**
     * Appends a group's hydrogens, if it has any, as {@code H} and a number above one, each
     * isotopically labelled hydrogen written as its own nuclide, e.g. {@code CH2D}.
     *
     * @param group the group being built, appended to in place
     * @param atom the atom whose hydrogens are to be written
     */
    private void appendHydrogens(StringBuilder group, Atom atom)
    {
        // ascending by mass number, so unlabelled hydrogens are written before any heavier ones
        molecule.hydrogenIsotopeCounts(atom).forEach((massNumber, count) ->
        {
            group.append(Nuclide.render(Nuclide.symbolOf("H", massNumber), unicode, isotopeStyle));
            if (count > 1)
            {
                group.append(unicode ? Unicode.subscript(count) : String.valueOf(count));
            }
        });
    }

    /**
     * @param bond the bond to be written
     * @return the bond's symbol, or {@link #NO_SYMBOL} if we don't need to render it
     */
    private char bondSymbol(Bond bond)
    {
        // a single bond is the default between two groups, and an aromatic one has no order to mark
        if (!renderBondSymbols || bond.order() == BondOrder.SINGLE || bond.order() == BondOrder.AROMATIC)
        {
            return NO_SYMBOL;
        }
        return bond.order().symbol();
    }
}
