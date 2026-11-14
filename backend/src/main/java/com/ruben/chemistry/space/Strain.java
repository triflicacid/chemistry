package com.ruben.chemistry.space;

/**
 * The strain of a set of coordinates: how far each pair strays from its bounds, how far each
 * declared stereocentre is from its handedness and how far each planar atom is from flat.
 * <p>
 * Coordinates are laid out node by node, {@code dimensions} numbers each. A fourth dimension
 * gives a centre room to turn itself inside out, and is penalised until it flattens away.
 */
final class Strain implements Minimiser.Objective
{
    private static final double CHIRAL_WEIGHT = 10;

    private static final double CHIRAL_VOLUME_SHARE = 0.3;

    private static final double PLANAR_WEIGHT = 5;

    private static final double CHAIN_WEIGHT = 10;

    private static final double LINEAR_WEIGHT = 50;

    private final Topology topology;

    private final Bounds bounds;

    private final int dimensions;

    private final int[] centres;

    private final int[][] centreLigands;

    private final double[] centreSign;

    private final double[] centreVolume;

    private final int[] flatNodes;

    /** How strongly the fourth dimension is penalised. */
    double flatten;

    Strain(Topology topology, Bounds bounds, int dimensions, double flatten)
    {
        this.topology = topology;
        this.bounds = bounds;
        this.dimensions = dimensions;
        this.flatten = flatten;

        int declared = topology.centres.size();
        centres = new int[declared];
        centreLigands = new int[declared][];
        centreSign = new double[declared];
        centreVolume = new double[declared];
        for (int at = 0; at < declared; at++)
        {
            Topology.Centre centre = topology.centres.get(at);
            centres[at] = centre.centre();
            centreLigands[at] = centre.ligands();
            centreSign[at] = centre.sign();
            double product = 1;
            for (int slot = 1; slot < 4; slot++)
            {
                product *= bounds.lower[centre.centre()][centre.ligands()[slot]];
            }
            centreVolume[at] = CHIRAL_VOLUME_SHARE * product;
        }

        flatNodes = new int[topology.count];
        int flat = 0;
        for (int node = 0; node < topology.count; node++)
        {
            if (topology.planar[node] && topology.neighbours[node].length == 3)
            {
                flatNodes[flat++] = node;
            }
        }
        flatNodesCount = flat;

        flatChains = bounds.flatChains.toArray(new int[0][]);
        int[][] straight = new int[topology.count][];
        int straightCount = 0;
        for (int node = 0; node < topology.count; node++)
        {
            int[] around = topology.neighbours[node];
            if (around.length == 2 && !topology.flexible[node] && topology.idealAngle[node] >= 179)
            {
                straight[straightCount++] = new int[] {node, around[0], around[1]};
            }
        }
        linear = java.util.Arrays.copyOf(straight, straightCount);
    }

    private final int flatNodesCount;

    private final int[][] flatChains;

    private final int[][] linear;

    /**
     * @return how many numbers the coordinates need
     */
    int size()
    {
        return topology.count * dimensions;
    }

    /**
     * @param x the coordinates
     * @param gradient receives the slope of the strain with respect to each coordinate, if not {@code null}
     * @return the strain
     */
    @Override
    public double evaluate(double[] x, double[] gradient)
    {
        if (gradient != null)
        {
            java.util.Arrays.fill(gradient, 0);
        }
        int count = topology.count;
        double energy = 0;

        for (int first = 0; first < count; first++)
        {
            for (int second = first + 1; second < count; second++)
            {
                double distanceSquared = 0;
                for (int axis = 0; axis < dimensions; axis++)
                {
                    double difference = x[first * dimensions + axis] - x[second * dimensions + axis];
                    distanceSquared += difference * difference;
                }
                double low = bounds.lower[first][second];
                double high = bounds.upper[first][second];
                // cheap rejection of the common case of a pair well within its bounds
                if (distanceSquared >= low * low && distanceSquared <= high * high)
                {
                    continue;
                }

                double distance = Math.sqrt(distanceSquared);
                double violation = distance < low ? distance - low : distance - high;
                double stiffness = bounds.weight[first][second];
                energy += stiffness * violation * violation;
                if (gradient != null)
                {
                    double scale = distance < 1e-9 ? 0 : 2 * stiffness * violation / distance;
                    for (int axis = 0; axis < dimensions; axis++)
                    {
                        double difference = x[first * dimensions + axis] - x[second * dimensions + axis];
                        gradient[first * dimensions + axis] += scale * difference;
                        gradient[second * dimensions + axis] -= scale * difference;
                    }
                    if (distance < 1e-9)
                    {
                        // coincident points are pushed apart along the first axis
                        gradient[first * dimensions] -= 2 * stiffness * Math.abs(violation);
                        gradient[second * dimensions] += 2 * stiffness * Math.abs(violation);
                    }
                }
            }
        }

        for (int at = 0; at < centres.length; at++)
        {
            energy += chirality(x, gradient, at);
        }
        for (int at = 0; at < flatNodesCount; at++)
        {
            energy += planarity(x, gradient, flatNodes[at]);
        }

        for (int[] chain : flatChains)
        {
            energy += coplanar(x, gradient, chain);
        }
        for (int[] line : linear)
        {
            energy += straightness(x, gradient, line);
        }

        if (dimensions == 4 && flatten > 0)
        {
            for (int node = 0; node < count; node++)
            {
                double w = x[node * 4 + 3];
                energy += flatten * w * w;
                if (gradient != null)
                {
                    gradient[node * 4 + 3] += 2 * flatten * w;
                }
            }
        }
        return energy;
    }

    private double chirality(double[] x, double[] gradient, int at)
    {
        int[] ligands = centreLigands[at];
        int centre = centres[at];
        double[] first = offset(x, ligands[1], centre);
        double[] second = offset(x, ligands[2], centre);
        double[] third = offset(x, ligands[3], centre);
        double volume = triple(first, second, third);

        double shortfall = centreVolume[at] - centreSign[at] * volume;
        if (shortfall <= 0)
        {
            return 0;
        }
        if (gradient != null)
        {
            double slope = -2 * CHIRAL_WEIGHT * centreSign[at] * shortfall;
            accumulate(gradient, ligands[1], centre, cross(second, third), slope);
            accumulate(gradient, ligands[2], centre, cross(third, first), slope);
            accumulate(gradient, ligands[3], centre, cross(first, second), slope);
        }
        return CHIRAL_WEIGHT * shortfall * shortfall;
    }

    /** Keeps the four atoms of a chain {@code j, i, k, l} in one plane, as when torsion is 0 or 180. */
    private double coplanar(double[] x, double[] gradient, int[] chain)
    {
        int centre = chain[0];
        double[] first = offset(x, chain[1], centre);
        double[] second = offset(x, chain[2], centre);
        double[] third = offset(x, chain[3], centre);
        double volume = triple(first, second, third);
        if (gradient != null)
        {
            double slope = 2 * CHAIN_WEIGHT * volume;
            accumulate(gradient, chain[1], centre, cross(second, third), slope);
            accumulate(gradient, chain[2], centre, cross(third, first), slope);
            accumulate(gradient, chain[3], centre, cross(first, second), slope);
        }
        return CHAIN_WEIGHT * volume * volume;
    }

    /** Keeps the two bonds of a linear atom on one line. */
    private double straightness(double[] x, double[] gradient, int[] line)
    {
        double[] first = offset(x, line[1], line[0]);
        double[] second = offset(x, line[2], line[0]);
        double[] product = cross(first, second);
        if (gradient != null)
        {
            double[] towardsFirst = cross(second, product);
            double[] towardsSecond = cross(product, first);
            for (int axis = 0; axis < 3; axis++)
            {
                gradient[line[1] * dimensions + axis] += 2 * LINEAR_WEIGHT * towardsFirst[axis];
                gradient[line[2] * dimensions + axis] += 2 * LINEAR_WEIGHT * towardsSecond[axis];
                gradient[line[0] * dimensions + axis] -= 2 * LINEAR_WEIGHT * (towardsFirst[axis] + towardsSecond[axis]);
            }
        }
        return LINEAR_WEIGHT * (product[0] * product[0] + product[1] * product[1] + product[2] * product[2]);
    }

    private double planarity(double[] x, double[] gradient, int node)
    {
        int[] around = topology.neighbours[node];
        double[] first = offset(x, around[0], node);
        double[] second = offset(x, around[1], node);
        double[] third = offset(x, around[2], node);
        double volume = triple(first, second, third);
        if (gradient != null)
        {
            double slope = 2 * PLANAR_WEIGHT * volume;
            accumulate(gradient, around[0], node, cross(second, third), slope);
            accumulate(gradient, around[1], node, cross(third, first), slope);
            accumulate(gradient, around[2], node, cross(first, second), slope);
        }
        return PLANAR_WEIGHT * volume * volume;
    }

    private double[] offset(double[] x, int node, int centre)
    {
        return new double[] {
            x[node * dimensions] - x[centre * dimensions],
            x[node * dimensions + 1] - x[centre * dimensions + 1],
            x[node * dimensions + 2] - x[centre * dimensions + 2]
        };
    }

    private void accumulate(double[] gradient, int node, int centre, double[] direction, double slope)
    {
        for (int axis = 0; axis < 3; axis++)
        {
            gradient[node * dimensions + axis] += slope * direction[axis];
            gradient[centre * dimensions + axis] -= slope * direction[axis];
        }
    }

    private static double triple(double[] a, double[] b, double[] c)
    {
        double[] product = cross(b, c);
        return a[0] * product[0] + a[1] * product[1] + a[2] * product[2];
    }

    private static double[] cross(double[] a, double[] b)
    {
        return new double[] {
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }
}
