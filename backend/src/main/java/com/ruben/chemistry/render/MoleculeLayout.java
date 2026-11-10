package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.StereoDeclaration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Lays a {@link Molecule} out into a {@link Depiction}.
 */
public final class MoleculeLayout
{
    /** How far apart two disconnected parts of a molecule are drawn, in bond lengths. */
    private static final double FRAGMENT_GAP = 1.5;

    /** The direction the first neighbour of a straight-chain layout's starting atom sits in. */
    private static final double STRAIGHT_START = 0;

    /** How far a chain turns at an atom it passes straight through. */
    private static final double STRAIGHT_TURN = Math.PI;

    /** The direction the first neighbour of a zig-zag layout's starting atom sits in */
    private static final double ZIGZAG_START = -Math.PI / 6;

    /** How far a zig-zag chain turns at each atom. */
    private static final double ZIGZAG_TURN = 2 * Math.PI / 3;

    /** The most neighbours an atom can have and still be drawn trigonal about a double bond. */
    private static final int TRIGONAL_NEIGHBOURS = 3;

    /** How close together, in bond lengths, two atoms have to be drawn to count as colliding. */
    private static final double COLLISION_DISTANCE = 0.5;

    /** How many directions a fan is tried in when spinning it clear of its surroundings. */
    private static final int COLLISION_ROTATIONS = 24;

    /** How far off a bond a substituent has to be before it counts as being to one side of it. */
    private static final double COLLINEAR = 1e-9;

    /** Where a ring-atom's gap is probed to tell whether it faces into a ring, in bond lengths from the atom. */
    private static final double RING_PROBE = 0.1;

    /** Where a ligand sits when the options leave it off the page altogether. */
    private static final int UNDRAWN = -1;

    private final Molecule molecule;
    private final MoleculeSnapshot snapshot;
    private final DepictionOptions options;
    private final List<Atom> atoms;
    private final Map<Atom, Integer> indices;

    /** Each of the molecule's own bonds' index within {@link Molecule#bonds()}. */
    private final Map<Bond, Integer> bondIndices = new IdentityHashMap<>();

    /** Where each of the molecule's own atoms ended up, by atom index. */
    private final Point[] positions;

    /** Each of the molecule's own atoms' placement index, by atom index. */
    private final int[] placementOfAtom;

    /** The placements of the hydrogens made up for each atom, by atom index. */
    private final List<List<Integer>> hydrogensOfAtom;

    /** Which of the molecule's own atoms have been placed, by atom index. */
    private final boolean[] placed;

    private final List<AtomPlacement> placements = new ArrayList<>();
    private final List<BondPlacement> bonds = new ArrayList<>();

    /** The rings drawn so far. */
    private final List<RingPlacement> ringPlacements = new ArrayList<>();

    /** Every ring of the molecule, each as its atoms in the order they are joined around it, or {@code null} until needed. */
    private List<List<Atom>> ringOrders;

    /** Where each bond sits in {@link #bonds}, by the placement pair it joins. */
    private final Map<Long, Integer> bondsByPair = new HashMap<>();

    /** Every declared double bond configuration, by the atom pair it is declared across. */
    private final Map<Long, StereoDeclaration.DoubleBond> configurations = new HashMap<>();

    /** The half-open range of placements belonging to each disconnected part of the molecule, then that part's fragment index. */
    private final List<int[]> parts = new ArrayList<>();

    /** Where the part being laid out starts in {@link #placements}. */
    private int partStart;

    /**
     * @param molecule the molecule to lay out
     * @param options the choices to lay it out by
     */
    private MoleculeLayout(Molecule molecule, DepictionOptions options)
    {
        this.molecule = molecule;
        this.snapshot = MoleculeSnapshot.of(molecule);
        this.options = options;
        this.atoms = molecule.atoms();
        this.indices = new IdentityHashMap<>();
        this.positions = new Point[atoms.size()];
        this.placementOfAtom = new int[atoms.size()];
        this.hydrogensOfAtom = new ArrayList<>(atoms.size());
        this.placed = new boolean[atoms.size()];
        for (int index = 0; index < atoms.size(); index++)
        {
            indices.put(atoms.get(index), index);
            hydrogensOfAtom.add(new ArrayList<>());
        }
        List<Bond> moleculeBonds = molecule.bonds();
        for (int index = 0; index < moleculeBonds.size(); index++)
        {
            bondIndices.put(moleculeBonds.get(index), index);
        }
    }

    /**
     * Lays a molecule out as an ordinary structural diagram.
     *
     * @param molecule the molecule to lay out
     * @return the layout
     * @throws NullPointerException if {@code molecule} is {@code null}
     */
    public static Layout layout(Molecule molecule)
    {
        return layout(molecule, DepictionOptions.defaults());
    }

    /**
     * Lays a molecule out.
     *
     * @param molecule the molecule to lay out
     * @param options the choices to lay it out by
     * @return the layout
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code molecule} declares a parity at an atom whose hydrogen the options leave undrawn
     */
    public static Layout layout(Molecule molecule, DepictionOptions options)
    {
        MoleculeLayout layout = new MoleculeLayout(molecule, options);
        return new Layout(layout.build(), layout.snapshot);
    }

    /**
     * @return the laid-out molecule
     */
    private Depiction build()
    {
        if (atoms.isEmpty())
        {
            return Depiction.empty();
        }
        readConfigurations();

        for (Atom atom : atoms)
        {
            if (!placed[indexOf(atom)])
            {
                layOutPart(atom);
            }
        }

        applyParities();
        markVertices();
        for (int[] part : parts)
        {
            for (int placement = part[0]; placement < part[1]; placement++)
            {
                placements.set(placement, placements.get(placement).inFragment(part[2]));
            }
        }
        spreadParts();
        return new Depiction(placements, bonds, ringPlacements);
    }

    /**
     * Leaves every carbon a bare corner of a skeletal drawing, barring lone or non-standard carbons.
     */
    private void markVertices()
    {
        if (options.style() != DepictionStyle.SKELETAL)
        {
            return;
        }

        int[] degrees = degreesOfPlacements();
        for (int index = 0; index < placements.size(); index++)
        {
            AtomPlacement placement = placements.get(index);
            boolean plainCarbon = placement.element().equals("C")
                && placement.charge() == 0
                && placement.isotope().isEmpty();
            placements.set(index, placement.asVertex(plainCarbon && degrees[index] > 0));
        }
    }

    /**
     * Collects the molecule's declared double bond configurations.
     */
    private void readConfigurations()
    {
        for (StereoDeclaration declaration : molecule.stereochemistry().declarations())
        {
            if (declaration instanceof StereoDeclaration.DoubleBond configuration)
            {
                long pair = StereoDeclaration.DoubleBond.pairKey(configuration.firstAtom(), configuration.secondAtom());
                configurations.put(pair, configuration);
            }
        }
    }

    /**
     * Walks one connected part of the molecule, placing every atom it reaches.
     *
     * @param root the atom to start from, which is placed at the origin
     */
    private void layOutPart(Atom root)
    {
        int start = placements.size();
        partStart = start;
        Deque<Walk> pending = new ArrayDeque<>();
        if (isRingAtom(root))
        {
            placeRingSystem(root, Point.ORIGIN, Double.NaN, pending);
        }
        else
        {
            place(root, Point.ORIGIN);
            pending.push(new Walk(root, null, null, Double.NaN, 1));
        }
        while (!pending.isEmpty())
        {
            expand(pending.pop(), pending);
        }

        parts.add(new int[] {start, placements.size(), snapshot.getFragmentIndexOf(indexOf(root))});
    }

    /**
     * @param atom one of the molecule's own atoms
     * @return whether it is on a ring
     */
    private boolean isRingAtom(Atom atom)
    {
        return molecule.ringCount(atom) > 0;
    }

    /**
     * Places a whole ring system.
     *
     * @param entry the system's atom the walk has reached
     * @param position where {@code entry} goes
     * @param backAngle the direction the atom the walk came from lies in, or {@link Double#NaN} if there was none
     * @param pending the walk still to be done, which this adds every atom of the system to
     * @return {@code entry}'s placement index
     */
    private int placeRingSystem(Atom entry, Point position, double backAngle, Deque<Walk> pending)
    {
        // the system is every atom reachable from the entry along ring bonds
        List<Atom> system = new ArrayList<>();
        Map<Atom, Boolean> seen = new IdentityHashMap<>();
        Deque<Atom> queue = new ArrayDeque<>();
        queue.add(entry);
        seen.put(entry, true);
        while (!queue.isEmpty())
        {
            Atom atom = queue.poll();
            system.add(atom);
            for (Bond bond : molecule.bondsOf(atom))
            {
                Atom other = bond.getOther(atom);
                if (molecule.ringBonds().contains(bond) && seen.put(other, true) == null)
                {
                    queue.add(other);
                }
            }
        }

        List<List<Atom>> systemRings = new ArrayList<>();
        for (List<Atom> ring : ringsOfMolecule())
        {
            if (seen.containsKey(ring.getFirst()))
            {
                systemRings.add(ring);
            }
        }

        Map<Atom, Point> local = RingSystemLayout.layOut(systemRings);
        Point origin = local.get(entry);

        // turn the system so that the entry atom's way out points back at the atom the walk came from
        double turn = 0;
        if (!Double.isNaN(backAngle))
        {
            Point inwards = Point.ORIGIN;
            for (Bond bond : molecule.bondsOf(entry))
            {
                Point neighbour = local.get(bond.getOther(entry));
                if (neighbour != null && molecule.ringBonds().contains(bond))
                {
                    Point towards = neighbour.minus(origin);
                    inwards = inwards.plus(towards.scale(1 / towards.length()));
                }
            }
            turn = backAngle - (inwards.length() > 1e-9 ? inwards.scale(-1).angle() : 0);
        }

        for (Atom atom : system)
        {
            Point offset = local.get(atom).minus(origin);
            Point at = position.plus(Point.polar(offset.angle() + turn, offset.length()));
            place(atom, at);
        }

        for (Bond bond : molecule.bonds())
        {
            if (molecule.ringBonds().contains(bond) && seen.containsKey(bond.first()))
            {
                addBond(placementOfAtom[indexOf(bond.first())], placementOfAtom[indexOf(bond.second())], bond);
            }
        }

        for (List<Atom> ring : systemRings)
        {
            List<Integer> members = new ArrayList<>();
            boolean aromatic = true;
            for (int atom = 0; atom < ring.size(); atom++)
            {
                members.add(placementOfAtom[indexOf(ring.get(atom))]);
                Bond bond = molecule.bondBetween(ring.get(atom), ring.get((atom + 1) % ring.size())).orElseThrow();
                aromatic &= bond.order() == BondOrder.AROMATIC;
            }
            ringPlacements.add(new RingPlacement(members, aromatic && options.style() == DepictionStyle.SKELETAL));
        }

        for (Atom atom : system)
        {
            pending.push(new Walk(atom, null, null, Double.NaN, 1));
        }
        return placementOfAtom[indexOf(entry)];
    }

    /**
     * @return every ring of the molecule, each as its atoms in the order they are joined around it
     */
    private List<List<Atom>> ringsOfMolecule()
    {
        if (ringOrders == null)
        {
            ringOrders = new ArrayList<>();
            for (Molecule ring : molecule.rings())
            {
                List<Atom> order = new ArrayList<>();
                Atom first = ring.atoms().getFirst();
                Atom previous = null;
                Atom current = first;
                do
                {
                    order.add(current);
                    Atom next = null;
                    for (Bond bond : ring.bondsOf(current))
                    {
                        Atom other = bond.getOther(current);
                        if (other != previous)
                        {
                            next = other;
                            break;
                        }
                    }
                    previous = current;
                    current = next;
                }
                while (current != first && current != null);
                ringOrders.add(order);
            }
        }
        return ringOrders;
    }

    /**
     * Places everything hanging off one already-placed atom. Queues its neighbours to be expanded in turn.
     *
     * @param walk the atom to expand, and how the walk arrived at it
     * @param pending the walk still to be done, which this adds to
     */
    private void expand(Walk walk, Deque<Walk> pending)
    {
        Atom atom = walk.atom();
        int index = indexOf(atom);
        Point centre = positions[index];

        List<Bond> children = childBondsOf(atom, walk.parent());
        int hydrogens = options.effectiveHydrogens().isSeparate() ? molecule.totalHydrogenCount(atom) : 0;
        int slots = children.size() + hydrogens;
        if (slots == 0)
        {
            return;
        }

        double[] directions;
        boolean ring = isRingAtom(atom);
        if (ring)
        {
            directions = ringDirections(atom, centre, slots);
        }
        else
        {
            directions = directionsFor(walk, slots, children);
            directions = honourConfiguration(walk, directions, children, hydrogens);
            directions = avoidCollisions(walk, centre, directions, children);
        }

        for (int child = 0; child < children.size(); child++)
        {
            Bond bond = children.get(child);
            Atom neighbour = bond.getOther(atom);
            Point position = centre.plus(Point.polar(directions[child], Depiction.BOND_LENGTH));
            if (isRingAtom(neighbour))
            {
                int placement = placeRingSystem(neighbour, position, directions[child] + Math.PI, pending);
                addBond(placementOfAtom[index], placement, bond);
                continue;
            }
            int placement = place(neighbour, position);
            addBond(placementOfAtom[index], placement, bond);
            pending.push(new Walk(neighbour, atom, bond, directions[child] + Math.PI, -walk.zigzagSign()));
        }

        placeHydrogens(atom, index, centre, directions, children.size());
    }

    /**
     * Shares out the directions the substituents of a ring atom sit in, in whatever room the ring's own bonds leave.
     *
     * @param atom the ring atom
     * @param centre where it sits
     * @param slots how many neighbours there are to place
     * @return one direction per neighbour
     */
    private double[] ringDirections(Atom atom, Point centre, int slots)
    {
        List<Double> fixed = new ArrayList<>();
        for (Bond bond : molecule.bondsOf(atom))
        {
            Atom other = bond.getOther(atom);
            if (placed[indexOf(other)])
            {
                fixed.add(positions[indexOf(other)].minus(centre).angle());
            }
        }
        if (fixed.isEmpty())
        {
            return AngleFan.evenlyAround(0, slots);
        }

        // only the gaps between bonds that face away from the rings are open to substituents
        double[] edges = fixed.stream().mapToDouble(AngleFan::normalise).sorted().toArray();
        List<double[]> open = new ArrayList<>();
        for (int edge = 0; edge < edges.length; edge++)
        {
            double end = edge + 1 < edges.length ? edges[edge + 1] : edges[0] + AngleFan.TURN;
            double span = end - edges[edge];
            Point probe = centre.plus(Point.polar(edges[edge] + span / 2, RING_PROBE));
            if (!insideRingOf(atom, probe))
            {
                open.add(new double[] {edges[edge], span});
            }
        }
        if (open.isEmpty())
        {
            return AngleFan.fillGaps(fixed, slots).stream().mapToDouble(Double::doubleValue).toArray();
        }

        // hand each substituent to whichever open gap is left roomiest by taking it
        int[] taken = new int[open.size()];
        int[] chosen = new int[slots];
        for (int slot = 0; slot < slots; slot++)
        {
            int roomiest = 0;
            for (int gap = 1; gap < open.size(); gap++)
            {
                if (open.get(gap)[1] / (taken[gap] + 2) > open.get(roomiest)[1] / (taken[roomiest] + 2) + COLLINEAR)
                {
                    roomiest = gap;
                }
            }
            taken[roomiest]++;
            chosen[slot] = roomiest;
        }

        int[] used = new int[open.size()];
        double[] directions = new double[slots];
        for (int slot = 0; slot < slots; slot++)
        {
            int gap = chosen[slot];
            used[gap]++;
            directions[slot] = open.get(gap)[0] + open.get(gap)[1] * used[gap] / (taken[gap] + 1.0);
        }
        return directions;
    }

    /**
     * @param atom a ring atom
     * @param point a point near it
     * @return whether the point lies inside any ring the atom is in
     */
    private boolean insideRingOf(Atom atom, Point point)
    {
        int placement = placementOfAtom[indexOf(atom)];
        for (RingPlacement ring : ringPlacements)
        {
            if (!ring.atoms().contains(placement))
            {
                continue;
            }
            boolean inside = false;
            List<Integer> members = ring.atoms();
            for (int member = 0, previous = members.size() - 1; member < members.size(); previous = member++)
            {
                Point a = placements.get(members.get(member)).position();
                Point b = placements.get(members.get(previous)).position();
                if ((a.y() > point.y()) != (b.y() > point.y())
                    && point.x() < (b.x() - a.x()) * (point.y() - a.y()) / (b.y() - a.y()) + a.x())
                {
                    inside = !inside;
                }
            }
            if (inside)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @param atom the atom being expanded
     * @param parent the atom the walk arrived from, or {@code null} if it started here
     * @return the bonds leading to atoms still to be placed
     */
    private List<Bond> childBondsOf(Atom atom, Atom parent)
    {
        List<Bond> children = new ArrayList<>();
        for (Bond bond : molecule.bondsOf(atom))
        {
            Atom other = bond.getOther(atom);
            if (other != parent && !placed[indexOf(other)])
            {
                children.add(bond);
            }
        }
        return children;
    }

    /**
     * Shares out the directions an atom's neighbours sit in.
     *
     * @param walk the atom being expanded, and how the walk arrived at it
     * @param slots how many neighbours there are to place
     * @param children the bonds to the neighbours still to be placed, the first of which the chain through this atom carries on along
     * @return one direction per neighbour
     */
    private double[] directionsFor(Walk walk, int slots, List<Bond> children)
    {
        if (walk.parent() == null)
        {
            double start = options.chainGeometry() == ChainGeometry.ZIGZAG ? ZIGZAG_START : STRAIGHT_START;
            return AngleFan.evenlyAround(start, slots);
        }

        double back = walk.backAngle();
        List<Double> fixed = new ArrayList<>();
        fixed.add(back);

        double[] directions = new double[slots];
        int next = 0;
        if (!children.isEmpty())
        {
            double onwards = back + continuationTurn(walk, children.getFirst().order(), slots + 1);
            fixed.add(onwards);
            directions[next++] = onwards;
        }

        for (double direction : AngleFan.fillGaps(fixed, slots - next))
        {
            directions[next++] = direction;
        }
        return directions;
    }

    /**
     * @param walk the atom being expanded, and how the walk arrived at it
     * @param onwards the order of the bond the chain carries on along
     * @param neighbours how many atoms are bonded to this one, the one walked in from included
     * @return the angle between the bond the walk arrived on and the bond it carries on along
     */
    private double continuationTurn(Walk walk, BondOrder onwards, int neighbours)
    {
        BondOrder arrived = walk.parentBond().order();

        // an atom a triple bond ends at is linear, and so is one two double bonds pass through
        if (arrived == BondOrder.TRIPLE || onwards == BondOrder.TRIPLE || (isTrigonal(arrived) && isTrigonal(onwards)))
        {
            return STRAIGHT_TURN;
        }

        // an atom with three neighbours is trigonal, as is one a single double bond ends at, and
        // both are drawn with their bonds fanned evenly about them; an atom with more neighbours
        // than that is better off carrying the chain straight on and spreading the rest around it
        if (neighbours == TRIGONAL_NEIGHBOURS
            || (neighbours < TRIGONAL_NEIGHBOURS && (isTrigonal(arrived) || isTrigonal(onwards))))
        {
            return walk.zigzagSign() * ZIGZAG_TURN;
        }

        if (options.chainGeometry() == ChainGeometry.ZIGZAG)
        {
            return walk.zigzagSign() * ZIGZAG_TURN;
        }
        return STRAIGHT_TURN;
    }

    /**
     * @param order a bond order
     * @return whether a bond of that order leaves the atoms it joins trigonal
     */
    private static boolean isTrigonal(BondOrder order)
    {
        return order == BondOrder.DOUBLE || order == BondOrder.AROMATIC;
    }

    /**
     * Spins an atom's free neighbours about it, where they would otherwise land on top of
     * something already drawn.
     * <p>
     * Only the neighbours shared out into whatever room is left over move; the bond the walk
     * arrived along, and the one the chain carries on down, stay where they were put. An atom
     * whose neighbours have been placed to honour a declared configuration is left alone
     * altogether, so that spinning them cannot undo it.
     *
     * @param walk the atom being expanded, and how the walk arrived at it
     * @param centre where the atom sits
     * @param directions the directions its neighbours would sit in
     * @param children the bonds to the neighbours still to be placed
     * @return the directions to use
     */
    private double[] avoidCollisions(Walk walk, Point centre, double[] directions, List<Bond> children)
    {
        int free = firstFreeSlot(walk, children);
        if (free >= directions.length || declaresConfiguration(walk))
        {
            return directions;
        }

        double[] radii = new double[directions.length];
        for (int slot = 0; slot < radii.length; slot++)
        {
            radii[slot] = slot < children.size() ? Depiction.BOND_LENGTH : Depiction.HYDROGEN_BOND_LENGTH;
        }

        List<Point> occupied = occupiedAround(centre, directions, radii, free);
        List<Point[]> drawn = segmentsAround(centre, directions, radii, free);
        double best = clearance(centre, directions, radii, free, occupied, drawn, 0);
        if (best >= COLLISION_DISTANCE)
        {
            return directions;
        }

        double spin = 0;
        for (int step = 1; step <= COLLISION_ROTATIONS / 2; step++)
        {
            for (int sign = 1; sign >= -1; sign -= 2)
            {
                double offset = sign * step * 2 * Math.PI / COLLISION_ROTATIONS;
                double gained = clearance(centre, directions, radii, free, occupied, drawn, offset);
                if (gained > best + COLLINEAR)
                {
                    best = gained;
                    spin = offset;
                }
            }
        }

        if (spin == 0)
        {
            return directions;
        }

        double[] spun = directions.clone();
        for (int slot = free; slot < spun.length; slot++)
        {
            spun[slot] = AngleFan.normalise(spun[slot] + spin);
        }
        return spun;
    }

    /**
     * @param walk the atom being expanded, and how the walk arrived at it
     * @param children the bonds to the neighbours still to be placed
     * @return the first slot whose direction was shared out rather than fixed
     */
    private static int firstFreeSlot(Walk walk, List<Bond> children)
    {
        return walk.parent() != null && !children.isEmpty() ? 1 : 0;
    }

    /**
     * @param walk the atom being expanded, and how the walk arrived at it
     * @return whether the bond the walk arrived along declares a configuration
     */
    private boolean declaresConfiguration(Walk walk)
    {
        Atom parent = walk.parent();
        return parent != null
            && configurations.containsKey(StereoDeclaration.DoubleBond.pairKey(indexOf(parent), indexOf(walk.atom())));
    }

    /**
     * @param centre where the atom being expanded sits
     * @param directions the directions its neighbours would sit in
     * @param radii how far along each of those directions its neighbours sit
     * @param free the first slot whose direction can still move
     * @return every atom of this part of the molecule that is already drawn, the neighbours whose
     *     directions are fixed included, but not the atom being expanded itself
     */
    private List<Point> occupiedAround(Point centre, double[] directions, double[] radii, int free)
    {
        List<Point> occupied = new ArrayList<>();
        for (int placement = partStart; placement < placements.size(); placement++)
        {
            Point position = placements.get(placement).position();
            if (position.distanceTo(centre) > COLLINEAR)
            {
                occupied.add(position);
            }
        }
        for (int slot = 0; slot < free; slot++)
        {
            occupied.add(centre.plus(Point.polar(directions[slot], radii[slot])));
        }
        return occupied;
    }

    /**
     * @param centre where the atom being expanded sits
     * @param directions the directions its neighbours would sit in
     * @param radii how far along each of those directions its neighbours sit
     * @param free the first slot whose direction can still move
     * @return every bond of this part of the molecule that is already drawn, the bonds to the
     *     neighbours whose directions are fixed included
     */
    private List<Point[]> segmentsAround(Point centre, double[] directions, double[] radii, int free)
    {
        List<Point[]> drawn = new ArrayList<>();
        for (BondPlacement bond : bonds)
        {
            if (bond.first() >= partStart)
            {
                drawn.add(new Point[] {placements.get(bond.first()).position(), placements.get(bond.second()).position()});
            }
        }
        for (int slot = 0; slot < free; slot++)
        {
            drawn.add(new Point[] {centre, centre.plus(Point.polar(directions[slot], radii[slot]))});
        }
        return drawn;
    }

    /**
     * Measures how much room a fan would have if it were spun round.
     * <p>
     * A neighbour needs clear space both from the atoms already drawn and from the bonds between
     * them, and the bond drawn out to it needs clear space from those same atoms, since a bond
     * running under a symbol reads no better than two symbols on top of one another.
     *
     * @param centre where the atom being expanded sits
     * @param directions the directions its neighbours would sit in
     * @param radii how far along each of those directions its neighbours sit
     * @param free the first slot whose direction can still move
     * @param occupied the atoms already drawn
     * @param drawn the bonds already drawn
     * @param offset how far to spin the free directions round first
     * @return how close the tightest of them would come to anything already drawn
     */
    private static double clearance(
        Point centre,
        double[] directions,
        double[] radii,
        int free,
        List<Point> occupied,
        List<Point[]> drawn,
        double offset)
    {
        double closest = Double.MAX_VALUE;
        for (int slot = free; slot < directions.length; slot++)
        {
            Point point = centre.plus(Point.polar(directions[slot] + offset, radii[slot]));
            for (Point taken : occupied)
            {
                closest = Math.min(closest, point.distanceTo(taken));
                closest = Math.min(closest, distanceToSegment(taken, centre, point));
            }
            for (Point[] bond : drawn)
            {
                closest = Math.min(closest, distanceToSegment(point, bond[0], bond[1]));
            }
        }
        return closest;
    }

    /**
     * @param point the point to measure from
     * @param from one end of the line
     * @param to the other end of the line
     * @return how far {@code point} lies from the nearest point of the line between them
     */
    private static double distanceToSegment(Point point, Point from, Point to)
    {
        Point along = to.minus(from);
        double length = along.x() * along.x() + along.y() * along.y();
        if (length <= COLLINEAR)
        {
            return point.distanceTo(from);
        }

        Point offset = point.minus(from);
        double fraction = (offset.x() * along.x() + offset.y() * along.y()) / length;
        double clamped = Math.max(0, Math.min(1, fraction));
        return point.distanceTo(from.plus(along.scale(clamped)));
    }

    /**
     * Mirrors an atom's neighbours across the bond the walk arrived along, where that bond
     * declares a configuration its substituents would otherwise contradict.
     * <p>
     * Mirroring leaves the bond itself, and the direction a chain carries on in, where they are,
     * so it only swaps over which side of the double bond everything else comes out on.
     *
     * @param walk the atom being expanded, and how the walk arrived at it
     * @param directions the directions its neighbours would sit in
     * @param children the bonds to the neighbours still to be placed
     * @param hydrogens how many hydrogens are being drawn for this atom
     * @return the directions to use
     */
    private double[] honourConfiguration(Walk walk, double[] directions, List<Bond> children, int hydrogens)
    {
        Atom atom = walk.atom();
        Atom parent = walk.parent();
        if (parent == null)
        {
            return directions;
        }

        int atomIndex = indexOf(atom);
        int parentIndex = indexOf(parent);
        StereoDeclaration.DoubleBond declaration = configurations.get(StereoDeclaration.DoubleBond.pairKey(parentIndex, atomIndex));
        if (declaration == null)
        {
            return directions;
        }

        Point axis = positions[atomIndex].minus(positions[parentIndex]);
        Optional<Reference> near = referenceBeside(parent, atom, axis);
        Optional<Reference> far = prospectiveReferenceBeside(atom, children, hydrogens, directions, axis);
        // with nothing to either side of one of its ends, the bond shows no configuration at all
        if (near.isEmpty() || far.isEmpty())
        {
            return directions;
        }

        boolean parentIsFirst = declaration.firstAtom() == parentIndex;
        DoubleBondStereo wanted = parentIsFirst
            ? declaration.configurationAgainst(near.get().atom(), far.get().atom())
            : declaration.configurationAgainst(far.get().atom(), near.get().atom());

        boolean sameSide = near.get().side() == far.get().side();
        if (sameSide == (wanted == DoubleBondStereo.SAME_SIDE))
        {
            return directions;
        }

        double mirror = axis.angle();
        double[] mirrored = new double[directions.length];
        for (int slot = 0; slot < directions.length; slot++)
        {
            mirrored[slot] = AngleFan.mirror(directions[slot], mirror);
        }
        return mirrored;
    }

    /**
     * Picks a substituent of an already-placed end of a double bond to measure its configuration
     * from, skipping any that lies along the bond and so sits to neither side of it.
     *
     * @param atom the end of the double bond to measure at
     * @param across the other end of the double bond
     * @param axis the direction of the double bond
     * @return the chosen substituent, or empty if nothing sits to either side
     */
    private Optional<Reference> referenceBeside(Atom atom, Atom across, Point axis)
    {
        int index = indexOf(atom);
        Point centre = positions[index];
        for (Bond bond : molecule.bondsOf(atom))
        {
            Atom other = bond.getOther(atom);
            if (other == across)
            {
                continue;
            }
            int side = sideOf(axis, positions[indexOf(other)].minus(centre));
            if (side != 0)
            {
                return Optional.of(new Reference(indexOf(other), side));
            }
        }

        // a hydrogen can only be named as the substituent measured from where the atom has just
        // the one, since a declaration says no more than that it is "the implicit hydrogen"
        List<Integer> hydrogens = hydrogensOfAtom.get(index);
        if (hydrogens.size() == 1)
        {
            int side = sideOf(axis, placements.get(hydrogens.getFirst()).position().minus(centre));
            if (side != 0)
            {
                return Optional.of(new Reference(StereoDeclaration.IMPLICIT_HYDROGEN, side));
            }
        }
        return Optional.empty();
    }

    /**
     * Picks a substituent of the end of a double bond about to be expanded, from the directions
     * its neighbours are going to sit in.
     *
     * @param atom the end of the double bond to measure at
     * @param children the bonds to the neighbours still to be placed
     * @param hydrogens how many hydrogens are being drawn for this atom
     * @param directions the directions its neighbours would sit in
     * @param axis the direction of the double bond
     * @return the chosen substituent, or empty if nothing would sit to either side
     */
    private Optional<Reference> prospectiveReferenceBeside(Atom atom, List<Bond> children, int hydrogens, double[] directions, Point axis)
    {
        for (int child = 0; child < children.size(); child++)
        {
            int side = sideOf(axis, Point.polar(directions[child], Depiction.BOND_LENGTH));
            if (side != 0)
            {
                return Optional.of(new Reference(indexOf(children.get(child).getOther(atom)), side));
            }
        }
        if (hydrogens == 1)
        {
            int side = sideOf(axis, Point.polar(directions[children.size()], Depiction.BOND_LENGTH));
            if (side != 0)
            {
                return Optional.of(new Reference(StereoDeclaration.IMPLICIT_HYDROGEN, side));
            }
        }
        return Optional.empty();
    }

    /**
     * @param axis the direction of the bond to measure against
     * @param offset where the substituent sits, relative to its own end of the bond
     * @return {@code 1} or {@code -1} for the side the substituent is on, or {@code 0} if it lies
     *     along the bond and so is on neither
     */
    private static int sideOf(Point axis, Point offset)
    {
        double side = axis.cross(offset);
        if (Math.abs(side) <= COLLINEAR)
        {
            return 0;
        }
        return side > 0 ? 1 : -1;
    }

    /**
     * Draws in the hydrogens an atom's own label would otherwise have carried, each labelled with
     * its own nuclide.
     *
     * @param atom the atom they hang off
     * @param index {@code atom}'s index
     * @param centre where {@code atom} sits
     * @param directions the directions {@code atom}'s neighbours sit in
     * @param from the first direction not already taken by a bonded neighbour
     */
    private void placeHydrogens(Atom atom, int index, Point centre, double[] directions, int from)
    {
        if (!options.effectiveHydrogens().isSeparate())
        {
            return;
        }

        int slot = from;
        for (Map.Entry<Integer, Integer> nuclide : molecule.hydrogenIsotopeCounts(atom).entrySet())
        {
            int massNumber = nuclide.getKey();
            // protium is what an unlabelled hydrogen already means, so labelling it says nothing
            OptionalInt isotope = massNumber > 1 ? OptionalInt.of(massNumber) : OptionalInt.empty();
            for (int count = 0; count < nuclide.getValue(); count++)
            {
                Point position = centre.plus(Point.polar(directions[slot++], Depiction.HYDROGEN_BOND_LENGTH));
                int placement = addPlacement("H", isotope, 0, 0, false, OptionalInt.empty(), position);
                hydrogensOfAtom.get(index).add(placement);
                addBond(placementOfAtom[index], placement, BondOrder.SINGLE);
            }
        }
    }

    /**
     * Gives every stereocentre a bond leaving the plane of the page, picked so that the
     * arrangement drawn reads as the parity the molecule declares.
     */
    private void applyParities()
    {
        int[] degrees = degreesOfPlacements();
        for (StereoDeclaration declaration : molecule.stereochemistry().declarations())
        {
            if (declaration instanceof StereoDeclaration.Tetrahedral parity)
            {
                applyParity(parity, degrees);
            }
        }
    }

    /**
     * @param declaration the parity to draw
     * @param degrees how many bonds each placement has
     * @throws IllegalArgumentException if a ligand is a hydrogen that cannot be made sense of
     * @throws IllegalStateException if no bond can be drawn out of the plane to show the parity
     */
    private void applyParity(StereoDeclaration.Tetrahedral declaration, int[] degrees)
    {
        int centreAtom = declaration.centre();
        int centre = placementOfAtom[centreAtom];
        Point position = placements.get(centre).position();

        int[] ligands = new int[Parity.LIGANDS];
        List<Point> ligandPositions = new ArrayList<>(Parity.LIGANDS);
        int undrawn = -1;
        for (int ligand = 0; ligand < Parity.LIGANDS; ligand++)
        {
            ligands[ligand] = placementOfLigand(centreAtom, declaration.ligands()[ligand]);
            if (ligands[ligand] == UNDRAWN)
            {
                // an undrawn hydrogen is read as sitting behind the stereocentre itself
                undrawn = ligand;
                ligandPositions.add(position);
            }
            else
            {
                ligandPositions.add(placements.get(ligands[ligand]).position());
            }
        }

        for (int candidate : wedgeOrder(ligands, degrees))
        {
            if (ligands[candidate] == UNDRAWN)
            {
                continue;
            }

            double[] depths = new double[Parity.LIGANDS];
            depths[candidate] = Parity.depthOf(BondStyle.WEDGE);
            if (undrawn >= 0)
            {
                // the hydrogen nobody drew lies on the far side of the page from the bond that leaves it
                depths[undrawn] = -depths[candidate];
            }

            Optional<Chirality> drawn = Parity.read(position, ligandPositions, depths);
            // a candidate whose arrangement reads flat says nothing either way, so try the next
            if (drawn.isEmpty())
            {
                continue;
            }

            styleBond(centre, ligands[candidate], drawn.get() == declaration.parity() ? BondStyle.WEDGE : BondStyle.HASH);
            return;
        }

        throw new IllegalStateException("No bond at atom " + centreAtom + " can be drawn out of the plane to show its declared parity");
    }

    /**
     * @param centreAtom the stereocentre's atom index
     * @param ligand one of its ligands, as a {@link Molecule#atoms()} index or
     *     {@link StereoDeclaration#IMPLICIT_HYDROGEN}
     * @return the placement of that ligand, or {@link #UNDRAWN} where it is a hydrogen the options
     *     gather into a label or leave out
     * @throws IllegalArgumentException if the ligand is a hydrogen that cannot be made sense of
     */
    private int placementOfLigand(int centreAtom, int ligand)
    {
        if (ligand != StereoDeclaration.IMPLICIT_HYDROGEN)
        {
            return placementOfAtom[ligand];
        }

        List<Integer> hydrogens = hydrogensOfAtom.get(centreAtom);
        if (hydrogens.isEmpty() && !options.effectiveHydrogens().isSeparate())
        {
            return UNDRAWN;
        }
        if (hydrogens.size() != 1)
        {
            throw new IllegalArgumentException("Atom " + centreAtom + " declares a parity about its implicit hydrogen,"
                + " which cannot be drawn: " + hydrogens.size() + " hydrogens were placed for it");
        }
        return hydrogens.getFirst();
    }

    /**
     * Orders a stereocentre's ligands by how willingly each takes the bond that leaves the plane:
     * a hydrogen first, as the convention is to wedge the hydrogen, then anything else with
     * nothing further hanging off it, and only then an atom carrying the rest of the structure.
     *
     * @param ligands the ligands' placements, in declared order
     * @param degrees how many bonds each placement has
     * @return the ligands' positions within {@code ligands}, best candidate first
     */
    private List<Integer> wedgeOrder(int[] ligands, int[] degrees)
    {
        List<Integer> order = new ArrayList<>(Parity.LIGANDS);
        for (int ligand = 0; ligand < ligands.length; ligand++)
        {
            order.add(ligand);
        }
        order.sort(Comparator.comparingInt(ligand -> rankAsWedge(ligands[ligand], degrees)));
        return order;
    }

    /**
     * @param placement a ligand's placement
     * @param degrees how many bonds each placement has
     * @return how willingly the ligand takes the bond that leaves the plane, lowest first
     */
    private int rankAsWedge(int placement, int[] degrees)
    {
        if (placement == UNDRAWN)
        {
            // a ligand with no bond on the page cannot carry the one that leaves it
            return Integer.MAX_VALUE;
        }
        if (placements.get(placement).isGeneratedHydrogen())
        {
            return 0;
        }
        return degrees[placement] == 1 ? 1 : 2;
    }

    /**
     * @return how many bonds each placement has, by placement index
     */
    private int[] degreesOfPlacements()
    {
        int[] degrees = new int[placements.size()];
        for (BondPlacement bond : bonds)
        {
            degrees[bond.first()]++;
            degrees[bond.second()]++;
        }
        return degrees;
    }

    /**
     * Restyles the bond between two placements, drawing it from the stereocentre outwards.
     *
     * @param centre the stereocentre's placement, which the narrow end sits on
     * @param ligand the other end's placement
     * @param style how the bond leaves the plane of the page
     */
    private void styleBond(int centre, int ligand, BondStyle style)
    {
        int at = bondsByPair.get(pairOf(centre, ligand));
        BondPlacement bond = bonds.get(at);
        bonds.set(at, (bond.first() == centre ? bond : bond.reversed()).styled(style));
    }

    /**
     * Moves each disconnected part of the molecule clear of the last, left to right.
     */
    private void spreadParts()
    {
        if (parts.size() < 2)
        {
            return;
        }

        double offset = 0;
        for (int[] part : parts)
        {
            List<Point> points = new ArrayList<>(part[1] - part[0]);
            for (int placement = part[0]; placement < part[1]; placement++)
            {
                points.add(placements.get(placement).position());
            }

            Bounds bounds = Bounds.around(points);
            double dx = offset - bounds.minX();
            double dy = -bounds.centre().y();
            for (int placement = part[0]; placement < part[1]; placement++)
            {
                AtomPlacement atom = placements.get(placement);
                placements.set(placement, atom.at(atom.position().translate(dx, dy)));
            }
            offset += bounds.width() + FRAGMENT_GAP;
        }
    }

    /**
     * @param atom one of the molecule's own atoms
     * @param position where to put it
     * @return its placement index
     */
    private int place(Atom atom, Point position)
    {
        int index = indexOf(atom);
        placed[index] = true;
        positions[index] = position;
        int condensed = options.effectiveHydrogens() == HydrogenStyle.CONDENSED ? molecule.totalHydrogenCount(atom) : 0;
        placementOfAtom[index] = addPlacement(
            atom.element(), atom.isotope(), atom.charge(), condensed, atom.aromatic(), OptionalInt.of(index), position);
        return placementOfAtom[index];
    }

    /**
     * @param element the canonical element symbol
     * @param isotope the mass number to label the atom with, or empty to leave it unlabelled
     * @param charge the net formal charge
     * @param hydrogens how many hydrogens are gathered into the atom's own label
     * @param aromatic whether the atom is aromatic
     * @param sourceAtom the molecule atom it came from, or empty for a made-up hydrogen
     * @param position where to put it
     * @return its placement index
     */
    private int addPlacement(String element, OptionalInt isotope, int charge, int hydrogens, boolean aromatic, OptionalInt sourceAtom, Point position)
    {
        int index = placements.size();
        placements.add(new AtomPlacement(index, element, isotope, charge, hydrogens, aromatic, false, sourceAtom, 0, position));
        return index;
    }

    /**
     * @param first the placement the bond is drawn from
     * @param second the placement it is drawn to
     * @param order the bond order
     */
    private void addBond(int first, int second, BondOrder order)
    {
        addBond(first, second, order, OptionalInt.empty());
    }

    /**
     * @param first the placement the bond is drawn from
     * @param second the placement it is drawn to
     * @param bond the molecule's own bond being drawn
     */
    private void addBond(int first, int second, Bond bond)
    {
        addBond(first, second, bond.order(), OptionalInt.of(bondIndices.get(bond)));
    }

    /**
     * @param first the placement the bond is drawn from
     * @param second the placement it is drawn to
     * @param order the bond order
     * @param sourceBond the index of the molecule bond it came from, or empty for a made-up hydrogen's
     */
    private void addBond(int first, int second, BondOrder order, OptionalInt sourceBond)
    {
        bondsByPair.put(pairOf(first, second), bonds.size());
        bonds.add(new BondPlacement(first, second, order, BondStyle.PLAIN, sourceBond));
    }

    /**
     * @param first one placement index
     * @param second the other placement index
     * @return a key identifying the pair, whichever way round the two are given
     */
    private static long pairOf(int first, int second)
    {
        return ((long) Math.min(first, second) << Integer.SIZE) | Math.max(first, second);
    }

    /**
     * @param atom one of the molecule's own atoms, by reference identity
     * @return its index within {@link Molecule#atoms()}
     */
    private int indexOf(Atom atom)
    {
        return indices.get(atom);
    }

    /**
     * One atom waiting to have everything hanging off it placed.
     *
     * @param atom the atom to expand, which has already been placed
     * @param parent the atom the walk arrived from, or {@code null} if the walk started here
     * @param parentBond the bond the walk arrived on, or {@code null} if the walk started here
     * @param backAngle the direction {@code parent} lies in, or {@link Double#NaN} if there is none
     * @param zigzagSign which way a zig-zag chain turns at this atom, alternating along a chain
     */
    private record Walk(Atom atom, Atom parent, Bond parentBond, double backAngle, int zigzagSign)
    {
    }

    /**
     * A substituent one end of a double bond has its configuration measured from.
     *
     * @param atom the substituent's atom index, or {@link StereoDeclaration#IMPLICIT_HYDROGEN} for
     *     an atom's own hydrogen
     * @param side which side of the double bond it sits on, as {@code 1} or {@code -1}
     */
    private record Reference(int atom, int side)
    {
    }
}
