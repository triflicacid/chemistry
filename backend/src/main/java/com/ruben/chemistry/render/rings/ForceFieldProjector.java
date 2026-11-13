package com.ruben.chemistry.render.rings;

import com.ruben.chemistry.render.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Draws a connected set of atoms as a flat view of its three-dimensional shape: it builds the structure in space with a
 * simple force field and looks at it from the best direction. Atoms are never moved within the drawing, so a drawing is
 * always a true view of the structure and never a reshaped one; only the view as a whole is scaled, so that its
 * middling bond is one bond length.
 * <p>
 * The force field holds every bond at one bond length, keeps the two ends of a pair of bonds at a tetrahedral distance,
 * and pushes atoms that are not bonded apart.
 * A bond seen at an angle looks shorter, so bonds of one length in space are drawn different lengths. The best view is
 * the one that keeps atoms apart and then foreshortens most evenly, as a bond drawn far shorter than the bonds around
 * it reads as a kink rather than as a bond pointing away from the reader.
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
    /** how many of the best views of each structure are kept to begin with */
    private static final int SHORTLIST_VIEWS = 6;

    /**
     * How far apart atoms have to be, in bond lengths, for a view of a structure to count as keeping them apart. Views
     * that keep them this far apart are told apart by how evenly they foreshorten rather than by room to spare, so this
     * is the room a drawing needs rather than a distance to maximise.
     */
    static final double VIEW_CLEARANCE = 0.45;

    /** How short a bond may look, in bond lengths, before the view counts as colliding. */
    private static final double MIN_BOND = 0.35;

    /** How much two views' evenness has to differ by for one of them to count as the more even. */
    private static final double EVENNESS_RESOLUTION = 0.05;

    /** how much two clearances have to differ by to count as different */
    private static final double CLEARANCE_RESOLUTION = 1e-3;

    /** How far, in bond lengths, the sorted distances between atoms of two drawings may differ for them to count as one drawing. */
    private static final double SAME_DRAWING_TOLERANCE = 0.05;

    /** Two bonds at an atom whose angle has a cosine below this look like a straight line. */
    private static final double STRAIGHT_COSINE = -0.94;

    /** Two bonds at an atom whose angle has a cosine above this are drawn one on top of the other. */
    private static final double SHARP_COSINE = 0.94;

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
     * Takes the usual shortlist of views, and widens it only while that leaves fewer different drawings than wanted.
     * How many views there are to draw from is set by the effort alone, so asking for more drawings never changes which
     * drawing is best; it only reaches further down the same ranking, and returns fewer drawings than wanted once the
     * structure has no more views that look different from those already found.
     *
     * @param wanted how many different drawings are wanted
     * @return the different drawings found, best first, as x and y of each atom in turn; empty if the system could not be built in space
     */
    List<double[]> draws(int wanted)
    {
        List<double[]> structures = structures();
        List<View> found = new ArrayList<>();
        int[] done = new int[structures.size()];
        int keptStructures = Math.min(KEPT_EMBEDDINGS, structures.size());
        int viewsEach = SHORTLIST_VIEWS;
        List<View> distinct;
        while (true)
        {
            boolean grew = false;
            for (int rank = 0; rank < keptStructures; rank++)
            {
                List<double[]> views = bestViews(structures.get(rank), viewsEach);
                for (int index = done[rank]; index < views.size(); index++)
                {
                    found.add(measure(orient(views.get(index))));
                    grew = true;
                }
                done[rank] = Math.max(done[rank], views.size());
            }
            distinct = distinctViews(found);
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
     * @param drawing x and y of each atom in turn, scaled so that the middling bond is one bond length
     * @param clearance how far apart the closest atoms are, or bonds short, no more than the distance that counts as clear
     * @param crossings how many pairs of bonds cross
     * @param hidden how many pairs of bonds at an atom are drawn as one line
     * @param shortest how long the shortest bond is drawn
     * @param longest how long the longest bond is drawn
     */
    private record View(double[] drawing, double clearance, int crossings, int hidden, double shortest, double longest)
    {
        boolean isClear()
        {
            return clearance >= VIEW_CLEARANCE - CLEARANCE_RESOLUTION;
        }

        /**
         * How evenly the view foreshortens: 1 when every bond is drawn the same length, falling towards 0 as one bond
         * is drawn far shorter than another. It is a ratio, so it says the same thing whatever size the view is drawn
         * at, which the lengths on their own would not.
         *
         * @return the evenness
         */
        double evenness()
        {
            return longest < 1e-12 ? 0 : shortest / longest;
        }
    }

    // views are ranked by whether atoms are kept clear (and, if not, how nearly), then by how evenly they foreshorten,
    // and only between views as even as each other by crossings and then by pairs of bonds drawn as one line.
    // Crossings come second to evenness because a bridge drawn across a ring is how a cage is read, whereas a bond
    // drawn far shorter than the bonds around it is never read as a bond at all.
    private static final Comparator<View> ORDER = Comparator
        .comparingInt((View view) -> view.isClear() ? 0 : 1)
        .thenComparingLong(view -> view.isClear() ? 0 : -Math.round(view.clearance / CLEARANCE_RESOLUTION))
        .thenComparingLong(view -> -Math.round(view.evenness() / EVENNESS_RESOLUTION))
        .thenComparingInt(View::crossings)
        .thenComparingInt(View::hidden)
        .thenComparingLong(view -> -Math.round(view.evenness() * 1e6));

    private View measure(double[] drawing)
    {
        // measured at the size it will be drawn, so that views of the same structure are judged against each other
        double[] scaled = toMedianBond(drawing);
        Point[] points = new Point[count];
        for (int atom = 0; atom < count; atom++)
        {
            points[atom] = new Point(scaled[atom * 2], scaled[atom * 2 + 1]);
        }
        double shortest = Double.POSITIVE_INFINITY;
        double longest = 0;
        for (int[] bond : bonds)
        {
            double length = points[bond[0]].minus(points[bond[1]]).length();
            shortest = Math.min(shortest, length);
            longest = Math.max(longest, length);
        }
        // a very short bond is as bad as atoms that collide, as it looks like one atom
        double clearance = Math.min(VIEW_CLEARANCE, LayoutQuality.minimumAtomDistance(points, bonds));
        clearance = Math.min(clearance, shortest * VIEW_CLEARANCE / MIN_BOND);
        return new View(scaled, clearance, LayoutQuality.crossings(points, bonds), hiddenBends(points), shortest, longest);
    }

    /**
     * Scales a drawing about the origin until its middling bond is one bond length. Scaling leaves the shape untouched,
     * so this is not a reshaping; it only puts the view at the size the rest of the molecule is drawn at, since every
     * bond of a view is foreshortened and the view would otherwise come out small.
     *
     * @param drawing x and y of each atom in turn
     * @return the scaled drawing, or the drawing itself if it has collapsed to a point
     */
    private double[] toMedianBond(double[] drawing)
    {
        double[] lengths = new double[bonds.length];
        for (int bond = 0; bond < bonds.length; bond++)
        {
            lengths[bond] = Math.hypot(
                drawing[bonds[bond][0] * 2] - drawing[bonds[bond][1] * 2],
                drawing[bonds[bond][0] * 2 + 1] - drawing[bonds[bond][1] * 2 + 1]);
        }
        Arrays.sort(lengths);
        int middle = lengths.length / 2;
        double median = lengths.length % 2 == 1 ? lengths[middle] : (lengths[middle - 1] + lengths[middle]) / 2;
        if (median < 1e-9)
        {
            return drawing;
        }
        double[] scaled = new double[drawing.length];
        for (int at = 0; at < drawing.length; at++)
        {
            scaled[at] = drawing[at] / median;
        }
        return scaled;
    }

    /**
     * @param points where each atom is drawn
     * @return how many pairs of bonds at an atom are drawn as one line: either so near straight that the atom looks as
     *     if it were not a bend at all, or so near each other that one bond lies on top of the other
     */
    private int hiddenBends(Point[] points)
    {
        int hidden = 0;
        for (int[] angle : angles)
        {
            Point first = points[angle[1]].minus(points[angle[0]]);
            Point second = points[angle[2]].minus(points[angle[0]]);
            double length = first.length() * second.length();
            if (length <= 1e-12)
            {
                continue;
            }
            double cosine = (first.x() * second.x() + first.y() * second.y()) / length;
            if (cosine < STRAIGHT_COSINE || cosine > SHARP_COSINE)
            {
                hidden++;
            }
        }
        return hidden;
    }

    /**
     * @param structure the system in space
     * @param limit how many of the best views to return
     * @return the best few flat views of it
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
