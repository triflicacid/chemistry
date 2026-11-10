package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Atom;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays out one ring system.
 * <p>
 * The first ring is drawn as a regular polygon, and each ring after it is built on the atoms it shares with what is
 * already drawn, bulging away from the ring it was built on.
 */
final class RingSystemLayout
{
    /** How close to zero a length has to be to count as nothing. */
    private static final double EPSILON = 1e-9;

    /** How many halvings the search for the right arc takes, which is far finer than any screen. */
    private static final int BISECTIONS = 60;

    private final List<List<Atom>> rings;
    private final Map<Atom, Point> positions = new IdentityHashMap<>();
    private final Point[] centres;

    /**
     * @param rings the system's rings, each as its atoms in the order they are joined around it
     */
    private RingSystemLayout(List<List<Atom>> rings)
    {
        this.rings = rings;
        this.centres = new Point[rings.size()];
    }

    /**
     * @param rings the system's rings, each as its atoms in the order they are joined around it; they must form one connected system
     * @return where every atom of the system sits, with bonds a bond length long
     */
    static Map<Atom, Point> layOut(List<List<Atom>> rings)
    {
        RingSystemLayout layout = new RingSystemLayout(rings);
        layout.build();
        return layout.positions;
    }

    /**
     * Places every ring, most-anchored first.
     */
    private void build()
    {
        placeFirst(0);
        while (true)
        {
            int next = -1;
            int most = 0;
            for (int ring = 0; ring < rings.size(); ring++)
            {
                if (centres[ring] == null)
                {
                    int shared = sharedWithPlaced(ring);
                    if (shared > most)
                    {
                        most = shared;
                        next = ring;
                    }
                }
            }
            if (next < 0)
            {
                return;
            }
            placeAttached(next);
        }
    }

    /**
     * @param ring a ring
     * @return how many of its atoms are already placed
     */
    private int sharedWithPlaced(int ring)
    {
        int shared = 0;
        for (Atom atom : rings.get(ring))
        {
            if (positions.containsKey(atom))
            {
                shared++;
            }
        }
        return shared;
    }

    /**
     * Draws a ring as a regular polygon around the origin.
     *
     * @param ring the ring
     */
    private void placeFirst(int ring)
    {
        placePolygon(ring, Point.ORIGIN, 0);
    }

    /**
     * @param ring the ring
     * @param centre where its middle is
     * @param start the direction from the middle to its first atom
     */
    private void placePolygon(int ring, Point centre, double start)
    {
        List<Atom> atoms = rings.get(ring);
        int size = atoms.size();
        double radius = 1 / (2 * Math.sin(Math.PI / size));
        for (int atom = 0; atom < size; atom++)
        {
            positions.put(atoms.get(atom), centre.plus(Point.polar(start + atom * AngleFan.TURN / size, radius)));
        }
        centres[ring] = centre;
    }

    /**
     * Draws a ring that shares at least one atom with what is already drawn.
     *
     * @param ring the ring
     */
    private void placeAttached(int ring)
    {
        List<Atom> atoms = rings.get(ring);
        int size = atoms.size();
        boolean[] fixed = new boolean[size];
        int count = 0;
        for (int atom = 0; atom < size; atom++)
        {
            fixed[atom] = positions.containsKey(atoms.get(atom));
            if (fixed[atom])
            {
                count++;
            }
        }

        if (count == 1)
        {
            placeSpiro(ring, fixed);
            return;
        }

        for (int from = 0; from < size; from++)
        {
            if (!fixed[from] || fixed[(from + 1) % size])
            {
                continue;
            }
            // from is the last drawn atom before a run of undrawn ones, which ends at the next drawn atom
            int to = (from + 1) % size;
            int missing = 0;
            while (!fixed[to])
            {
                to = (to + 1) % size;
                missing++;
            }
            placeArc(ring, from, to, missing);
        }
        centres[ring] = centroid(atoms);
    }

    /**
     * Draws a ring that touches what is drawn at a single atom, as a regular polygon pointing away from it.
     *
     * @param ring the ring
     * @param fixed which of its atoms are already drawn, which is just the one
     */
    private void placeSpiro(int ring, boolean[] fixed)
    {
        List<Atom> atoms = rings.get(ring);
        int size = atoms.size();
        int anchor = 0;
        while (!fixed[anchor])
        {
            anchor++;
        }
        Point joint = positions.get(atoms.get(anchor));

        // the new ring points into the roomiest gap among the bonds already leaving the joint, however many rings meet there
        List<Double> taken = new ArrayList<>();
        for (List<Atom> other : rings)
        {
            int at = indexIn(other, atoms.get(anchor));
            if (at < 0)
            {
                continue;
            }
            for (int offset : new int[] {-1, 1})
            {
                Point neighbour = positions.get(other.get(Math.floorMod(at + offset, other.size())));
                if (neighbour != null)
                {
                    taken.add(neighbour.minus(joint).angle());
                }
            }
        }
        double away = taken.isEmpty() ? 0 : AngleFan.fillGaps(taken, 1).getFirst();
        double radius = 1 / (2 * Math.sin(Math.PI / size));
        Point centre = joint.plus(Point.polar(away, radius));
        double start = away + Math.PI - anchor * AngleFan.TURN / size;
        placePolygon(ring, centre, start);
    }

    /**
     * @param ring a ring's atoms
     * @param atom an atom
     * @return where the very atom sits in the ring, or {@code -1} if it is not in it
     */
    private static int indexIn(List<Atom> ring, Atom atom)
    {
        for (int index = 0; index < ring.size(); index++)
        {
            if (ring.get(index) == atom)
            {
                return index;
            }
        }
        return -1;
    }

    /**
     * Draws the undrawn atoms between two drawn ones on a circular arc, bulging away from what is already drawn.
     *
     * @param ring the ring
     * @param from the position within the ring of the drawn atom the run starts after
     * @param to the position within the ring of the drawn atom the run ends before
     * @param missing how many atoms there are to draw between them
     */
    private void placeArc(int ring, int from, int to, int missing)
    {
        List<Atom> atoms = rings.get(ring);
        Point start = positions.get(atoms.get(from));
        Point end = positions.get(atoms.get(to));
        Point chord = end.minus(start);
        double length = chord.length();
        Point middle = start.plus(chord.scale(0.5));
        Point normal = new Point(-chord.y(), chord.x()).scale(1 / length);

        Point reference = referenceFor(middle, atoms.get(from), atoms.get(to), ring);
        Point bulge = normal.scale(Math.signum(normal.x() * (middle.x() - reference.x()) + normal.y() * (middle.y() - reference.y())));
        if (bulge.length() < EPSILON)
        {
            bulge = normal;
        }

        int segments = missing + 1;
        if (length >= segments - EPSILON)
        {
            // already as stretched as the bonds allow, so the run is a straight line
            for (int step = 1; step <= missing; step++)
            {
                positions.put(atoms.get((from + step) % atoms.size()), start.plus(chord.scale((double) step / segments)));
            }
            return;
        }

        double step = angleFor(segments, length);
        double radius = 1 / (2 * Math.sin(step / 2));
        double half = segments * step / 2;
        Point centre = middle.minus(bulge.scale(radius * Math.cos(half)));
        double first = start.minus(centre).angle();
        double sweep = 2 * half;
        double delta = AngleFan.normalise(end.minus(centre).angle() - first);
        double direction;
        if (Math.abs(sweep - Math.PI) < EPSILON)
        {
            // a semicircle reaches the far end either way round, so the side it bulges to decides
            Point arcMiddle = centre.plus(Point.polar(first + half, radius));
            direction = arcMiddle.minus(middle).x() * bulge.x() + arcMiddle.minus(middle).y() * bulge.y() >= 0 ? 1 : -1;
        }
        else
        {
            // the far end lies a whole sweep on from the start, whichever way round that is
            direction = Math.abs(delta - sweep) <= Math.abs(delta - (AngleFan.TURN - sweep)) ? 1 : -1;
        }

        for (int index = 1; index <= missing; index++)
        {
            positions.put(atoms.get((from + index) % atoms.size()), centre.plus(Point.polar(first + direction * index * step, radius)));
        }
    }

    /**
     * Finds the angle each unit-length chord of an arc turns through, where {@code segments} of them
     * span a straight-line distance of {@code span}.
     *
     * @param segments how many chords there are
     * @param span how far apart the arc's ends are, which is less than {@code segments}
     * @return the angle each chord subtends at the arc's centre
     */
    private static double angleFor(int segments, double span)
    {
        // sin(segments * t / 2) / sin(t / 2) falls steadily from segments to nothing over this range
        double low = 0;
        double high = AngleFan.TURN / segments;
        for (int round = 0; round < BISECTIONS; round++)
        {
            double middle = (low + high) / 2;
            double reach = Math.sin(segments * middle / 2) / Math.sin(middle / 2);
            if (reach > span)
            {
                low = middle;
            }
            else
            {
                high = middle;
            }
        }
        return (low + high) / 2;
    }

    /**
     * Finds a point on the side of an attachment that a new ring should bulge away from.
     *
     * @param near the point being built out from
     * @param first an atom of the attachment
     * @param second the attachment's other end, or {@code null} if it is a single atom
     * @param except the ring being drawn, which is not a reference for itself
     * @return the point to bulge away from
     */
    private Point referenceFor(Point near, Atom first, Atom second, int except)
    {
        for (int ring = 0; ring < rings.size(); ring++)
        {
            if (ring != except && centres[ring] != null && holds(ring, first) && (second == null || holds(ring, second)))
            {
                return centres[ring];
            }
        }
        return centroid(positions.keySet());
    }

    /**
     * @param ring a ring
     * @param atom an atom
     * @return whether the ring contains that very atom, as atoms are only equal to themselves here
     */
    private boolean holds(int ring, Atom atom)
    {
        for (Atom member : rings.get(ring))
        {
            if (member == atom)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @param atoms drawn atoms
     * @return the middle of them
     */
    private Point centroid(Iterable<Atom> atoms)
    {
        double x = 0;
        double y = 0;
        int count = 0;
        for (Atom atom : atoms)
        {
            Point position = positions.get(atom);
            if (position != null)
            {
                x += position.x();
                y += position.y();
                count++;
            }
        }
        return new Point(x / count, y / count);
    }
}
