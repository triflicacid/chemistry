package com.ruben.chemistry.render.rings;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.render.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Redraws a ring system whose first drawing collides with itself.
 * It improves a drawing made by {@link PolygonRingPlacer}.
 * <p>
 * Every bond is held at exactly one bond length. Among the drawings that allow, a penalty is minimised that keeps each ring
 * near a regular polygon, keeps atoms apart from each other and from bonds they are not on, and gently pulls atoms
 * that are equivalent by symmetry to look alike. The search starts from several drawings and keeps the best result.
 * <p>
 * Some systems, such as cubane, cannot be drawn with bonds of one length without bonds crossing. They get the drawing
 * with the fewest crossings that keeps atoms apart.
 */
final class RingEnergyMinimiser
{
    /** How far, in bond lengths, atoms have to be from each other for the drawing not to count as colliding. */
    static final double CLEAR_DISTANCE = 0.5;

    /** How far a bond's length may be from one bond length and still count as exactly that. */
    private static final double LENGTH_TOLERANCE = 1e-7;

    /** How much two drawings' closest atoms have to differ by for one to count as clearer. */
    private static final double DISTANCE_RESOLUTION = 1e-3;

    /** Non-bonded atoms pushed apart when closer than this. */
    private static final double REPULSION_RADIUS = 1.0;

    /** How much harder atoms are pushed apart than rings are pulled regular, as a drawing where atoms touch is worse than any ring being irregular. */
    private static final double REPULSION_WEIGHT = 12;

    /** How much a four-ring is also drawn square, which is only to settle between parallelograms. */
    private static final double SQUARE_WEIGHT = 0.1;

    /** Atoms pushed away from bonds that they are not on when closer than this. */
    private static final double BOND_CLEARANCE = 0.3;

    /** How much a bond is penalised for being off one bond length, when bonds are let give. */
    private static final double LENGTH_WEIGHT = 30;

    /** How strongly equivalent atoms are drawn alike, which is only to settle between otherwise equal drawings. */
    private static final double SYMMETRY_WEIGHT = 0.02;

    /** How many drawings the search starts from, for the smallest systems. */
    private static final int STARTS = 40;

    /** Larger systems cost more to draw, so are started from fewer drawings. */
    private static final int STARTS_BUDGET = 4000;

    private static final int MAX_DESCENT_STEPS = 400;
    private static final int MAX_PROJECTIONS = 100;
    private static final double GRADIENT_TOLERANCE = 1e-7;
    private static final double RIDGE = 1e-12;
    private static final long SEED = 0x5EEDL;

    private final List<Atom> atoms = new ArrayList<>();
    private final int[][] bonds;
    private final int[][] rings;
    private final boolean[][] joined;
    private final int[][] symmetryClasses;
    private int effort = 1;
    private final int count;

    private RingEnergyMinimiser(List<List<Atom>> ringAtoms)
    {
        Map<Atom, Integer> indices = new IdentityHashMap<>();
        for (List<Atom> ring : ringAtoms)
        {
            for (Atom atom : ring)
            {
                if (indices.putIfAbsent(atom, atoms.size()) == null)
                {
                    atoms.add(atom);
                }
            }
        }
        count = atoms.size();

        rings = new int[ringAtoms.size()][];
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> edges = new ArrayList<>();
        for (int ring = 0; ring < rings.length; ring++)
        {
            List<Atom> members = ringAtoms.get(ring);
            rings[ring] = new int[members.size()];
            for (int member = 0; member < members.size(); member++)
            {
                rings[ring][member] = indices.get(members.get(member));
            }
            for (int member = 0; member < members.size(); member++)
            {
                int first = rings[ring][member];
                int second = rings[ring][(member + 1) % members.size()];
                if (seen.add(((long) Math.min(first, second) << Integer.SIZE) | Math.max(first, second)))
                {
                    edges.add(new int[] {first, second});
                }
            }
        }
        bonds = edges.toArray(new int[0][]);
        joined = new boolean[count][count];
        for (int[] bond : bonds)
        {
            joined[bond[0]][bond[1]] = true;
            joined[bond[1]][bond[0]] = true;
        }
        symmetryClasses = findSymmetryClasses();
    }

    /**
     * Redraws a ring system if its drawing collides with itself.
     *
     * @param rings the system's rings, each as its atoms in the order they are joined around it
     * @param drawing the system's first drawing
     * @param settings what to try and how hard; {@link RingRefinement#NONE} returns {@code drawing} untouched
     * @return the drawings found, best first, never worse than {@code drawing}; just {@code drawing} if it keeps clear of itself
     */
    static List<Map<Atom, Point>> improve(List<List<Atom>> rings, Map<Atom, Point> drawing, RingEffort settings)
    {
        if (!settings.refinement().isEnabled())
        {
            return List.of(drawing);
        }
        RingEnergyMinimiser solver = new RingEnergyMinimiser(rings);
        solver.effort = settings.refinement().effort();
        double[] seed = solver.pack(drawing);
        Score best = solver.score(seed);
        if (best.isClean())
        {
            return List.of(drawing);
        }

        double[] chosen = seed;
        for (double[] start : solver.starts(seed))
        {
            double[] result = solver.solve(start);
            if (result == null)
            {
                continue;
            }
            Score score = solver.score(result);
            if (score.betterThan(best))
            {
                best = score;
                chosen = result;
            }
        }
        List<double[]> views = settings.projectCages() ? new ForceFieldProjector(solver.count, solver.bonds, solver.effort).draws(settings.wanted()) : List.of();
        boolean isView = false;
        if (!views.isEmpty())
        {
            double[] projected = views.get(0);
            Score score = solver.score(projected);
            // a view of the real structure is preferred to any flat drawing that happens to be as clear
            boolean keepsApart = score.clearance() >= ForceFieldProjector.VIEW_CLEARANCE - 1e-3;
            if (keepsApart ? score.crossings() <= best.crossings() || !best.isClear() : !best.isClear() && score.betterThan(best))
            {
                best = score;
                chosen = projected;
                isView = keepsApart;
            }
        }
        if (!best.isClear() && !isView)
        {
            // no drawing with bonds of one length keeps atoms apart, so let the bonds give a little
            for (double[] start : solver.starts(chosen))
            {
                double[] result = solver.relax(start);
                Score score = solver.score(result);
                if (score.betterThan(best))
                {
                    best = score;
                    chosen = result;
                }
            }
        }

        List<Map<Atom, Point>> drawings = new ArrayList<>();
        drawings.add(solver.unpack(chosen));
        for (double[] view : views)
        {
            if (view != chosen && solver.score(view).clearance() >= ForceFieldProjector.VIEW_CLEARANCE - 1e-3)
            {
                drawings.add(solver.unpack(view));
            }
        }
        return drawings;
    }

    /**
     * How good a drawing is, in order of importance.
     *
     * @param exact whether every bond is one bond length long
     * @param clearance how close the closest atoms that are not bonded are, no more than {@link #CLEAR_DISTANCE}
     * @param crossings how many pairs of bonds cross
     * @param energy the penalty for the drawing's shape
     */
    private record Score(boolean exact, double clearance, int crossings, double energy)
    {
        boolean isClean()
        {
            return exact && clearance >= CLEAR_DISTANCE - DISTANCE_RESOLUTION && crossings == 0;
        }

        boolean isClear()
        {
            return clearance >= CLEAR_DISTANCE - DISTANCE_RESOLUTION;
        }

        boolean betterThan(Score other)
        {
            if (isClear() != other.isClear())
            {
                return isClear();
            }
            if (!isClear() && Math.abs(clearance - other.clearance) > DISTANCE_RESOLUTION)
            {
                return clearance > other.clearance;
            }
            if (exact != other.exact)
            {
                return exact;
            }
            if (Math.abs(clearance - other.clearance) > DISTANCE_RESOLUTION)
            {
                return clearance > other.clearance;
            }
            if (crossings != other.crossings)
            {
                return crossings < other.crossings;
            }
            return energy < other.energy - 1e-9;
        }
    }

    private Score score(double[] x)
    {
        Point[] points = points(x);
        boolean exact = LayoutQuality.lengthError(points, bonds, 1) <= LENGTH_TOLERANCE;
        double clearance = Math.min(CLEAR_DISTANCE, LayoutQuality.minimumAtomDistance(points, bonds));
        return new Score(exact, clearance, LayoutQuality.crossings(points, bonds), energy(x, null));
    }

    /**
     * @param seed the drawing already made
     * @return the drawings to search from: the given one, a spring drawing, and noisy copies of both
     */
    private List<double[]> starts(double[] seed)
    {
        List<double[]> starts = new ArrayList<>();
        starts.add(seed);
        starts.add(springDrawing());

        Random random = new Random(SEED);
        double[][] embedding = spectralEmbedding();
        int views = Math.max(8, Math.min(STARTS, STARTS_BUDGET / (count * count))) * effort;
        for (int view = 0; view < views; view++)
        {
            double[] direction = unit(random);
            double[] across = unit(random);
            // two directions at right angles to each other and to nothing else in particular make the page
            double along = dot(direction, across);
            for (int axis = 0; axis < 3; axis++)
            {
                across[axis] -= along * direction[axis];
            }
            double norm = Math.sqrt(dot(across, across));
            if (norm < 1e-6)
            {
                continue;
            }
            for (int axis = 0; axis < 3; axis++)
            {
                across[axis] /= norm;
            }
            double[] up = {
                direction[1] * across[2] - direction[2] * across[1],
                direction[2] * across[0] - direction[0] * across[2],
                direction[0] * across[1] - direction[1] * across[0]};

            double[] drawing = new double[count * 2];
            for (int atom = 0; atom < count; atom++)
            {
                drawing[atom * 2] = dot(embedding[atom], across);
                drawing[atom * 2 + 1] = dot(embedding[atom], up);
            }
            starts.add(drawing);
        }

        double spread = Math.sqrt(count) / 2;
        for (int scattered = 0; scattered < views / 2; scattered++)
        {
            double[] drawing = new double[count * 2];
            for (int at = 0; at < drawing.length; at++)
            {
                drawing[at] = random.nextGaussian() * spread;
            }
            starts.add(drawing);
        }
        return starts;
    }

    private static double[] unit(Random random)
    {
        double[] vector = {random.nextGaussian(), random.nextGaussian(), random.nextGaussian()};
        double norm = Math.sqrt(dot(vector, vector));
        for (int axis = 0; axis < 3; axis++)
        {
            vector[axis] /= norm;
        }
        return vector;
    }

    /**
     * Puts every atom in space so that how far apart atoms are tracks how many bonds apart they are, which makes a cage
     * into something like the solid it is, to be looked at from any side.
     *
     * @return each atom's three coordinates, scaled so that bonds are about one bond length long
     */
    private double[][] spectralEmbedding()
    {
        double[][] squared = new double[count][count];
        for (int from = 0; from < count; from++)
        {
            int[] distance = new int[count];
            Arrays.fill(distance, -1);
            distance[from] = 0;
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            queue.add(from);
            while (!queue.isEmpty())
            {
                int atom = queue.poll();
                for (int other = 0; other < count; other++)
                {
                    if (joined[atom][other] && distance[other] < 0)
                    {
                        distance[other] = distance[atom] + 1;
                        queue.add(other);
                    }
                }
            }
            for (int other = 0; other < count; other++)
            {
                squared[from][other] = (double) distance[other] * distance[other];
            }
        }

        // double centring turns squared distances into dot products about the middle
        double[] rowMeans = new double[count];
        double total = 0;
        for (int row = 0; row < count; row++)
        {
            for (int column = 0; column < count; column++)
            {
                rowMeans[row] += squared[row][column] / count;
            }
            total += rowMeans[row] / count;
        }
        double[][] products = new double[count][count];
        for (int row = 0; row < count; row++)
        {
            for (int column = 0; column < count; column++)
            {
                products[row][column] = -0.5 * (squared[row][column] - rowMeans[row] - rowMeans[column] + total);
            }
        }

        double[][] vectors = new double[count][count];
        double[] values = eigen(products, vectors);
        Integer[] order = new Integer[count];
        for (int index = 0; index < count; index++)
        {
            order[index] = index;
        }
        Arrays.sort(order, (first, second) -> Double.compare(values[second], values[first]));

        double[][] embedding = new double[count][3];
        for (int axis = 0; axis < 3 && axis < count; axis++)
        {
            double root = Math.sqrt(Math.max(0, values[order[axis]]));
            for (int atom = 0; atom < count; atom++)
            {
                embedding[atom][axis] = vectors[atom][order[axis]] * root;
            }
        }

        double bondLength = 0;
        for (int[] bond : bonds)
        {
            for (int axis = 0; axis < 3; axis++)
            {
                double difference = embedding[bond[0]][axis] - embedding[bond[1]][axis];
                bondLength += difference * difference;
            }
        }
        double scale = bondLength > 1e-12 ? 1 / Math.sqrt(bondLength / bonds.length) : 1;
        for (double[] point : embedding)
        {
            for (int axis = 0; axis < 3; axis++)
            {
                point[axis] *= scale;
            }
        }
        return embedding;
    }

    /**
     * Finds the eigenvalues and eigenvectors of a symmetric matrix by Jacobi rotations.
     *
     * @param matrix the matrix, which is destroyed
     * @param vectors where to put the eigenvectors, one per column
     * @return the eigenvalues
     */
    private static double[] eigen(double[][] matrix, double[][] vectors)
    {
        int size = matrix.length;
        for (int row = 0; row < size; row++)
        {
            Arrays.fill(vectors[row], 0);
            vectors[row][row] = 1;
        }
        for (int sweep = 0; sweep < 60; sweep++)
        {
            double off = 0;
            for (int row = 0; row < size; row++)
            {
                for (int column = row + 1; column < size; column++)
                {
                    off += matrix[row][column] * matrix[row][column];
                }
            }
            if (off < 1e-22)
            {
                break;
            }
            for (int p = 0; p < size; p++)
            {
                for (int q = p + 1; q < size; q++)
                {
                    if (Math.abs(matrix[p][q]) < 1e-300)
                    {
                        continue;
                    }
                    double theta = (matrix[q][q] - matrix[p][p]) / (2 * matrix[p][q]);
                    double tangent = Math.signum(theta == 0 ? 1 : theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
                    double cosine = 1 / Math.sqrt(tangent * tangent + 1);
                    double sine = tangent * cosine;
                    for (int k = 0; k < size; k++)
                    {
                        double kp = matrix[k][p];
                        double kq = matrix[k][q];
                        matrix[k][p] = cosine * kp - sine * kq;
                        matrix[k][q] = sine * kp + cosine * kq;
                    }
                    for (int k = 0; k < size; k++)
                    {
                        double pk = matrix[p][k];
                        double qk = matrix[q][k];
                        matrix[p][k] = cosine * pk - sine * qk;
                        matrix[q][k] = sine * pk + cosine * qk;
                    }
                    for (int k = 0; k < size; k++)
                    {
                        double kp = vectors[k][p];
                        double kq = vectors[k][q];
                        vectors[k][p] = cosine * kp - sine * kq;
                        vectors[k][q] = sine * kp + cosine * kq;
                    }
                }
            }
        }
        double[] values = new double[size];
        for (int index = 0; index < size; index++)
        {
            values[index] = matrix[index][index];
        }
        return values;
    }

    /**
     * @return the largest ring drawn as a regular polygon, with everything else pulled to the middle of its neighbours
     */
    private double[] springDrawing()
    {
        int outer = 0;
        for (int ring = 1; ring < rings.length; ring++)
        {
            if (rings[ring].length > rings[outer].length)
            {
                outer = ring;
            }
        }

        double[] x = new double[count * 2];
        boolean[] fixed = new boolean[count];
        int size = rings[outer].length;
        double radius = 1 / (2 * Math.sin(Math.PI / size));
        for (int member = 0; member < size; member++)
        {
            int atom = rings[outer][member];
            fixed[atom] = true;
            x[atom * 2] = Math.cos(2 * Math.PI * member / size) * radius;
            x[atom * 2 + 1] = Math.sin(2 * Math.PI * member / size) * radius;
        }

        for (int round = 0; round < 300; round++)
        {
            for (int atom = 0; atom < count; atom++)
            {
                if (fixed[atom])
                {
                    continue;
                }
                double sumX = 0;
                double sumY = 0;
                int neighbours = 0;
                for (int other = 0; other < count; other++)
                {
                    if (joined[atom][other])
                    {
                        sumX += x[other * 2];
                        sumY += x[other * 2 + 1];
                        neighbours++;
                    }
                }
                x[atom * 2] = sumX / neighbours;
                x[atom * 2 + 1] = sumY / neighbours;
            }
        }
        return x;
    }

    /**
     * Walks downhill on the drawing's penalty plus a penalty for each bond's length being off one bond length, so bonds
     * may stretch or shrink where that is the only way to keep atoms apart.
     *
     * @param start where to start from
     * @return the drawing reached
     */
    private double[] relax(double[] start)
    {
        double[] x = start.clone();
        double[] gradient = new double[x.length];
        double step = 0.01;
        double total = relaxedEnergy(x, null);
        for (int iteration = 0; iteration < 2000; iteration++)
        {
            relaxedEnergy(x, gradient);
            double norm = dot(gradient, gradient);
            if (Math.sqrt(norm) < GRADIENT_TOLERANCE)
            {
                break;
            }
            boolean moved = false;
            for (int attempt = 0; attempt < 30; attempt++)
            {
                double[] next = x.clone();
                for (int at = 0; at < next.length; at++)
                {
                    next[at] -= step * gradient[at];
                }
                double nextTotal = relaxedEnergy(next, null);
                if (nextTotal < total - 1e-4 * step * norm)
                {
                    x = next;
                    total = nextTotal;
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

    private double relaxedEnergy(double[] x, double[] gradient)
    {
        double total = energy(x, gradient);
        for (int[] bond : bonds)
        {
            int first = bond[0] * 2;
            int second = bond[1] * 2;
            double dx = x[first] - x[second];
            double dy = x[first + 1] - x[second + 1];
            double length = Math.sqrt(dx * dx + dy * dy);
            double error = length - 1;
            total += LENGTH_WEIGHT * error * error;
            if (gradient != null && length > 1e-12)
            {
                double scale = 2 * LENGTH_WEIGHT * error / length;
                gradient[first] += scale * dx;
                gradient[first + 1] += scale * dy;
                gradient[second] -= scale * dx;
                gradient[second + 1] -= scale * dy;
            }
        }
        return total;
    }

    /**
     * Holds every bond at one bond length, then walks downhill without leaving that.
     *
     * @param start where to start from
     * @return the drawing reached, or {@code null} if the bonds could not all be made one bond length long
     */
    private double[] solve(double[] start)
    {
        double[] x = start.clone();
        if (!project(x))
        {
            return null;
        }

        double energy = energy(x, null);
        double step = 0.1;
        double[] gradient = new double[x.length];
        for (int iteration = 0; iteration < MAX_DESCENT_STEPS; iteration++)
        {
            energy(x, gradient);
            double[] direction = tangent(x, gradient);
            double norm = dot(direction, direction);
            if (Math.sqrt(norm) < GRADIENT_TOLERANCE)
            {
                break;
            }

            boolean moved = false;
            for (int attempt = 0; attempt < 30; attempt++)
            {
                double[] next = x.clone();
                for (int at = 0; at < next.length; at++)
                {
                    next[at] -= step * direction[at];
                }
                if (project(next))
                {
                    double nextEnergy = energy(next, null);
                    if (nextEnergy < energy - 1e-4 * step * norm)
                    {
                        x = next;
                        energy = nextEnergy;
                        step *= 1.5;
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
     * Moves a drawing, as little as it can, until every bond is one bond length long.
     *
     * @param x the drawing, which is changed
     * @return whether it got there
     */
    private boolean project(double[] x)
    {
        double ridge = 1e-6;
        double[] residual = residualOf(x);
        double size = dot(residual, residual);
        for (int round = 0; round < MAX_PROJECTIONS; round++)
        {
            if (worst(residual) < 1e-13)
            {
                return true;
            }

            double[] multipliers = solveGram(x, residual, ridge);
            if (multipliers != null)
            {
                double[] next = x.clone();
                for (int bond = 0; bond < bonds.length; bond++)
                {
                    int first = bonds[bond][0];
                    int second = bonds[bond][1];
                    double dx = x[first * 2] - x[second * 2];
                    double dy = x[first * 2 + 1] - x[second * 2 + 1];
                    next[first * 2] -= 2 * dx * multipliers[bond];
                    next[first * 2 + 1] -= 2 * dy * multipliers[bond];
                    next[second * 2] += 2 * dx * multipliers[bond];
                    next[second * 2 + 1] += 2 * dy * multipliers[bond];
                }
                double[] nextResidual = residualOf(next);
                double nextSize = dot(nextResidual, nextResidual);
                if (nextSize < size)
                {
                    System.arraycopy(next, 0, x, 0, x.length);
                    residual = nextResidual;
                    size = nextSize;
                    ridge = Math.max(ridge / 10, RIDGE);
                    continue;
                }
            }

            ridge *= 10;
            if (ridge > 1e8)
            {
                return false;
            }
        }
        return worst(residual) < 1e-13;
    }

    /**
     * @param x the drawing
     * @return how far each bond's squared length is from one
     */
    private double[] residualOf(double[] x)
    {
        double[] residual = new double[bonds.length];
        for (int bond = 0; bond < bonds.length; bond++)
        {
            double dx = x[bonds[bond][0] * 2] - x[bonds[bond][1] * 2];
            double dy = x[bonds[bond][0] * 2 + 1] - x[bonds[bond][1] * 2 + 1];
            residual[bond] = dx * dx + dy * dy - 1;
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

    /**
     * @param x the drawing
     * @param gradient which way the penalty rises
     * @return that, with any part that would change a bond's length taken out
     */
    private double[] tangent(double[] x, double[] gradient)
    {
        double[] along = new double[bonds.length];
        for (int bond = 0; bond < bonds.length; bond++)
        {
            int first = bonds[bond][0];
            int second = bonds[bond][1];
            along[bond] = 2 * ((x[first * 2] - x[second * 2]) * (gradient[first * 2] - gradient[second * 2])
                + (x[first * 2 + 1] - x[second * 2 + 1]) * (gradient[first * 2 + 1] - gradient[second * 2 + 1]));
        }
        double[] multipliers = solveGram(x, along);
        double[] result = gradient.clone();
        if (multipliers == null)
        {
            return result;
        }
        for (int bond = 0; bond < bonds.length; bond++)
        {
            int first = bonds[bond][0];
            int second = bonds[bond][1];
            double dx = x[first * 2] - x[second * 2];
            double dy = x[first * 2 + 1] - x[second * 2 + 1];
            result[first * 2] -= 2 * dx * multipliers[bond];
            result[first * 2 + 1] -= 2 * dy * multipliers[bond];
            result[second * 2] += 2 * dx * multipliers[bond];
            result[second * 2 + 1] += 2 * dy * multipliers[bond];
        }
        return result;
    }

    /**
     * Solves {@code (J Jᵀ + ridge) y = rhs}, where {@code J} is how each bond's squared length changes with each coordinate.
     *
     * @param x the drawing
     * @param rhs the right-hand side, one entry per bond
     * @return {@code y}, or {@code null} if the system is singular
     */
    private double[] solveGram(double[] x, double[] rhs)
    {
        return solveGram(x, rhs, RIDGE);
    }

    private double[] solveGram(double[] x, double[] rhs, double ridge)
    {
        int size = bonds.length;
        double[][] gram = new double[size][size + 1];
        for (int row = 0; row < size; row++)
        {
            for (int column = row; column < size; column++)
            {
                double value = shared(x, bonds[row], bonds[column]);
                gram[row][column] = value;
                gram[column][row] = value;
            }
            gram[row][row] += ridge;
            gram[row][size] = rhs[row];
        }

        for (int pivot = 0; pivot < size; pivot++)
        {
            int best = pivot;
            for (int row = pivot + 1; row < size; row++)
            {
                if (Math.abs(gram[row][pivot]) > Math.abs(gram[best][pivot]))
                {
                    best = row;
                }
            }
            if (Math.abs(gram[best][pivot]) < 1e-14)
            {
                return null;
            }
            double[] swap = gram[pivot];
            gram[pivot] = gram[best];
            gram[best] = swap;
            for (int row = pivot + 1; row < size; row++)
            {
                double factor = gram[row][pivot] / gram[pivot][pivot];
                for (int column = pivot; column <= size; column++)
                {
                    gram[row][column] -= factor * gram[pivot][column];
                }
            }
        }

        double[] solution = new double[size];
        for (int row = size - 1; row >= 0; row--)
        {
            double sum = gram[row][size];
            for (int column = row + 1; column < size; column++)
            {
                sum -= gram[row][column] * solution[column];
            }
            solution[row] = sum / gram[row][row];
        }
        return solution;
    }

    /**
     * @param x the drawing
     * @param first a bond
     * @param second a bond
     * @return the dot product of the two bonds' rows of {@code J}
     */
    private static double shared(double[] x, int[] first, int[] second)
    {
        double total = 0;
        for (int end = 0; end < 2; end++)
        {
            for (int other = 0; other < 2; other++)
            {
                if (first[end] != second[other])
                {
                    continue;
                }
                int partnerFirst = first[1 - end];
                int partnerSecond = second[1 - other];
                double ax = x[first[end] * 2] - x[partnerFirst * 2];
                double ay = x[first[end] * 2 + 1] - x[partnerFirst * 2 + 1];
                double bx = x[second[other] * 2] - x[partnerSecond * 2];
                double by = x[second[other] * 2 + 1] - x[partnerSecond * 2 + 1];
                total += 4 * (ax * bx + ay * by);
            }
        }
        return total;
    }

    /**
     * @param x the drawing
     * @param gradient where to put which way the penalty rises, or {@code null} to skip that
     * @return the penalty for the drawing's shape
     */
    private double energy(double[] x, double[] gradient)
    {
        if (gradient != null)
        {
            Arrays.fill(gradient, 0);
        }
        double energy = 0;

        // each ring wants its atoms two apart as far as a regular polygon would have them
        for (int[] ring : rings)
        {
            int size = ring.length;
            if (size < 4)
            {
                continue;
            }
            if (size == 4)
            {
                // a four-ring is happy as any parallelogram, which is what a face of a cage looks like from the side
                energy += parallelogram(x, gradient, ring);
                for (int member = 0; member < 2; member++)
                {
                    energy += pull(x, gradient, ring[member], ring[member + 2], Math.sqrt(2), SQUARE_WEIGHT);
                }
                continue;
            }
            double wanted = 2 * Math.cos(Math.PI / size);
            for (int member = 0; member < size; member++)
            {
                energy += pull(x, gradient, ring[member], ring[(member + 2) % size], wanted, 1);
            }
        }

        for (int first = 0; first < count; first++)
        {
            for (int second = first + 1; second < count; second++)
            {
                if (!joined[first][second])
                {
                    energy += push(x, gradient, first, second);
                }
            }
        }

        for (int atom = 0; atom < count; atom++)
        {
            for (int[] bond : bonds)
            {
                if (bond[0] != atom && bond[1] != atom)
                {
                    energy += pushFromBond(x, gradient, atom, bond);
                }
            }
        }

        return energy + symmetry(x, gradient);
    }

    private static double pull(double[] x, double[] gradient, int first, int second, double wanted, double weight)
    {
        double dx = x[first * 2] - x[second * 2];
        double dy = x[first * 2 + 1] - x[second * 2 + 1];
        double distance = Math.hypot(dx, dy);
        double off = distance - wanted;
        if (gradient != null && distance > 1e-12)
        {
            double scale = 2 * weight * off / distance;
            gradient[first * 2] += scale * dx;
            gradient[first * 2 + 1] += scale * dy;
            gradient[second * 2] -= scale * dx;
            gradient[second * 2 + 1] -= scale * dy;
        }
        return weight * off * off;
    }

    /**
     * @param x the drawing
     * @param gradient where to add which way the penalty rises, or {@code null} to skip that
     * @param ring a four-ring's atoms in order
     * @return how far the ring is from a parallelogram, which is how far its diagonals are from sharing a middle
     */
    private static double parallelogram(double[] x, double[] gradient, int[] ring)
    {
        double gapX = x[ring[0] * 2] + x[ring[2] * 2] - x[ring[1] * 2] - x[ring[3] * 2];
        double gapY = x[ring[0] * 2 + 1] + x[ring[2] * 2 + 1] - x[ring[1] * 2 + 1] - x[ring[3] * 2 + 1];
        if (gradient != null)
        {
            for (int corner = 0; corner < 4; corner++)
            {
                double sign = corner % 2 == 0 ? 1 : -1;
                gradient[ring[corner] * 2] += 2 * sign * gapX;
                gradient[ring[corner] * 2 + 1] += 2 * sign * gapY;
            }
        }
        return gapX * gapX + gapY * gapY;
    }

    private static double push(double[] x, double[] gradient, int first, int second)
    {
        double dx = x[first * 2] - x[second * 2];
        double dy = x[first * 2 + 1] - x[second * 2 + 1];
        double distance = Math.hypot(dx, dy);
        if (distance >= REPULSION_RADIUS)
        {
            return 0;
        }
        double overlap = REPULSION_RADIUS - distance;
        double cubed = overlap * overlap * overlap;
        if (gradient != null)
        {
            // atoms on top of one another have no direction to part in, so are parted along a fixed one
            double ux = distance > 1e-12 ? dx / distance : 1;
            double uy = distance > 1e-12 ? dy / distance : 0;
            gradient[first * 2] -= 4 * REPULSION_WEIGHT * cubed * ux;
            gradient[first * 2 + 1] -= 4 * REPULSION_WEIGHT * cubed * uy;
            gradient[second * 2] += 4 * REPULSION_WEIGHT * cubed * ux;
            gradient[second * 2 + 1] += 4 * REPULSION_WEIGHT * cubed * uy;
        }
        return REPULSION_WEIGHT * cubed * overlap;
    }

    private static double pushFromBond(double[] x, double[] gradient, int atom, int[] bond)
    {
        double px = x[atom * 2];
        double py = x[atom * 2 + 1];
        double ax = x[bond[0] * 2];
        double ay = x[bond[0] * 2 + 1];
        double sx = x[bond[1] * 2] - ax;
        double sy = x[bond[1] * 2 + 1] - ay;
        double length = sx * sx + sy * sy;
        double along = length > 1e-12 ? Math.clamp(((px - ax) * sx + (py - ay) * sy) / length, 0, 1) : 0;
        double dx = px - (ax + along * sx);
        double dy = py - (ay + along * sy);
        double distance = Math.hypot(dx, dy);
        if (distance >= BOND_CLEARANCE)
        {
            return 0;
        }
        double overlap = BOND_CLEARANCE - distance;
        if (gradient != null && distance > 1e-12)
        {
            double scale = 2 * overlap / distance;
            gradient[atom * 2] -= scale * dx;
            gradient[atom * 2 + 1] -= scale * dy;
            gradient[bond[0] * 2] += scale * dx * (1 - along);
            gradient[bond[0] * 2 + 1] += scale * dy * (1 - along);
            gradient[bond[1] * 2] += scale * dx * along;
            gradient[bond[1] * 2 + 1] += scale * dy * along;
        }
        return overlap * overlap;
    }

    /**
     * @param x the drawing
     * @param gradient where to add which way the penalty rises, or {@code null} to skip that
     * @return how unevenly atoms that are equivalent by symmetry sit from the middle of the drawing
     */
    private double symmetry(double[] x, double[] gradient)
    {
        double middleX = 0;
        double middleY = 0;
        for (int atom = 0; atom < count; atom++)
        {
            middleX += x[atom * 2] / count;
            middleY += x[atom * 2 + 1] / count;
        }

        double energy = 0;
        double[] pullX = new double[count];
        double[] pullY = new double[count];
        for (int[] members : symmetryClasses)
        {
            if (members.length < 2)
            {
                continue;
            }
            double[] radii = new double[members.length];
            double mean = 0;
            for (int member = 0; member < members.length; member++)
            {
                radii[member] = Math.hypot(x[members[member] * 2] - middleX, x[members[member] * 2 + 1] - middleY);
                mean += radii[member] / members.length;
            }
            for (int member = 0; member < members.length; member++)
            {
                double off = radii[member] - mean;
                energy += SYMMETRY_WEIGHT * off * off;
                if (radii[member] > 1e-12)
                {
                    pullX[members[member]] += 2 * SYMMETRY_WEIGHT * off * (x[members[member] * 2] - middleX) / radii[member];
                    pullY[members[member]] += 2 * SYMMETRY_WEIGHT * off * (x[members[member] * 2 + 1] - middleY) / radii[member];
                }
            }
        }

        if (gradient != null)
        {
            double meanX = 0;
            double meanY = 0;
            for (int atom = 0; atom < count; atom++)
            {
                meanX += pullX[atom] / count;
                meanY += pullY[atom] / count;
            }
            for (int atom = 0; atom < count; atom++)
            {
                gradient[atom * 2] += pullX[atom] - meanX;
                gradient[atom * 2 + 1] += pullY[atom] - meanY;
            }
        }
        return energy;
    }

    /**
     * @return the atoms grouped by what they are and what their neighbours are, refined until nothing more tells them apart
     */
    private int[][] findSymmetryClasses()
    {
        int[] colours = new int[count];
        Map<String, Integer> names = new HashMap<>();
        for (int atom = 0; atom < count; atom++)
        {
            colours[atom] = names.computeIfAbsent(atoms.get(atom).element(), key -> names.size());
        }

        int classes = names.size();
        while (true)
        {
            Map<String, Integer> signatures = new HashMap<>();
            int[] next = new int[count];
            for (int atom = 0; atom < count; atom++)
            {
                List<Integer> around = new ArrayList<>();
                for (int other = 0; other < count; other++)
                {
                    if (joined[atom][other])
                    {
                        around.add(colours[other]);
                    }
                }
                around.sort(null);
                next[atom] = signatures.computeIfAbsent(colours[atom] + ":" + around, key -> signatures.size());
            }
            colours = next;
            if (signatures.size() == classes)
            {
                break;
            }
            classes = signatures.size();
        }

        List<List<Integer>> grouped = new ArrayList<>();
        for (int atom = 0; atom < count; atom++)
        {
            while (grouped.size() <= colours[atom])
            {
                grouped.add(new ArrayList<>());
            }
            grouped.get(colours[atom]).add(atom);
        }
        return grouped.stream().map(group -> group.stream().mapToInt(Integer::intValue).toArray()).toArray(int[][]::new);
    }

    private double[] pack(Map<Atom, Point> drawing)
    {
        double[] x = new double[count * 2];
        for (int atom = 0; atom < count; atom++)
        {
            Point point = drawing.get(atoms.get(atom));
            x[atom * 2] = point.x();
            x[atom * 2 + 1] = point.y();
        }
        return x;
    }

    private Map<Atom, Point> unpack(double[] x)
    {
        Map<Atom, Point> drawing = new IdentityHashMap<>();
        Point[] points = points(x);
        for (int atom = 0; atom < count; atom++)
        {
            drawing.put(atoms.get(atom), points[atom]);
        }
        return drawing;
    }

    private Point[] points(double[] x)
    {
        Point[] points = new Point[count];
        for (int atom = 0; atom < count; atom++)
        {
            points[atom] = new Point(x[atom * 2], x[atom * 2 + 1]);
        }
        return points;
    }

    private static double dot(double[] first, double[] second)
    {
        double total = 0;
        for (int at = 0; at < first.length; at++)
        {
            total += first[at] * second[at];
        }
        return total;
    }
}
