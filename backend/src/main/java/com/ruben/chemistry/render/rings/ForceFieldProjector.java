package com.ruben.chemistry.render.rings;

import com.ruben.chemistry.render.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Draws a connected set of atoms as a flat view of its three-dimensional shape: it builds the structure in space with a
 * simple force field, looks at it from the best direction, and then moves atoms only as far as that makes more bonds one
 * bond length long without harming the view.
 * <p>
 * The force field holds every bond at one bond length, keeps the two ends of a pair of bonds at a tetrahedral distance,
 * and pushes atoms that are not bonded apart.
 * A bond seen at an angle looks shorter, never longer, so the drawing keeps the structure's shape and some bonds are
 * foreshortened. The best view is the one that keeps atoms apart, then has the fewest crossings, then the most bonds
 * of one bond length.
 */
final class ForceFieldProjector
{
    /** Atoms that are not bonded are pushed apart in space when closer than this. */
    private static final double REPULSION_RADIUS = 1.6;

    /** How far apart the ends of two bonds at a tetrahedral atom are, in bond lengths. */
    private static final double TETRAHEDRAL_DISTANCE = 1.633;

    /** How firmly such atoms are held at that distance, as angles in a strained structure often cannot be tetrahedral. */
    private static final double ANGLE_WEIGHT = 1;

    /** How weakly atoms three bonds apart are pulled together, a stand-in for a torsion term that prefers eclipsed bonds. */
    private static final double TORSION_WEIGHT = 0.05;

    /** how many structures are built from random starts, times the effort; those with the same energy count as one */
    private static final int EMBEDDINGS = 12;
    /** how many of the lowest-energy structures are looked at */
    private static final int KEPT_EMBEDDINGS = 2;
    /** how many directions each structure is looked at from, times the effort, spread evenly over a sphere */
    private static final int VIEWS = 600;
    /** how many of the best views of each structure have their bond lengths polished */
    private static final int POLISHED_VIEWS = 6;

    /** How far apart atoms have to be, in bond lengths, for a view of a structure to count as keeping them apart. */
    static final double VIEW_CLEARANCE = 0.35;

    /** How short a bond may look, in bond lengths, before the view counts as colliding. */
    private static final double MIN_BOND = 0.35;

    /** How far a bond's length may be from one bond length and still count as one. */
    private static final double UNIT_TOLERANCE = 0.02;

    /** how much two clearances have to differ by to count as different */
    private static final double CLEARANCE_RESOLUTION = 1e-3;

    /** How far, in bond lengths, the sorted distances between atoms of two drawings may differ for them to count as one drawing. */
    private static final double SAME_DRAWING_TOLERANCE = 0.05;

    /** Two bonds at an atom whose angle has a cosine below this look like a straight line. */
    private static final double STRAIGHT_COSINE = -0.94;

    /** fixed, so that a molecule is always drawn the same way */
    private static final long SEED = 0xCA6EL;

    private final int effort;
    private final int count;
    private final int[][] bonds;
    private final boolean[][] joined;
    private final boolean[][] angleMates;
    private final boolean[][] torsionMates;
    private final List<int[]> angles = new ArrayList<>();

    /**
     * @param count how many atoms there are
     * @param bonds the bonds, as pairs of atom indices
     */
    ForceFieldProjector(int count, int[][] bonds)
    {
        this(count, bonds, 1);
    }

    /**
     * @param count how many atoms there are
     * @param bonds the bonds, as pairs of atom indices
     * @param effort how many times the usual number of structures and views to search
     */
    ForceFieldProjector(int count, int[][] bonds, int effort)
    {
        this.effort = effort;
        this.count = count;
        this.bonds = bonds;
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
                        angles.add(new int[] {centre, first, second});
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
     * @return a drawing, as x and y of each atom in turn, or {@code null} if the system could not be built in space
     */
    double[] draw()
    {
        List<double[]> drawings = draws(1);
        return drawings.isEmpty() ? null : drawings.getFirst();
    }

    /**
     * Polishes the usual shortlist of views, and widens it only while that leaves fewer different drawings than wanted.
     *
     * @param wanted how many different drawings are wanted
     * @return the different drawings found, best first, as x and y of each atom in turn; empty if the system could not be built in space
     */
    List<double[]> draws(int wanted)
    {
        // polish the usual shortlist of views, and widen it only while that leaves fewer different drawings than wanted
        List<double[]> structures = structures();
        List<View> polished = new ArrayList<>();
        int[] done = new int[structures.size()];
        int keptStructures = Math.min(KEPT_EMBEDDINGS, structures.size());
        int viewsEach = POLISHED_VIEWS;
        List<View> distinct;
        while (true)
        {
            boolean grew = false;
            for (int rank = 0; rank < keptStructures; rank++)
            {
                List<double[]> views = bestViews(structures.get(rank), viewsEach);
                for (int index = done[rank]; index < views.size(); index++)
                {
                    polished.add(measure(orient(polish(views.get(index)))));
                    grew = true;
                }
                done[rank] = Math.max(done[rank], views.size());
            }
            distinct = distinctViews(polished);
            if (distinct.size() >= wanted || !grew && keptStructures == structures.size())
            {
                break;
            }
            viewsEach *= 2;
            keptStructures = Math.min(structures.size(), keptStructures * 2);
        }
        return distinct.stream().map(View::drawing).toList();
    }

    /**
     * @param views views of the system
     * @return the views that look different from each other, best first
     */
    private List<View> distinctViews(List<View> views)
    {
        List<View> found = new ArrayList<>(views);
        View best = null;
        for (View view : views)
        {
            if (best == null || ORDER.compare(view, best) < 0)
            {
                best = view;
            }
        }
        found.sort(ORDER);
        if (best != null)
        {
            found.remove(best);
            found.addFirst(best);
        }

        List<View> distinct = new ArrayList<>();
        for (View view : found)
        {
            if (distinct.stream().noneMatch(kept -> looksTheSame(kept.drawing, view.drawing)))
            {
                distinct.add(view);
            }
        }
        return distinct;
    }

    /**
     * @param first one drawing
     * @param second another drawing
     * @return whether they differ by no more than a turn, a reflection or a relabelling of equivalent atoms,
     *     judged by comparing the sorted distances between every pair of atoms
     */
    private boolean looksTheSame(double[] first, double[] second)
    {
        double[] firstDistances = sortedDistances(first);
        double[] secondDistances = sortedDistances(second);
        for (int index = 0; index < firstDistances.length; index++)
        {
            if (Math.abs(firstDistances[index] - secondDistances[index]) > SAME_DRAWING_TOLERANCE)
            {
                return false;
            }
        }
        return true;
    }

    private double[] sortedDistances(double[] drawing)
    {
        double[] distances = new double[count * (count - 1) / 2];
        int next = 0;
        for (int first = 0; first < count; first++)
        {
            for (int second = first + 1; second < count; second++)
            {
                distances[next++] = Math.hypot(drawing[first * 2] - drawing[second * 2], drawing[first * 2 + 1] - drawing[second * 2 + 1]);
            }
        }
        Arrays.sort(distances);
        return distances;
    }

    /**
     * @return the different ways the system can be built in space, as x, y and z of each atom in turn, lowest energy first
     */
    List<double[]> structures()
    {
        Random random = new Random(SEED);
        List<double[]> structures = new ArrayList<>();
        List<Double> energies = new ArrayList<>();
        for (int attempt = 0; attempt < EMBEDDINGS * effort; attempt++)
        {
            double[] structure = embed(random);
            if (structure == null)
            {
                continue;
            }
            double energy = repulsion(structure, null);
            // a structure with the same energy as one already found is taken to be the same structure
            boolean known = false;
            for (double other : energies)
            {
                known |= Math.abs(other - energy) < 1e-3;
            }
            if (!known)
            {
                structures.add(structure);
                energies.add(energy);
            }
        }
        // lowest energy first
        Integer[] order = new Integer[structures.size()];
        Arrays.setAll(order, index -> index);
        Arrays.sort(order, Comparator.comparingDouble(energies::get));
        List<double[]> sorted = new ArrayList<>();
        for (int index : order)
        {
            sorted.add(structures.get(index));
        }
        return sorted;
    }

    /**
     * How good a view is.
     *
     * @param drawing x and y of each atom in turn
     * @param clearance how far apart the closest atoms are, or bonds short, no more than the distance that counts as clear
     * @param crossings how many pairs of bonds cross
     * @param unit how many bonds are one bond length long
     * @param deviation the sum of squares of how far each bond is from one bond length
     */
    private record View(double[] drawing, double clearance, int crossings, int straight, int unit, double deviation)
    {
        boolean isClear()
        {
            return clearance >= VIEW_CLEARANCE - CLEARANCE_RESOLUTION;
        }
    }

    // views are ranked by whether atoms are kept clear (and, if not, how nearly), then by crossings, near-straight angles,
    // the most bonds of one bond length and finally the smallest total error in bond lengths
    private static final Comparator<View> ORDER = Comparator
        .comparingInt((View view) -> view.isClear() ? 0 : 1)
        .thenComparingLong(view -> view.isClear() ? 0 : -Math.round(view.clearance / CLEARANCE_RESOLUTION))
        .thenComparingInt(View::crossings)
        .thenComparingInt(View::straight)
        .thenComparingInt(view -> -view.unit)
        .thenComparingLong(view -> Math.round(view.deviation * 1e6));

    private View measure(double[] drawing)
    {
        Point[] points = new Point[count];
        for (int atom = 0; atom < count; atom++)
        {
            points[atom] = new Point(drawing[atom * 2], drawing[atom * 2 + 1]);
        }
        double shortest = Double.POSITIVE_INFINITY;
        double deviation = 0;
        int unit = 0;
        for (int[] bond : bonds)
        {
            double length = points[bond[0]].minus(points[bond[1]]).length();
            shortest = Math.min(shortest, length);
            deviation += (length - 1) * (length - 1);
            if (Math.abs(length - 1) < UNIT_TOLERANCE)
            {
                unit++;
            }
        }
        // a very short bond is as bad as atoms that collide, as it looks like one atom
        double clearance = Math.min(VIEW_CLEARANCE, LayoutQuality.minimumAtomDistance(points, bonds));
        clearance = Math.min(clearance, shortest * VIEW_CLEARANCE / MIN_BOND);
        return new View(drawing, clearance, LayoutQuality.crossings(points, bonds), straightAngles(points), unit, deviation);
    }

    /**
     * @return how many pairs of bonds at an atom are close to a straight line, which makes the atom look as if it were
     *     not a bend at all
     */
    private int straightAngles(Point[] points)
    {
        int straight = 0;
        for (int[] angle : angles)
        {
            Point first = points[angle[1]].minus(points[angle[0]]);
            Point second = points[angle[2]].minus(points[angle[0]]);
            double length = first.length() * second.length();
            if (length > 1e-12 && (first.x() * second.x() + first.y() * second.y()) / length < STRAIGHT_COSINE)
            {
                straight++;
            }
        }
        return straight;
    }

    /**
     * @return whether each pair of bonds at an atom turns the same way in {@code drawing} as in {@code reference}
     */
    private boolean turnsAsBefore(double[] reference, double[] drawing)
    {
        for (int[] angle : angles)
        {
            // a pair of bonds turns the same way while the sign of their cross product is unchanged
            double before = turn(reference, angle);
            double after = turn(drawing, angle);
            if (before * after < 0)
            {
                return false;
            }
        }
        return true;
    }

    private static double turn(double[] drawing, int[] angle)
    {
        double ax = drawing[angle[1] * 2] - drawing[angle[0] * 2];
        double ay = drawing[angle[1] * 2 + 1] - drawing[angle[0] * 2 + 1];
        double bx = drawing[angle[2] * 2] - drawing[angle[0] * 2];
        double by = drawing[angle[2] * 2 + 1] - drawing[angle[0] * 2 + 1];
        return ax * by - ay * bx;
    }

    /**
     * @param structure the system in space
     * @return the best few flat views of it, before any polishing
     */
    private List<double[]> bestViews(double[] structure, int limit)
    {
        List<View> found = new ArrayList<>();
        int views = VIEWS * effort;
        // view directions run along a fibonacci spiral, which spreads them evenly over a sphere
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int view = 0; view < views; view++)
        {
            double z = 1 - 2 * (view + 0.5) / views;
            double radius = Math.sqrt(1 - z * z);
            double[] direction = {radius * Math.cos(view * golden), radius * Math.sin(view * golden), z};
            // right and up span the plane looked at; the helper is any direction that is not along the view
            double[] helper = Math.abs(z) > 0.9 ? new double[] {1, 0, 0} : new double[] {0, 0, 1};
            double[] right = normalised(cross(direction, helper));
            double[] up = cross(direction, right);

            double[] drawing = new double[count * 2];
            for (int atom = 0; atom < count; atom++)
            {
                double[] at = {structure[atom * 3], structure[atom * 3 + 1], structure[atom * 3 + 2]};
                drawing[atom * 2] = dot(at, right);
                drawing[atom * 2 + 1] = dot(at, up);
            }
            found.add(measure(drawing));
        }
        found.sort(ORDER);
        List<double[]> best = new ArrayList<>();
        for (int index = 0; index < Math.min(limit, found.size()); index++)
        {
            best.add(found.get(index).drawing);
        }
        return best;
    }

    /**
     * Moves atoms to bring bonds nearer one bond length, as long as atoms do not get closer than they are, or than they
     * have to be, and no more bonds cross.
     */
    private double[] polish(double[] drawing)
    {
        double[] x = drawing.clone();
        View current = measure(x);
        // atoms may not get closer than they already are, unless they are clear anyway
        double floor = Math.min(VIEW_CLEARANCE, current.clearance) - 1e-9;
        double step = 0.1;
        double[] gradient = new double[x.length];
        // gradient descent on the squared errors in bond lengths, taking only steps that harm nothing the view had
        for (int iteration = 0; iteration < 1500 && current.deviation > 1e-14; iteration++)
        {
            Arrays.fill(gradient, 0);
            for (int[] bond : bonds)
            {
                int first = bond[0] * 2;
                int second = bond[1] * 2;
                double dx = x[first] - x[second];
                double dy = x[first + 1] - x[second + 1];
                double length = Math.sqrt(dx * dx + dy * dy);
                if (length < 1e-12)
                {
                    continue;
                }
                double scale = 2 * (length - 1) / length;
                gradient[first] += scale * dx;
                gradient[first + 1] += scale * dy;
                gradient[second] -= scale * dx;
                gradient[second + 1] -= scale * dy;
            }

            boolean moved = false;
            for (int attempt = 0; attempt < 25; attempt++)
            {
                double[] next = x.clone();
                for (int at = 0; at < next.length; at++)
                {
                    next[at] -= step * gradient[at];
                }
                View view = measure(next);
                if (view.clearance >= floor && view.crossings <= current.crossings && view.straight <= current.straight
                    && view.deviation < current.deviation - 1e-12 && turnsAsBefore(drawing, next))
                {
                    x = next;
                    current = view;
                    step *= 1.5;
                    moved = true;
                    break;
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
     * @return the drawing, turned about its middle so that its longest direction runs left to right
     */
    private double[] orient(double[] drawing)
    {
        double meanX = 0;
        double meanY = 0;
        for (int atom = 0; atom < count; atom++)
        {
            meanX += drawing[atom * 2] / count;
            meanY += drawing[atom * 2 + 1] / count;
        }
        double xx = 0;
        double yy = 0;
        double xy = 0;
        for (int atom = 0; atom < count; atom++)
        {
            double dx = drawing[atom * 2] - meanX;
            double dy = drawing[atom * 2 + 1] - meanY;
            xx += dx * dx;
            yy += dy * dy;
            xy += dx * dy;
        }
        // the angle of the principal axis of the atoms' spread, from their second moments
        double turn = -0.5 * Math.atan2(2 * xy, xx - yy);
        double[] turned = new double[drawing.length];
        for (int atom = 0; atom < count; atom++)
        {
            double dx = drawing[atom * 2] - meanX;
            double dy = drawing[atom * 2 + 1] - meanY;
            turned[atom * 2] = dx * Math.cos(turn) - dy * Math.sin(turn);
            turned[atom * 2 + 1] = dx * Math.sin(turn) + dy * Math.cos(turn);
        }
        return turned;
    }

    /**
     * @return the system built in space with every bond one bond length long and atoms pushed apart, or {@code null}
     */
    private double[] embed(Random random)
    {
        double[] x = new double[count * 3];
        // start from random positions in a box that grows with the cube root of the number of atoms
        double spread = Math.cbrt(count);
        for (int at = 0; at < x.length; at++)
        {
            x[at] = (random.nextDouble() - 0.5) * 2 * spread;
        }
        if (!project(x))
        {
            return null;
        }

        // descend the energy, restoring the bond lengths after each step and keeping the step only if it helped
        double energy = repulsion(x, null);
        double step = 0.05;
        double[] gradient = new double[x.length];
        for (int iteration = 0; iteration < 300; iteration++)
        {
            repulsion(x, gradient);
            boolean moved = false;
            for (int attempt = 0; attempt < 20; attempt++)
            {
                double[] next = x.clone();
                for (int at = 0; at < next.length; at++)
                {
                    next[at] -= step * gradient[at];
                }
                if (project(next))
                {
                    double nextEnergy = repulsion(next, null);
                    if (nextEnergy < energy - 1e-9)
                    {
                        x = next;
                        energy = nextEnergy;
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
     * @param structure the system in space
     * @return its strain: how far its angles are from tetrahedral, plus the overlap between atoms that are not bonded
     */
    double energyOf(double[] structure)
    {
        return repulsion(structure, null);
    }

    private double repulsion(double[] x, double[] gradient)
    {
        if (gradient != null)
        {
            Arrays.fill(gradient, 0);
        }
        double energy = 0;
        for (int first = 0; first < count; first++)
        {
            for (int second = first + 1; second < count; second++)
            {
                if (joined[first][second])
                {
                    continue;
                }
                double[] offset = offset(x, first, second);
                double distance = Math.sqrt(dot(offset, offset));
                // torsion mates are pulled gently together
                if (torsionMates[first][second])
                {
                    energy += TORSION_WEIGHT * distance * distance;
                    if (gradient != null)
                    {
                        for (int axis = 0; axis < 3; axis++)
                        {
                            double part = 2 * TORSION_WEIGHT * offset[axis];
                            gradient[first * 3 + axis] += part;
                            gradient[second * 3 + axis] -= part;
                        }
                    }
                }
                // angle mates are held near the tetrahedral distance, and are not pushed apart as well
                if (angleMates[first][second])
                {
                    double off = distance - TETRAHEDRAL_DISTANCE;
                    energy += ANGLE_WEIGHT * off * off;
                    if (gradient != null && distance > 1e-12)
                    {
                        for (int axis = 0; axis < 3; axis++)
                        {
                            double part = 2 * ANGLE_WEIGHT * off * offset[axis] / distance;
                            gradient[first * 3 + axis] += part;
                            gradient[second * 3 + axis] -= part;
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
                    energy += REPULSION_RADIUS * REPULSION_RADIUS;
                    continue;
                }
                double overlap = REPULSION_RADIUS - distance;
                energy += overlap * overlap;
                if (gradient != null)
                {
                    for (int axis = 0; axis < 3; axis++)
                    {
                        double part = -2 * overlap * offset[axis] / distance;
                        gradient[first * 3 + axis] += part;
                        gradient[second * 3 + axis] -= part;
                    }
                }
            }
        }
        return energy;
    }

    /**
     * Moves a structure, as little as it can, until every bond is one bond length long.
     *
     * @param x the structure, which is changed
     * @return whether it got there
     */
    private boolean project(double[] x)
    {
        // gauss-newton on the errors in bond lengths, damped by a ridge: each round solves (j.jt + ridge) m = r for the
        // multipliers m, then moves atoms by -jt.m, the smallest move that would cancel the errors if they were linear
        int size = bonds.length;
        double ridge = 1e-3;
        double[] residual = residual(x);
        double norm = dot(residual, residual);
        for (int round = 0; round < 200; round++)
        {
            if (worst(residual) < 1e-12)
            {
                return true;
            }

            double[][] jacobian = new double[size][x.length];
            for (int bond = 0; bond < size; bond++)
            {
                double[] offset = offset(x, bonds[bond][0], bonds[bond][1]);
                double length = Math.sqrt(dot(offset, offset));
                for (int axis = 0; axis < 3; axis++)
                {
                    double part = length < 1e-12 ? 0 : offset[axis] / length;
                    jacobian[bond][bonds[bond][0] * 3 + axis] = part;
                    jacobian[bond][bonds[bond][1] * 3 + axis] = -part;
                }
            }
            // j.jt plus the ridge on its diagonal, with the errors as the last column
            double[][] gram = new double[size][size + 1];
            for (int row = 0; row < size; row++)
            {
                for (int column = 0; column < size; column++)
                {
                    gram[row][column] = dot(jacobian[row], jacobian[column]) + (row == column ? ridge : 0);
                }
                gram[row][size] = residual[row];
            }
            double[] multipliers = eliminate(gram);
            if (multipliers == null)
            {
                return false;
            }

            double[] next = x.clone();
            for (int bond = 0; bond < size; bond++)
            {
                for (int at = 0; at < next.length; at++)
                {
                    next[at] -= jacobian[bond][at] * multipliers[bond];
                }
            }
            double[] nextResidual = residual(next);
            double nextNorm = dot(nextResidual, nextResidual);
            // a step that helped is kept and the damping eased; one that did not is dropped and the damping raised
            if (nextNorm < norm)
            {
                System.arraycopy(next, 0, x, 0, x.length);
                residual = nextResidual;
                norm = nextNorm;
                ridge = Math.max(ridge / 3, 1e-12);
            }
            else
            {
                ridge *= 5;
                if (ridge > 1e12)
                {
                    return false;
                }
            }
        }
        return worst(residual) < 1e-12;
    }

    private double[] residual(double[] x)
    {
        double[] residual = new double[bonds.length];
        for (int bond = 0; bond < bonds.length; bond++)
        {
            double[] offset = offset(x, bonds[bond][0], bonds[bond][1]);
            residual[bond] = Math.sqrt(dot(offset, offset)) - 1;
        }
        return residual;
    }

    private static double worst(double[] values)
    {
        double worst = 0;
        for (double value : values)
        {
            worst = Math.max(worst, Math.abs(value));
        }
        return worst;
    }

    private static double[] offset(double[] x, int first, int second)
    {
        return new double[] {
            x[first * 3] - x[second * 3],
            x[first * 3 + 1] - x[second * 3 + 1],
            x[first * 3 + 2] - x[second * 3 + 2]};
    }

    /**
     * Solves a system of linear equations given as rows of an augmented matrix.
     *
     * @return the solution, or {@code null} if the system is singular
     */
    private static double[] eliminate(double[][] matrix)
    {
        int size = matrix.length;
        for (int column = 0; column < size; column++)
        {
            // take the row with the largest entry in this column as the pivot, which keeps the division stable
            int pivot = column;
            for (int row = column + 1; row < size; row++)
            {
                if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column]))
                {
                    pivot = row;
                }
            }
            if (Math.abs(matrix[pivot][column]) < 1e-14)
            {
                return null;
            }
            double[] swap = matrix[column];
            matrix[column] = matrix[pivot];
            matrix[pivot] = swap;
            for (int row = column + 1; row < size; row++)
            {
                double factor = matrix[row][column] / matrix[column][column];
                for (int at = column; at <= size; at++)
                {
                    matrix[row][at] -= factor * matrix[column][at];
                }
            }
        }
        double[] solution = new double[size];
        for (int row = size - 1; row >= 0; row--)
        {
            double sum = matrix[row][size];
            for (int column = row + 1; column < size; column++)
            {
                sum -= matrix[row][column] * solution[column];
            }
            solution[row] = sum / matrix[row][row];
        }
        return solution;
    }

    private static double dot(double[] first, double[] second)
    {
        double sum = 0;
        for (int at = 0; at < first.length; at++)
        {
            sum += first[at] * second[at];
        }
        return sum;
    }

    private static double[] cross(double[] a, double[] b)
    {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalised(double[] vector)
    {
        double length = Math.sqrt(dot(vector, vector));
        return new double[] {vector[0] / length, vector[1] / length, vector[2] / length};
    }
}
