package com.ruben.chemistry.space;

import com.ruben.chemistry.core.BondOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The distances every pair of nodes may be apart, drawn from the molecule's bonding.
 * <p>
 * Bonded pairs get a length from the covalent radii and the bond order. Pairs two bonds apart get a
 * distance range from the ideal angle at the node between them, which accounts for lone pairs and
 * small rings. Pairs three bonds apart get a range from the torsion about the bond between them,
 * pinned where the bond is planar and its sides are known, which is where E/Z configuration enters.
 * All other pairs only keep out of each other's van der Waals contact. The bounds are then smoothed
 * so each respects the triangle inequality.
 */
final class Bounds
{
    /** Weights, stiffest for bond lengths. */
    static final double BOND_WEIGHT = 100;

    static final double ANGLE_WEIGHT = 50;

    static final double PLANAR_TORSION_WEIGHT = 25;

    static final double FREE_TORSION_WEIGHT = 10;

    static final double CONTACT_WEIGHT = 2;

    private static final double CONTACT_SCALE = 0.75;

    private static final double LENGTH_SLACK = 0.01;

    private static final double ANGLE_SLACK = 4;

    private static final double TORSION_DISTANCE_SLACK = 0.08;

    final int count;

    final double[][] lower;

    final double[][] upper;

    final double[][] weight;

    /** Chains {@code j, i, k, l} that planarity holds flat: {@code i} and {@code l} either side of bond {@code j-k}. */
    final List<int[]> flatChains = new ArrayList<>();

    private final Topology topology;

    /** Which side of a double bond a substituent is on, by {@code centre * count + substituent}. */
    private final Map<Long, Integer> sides = new HashMap<>();

    private Bounds(Topology topology)
    {
        this.topology = topology;
        this.count = topology.count;
        this.lower = new double[count][count];
        this.upper = new double[count][count];
        this.weight = new double[count][count];
    }

    /**
     * @param topology the molecule
     * @param random where unspecified choices (such as the shape of an unconfigured double bond) are made
     * @return the smoothed bounds
     */
    static Bounds of(Topology topology, Random random)
    {
        Bounds bounds = new Bounds(topology);
        bounds.assignSides(random);
        bounds.fill();
        bounds.smooth();
        return bounds;
    }

    /**
     * @param link a bond
     * @return its length in angstroms
     */
    static double length(Topology topology, Topology.Link link)
    {
        double sum = ElementData.covalentRadius(topology.atomicNumber[link.first()])
            + ElementData.covalentRadius(topology.atomicNumber[link.second()]);
        double shortening = switch (link.order())
        {
            case SINGLE -> 0;
            case AROMATIC -> 0.13;
            case DOUBLE -> 0.19;
            case TRIPLE -> 0.32;
            case QUADRUPLE -> 0.4;
        };
        return sum - shortening;
    }

    /**
     * @param a the length of one side
     * @param b the length of the other
     * @param degrees the angle between them
     * @return the length of the side opposite the angle
     */
    static double across(double a, double b, double degrees)
    {
        return Math.sqrt(Math.max(0, a * a + b * b - 2 * a * b * Math.cos(Math.toRadians(degrees))));
    }

    private double length(int first, int second)
    {
        return length(topology, topology.link(first, second));
    }

    private void fill()
    {
        double cap = 15 + 0.2 * count;
        for (int first = 0; first < count; first++)
        {
            for (int second = first + 1; second < count; second++)
            {
                double contact = CONTACT_SCALE * (ElementData.vanDerWaalsRadius(topology.atomicNumber[first])
                    + ElementData.vanDerWaalsRadius(topology.atomicNumber[second]));
                set(first, second, contact, cap, CONTACT_WEIGHT);
            }
        }

        for (Topology.Link link : topology.links)
        {
            double length = length(topology, link);
            set(link.first(), link.second(), length - LENGTH_SLACK, length + LENGTH_SLACK, BOND_WEIGHT);
        }

        for (int middle = 0; middle < count; middle++)
        {
            int[] around = topology.neighbours[middle];
            for (int first = 0; first < around.length; first++)
            {
                for (int second = first + 1; second < around.length; second++)
                {
                    addAngle(around[first], middle, around[second]);
                }
            }
        }

        for (Topology.Link link : topology.links)
        {
            int j = link.first();
            int k = link.second();
            for (int i : topology.neighbours[j])
            {
                for (int l : topology.neighbours[k])
                {
                    if (i != k && l != j && i != l)
                    {
                        addTorsion(i, j, k, l);
                    }
                }
            }
        }
    }

    private void set(int first, int second, double low, double high, double stiffness)
    {
        lower[first][second] = lower[second][first] = low;
        upper[first][second] = upper[second][first] = high;
        weight[first][second] = weight[second][first] = stiffness;
    }

    /**
     * Narrows a pair's bounds to the overlap with new ones, or takes the new ones if they do not overlap.
     */
    private void narrow(int first, int second, double low, double high, double stiffness)
    {
        if (weight[first][second] >= BOND_WEIGHT)
        {
            return;
        }
        if (weight[first][second] > CONTACT_WEIGHT)
        {
            double newLow = Math.max(low, lower[first][second]);
            double newHigh = Math.min(high, upper[first][second]);
            if (newLow <= newHigh)
            {
                set(first, second, newLow, newHigh, Math.max(stiffness, weight[first][second]));
            }
            return;
        }
        set(first, second, low, high, stiffness);
    }

    /**
     * @return the smallest ring holding every given node, or {@code null}
     */
    private Topology.Ring ringHolding(int... nodes)
    {
        Topology.Ring best = null;
        for (Topology.Ring ring : topology.rings)
        {
            boolean holds = true;
            for (int node : nodes)
            {
                holds &= ring.nodes().contains(node);
            }
            if (holds && (best == null || ring.size() < best.size()))
            {
                best = ring;
            }
        }
        return best;
    }

    /**
     * @return the smallest and largest angle, in degrees, the bonds from {@code middle} to
     *     {@code first} and {@code second} may make
     */
    private double[] angleRange(int first, int middle, int second)
    {
        if (topology.flexible[middle])
        {
            return new double[] {Topology.HYPERVALENT_MINIMUM, 180};
        }

        double ideal = topology.idealAngle[middle];
        double slack = ideal >= 179 ? 1 : ANGLE_SLACK;
        Topology.Ring ring = ringHolding(first, middle, second);
        if (ring != null && ideal < 179)
        {
            int size = ring.size();
            if (ring.planar())
            {
                ideal = 180.0 * (size - 2) / size;
            }
            else
            {
                ideal = switch (size)
                {
                    case 3 -> 60;
                    case 4 -> 90;
                    case 5 -> Math.min(ideal, 104);
                    case 7 -> Math.min(ideal, 114);
                    default -> ideal;
                };
            }
            slack = size == 3 ? 2 : ANGLE_SLACK;
        }
        return new double[] {Math.max(0, ideal - slack), Math.min(180, ideal + slack)};
    }

    private void addAngle(int first, int middle, int second)
    {
        if (topology.link(first, second) != null)
        {
            return;
        }
        double a = length(first, middle);
        double b = length(middle, second);
        double[] range = angleRange(first, middle, second);
        narrow(first, second, across(a, b, range[0]), across(a, b, range[1]), ANGLE_WEIGHT);
    }

    private void addTorsion(int i, int j, int k, int l)
    {
        if (topology.link(i, l) != null || topology.flexible[j] || topology.flexible[k])
        {
            return;
        }
        // a pair of ends sharing a bond is in a 3-ring, which has no torsion to speak of
        if (topology.link(i, k) != null || topology.link(j, l) != null)
        {
            return;
        }

        double[] first = angleRange(i, j, k);
        double[] second = angleRange(j, k, l);
        double a = length(i, j);
        double b = length(j, k);
        double c = length(k, l);
        double theta1 = (first[0] + first[1]) / 2;
        double theta2 = (second[0] + second[1]) / 2;

        double minimumTorsion = 0;
        double maximumTorsion = 180;
        double stiffness = FREE_TORSION_WEIGHT;
        int forced = forcedTorsion(i, j, k, l);
        if (forced >= 0)
        {
            minimumTorsion = maximumTorsion = forced;
            stiffness = PLANAR_TORSION_WEIGHT;
            flatChains.add(new int[] {j, i, k, l});
        }
        else
        {
            Topology.Ring ring = ringHolding(i, j, k, l);
            if (ring != null)
            {
                maximumTorsion = switch (ring.size())
                {
                    case 4 -> 35;
                    case 5 -> 50;
                    case 6 -> 75;
                    default -> 180;
                };
            }
        }

        double near = torsionDistance(a, b, c, theta1, theta2, minimumTorsion) - TORSION_DISTANCE_SLACK;
        double far = torsionDistance(a, b, c, theta1, theta2, maximumTorsion) + TORSION_DISTANCE_SLACK;
        narrow(i, l, Math.max(0, near), far, stiffness);
    }

    /**
     * @return the distance between the ends of a chain of three bonds of lengths {@code a}, {@code b},
     *     {@code c}, with the given bond angles and torsion about the middle bond
     */
    static double torsionDistance(double a, double b, double c, double theta1, double theta2, double torsion)
    {
        double t1 = Math.toRadians(theta1);
        double t2 = Math.toRadians(theta2);
        double phi = Math.toRadians(torsion);
        double x = b - c * Math.cos(t2) - a * Math.cos(t1);
        double y = c * Math.sin(t2) * Math.cos(phi) - a * Math.sin(t1);
        double z = c * Math.sin(t2) * Math.sin(phi);
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * @return 0 or 180 if the bond from {@code j} to {@code k} holds {@code i} and {@code l} cis or trans
     *     by planarity, or -1 if their relation is free
     */
    private int forcedTorsion(int i, int j, int k, int l)
    {
        Integer sideI = sides.get(sideKey(j, i));
        Integer sideL = sides.get(sideKey(k, l));
        if (sideI != null && sideL != null)
        {
            return sideI.equals(sideL) ? 0 : 180;
        }

        Topology.Ring shared = ringHolding(j, k);
        if (shared == null || !shared.planar())
        {
            return -1;
        }
        // a flat ring keeps its own atoms cis to one another and everything outside it trans to the ring
        boolean flatTogether = false;
        boolean iInRing = false;
        boolean lInRing = false;
        for (Topology.Ring ring : topology.rings)
        {
            if (!ring.planar() || !ring.nodes().contains(j) || !ring.nodes().contains(k))
            {
                continue;
            }
            boolean hasI = ring.nodes().contains(i);
            boolean hasL = ring.nodes().contains(l);
            flatTogether |= hasI && hasL;
            iInRing |= hasI;
            lInRing |= hasL;
        }
        if (flatTogether)
        {
            return 0;
        }
        return iInRing || lInRing ? 180 : 0;
    }

    private long sideKey(int centre, int substituent)
    {
        return (long) centre * count + substituent;
    }

    private void assignSides(Random random)
    {
        for (Topology.Link link : topology.links)
        {
            if (link.order() != BondOrder.DOUBLE)
            {
                continue;
            }
            int j = link.first();
            int k = link.second();
            int[] first = substituents(j, k);
            int[] second = substituents(k, j);
            if (first.length == 0 || first.length > 2 || second.length == 0 || second.length > 2
                || topology.flexible[j] || topology.flexible[k]
                || topology.idealAngle[j] >= 179 || topology.idealAngle[k] >= 179)
            {
                continue;
            }

            sides.put(sideKey(j, first[0]), 1);
            if (first.length == 2)
            {
                sides.put(sideKey(j, first[1]), -1);
            }

            int sideOfFirstEnd = declaredOrRing(link, j, k, first, second, random);
            sides.put(sideKey(k, second[0]), sideOfFirstEnd);
            if (second.length == 2)
            {
                sides.put(sideKey(k, second[1]), -sideOfFirstEnd);
            }
        }
    }

    /**
     * @return the side, as {@code +1} or {@code -1}, of {@code second[0]}, given that {@code first[0]} is on {@code +1}
     */
    private int declaredOrRing(Topology.Link link, int j, int k, int[] first, int[] second, Random random)
    {
        for (Topology.Configured declared : topology.configured)
        {
            boolean forward = declared.first() == j && declared.second() == k;
            boolean backward = declared.first() == k && declared.second() == j;
            if (!forward && !backward)
            {
                continue;
            }
            int referenceJ = forward ? declared.firstReference() : declared.secondReference();
            int referenceK = forward ? declared.secondReference() : declared.firstReference();
            int sideJ = referenceJ == first[0] ? 1 : -1;
            int sideK = declared.sameSide() ? sideJ : -sideJ;
            return referenceK == second[0] ? sideK : -sideK;
        }

        // a bond in a small ring keeps the ring's own substituents on one side
        for (int i : first)
        {
            for (int l : second)
            {
                Topology.Ring ring = ringHolding(i, j, k, l);
                if (ring != null && ring.size() <= 7)
                {
                    return (i == first[0] ? 1 : -1) * (l == second[0] ? 1 : -1);
                }
            }
        }
        return random.nextBoolean() ? 1 : -1;
    }

    private int[] substituents(int node, int except)
    {
        int[] around = topology.neighbours[node];
        int[] result = new int[around.length - 1];
        int at = 0;
        for (int neighbour : around)
        {
            if (neighbour != except)
            {
                result[at++] = neighbour;
            }
        }
        return result;
    }

    /**
     * Tightens every bound by the triangle inequality, using the bounds of the other two sides.
     */
    private void smooth()
    {
        for (int via = 0; via < count; via++)
        {
            for (int first = 0; first < count; first++)
            {
                if (first == via)
                {
                    continue;
                }
                for (int second = first + 1; second < count; second++)
                {
                    if (second == via)
                    {
                        continue;
                    }
                    double sum = upper[first][via] + upper[via][second];
                    if (sum < upper[first][second])
                    {
                        upper[first][second] = upper[second][first] = sum;
                    }
                    double gap = Math.max(lower[first][via] - upper[via][second], lower[via][second] - upper[first][via]);
                    if (gap > lower[first][second])
                    {
                        lower[first][second] = lower[second][first] = gap;
                    }
                }
            }
        }
        // a contradiction between bounds is resolved in favour of the upper one
        for (int first = 0; first < count; first++)
        {
            for (int second = first + 1; second < count; second++)
            {
                if (lower[first][second] > upper[first][second])
                {
                    lower[first][second] = lower[second][first] = upper[first][second];
                }
            }
        }
    }
}
