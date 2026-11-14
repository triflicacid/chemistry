package com.ruben.chemistry.space;

import com.ruben.chemistry.render.Point;
import com.ruben.chemistry.render.rings.LayoutQuality;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Draws a connected set of atoms as a flat view of the shape {@link StructureEmbedder} builds for it in space. Atoms
 * are never moved within a drawing, so a drawing is always a true view of the structure and never a reshaped one.
 */
public final class StructureProjector
{
    /** how many of the least strained structures are looked at */
    private static final int KEPT_STRUCTURES = 2;

    /** how many directions each structure is looked at from, times the effort, spread evenly over a sphere */
    private static final int VIEWS = 600;

    /** how many of the best views of each structure are kept to begin with */
    private static final int SHORTLIST_VIEWS = 6;

    /** How far apart atoms have to be, in bond lengths, for a view of a structure to count as keeping them apart. */
    public static final double VIEW_CLEARANCE = 0.45;

    /** How short a bond may look, in bond lengths, before the view counts as colliding. */
    private static final double MIN_BOND = 0.35;

    /** How much two views' evenness has to differ by for one of them to count as the more even. */
    private static final double EVENNESS_RESOLUTION = 0.05;

    /** how much two clearances have to differ by to count as different */
    private static final double CLEARANCE_RESOLUTION = 1e-3;

    /** How far, in bond lengths, the sorted distances between atoms of two drawings may differ for them to count as one drawing. */
    private static final double SAME_DRAWING_TOLERANCE = 0.05;

    private final int effort;
    private final int count;
    private final int[][] bonds;
    private StructureEmbedder embedder;

    /**
     * @param count how many atoms there are
     * @param bonds the bonds, as pairs of atom indices
     * @param effort how many times the usual number of structures and views to search
     */
    public StructureProjector(int count, int[][] bonds, int effort)
    {
        this.effort = effort;
        this.count = count;
        this.bonds = bonds;
    }

    /**
     * @param structure a system already in space, as x, y and z of each atom in turn
     * @return the best flat view of it, as x and y of each atom in turn, scaled so that the middling bond is one bond length
     *     and turned so that it is longest from left to right
     */
    public double[] bestViewOf(double[] structure)
    {
        return orient(bestViews(structure, 1).getFirst());
    }

    /**
     * @param structure a system already in space, as x, y and z of each atom in turn
     * @return the same view as {@link #bestViewOf}, as x, y and depth of each atom in turn, the depth being how far
     *     the atom is from the viewer at the same scale as x and y
     */
    public double[] bestViewWithDepthOf(double[] structure)
    {
        View best = rankedViews(structure, 1).getFirst();
        double[] flat = orient(best.drawing);
        double[] result = new double[count * 3];
        for (int atom = 0; atom < count; atom++)
        {
            result[atom * 3] = flat[atom * 2];
            result[atom * 3 + 1] = flat[atom * 2 + 1];
            result[atom * 3 + 2] = best.depth[atom];
        }
        return result;
    }

    /**
     * @return the best drawing, as x and y of each atom in turn, or {@code null} if the system could not be built in space
     */
    public double[] draw()
    {
        List<double[]> drawings = draws(1);
        return drawings.isEmpty() ? null : drawings.getFirst();
    }

    /**
     * Takes the usual shortlist of views, and widens it only while that leaves fewer different drawings than wanted.
     *
     * @param wanted how many different drawings are wanted
     * @return the different drawings found, best first, as x and y of each atom in turn; empty if the system could not be built in space
     */
    public List<double[]> draws(int wanted)
    {
        if (embedder == null)
        {
            embedder = new StructureEmbedder(count, bonds, effort);
        }
        List<double[]> structures = embedder.structures();
        List<View> found = new ArrayList<>();
        int[] done = new int[structures.size()];
        int keptStructures = Math.min(KEPT_STRUCTURES, structures.size());
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
     * How good a view is.
     *
     * @param drawing x and y of each atom in turn, scaled so that the middling bond is one bond length
     * @param depth how far each atom is from the viewer, at the same scale as {@code drawing}
     * @param clearance how far apart the closest atoms are, or bonds short, no more than the distance that counts as clear
     * @param crossings how many pairs of bonds cross
     * @param hidden how many pairs of bonds at an atom are drawn as one line
     * @param shortest how long the shortest bond is drawn
     * @param longest how long the longest bond is drawn
     */
    private record View(double[] drawing, double[] depth, double clearance, int crossings, int hidden, double shortest, double longest)
    {
        boolean isClear()
        {
            return clearance >= VIEW_CLEARANCE - CLEARANCE_RESOLUTION;
        }

        /**
         * @return how evenly the view foreshortens: 1 when every bond is drawn the same length, falling towards 0 as
         *     one bond is drawn far shorter than another
         */
        double evenness()
        {
            // a ratio, so that it says the same thing whatever size the view is drawn at
            return longest < 1e-12 ? 0 : shortest / longest;
        }
    }

    // crossings come second to evenness, because a bridge drawn across a ring is how a cage is read, whereas a bond
    // drawn far shorter than the bonds around it is never read as a bond at all
    private static final Comparator<View> ORDER = Comparator
        .comparingInt((View view) -> view.isClear() ? 0 : 1)
        .thenComparingLong(view -> view.isClear() ? 0 : -Math.round(view.clearance / CLEARANCE_RESOLUTION))
        .thenComparingLong(view -> -Math.round(view.evenness() / EVENNESS_RESOLUTION))
        .thenComparingInt(View::crossings)
        .thenComparingInt(View::hidden)
        .thenComparingLong(view -> -Math.round(view.evenness() * 1e6));

    private View measure(double[] drawing)
    {
        return measure(drawing, new double[count]);
    }

    private View measure(double[] drawing, double[] depth)
    {
        // measured at the size it will be drawn, so that views of the same structure are judged against each other
        double[] scaled = toMedianBond(drawing);
        double[] scaledDepth = depth;
        double median = medianBond(drawing);
        if (median >= 1e-9)
        {
            scaledDepth = new double[depth.length];
            for (int at = 0; at < depth.length; at++)
            {
                scaledDepth[at] = depth[at] / median;
            }
        }
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
        return new View(scaled, scaledDepth, clearance, LayoutQuality.crossings(points, bonds),
            LayoutQuality.hiddenBends(points, bonds), shortest, longest);
    }

    /**
     * @param drawing x and y of each atom in turn
     * @return how long the middling bond is drawn
     */
    private double medianBond(double[] drawing)
    {
        double[] lengths = new double[bonds.length];
        for (int bond = 0; bond < bonds.length; bond++)
        {
            lengths[bond] = Math.hypot(
                drawing[bonds[bond][0] * 2] - drawing[bonds[bond][1] * 2],
                drawing[bonds[bond][0] * 2 + 1] - drawing[bonds[bond][1] * 2 + 1]);
        }
        // the middling bond, rather than the longest, so that a view is not shrunk by one bond lying square to the page
        Arrays.sort(lengths);
        int middle = lengths.length / 2;
        return lengths.length % 2 == 1 ? lengths[middle] : (lengths[middle - 1] + lengths[middle]) / 2;
    }

    /**
     * Scales a drawing about the origin until its middling bond is one bond length, which leaves its shape untouched.
     *
     * @param drawing x and y of each atom in turn
     * @return the scaled drawing, or the drawing itself if it has collapsed to a point
     */
    private double[] toMedianBond(double[] drawing)
    {
        double median = medianBond(drawing);
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
     * @param structure the system in space
     * @param limit how many of the best views to return
     * @return the best few flat views of it
     */
    private List<double[]> bestViews(double[] structure, int limit)
    {
        return rankedViews(structure, limit).stream().map(View::drawing).toList();
    }

    private List<View> rankedViews(double[] structure, int limit)
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
            double[] right = Vectors.normalised(Vectors.cross(direction, helper));
            double[] up = Vectors.cross(direction, right);

            double[] drawing = new double[count * 2];
            double[] depth = new double[count];
            for (int atom = 0; atom < count; atom++)
            {
                double[] at = {structure[atom * 3], structure[atom * 3 + 1], structure[atom * 3 + 2]};
                drawing[atom * 2] = Vectors.dot(at, right);
                drawing[atom * 2 + 1] = Vectors.dot(at, up);
                // the viewer stands on the side the direction points to, so what lies against it is further away
                depth[atom] = -Vectors.dot(at, direction);
            }
            found.add(measure(drawing, depth));
        }
        found.sort(ORDER);
        return new ArrayList<>(found.subList(0, Math.min(limit, found.size())));
    }

    /**
     * @param drawing x and y of each atom in turn
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
}
