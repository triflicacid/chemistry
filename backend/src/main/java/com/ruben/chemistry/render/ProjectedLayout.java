package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.space.StructureViews;
import com.ruben.chemistry.space.Camera;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Lays a molecule out as a flat view of its {@link Structure3D}.
 */
public final class ProjectedLayout
{
    private record Bonded(int first, int second, BondOrder order, OptionalInt source)
    {
    }

    private final Molecule molecule;
    private final DepictionOptions options;
    private final MoleculeSnapshot snapshot;
    private final List<Structure3D.Site> drawn = new ArrayList<>();
    private final List<Vec3> spots = new ArrayList<>();
    private final List<Bonded> bonds = new ArrayList<>();
    private final int[] placementOfAtom;
    /** Each fragment's index among those drawn, or -1 if it is left out. */
    private final int[] drawnFragment;

    private ProjectedLayout(Molecule molecule, Structure3D structure, int conformer, DepictionOptions options, Set<Integer> only)
    {
        if (conformer < 0 || conformer >= structure.conformers().size())
        {
            throw new IllegalArgumentException("There is no conformer " + conformer + "; there are " + structure.conformers().size());
        }
        this.molecule = molecule;
        this.options = options;
        this.snapshot = MoleculeSnapshot.of(molecule);
        this.placementOfAtom = new int[molecule.atoms().size()];
        Arrays.fill(placementOfAtom, -1);
        this.drawnFragment = chooseFragments(only);

        List<Vec3> positions = structure.conformers().get(conformer).positions();
        boolean hydrogensShown = options.effectiveHydrogens().isSeparate();
        int[] placementOfSite = new int[structure.sites().size()];
        Arrays.fill(placementOfSite, -1);
        for (Structure3D.Site site : structure.sites())
        {
            if (drawnFragment[snapshot.getFragmentIndexOf(site.atom())] < 0 || site.implied() && !hydrogensShown)
            {
                continue;
            }
            placementOfSite[site.index()] = drawn.size();
            if (!site.implied())
            {
                placementOfAtom[site.atom()] = drawn.size();
            }
            drawn.add(site);
            spots.add(positions.get(site.index()));
        }
        for (Structure3D.Connection connection : structure.connections())
        {
            int first = placementOfSite[connection.first()];
            int second = placementOfSite[connection.second()];
            if (first >= 0 && second >= 0)
            {
                bonds.add(new Bonded(first, second, connection.order(),
                    connection.bond() < 0 ? OptionalInt.empty() : OptionalInt.of(connection.bond())));
            }
        }
    }

    /** The fragments holding any of the atoms, numbered from 0 in order, and -1 for the rest; every fragment if {@code only} is null. */
    private int[] chooseFragments(Set<Integer> only)
    {
        int[] chosen = new int[snapshot.getFragmentCount()];
        Arrays.fill(chosen, only == null ? 0 : -1);
        if (only != null)
        {
            for (int atom : only)
            {
                if (atom < 0 || atom >= molecule.atoms().size())
                {
                    throw new IllegalArgumentException("There is no atom " + atom + "; there are " + molecule.atoms().size());
                }
                chosen[snapshot.getFragmentIndexOf(atom)] = 0;
            }
        }
        int next = 0;
        for (int fragment = 0; fragment < chosen.length; fragment++)
        {
            if (chosen[fragment] >= 0)
            {
                chosen[fragment] = next++;
            }
        }
        if (next == 0)
        {
            throw new IllegalArgumentException("There are no atoms to draw");
        }
        return chosen;
    }

    /**
     * Draws the molecule exactly as a camera sees it.
     *
     * @param molecule the molecule
     * @param structure the molecule in space
     * @param conformer the index of the conformer to draw
     * @param camera where to look from
     * @param options how much to spell out
     * @return the drawing
     * @throws IllegalArgumentException if there is no such conformer
     */
    public static Structure2D fromCamera(Molecule molecule, Structure3D structure, int conformer, Camera camera, DepictionOptions options)
    {
        return fromCamera(molecule, structure, conformer, camera, options, null);
    }

    /**
     * Draws the molecule exactly as a camera sees it, leaving out the disconnected parts that none of the given atoms are in.
     *
     * @param molecule the molecule
     * @param structure the molecule in space
     * @param conformer the index of the conformer to draw
     * @param camera where to look from
     * @param options how much to spell out
     * @param only the atoms whose disconnected parts to draw, or null to draw every part
     * @return the drawing
     * @throws IllegalArgumentException if there is no such conformer or atom, or no atoms to draw
     */
    public static Structure2D fromCamera(Molecule molecule, Structure3D structure, int conformer, Camera camera, DepictionOptions options, Set<Integer> only)
    {
        ProjectedLayout layout = new ProjectedLayout(molecule, structure, conformer, options, only);
        Point[] points = new Point[layout.drawn.size()];
        for (int atom = 0; atom < points.length; atom++)
        {
            Camera.Projected seen = camera.project(layout.spots.get(atom));
            points[atom] = new Point(seen.x(), seen.y());
        }
        layout.scale(points, layout.bonds);
        return layout.build(points, false);
    }

    /**
     * Draws the molecule from the direction that keeps its atoms apart and its bonds evenly long.
     *
     * @param molecule the molecule
     * @param structure the molecule in space
     * @param conformer the index of the conformer to draw
     * @param options how much to spell out
     * @return the drawing
     * @throws IllegalArgumentException if there is no such conformer
     */
    public static Structure2D bestView(Molecule molecule, Structure3D structure, int conformer, DepictionOptions options)
    {
        return bestView(molecule, structure, conformer, options, null);
    }

    /**
     * Draws the molecule from the direction that keeps its atoms apart and its bonds evenly long, leaving out the
     * disconnected parts that none of the given atoms are in.
     *
     * @param molecule the molecule
     * @param structure the molecule in space
     * @param conformer the index of the conformer to draw
     * @param options how much to spell out
     * @param only the atoms whose disconnected parts to draw, or null to draw every part
     * @return the drawing
     * @throws IllegalArgumentException if there is no such conformer or atom, or no atoms to draw
     */
    public static Structure2D bestView(Molecule molecule, Structure3D structure, int conformer, DepictionOptions options, Set<Integer> only)
    {
        ProjectedLayout layout = new ProjectedLayout(molecule, structure, conformer, options, only);
        Point[] points = new Point[layout.drawn.size()];
        for (int fragment = 0; fragment < layout.snapshot.getFragmentCount(); fragment++)
        {
            List<Integer> members = new ArrayList<>();
            for (int atom = 0; atom < points.length; atom++)
            {
                if (layout.fragmentOf(atom) == fragment)
                {
                    members.add(atom);
                }
            }
            if (members.isEmpty())
            {
                continue;
            }

            int[] localIndex = new int[points.length];
            double[] positions = new double[members.size() * 3];
            for (int at = 0; at < members.size(); at++)
            {
                Vec3 spot = layout.spots.get(members.get(at));
                positions[at * 3] = spot.x();
                positions[at * 3 + 1] = spot.y();
                positions[at * 3 + 2] = spot.z();
                localIndex[members.get(at)] = at;
            }
            List<int[]> inside = new ArrayList<>();
            for (Bonded bond : layout.bonds)
            {
                if (layout.fragmentOf(bond.first()) == fragment)
                {
                    inside.add(new int[] {localIndex[bond.first()], localIndex[bond.second()]});
                }
            }

            double[] view = StructureViews.best(positions, inside.toArray(new int[0][]));
            for (int at = 0; at < members.size(); at++)
            {
                points[members.get(at)] = new Point(view[at * 2], view[at * 2 + 1]);
            }
        }
        return layout.build(points, true);
    }

    private int fragmentOf(int placement)
    {
        return drawnFragment[snapshot.getFragmentIndexOf(drawn.get(placement).atom())];
    }

    /** Scales the points about the origin so that the middling bond is one bond length. */
    private static void scale(Point[] points, List<Bonded> bonds)
    {
        double[] lengths = bonds.stream()
            .mapToDouble(bond -> points[bond.first()].minus(points[bond.second()]).length())
            .sorted()
            .toArray();
        if (lengths.length == 0)
        {
            return;
        }
        int middle = lengths.length / 2;
        double median = lengths.length % 2 == 1 ? lengths[middle] : (lengths[middle - 1] + lengths[middle]) / 2;
        if (median < 1e-9)
        {
            return;
        }
        for (int atom = 0; atom < points.length; atom++)
        {
            points[atom] = new Point(points[atom].x() / median, points[atom].y() / median);
        }
    }

    /** @param centred whether to move each disconnected part to the origin; false keeps the parts where they were seen */
    private Structure2D build(Point[] points, boolean centred)
    {
        if (centred)
        {
            centreParts(points);
        }

        boolean condensed = options.effectiveHydrogens() == HydrogenStyle.CONDENSED;
        List<AtomPlacement> atoms = new ArrayList<>();
        for (int index = 0; index < points.length; index++)
        {
            Structure3D.Site site = drawn.get(index);
            Atom atom = site.implied() ? null : molecule.atoms().get(site.atom());
            atoms.add(new AtomPlacement(
                index,
                site.element(),
                atom != null ? atom.isotope() : OptionalInt.empty(),
                site.charge(),
                atom != null && condensed ? molecule.totalHydrogenCount(atom) : 0,
                atom != null && atom.aromatic(),
                false,
                site.implied() ? OptionalInt.empty() : OptionalInt.of(site.atom()),
                fragmentOf(index),
                points[index]));
        }

        List<BondPlacement> placements = new ArrayList<>();
        int[] degrees = new int[atoms.size()];
        for (Bonded bond : bonds)
        {
            placements.add(new BondPlacement(bond.first(), bond.second(), bond.order(), BondStyle.PLAIN, bond.source()));
            degrees[bond.first()]++;
            degrees[bond.second()]++;
        }

        if (options.style() == DepictionStyle.SKELETAL)
        {
            for (int index = 0; index < atoms.size(); index++)
            {
                AtomPlacement atom = atoms.get(index);
                boolean plainCarbon = atom.element().equals("C") && atom.charge() == 0 && atom.isotope().isEmpty();
                atoms.set(index, atom.asVertex(plainCarbon && degrees[index] > 0));
            }
        }
        return new Structure2D(new Depiction(atoms, placements, ringsOf()), snapshot);
    }

    /** Moves each disconnected part so that the middle of its bounds is the origin, as a layout does. */
    private void centreParts(Point[] points)
    {
        for (int fragment = 0; fragment < snapshot.getFragmentCount(); fragment++)
        {
            List<Point> part = new ArrayList<>();
            for (int atom = 0; atom < points.length; atom++)
            {
                if (fragmentOf(atom) == fragment)
                {
                    part.add(points[atom]);
                }
            }
            if (part.isEmpty())
            {
                continue;
            }
            Point middle = Bounds.around(part).centre();
            for (int atom = 0; atom < points.length; atom++)
            {
                if (fragmentOf(atom) == fragment)
                {
                    points[atom] = points[atom].translate(-middle.x(), -middle.y());
                }
            }
        }
    }

    private List<RingPlacement> ringsOf()
    {
        List<RingPlacement> rings = new ArrayList<>();
        for (Molecule ring : molecule.rings())
        {
            List<Atom> order = new ArrayList<>();
            Atom first = ring.atoms().getFirst();
            Atom previous = null;
            Atom current = first;
            do
            {
                order.add(current);
                Atom next = null;
                for (Bond bond : ring.bondsOf(current))
                {
                    Atom other = bond.getOther(current);
                    if (other != previous)
                    {
                        next = other;
                        break;
                    }
                }
                previous = current;
                current = next;
            }
            while (current != first && current != null);

            List<Integer> members = new ArrayList<>();
            boolean aromatic = true;
            for (int at = 0; at < order.size(); at++)
            {
                members.add(placementOfAtom[molecule.atoms().indexOf(order.get(at))]);
                Bond bond = molecule.bondBetween(order.get(at), order.get((at + 1) % order.size())).orElseThrow();
                aromatic &= bond.order() == BondOrder.AROMATIC;
            }
            if (!members.contains(-1))
            {
                rings.add(new RingPlacement(members, aromatic && options.style() == DepictionStyle.SKELETAL));
            }
        }
        return rings;
    }
}
