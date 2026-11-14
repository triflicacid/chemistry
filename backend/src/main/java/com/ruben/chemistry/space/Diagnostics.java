package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.PiSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @param strain the embedder's total strain for this conformer
 * @param lengths every bond
 * @param angles every angle between two bonds at an atom whose ideal is known
 * @param torsions the torsions about bonds outside rings between atoms whose ideal is known
 * @param clashes non-bonded pairs closer than their contact distance
 * @param lonePairs where the lone pairs point
 * @param conjugations the π orbitals of each conjugated system, as this conformer holds them
 * @param atomStrain each node's share of the deviation, where 1 is one term at its tolerance
 */
public record Diagnostics(
        double strain,
        List<Length> lengths,
        List<Angle> angles,
        List<Torsion> torsions,
        List<Clash> clashes,
        List<LonePair> lonePairs,
        List<Conjugation> conjugations,
        List<Double> atomStrain)
{
    /** How far a bond may stray from its ideal, in angstroms, before it counts as strained. */
    public static final double LENGTH_TOLERANCE = 0.05;

    /** How far an angle may stray from its ideal, in degrees. */
    public static final double ANGLE_TOLERANCE = 6;

    /** How far a torsion may stray from its ideal, in degrees. */
    public static final double TORSION_TOLERANCE = 20;

    /** How far two atoms may sit inside their contact distance, in angstroms. */
    public static final double CLASH_TOLERANCE = 0.05;

    /** The fraction of the van der Waals radii that atoms two or more bonds apart should keep. */
    public static final double CONTACT_SCALE = 0.75;

    private static final double LOBE_LENGTH = 0.9;
    private static final double TETRAHEDRAL_HALF = Math.toRadians(54.74);
    private static final double CONE = Math.toRadians(70.53);

    /** How far a π orbital's lobe reaches from its atom, in angstroms. */
    private static final double PI_LOBE_LENGTH = 0.75;

    /** How far off the plane of a π system its cloud lies, in angstroms. */
    private static final double PI_CLOUD_OFFSET = 0.45;

    /** The length below which a vector is taken to have no direction worth using. */
    private static final double TINY = 1e-6;

    /** A bond, its length and the length it should be, in angstroms. */
    public record Length(int first, int second, double actual, double ideal)
    {
        public double deviation()
        {
            return Math.abs(actual - ideal);
        }
    }

    /** The angle in degrees at {@code middle} between two of its neighbours. */
    public record Angle(int first, int middle, int second, double actual, double ideal)
    {
        public double deviation()
        {
            return Math.abs(actual - ideal);
        }
    }

    /**
     * The torsion in degrees, from -180 to 180, about the bond between {@code second} and {@code third}.
     * Its ideal is the eclipsed-free value: 0 or 180 between flat atoms, or the nearest staggered one
     * between tetrahedral atoms.
     */
    public record Torsion(int first, int second, int third, int fourth, double actual, double ideal)
    {
        public double deviation()
        {
            double difference = Math.abs(actual - ideal) % 360;
            return difference > 180 ? 360 - difference : difference;
        }
    }

    /** Two atoms nearer than they should be, in angstroms. */
    public record Clash(int first, int second, double distance, double contact)
    {
        public double overlap()
        {
            return contact - distance;
        }
    }

    /** One lone pair on a node, pointing in the direction of {@code tip}, a point near the atom. */
    public record LonePair(int node, Vec3 tip)
    {
    }

    /**
     * One atom's π orbital: a lobe either side of the plane its σ bonds lie in.
     *
     * @param node the node the orbital sits on
     * @param electrons how many π electrons the atom lends its system
     * @param above where the lobe on one side of the plane reaches
     * @param below where the lobe on the other side reaches
     */
    public record PiOrbital(int node, int electrons, Vec3 above, Vec3 below)
    {
    }

    /**
     * One bond's share of a π system's cloud: the lozenge lying over the bond, on each face of
     * the system. It runs the length of the bond, which its ends give.
     *
     * @param first the node at one end of the bond
     * @param second the node at the other
     * @param above the middle of the lozenge on one face of the system
     * @param below the middle of the one on the other face
     */
    public record PiCloud(int first, int second, Vec3 above, Vec3 below)
    {
    }

    /**
     * One conjugated π system, as the two pictures chemistry draws of one: the p orbital standing
     * on each atom, and the cloud they overlap into.
     *
     * @param orbitals one π orbital per atom of the system, in the molecule's own order
     * @param clouds the cloud over each of the system's bonds, in the molecule's own order; a bond
     *     whose atoms give no plane to lie either side of is left out
     * @param piElectrons the π electrons counted over the system's atoms
     * @param aromaticity what the system amounts to as written
     * @see PiSystem
     */
    public record Conjugation(List<PiOrbital> orbitals, List<PiCloud> clouds, int piElectrons,
        PiSystem.Aromaticity aromaticity)
    {
    }

    /**
     * @param molecule the molecule the structure was embedded from
     * @param structure the embedded structure
     * @param conformer the index of the conformer to examine
     * @return what its geometry says
     * @throws IllegalArgumentException if the structure is not of this molecule, or there is no such conformer
     */
    public static Diagnostics of(Molecule molecule, Structure3D structure, int conformer)
    {
        Topology topology = new Topology(molecule);
        if (topology.count != structure.sites().size())
        {
            throw new IllegalArgumentException("The structure is not an embedding of this molecule");
        }
        if (conformer < 0 || conformer >= structure.conformers().size())
        {
            throw new IllegalArgumentException("There is no conformer " + conformer + "; there are " + structure.conformers().size());
        }

        Structure3D.Conformer chosen = structure.conformers().get(conformer);
        List<Vec3> at = chosen.positions();
        double[] share = new double[topology.count];

        List<Length> lengths = new ArrayList<>();
        for (Topology.Link link : topology.links)
        {
            double actual = at.get(link.first()).distanceTo(at.get(link.second()));
            Length length = new Length(link.first(), link.second(), actual, Bounds.length(topology, link));
            lengths.add(length);
            double score = square(length.deviation() / LENGTH_TOLERANCE);
            share[link.first()] += score / 2;
            share[link.second()] += score / 2;
        }

        List<Angle> angles = new ArrayList<>();
        for (int middle = 0; middle < topology.count; middle++)
        {
            int[] around = topology.neighbours[middle];
            if (!known(topology, middle) || around.length < 2)
            {
                continue;
            }
            for (int i = 0; i < around.length; i++)
            {
                for (int j = i + 1; j < around.length; j++)
                {
                    double actual = Vec3.angle(at.get(around[i]), at.get(middle), at.get(around[j]));
                    Angle angle = new Angle(around[i], middle, around[j], actual, idealAngle(topology, around[i], middle, around[j]));
                    angles.add(angle);
                    double score = square(angle.deviation() / ANGLE_TOLERANCE);
                    share[middle] += score / 2;
                    share[around[i]] += score / 4;
                    share[around[j]] += score / 4;
                }
            }
        }

        List<Torsion> torsions = new ArrayList<>();
        for (Topology.Link link : topology.links)
        {
            int second = link.first();
            int third = link.second();
            int kind = torsionKind(topology, second, third);
            if (kind == 0 || sharedRing(topology, second, third))
            {
                continue;
            }
            for (int first : topology.neighbours[second])
            {
                for (int fourth : topology.neighbours[third])
                {
                    if (first == third || fourth == second)
                    {
                        continue;
                    }
                    double actual = Vec3.torsion(at.get(first), at.get(second), at.get(third), at.get(fourth));
                    Torsion torsion = new Torsion(first, second, third, fourth, actual, idealTorsion(kind, actual));
                    torsions.add(torsion);
                    double score = square(torsion.deviation() / TORSION_TOLERANCE);
                    share[second] += score / 4;
                    share[third] += score / 4;
                }
            }
        }

        List<Clash> clashes = new ArrayList<>();
        for (int first = 0; first < topology.count; first++)
        {
            boolean[] near = withinTwoBonds(topology, first);
            for (int second = first + 1; second < topology.count; second++)
            {
                if (near[second])
                {
                    continue;
                }
                double contact = CONTACT_SCALE * (ElementData.vanDerWaalsRadius(topology.atomicNumber[first])
                    + ElementData.vanDerWaalsRadius(topology.atomicNumber[second]));
                double distance = at.get(first).distanceTo(at.get(second));
                if (distance < contact)
                {
                    Clash clash = new Clash(first, second, distance, contact);
                    clashes.add(clash);
                    double score = square(clash.overlap() / CLASH_TOLERANCE);
                    share[first] += score / 2;
                    share[second] += score / 2;
                }
            }
        }

        List<LonePair> lonePairs = new ArrayList<>();
        for (int node = 0; node < topology.count; node++)
        {
            lonePairs.addAll(lonePairsOf(topology, node, at));
        }

        List<Conjugation> conjugations = new ArrayList<>();
        for (PiSystem system : molecule.piSystems())
        {
            conjugations.add(conjugationOf(molecule, topology, system, at));
        }

        List<Double> atomStrain = new ArrayList<>(topology.count);
        for (double value : share)
        {
            atomStrain.add(value);
        }
        return new Diagnostics(chosen.strain(), List.copyOf(lengths), List.copyOf(angles), List.copyOf(torsions),
            List.copyOf(clashes), List.copyOf(lonePairs), List.copyOf(conjugations), List.copyOf(atomStrain));
    }

    /**
     * @param scale how many times the tolerances to allow; 1 is the default and 0 includes everything
     * @return a readable account: the totals, then each term beyond its tolerance, worst first
     */
    public String report(double scale)
    {
        record Finding(double severity, String text)
        {
        }

        List<Finding> findings = new ArrayList<>();
        for (Length length : lengths)
        {
            if (length.deviation() > LENGTH_TOLERANCE * scale)
            {
                findings.add(new Finding(length.deviation() / LENGTH_TOLERANCE, String.format(Locale.ROOT,
                    "bond    %s-%s  %.3f A, ideal %.3f A", name(length.first()), name(length.second()), length.actual(), length.ideal())));
            }
        }
        for (Angle angle : angles)
        {
            if (angle.deviation() > ANGLE_TOLERANCE * scale)
            {
                findings.add(new Finding(angle.deviation() / ANGLE_TOLERANCE, String.format(Locale.ROOT,
                    "angle   %s-%s-%s  %.1f deg, ideal %.1f deg", name(angle.first()), name(angle.middle()), name(angle.second()),
                    angle.actual(), angle.ideal())));
            }
        }
        for (Torsion torsion : torsions)
        {
            if (torsion.deviation() > TORSION_TOLERANCE * scale)
            {
                findings.add(new Finding(torsion.deviation() / TORSION_TOLERANCE, String.format(Locale.ROOT,
                    "torsion %s-%s-%s-%s  %.1f deg, ideal %.0f deg", name(torsion.first()), name(torsion.second()),
                    name(torsion.third()), name(torsion.fourth()), torsion.actual(), torsion.ideal())));
            }
        }
        for (Clash clash : clashes)
        {
            if (clash.overlap() > CLASH_TOLERANCE * scale)
            {
                findings.add(new Finding(clash.overlap() / CLASH_TOLERANCE, String.format(Locale.ROOT,
                    "clash   %s...%s  %.2f A, contact %.2f A", name(clash.first()), name(clash.second()), clash.distance(), clash.contact())));
            }
        }
        findings.sort(Comparator.comparingDouble(Finding::severity).reversed());

        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
            "Strain %.3f; %d bonds, %d angles, %d torsions, %d clashes, %d lone pairs, %d pi systems%n",
            strain, lengths.size(), angles.size(), torsions.size(), clashes.size(), lonePairs.size(), conjugations.size()));
        if (findings.isEmpty())
        {
            text.append("Nothing beyond tolerance.").append(System.lineSeparator());
        }
        for (Finding finding : findings)
        {
            text.append(String.format(Locale.ROOT, "%5.1fx  %s%n", finding.severity(), finding.text()));
        }
        return text.toString();
    }

    private String name(int node)
    {
        return "#" + node;
    }

    private static double square(double value)
    {
        return value * value;
    }

    /** Whether an atom's angles have an ideal worth stating. */
    private static boolean known(Topology topology, int node)
    {
        return !topology.flexible[node]
            && topology.neighbours[node].length <= 4
            && topology.neighbours[node].length + topology.lonePairs[node] <= 4;
    }

    private static double idealAngle(Topology topology, int first, int middle, int second)
    {
        double ideal = topology.idealAngle[middle];
        for (Topology.Ring ring : topology.rings)
        {
            if (topology.planar[middle] && ring.planar() && ring.nodes().contains(first)
                && ring.nodes().contains(middle) && ring.nodes().contains(second))
            {
                return (ring.size() - 2) * 180.0 / ring.size();
            }
        }
        return ideal;
    }

    /** 1 for a bond between flat atoms, 2 between tetrahedral ones, and 0 where no ideal is stated. */
    private static int torsionKind(Topology topology, int second, int third)
    {
        if (topology.neighbours[second].length < 2 || topology.neighbours[third].length < 2
            || topology.flexible[second] || topology.flexible[third]
            || topology.idealAngle[second] >= 179 || topology.idealAngle[third] >= 179)
        {
            return 0;
        }
        if (topology.planar[second] && topology.planar[third])
        {
            return 1;
        }
        boolean tetrahedral = !topology.planar[second] && !topology.planar[third]
            && topology.neighbours[second].length + topology.lonePairs[second] == 4
            && topology.neighbours[third].length + topology.lonePairs[third] == 4;
        return tetrahedral ? 2 : 0;
    }

    private static double idealTorsion(int kind, double actual)
    {
        double[] options = kind == 1 ? new double[] {0, 180, -180} : new double[] {60, -60, 180, -180};
        double best = options[0];
        for (double option : options)
        {
            if (Math.abs(actual - option) < Math.abs(actual - best))
            {
                best = option;
            }
        }
        return best;
    }

    private static boolean sharedRing(Topology topology, int first, int second)
    {
        for (Topology.Ring ring : topology.rings)
        {
            if (ring.nodes().contains(first) && ring.nodes().contains(second))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean[] withinTwoBonds(Topology topology, int start)
    {
        boolean[] near = new boolean[topology.count];
        near[start] = true;
        List<Integer> frontier = List.of(start);
        for (int step = 0; step < 2; step++)
        {
            List<Integer> next = new ArrayList<>();
            for (int node : frontier)
            {
                for (int neighbour : topology.neighbours[node])
                {
                    if (!near[neighbour])
                    {
                        near[neighbour] = true;
                        next.add(neighbour);
                    }
                }
            }
            frontier = next;
        }
        return near;
    }

    /** Places an atom's lone pairs where its neighbours leave room, as VSEPR would for up to four domains. */
    private static List<LonePair> lonePairsOf(Topology topology, int node, List<Vec3> at)
    {
        int pairs = topology.lonePairs[node];
        int[] around = topology.neighbours[node];
        if (pairs == 0 || pairs + around.length > 4 || around.length == 0)
        {
            return List.of();
        }

        Vec3 centre = at.get(node);
        Vec3 sum = Vec3.ZERO;
        for (int neighbour : around)
        {
            sum = sum.plus(at.get(neighbour).minus(centre).normalised());
        }

        Vec3 axis;
        Vec3 side;
        if (around.length >= 2 && sum.length() > 1e-6)
        {
            axis = sum.times(-1).normalised();
            Vec3 normal = at.get(around[0]).minus(centre).cross(at.get(around[1]).minus(centre));
            side = normal.length() > 1e-6 ? normal.normalised() : perpendicular(axis);
        }
        else if (around.length == 1)
        {
            axis = at.get(around[0]).minus(centre).normalised().times(-1);
            side = perpendicular(axis);
        }
        else
        {
            // neighbours cancel out, as in a flat atom: the pairs lie above and below the plane
            Vec3 normal = at.get(around[0]).minus(centre).cross(at.get(around[1]).minus(centre));
            axis = normal.normalised();
            side = perpendicular(axis);
        }

        List<Vec3> directions = new ArrayList<>();
        boolean planarPair = around.length >= 2 && sum.length() <= 1e-6;
        if (planarPair)
        {
            directions.add(axis);
            if (pairs > 1)
            {
                directions.add(axis.times(-1));
            }
        }
        else if (pairs == 1)
        {
            directions.add(axis);
        }
        else if (pairs == 2)
        {
            directions.add(axis.times(Math.cos(TETRAHEDRAL_HALF)).plus(side.times(Math.sin(TETRAHEDRAL_HALF))));
            directions.add(axis.times(Math.cos(TETRAHEDRAL_HALF)).minus(side.times(Math.sin(TETRAHEDRAL_HALF))));
        }
        else
        {
            Vec3 other = axis.cross(side);
            for (int k = 0; k < pairs; k++)
            {
                double turn = 2 * Math.PI * k / pairs;
                Vec3 round = side.times(Math.cos(turn)).plus(other.times(Math.sin(turn)));
                directions.add(axis.times(Math.cos(CONE)).plus(round.times(Math.sin(CONE))));
            }
        }

        List<LonePair> found = new ArrayList<>();
        for (Vec3 direction : directions)
        {
            found.add(new LonePair(node, centre.plus(direction.normalised().times(LOBE_LENGTH))));
        }
        return found;
    }

    /**
     * Stands one π system's orbitals up on the conformer.
     * <p>
     * A π orbital is the p orbital left over once an atom's σ bonds are accounted for, so it points
     * along the normal of the plane those bonds lie in.
     *
     * @param molecule the molecule the system belongs to
     * @param topology the molecule restated as nodes, whose written atoms are numbered as its own are
     * @param system one of its π systems
     * @param at where every node sits
     * @return the system's orbitals and cloud, with its electron count and what it amounts to
     */
    private static Conjugation conjugationOf(Molecule molecule, Topology topology, PiSystem system, List<Vec3> at)
    {
        List<Atom> atoms = molecule.atoms();
        Map<Atom, Integer> nodeOf = new IdentityHashMap<>();
        for (int node = 0; node < atoms.size(); node++)
        {
            nodeOf.put(atoms.get(node), node);
        }

        Vec3 reference = Vec3.ZERO;
        Map<Atom, Vec3> normals = new IdentityHashMap<>();
        List<PiOrbital> orbitals = new ArrayList<>();
        for (Atom atom : system.atoms())
        {
            int node = nodeOf.get(atom);
            Vec3 normal = normalAt(topology, node, at);
            if (normal.length() <= TINY)
            {
                continue;
            }
            if (reference.length() <= TINY)
            {
                reference = normal;
            }
            else if (normal.dot(reference) < 0)
            {
                // ensure they point the same way
                normal = normal.times(-1);
            }

            Vec3 centre = at.get(node);
            Vec3 lobe = normal.times(PI_LOBE_LENGTH);
            int electrons = PiSystem.piElectronsOf(molecule, atom).orElse(0);
            normals.put(atom, normal);
            orbitals.add(new PiOrbital(node, electrons, centre.plus(lobe), centre.minus(lobe)));
        }

        List<PiCloud> clouds = new ArrayList<>();
        for (Bond bond : system.bonds())
        {
            Vec3 first = normals.get(bond.first());
            Vec3 second = normals.get(bond.second());
            if (first == null || second == null)
            {
                continue;
            }
            Vec3 normal = first.plus(second).normalised();
            Vec3 middle = at.get(nodeOf.get(bond.first())).plus(at.get(nodeOf.get(bond.second()))).times(0.5);
            Vec3 offset = normal.times(PI_CLOUD_OFFSET);
            clouds.add(new PiCloud(nodeOf.get(bond.first()), nodeOf.get(bond.second()),
                middle.plus(offset), middle.minus(offset)));
        }
        return new Conjugation(List.copyOf(orbitals), List.copyOf(clouds), system.piElectrons(), system.aromaticity());
    }

    /**
     * Fits a plane to the bonds at one atom and takes its normal.
     *
     * @param topology the molecule restated as nodes
     * @param node the node to fit the plane at
     * @param at where every node sits
     * @return the unit normal, or {@link Vec3#ZERO} if its bonds give no plane
     */
    private static Vec3 normalAt(Topology topology, int node, List<Vec3> at)
    {
        int[] around = topology.neighbours[node];
        Vec3 centre = at.get(node);
        Vec3 normal = Vec3.ZERO;
        for (int first = 0; first < around.length; first++)
        {
            for (int second = first + 1; second < around.length; second++)
            {
                Vec3 pair = at.get(around[first]).minus(centre).cross(at.get(around[second]).minus(centre));
                normal = pair.dot(normal) < 0 ? normal.minus(pair) : normal.plus(pair);
            }
        }
        return normal.length() > TINY ? normal.normalised() : Vec3.ZERO;
    }

    private static Vec3 perpendicular(Vec3 axis)
    {
        Vec3 helper = Math.abs(axis.x()) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return axis.cross(helper).normalised();
    }
}
