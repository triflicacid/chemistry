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
 * <p>
 * TODO Only acyclic molecules are laid out so far; a molecule with a ring is rejected.
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

    /** How far off a bond a substituent has to be before it counts as being to one side of it. */
    private static final double COLLINEAR = 1e-9;

    private final Molecule molecule;
    private final DepictionOptions options;
    private final List<Atom> atoms;
    private final Map<Atom, Integer> indices;

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

    /** Where each bond sits in {@link #bonds}, by the placement pair it joins. */
    private final Map<Long, Integer> bondsByPair = new HashMap<>();

    /** Every declared double bond configuration, by the atom pair it is declared across. */
    private final Map<Long, StereoDeclaration.DoubleBond> configurations = new HashMap<>();

    /** The half-open range of placements belonging to each disconnected part of the molecule. */
    private final List<int[]> parts = new ArrayList<>();

    /**
     * @param molecule the molecule to lay out
     * @param options the choices to lay it out by
     */
    private MoleculeLayout(Molecule molecule, DepictionOptions options)
    {
        this.molecule = molecule;
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
    }

    /**
     * Lays a molecule out as an ordinary structural diagram, drawing every hydrogen.
     *
     * @param molecule the molecule to lay out
     * @return where every atom and bond goes
     * @throws NullPointerException if {@code molecule} is {@code null}
     * @throws IllegalArgumentException if {@code molecule} has a ring, which is not supported yet
     */
    public static Depiction layout(Molecule molecule)
    {
        return layout(molecule, DepictionOptions.defaults());
    }

    /**
     * Lays a molecule out.
     *
     * @param molecule the molecule to lay out
     * @param options the choices to lay it out by
     * @return where every atom and bond goes
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code molecule} has a ring, which is not supported yet,
     *     or declares a parity at an atom whose hydrogen the options leave undrawn
     */
    public static Depiction layout(Molecule molecule, DepictionOptions options)
    {
        Objects.requireNonNull(molecule, "molecule");
        Objects.requireNonNull(options, "options");
        return new MoleculeLayout(molecule, options).build();
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
        requireAcyclic();
        readConfigurations();

        for (Atom atom : atoms)
        {
            if (!placed[indexOf(atom)])
            {
                layOutPart(atom);
            }
        }

        applyParities();
        spreadParts();
        return new Depiction(placements, bonds);
    }

    /**
     * @throws IllegalArgumentException if the molecule has a ring
     */
    private void requireAcyclic()
    {
        if (!molecule.ringBonds().isEmpty())
        {
            throw new IllegalArgumentException("Only acyclic molecules can be laid out so far, but this one has "
                + molecule.ringBonds().size() + " ring bonds");
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
        place(root, Point.ORIGIN);

        Deque<Walk> pending = new ArrayDeque<>();
        pending.push(new Walk(root, null, null, Double.NaN, 1));
        while (!pending.isEmpty())
        {
            expand(pending.pop(), pending);
        }

        parts.add(new int[] {start, placements.size()});
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
        int hydrogens = options.explicitHydrogens() ? molecule.totalHydrogenCount(atom) : 0;
        int slots = children.size() + hydrogens;
        if (slots == 0)
        {
            return;
        }

        double[] directions = directionsFor(walk, slots, children);
        directions = honourConfiguration(walk, directions, children, hydrogens);

        for (int child = 0; child < children.size(); child++)
        {
            Bond bond = children.get(child);
            Atom neighbour = bond.getOther(atom);
            int placement = place(neighbour, centre.plus(Point.polar(directions[child], Depiction.BOND_LENGTH)));
            addBond(placementOfAtom[index], placement, bond.order());
            pending.push(new Walk(neighbour, atom, bond, directions[child] + Math.PI, -walk.zigzagSign()));
        }

        placeHydrogens(atom, index, centre, directions, children.size());
    }

    /**
     * @param atom the atom being expanded
     * @param parent the atom the walk arrived from, or {@code null} if it started here
     * @return the bonds leading to atoms still to be placed
     * @throws IllegalArgumentException if a bond leads back to an atom already placed (so a ring)
     */
    private List<Bond> childBondsOf(Atom atom, Atom parent)
    {
        List<Bond> children = new ArrayList<>();
        for (Bond bond : molecule.bondsOf(atom))
        {
            Atom other = bond.getOther(atom);
            if (other == parent)
            {
                continue;
            }
            if (placed[indexOf(other)])
            {
                throw new IllegalArgumentException("Only acyclic molecules can be laid out so far, but a bond closes a ring at " + other);
            }
            children.add(bond);
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
            double onwards = back + continuationTurn(walk, children.getFirst().order());
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
     * @return the angle between the bond the walk arrived on and the bond it carries on along
     */
    private double continuationTurn(Walk walk, BondOrder onwards)
    {
        BondOrder arrived = walk.parentBond().order();

        // an atom a triple bond ends at is linear, and so is one two double bonds pass through
        if (arrived == BondOrder.TRIPLE || onwards == BondOrder.TRIPLE || (isTrigonal(arrived) && isTrigonal(onwards)))
        {
            return STRAIGHT_TURN;
        }

        // an atom a single double bond ends at is trigonal, and has to be drawn so for its
        // substituents to fall to one side of that bond or the other
        if (isTrigonal(arrived) || isTrigonal(onwards) || options.chainGeometry() == ChainGeometry.ZIGZAG)
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
        if (!options.explicitHydrogens())
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
                Point position = centre.plus(Point.polar(directions[slot++], Depiction.BOND_LENGTH));
                int placement = addPlacement("H", isotope, 0, false, OptionalInt.empty(), position);
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
     * @throws IllegalArgumentException if a ligand is a hydrogen the options leave undrawn
     * @throws IllegalStateException if no bond can be drawn out of the plane to show the parity
     */
    private void applyParity(StereoDeclaration.Tetrahedral declaration, int[] degrees)
    {
        int centreAtom = declaration.centre();
        int centre = placementOfAtom[centreAtom];
        Point position = placements.get(centre).position();

        int[] ligands = new int[Parity.LIGANDS];
        List<Point> ligandPositions = new ArrayList<>(Parity.LIGANDS);
        for (int ligand = 0; ligand < Parity.LIGANDS; ligand++)
        {
            ligands[ligand] = placementOfLigand(centreAtom, declaration.ligands()[ligand]);
            ligandPositions.add(placements.get(ligands[ligand]).position());
        }

        for (int candidate : wedgeOrder(ligands, degrees))
        {
            double[] depths = new double[Parity.LIGANDS];
            depths[candidate] = Parity.depthOf(BondStyle.WEDGE);

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
     * @return the placement of that ligand
     * @throws IllegalArgumentException if the ligand is a hydrogen that was not drawn
     */
    private int placementOfLigand(int centreAtom, int ligand)
    {
        if (ligand != StereoDeclaration.IMPLICIT_HYDROGEN)
        {
            return placementOfAtom[ligand];
        }

        List<Integer> hydrogens = hydrogensOfAtom.get(centreAtom);
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
        placementOfAtom[index] = addPlacement(
            atom.element(), atom.isotope(), atom.charge(), atom.aromatic(), OptionalInt.of(index), position);
        return placementOfAtom[index];
    }

    /**
     * @param element the canonical element symbol
     * @param isotope the mass number to label the atom with, or empty to leave it unlabelled
     * @param charge the net formal charge
     * @param aromatic whether the atom is aromatic
     * @param sourceAtom the molecule atom it came from, or empty for a made-up hydrogen
     * @param position where to put it
     * @return its placement index
     */
    private int addPlacement(String element, OptionalInt isotope, int charge, boolean aromatic, OptionalInt sourceAtom, Point position)
    {
        int index = placements.size();
        placements.add(new AtomPlacement(index, element, isotope, charge, aromatic, sourceAtom, position));
        return index;
    }

    /**
     * @param first the placement the bond is drawn from
     * @param second the placement it is drawn to
     * @param order the bond order
     */
    private void addBond(int first, int second, BondOrder order)
    {
        bondsByPair.put(pairOf(first, second), bonds.size());
        bonds.add(new BondPlacement(first, second, order, BondStyle.PLAIN));
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
