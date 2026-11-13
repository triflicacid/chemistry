package com.ruben.chemistry.render.rings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Builds a connected set of atoms in space with a simple force field, which holds every bond at one bond length, keeps
 * the two ends of a pair of bonds at a tetrahedral distance, and pushes atoms that are not bonded apart.
 * <p>
 * The search starts from random positions, so it is run several times over and the structures it settles on that differ
 * in strain are kept as the different shapes the system can take.
 */
final class StructureEmbedder
{
    /** Atoms that are not bonded are pushed apart in space when closer than this. */
    private static final double REPULSION_RADIUS = 1.6;

    /** How far apart the ends of two bonds at a tetrahedral atom are, in bond lengths. */
    private static final double TETRAHEDRAL_DISTANCE = 1.633;

    /** How firmly such atoms are held at that distance, as angles in a strained structure often cannot be tetrahedral. */
    private static final double ANGLE_WEIGHT = 1;

    /** How weakly atoms three bonds apart are pulled together, a stand-in for a torsion term that prefers eclipsed bonds. */
    private static final double TORSION_WEIGHT = 0.05;

    /** how many structures are built from random starts, times the effort; those with the same strain count as one */
    private static final int EMBEDDINGS = 12;

    /** How much two structures' strain has to differ by for them to count as different shapes. */
    private static final double SAME_SHAPE_TOLERANCE = 1e-3;

    /** how many rounds the strain is descended for */
    private static final int DESCENT_ROUNDS = 300;

    /** how many shorter steps are tried before a round gives up */
    private static final int STEP_ATTEMPTS = 20;

    /** fixed, so that a molecule is always built the same way */
    private static final long SEED = 0xCA6EL;

    /** How many components a point in space has. */
    private static final int DIMENSIONS = 3;

    private final int effort;
    private final int count;
    private final boolean[][] joined;
    private final boolean[][] angleMates;
    private final boolean[][] torsionMates;
    private final BondConstraints constraints;

    /**
     * @param count how many atoms there are
     * @param bonds the bonds, as pairs of atom indices
     * @param effort how many times the usual number of structures to search
     */
    StructureEmbedder(int count, int[][] bonds, int effort)
    {
        this.effort = effort;
        this.count = count;
        constraints = new BondConstraints(bonds, DIMENSIONS);
        joined = new boolean[count][count];
        angleMates = new boolean[count][count];
        for (int[] bond : bonds)
        {
            joined[bond[0]][bond[1]] = true;
            joined[bond[1]][bond[0]] = true;
        }
        // the two ends of a pair of bonds that share an atom are angle mates, held at a tetrahedral distance
        for (int centre = 0; centre < count; centre++)
        {
            for (int first = 0; first < count; first++)
            {
                for (int second = first + 1; second < count; second++)
                {
                    if (joined[centre][first] && joined[centre][second])
                    {
                        angleMates[first][second] = !joined[first][second];
                        angleMates[second][first] = !joined[first][second];
                    }
                }
            }
        }

        // the ends of a chain of three bonds are torsion mates, unless they are angle mates too
        torsionMates = new boolean[count][count];
        for (int[] middle : bonds)
        {
            for (int first = 0; first < count; first++)
            {
                for (int last = 0; last < count; last++)
                {
                    if (first != last && first != middle[1] && last != middle[0] && joined[first][middle[0]]
                        && joined[middle[1]][last] && !joined[first][last] && !angleMates[first][last])
                    {
                        torsionMates[first][last] = true;
                        torsionMates[last][first] = true;
                    }
                }
            }
        }
    }

    /**
     * @return the different shapes the system can take in space, as x, y and z of each atom in turn, least strained first
     */
    List<double[]> structures()
    {
        Random random = new Random(SEED);
        List<double[]> structures = new ArrayList<>();
        List<Double> strains = new ArrayList<>();
        for (int attempt = 0; attempt < EMBEDDINGS * effort; attempt++)
        {
            double[] structure = embed(random);
            if (structure == null)
            {
                continue;
            }
            double strain = strainOf(structure);
            boolean known = false;
            for (double other : strains)
            {
                known |= Math.abs(other - strain) < SAME_SHAPE_TOLERANCE;
            }
            if (!known)
            {
                structures.add(structure);
                strains.add(strain);
            }
        }
        Integer[] order = new Integer[structures.size()];
        Arrays.setAll(order, index -> index);
        Arrays.sort(order, Comparator.comparingDouble(strains::get));
        List<double[]> sorted = new ArrayList<>();
        for (int index : order)
        {
            sorted.add(structures.get(index));
        }
        return sorted;
    }

    /**
     * @param structure the system in space
     * @return its strain: how far its angles are from tetrahedral, plus the overlap between atoms that are not bonded
     */
    double strainOf(double[] structure)
    {
        return strain(structure, null);
    }

    /**
     * @param random where the starting positions come from
     * @return the system built in space with every bond one bond length long and atoms pushed apart, or {@code null}
     *     if the bonds could not all be made one bond length long
     */
    private double[] embed(Random random)
    {
        double[] x = new double[count * DIMENSIONS];
        // start from random positions in a box that grows with the cube root of the number of atoms
        double spread = Math.cbrt(count);
        for (int at = 0; at < x.length; at++)
        {
            x[at] = (random.nextDouble() - 0.5) * 2 * spread;
        }
        if (!constraints.restore(x))
        {
            return null;
        }

        // descend the strain, restoring the bond lengths after each step and keeping the step only if it helped
        double strain = strainOf(x);
        double step = 0.05;
        double[] gradient = new double[x.length];
        for (int round = 0; round < DESCENT_ROUNDS; round++)
        {
            strain(x, gradient);
            boolean moved = false;
            for (int attempt = 0; attempt < STEP_ATTEMPTS; attempt++)
            {
                double[] next = x.clone();
                for (int at = 0; at < next.length; at++)
                {
                    next[at] -= step * gradient[at];
                }
                if (constraints.restore(next))
                {
                    double nextStrain = strainOf(next);
                    if (nextStrain < strain - 1e-9)
                    {
                        x = next;
                        strain = nextStrain;
                        step *= 1.3;
                        moved = true;
                        break;
                    }
                }
                step *= 0.5;
            }
            if (!moved)
            {
                break;
            }
        }
        return x;
    }

    /**
     * @param x the system in space
     * @param gradient where to put which way the strain rises, or {@code null} to skip that
     * @return the strain
     */
    private double strain(double[] x, double[] gradient)
    {
        if (gradient != null)
        {
            Arrays.fill(gradient, 0);
        }
        double strain = 0;
        for (int first = 0; first < count; first++)
        {
            for (int second = first + 1; second < count; second++)
            {
                if (joined[first][second])
                {
                    continue;
                }
                double[] offset = Vectors.offset(x, DIMENSIONS, first, second);
                double distance = Math.sqrt(Vectors.dot(offset, offset));
                // torsion mates are pulled gently together
                if (torsionMates[first][second])
                {
                    strain += TORSION_WEIGHT * distance * distance;
                    if (gradient != null)
                    {
                        for (int axis = 0; axis < DIMENSIONS; axis++)
                        {
                            double part = 2 * TORSION_WEIGHT * offset[axis];
                            gradient[first * DIMENSIONS + axis] += part;
                            gradient[second * DIMENSIONS + axis] -= part;
                        }
                    }
                }
                // angle mates are held near the tetrahedral distance, and are not pushed apart as well
                if (angleMates[first][second])
                {
                    double off = distance - TETRAHEDRAL_DISTANCE;
                    strain += ANGLE_WEIGHT * off * off;
                    if (gradient != null && distance > 1e-12)
                    {
                        for (int axis = 0; axis < DIMENSIONS; axis++)
                        {
                            double part = 2 * ANGLE_WEIGHT * off * offset[axis] / distance;
                            gradient[first * DIMENSIONS + axis] += part;
                            gradient[second * DIMENSIONS + axis] -= part;
                        }
                    }
                    continue;
                }
                // every other pair only pushes apart, and only when closer than the radius
                if (distance >= REPULSION_RADIUS)
                {
                    continue;
                }
                // atoms on top of each other have no direction to be pushed in, so count the greatest overlap
                if (distance < 1e-12)
                {
                    strain += REPULSION_RADIUS * REPULSION_RADIUS;
                    continue;
                }
                double overlap = REPULSION_RADIUS - distance;
                strain += overlap * overlap;
                if (gradient != null)
                {
                    for (int axis = 0; axis < DIMENSIONS; axis++)
                    {
                        double part = -2 * overlap * offset[axis] / distance;
                        gradient[first * DIMENSIONS + axis] += part;
                        gradient[second * DIMENSIONS + axis] -= part;
                    }
                }
            }
        }
        return strain;
    }
}
