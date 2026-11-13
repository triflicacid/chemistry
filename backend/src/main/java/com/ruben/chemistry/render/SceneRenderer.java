package com.ruben.chemistry.render;

import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.render.annotate.Annotations;
import com.ruben.chemistry.render.annotate.AtomLook;
import com.ruben.chemistry.render.annotate.BondLook;
import com.ruben.chemistry.render.scene.AnnotationLayer;
import com.ruben.chemistry.render.scene.Attribute;
import com.ruben.chemistry.render.scene.Element;
import com.ruben.chemistry.render.scene.Gradient;
import com.ruben.chemistry.render.scene.Looks;
import com.ruben.chemistry.render.scene.Scene;
import com.ruben.chemistry.render.scene.SceneBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Lays a {@link Depiction} out on a page as a {@link Scene}: shapes and text at pixel coordinates. See
 * {@link SvgRenderer} to write one as SVG.
 */
public final class SceneRenderer
{
    /** The colour of the debug outline around each cell. */
    private static final Colour CELL_OUTLINE = Colour.of("#1c7ed6");

    /** The colour of the debug outline around each fragment. */
    private static final Colour FRAGMENT_OUTLINE = Colour.of("#e03131");

    /** How wide one character of a symbol is, as a fraction of the font size. */
    private static final double CHARACTER_WIDTH = 0.62;

    /** How far up a superscript is lifted, as a fraction of the font size. */
    private static final double SUPERSCRIPT_RISE = 0.45;

    /** How far down a subscript is dropped, as a fraction of the font size. */
    private static final double SUBSCRIPT_DROP = 0.2;

    /** How long each dash of an aromatic bond's inner line is, in pixels. */
    private static final double AROMATIC_DASH = 5;

    /** How big the circle inside an aromatic ring is, as a fraction of the distance from its middle to its bonds. */
    private static final double RING_CIRCLE_SIZE = 0.6;

    /** How far along a gradient bond its own atom's colour has given way to the blend. */
    private static final double GRADIENT_SHOULDER = 0.25;

    /** How far either side of a blend the middle of a gradient bond drifts. */
    private static final double GRADIENT_DRIFT = 0.08;

    /** How nearly along one another two bonds have to run before neither closes up against the other. */
    private static final double GRAZING = 0.2;

    /** The most a bond carries on past a vertex, as a multiple of what it is closing up against. */
    private static final double FURTHEST_REACH = 2;

    /** The depiction being drawn. */
    private final Depiction depiction;

    /** How it should be drawn. */
    private final RenderStyle style;

    /** The snapshot the depiction's indices refer to, or {@code null} if not known. */
    private final MoleculeSnapshot snapshot;

    /** How individual atoms and bonds are restyled; only ever has a look for something when there is a snapshot. */
    private final Annotations annotations;

    /** Draws the halos and badges of the annotations. */
    private final AnnotationLayer annotationLayer;

    /** How thick the bond being drawn is, in pixels. */
    private double bondWidth;

    /** How far each atom holds a bond off, in pixels, indexed as the depiction's atoms are. */
    private final double[] clearances;

    /** The bonds meeting at each atom, indexed as the depiction's atoms are. */
    private final Map<Integer, List<BondPlacement>> bondsAt = new HashMap<>();

    /** The depiction's extent, which decides where the origin of the drawing goes. */
    private final Bounds bounds;

    /** How far past the outermost atom the drawing reaches, in pixels. */
    private final double overhang;

    /** The middle of a ring each bond is in, in depiction space, keyed by the pair of atoms it joins. */
    private final Map<Long, Point> ringCentres = new HashMap<>();

    /** The bonds, keyed by the pair of atoms they join, of rings drawn with a circle inside, which are drawn as single bonds. */
    private final Set<Long> circled = new HashSet<>();

    /** The scene being built up. */
    private final SceneBuilder svg;

    /** What the bond being drawn is painted with: a plain colour, or one of the gradients. */
    private String bondPaint;

    /** The cell each part was given, as {minX, minY, maxX, maxY} in depiction space; kept for debugging. */
    private final List<double[]> cells = new ArrayList<>();

    /**
     * @param depiction the depiction to draw
     * @param style how it should be drawn
     * @param snapshot the snapshot the depiction's indices refer to, or {@code null} if not known
     * @param annotations how individual atoms and bonds are restyled
     */
    private SceneRenderer(Depiction depiction, RenderStyle style, MoleculeSnapshot snapshot, Annotations annotations)
    {
        this.snapshot = snapshot;
        this.annotations = annotations;
        this.bondWidth = style.bondWidth();
        this.style = style;
        this.clearances = new double[depiction.atoms().size()];
        double widest = 0;
        for (AtomPlacement atom : depiction.atoms())
        {
            double clearance = clearanceOf(atom);
            clearances[atom.placementIndex()] = clearance;
            widest = Math.max(widest, clearance);
        }
        this.overhang = widest;
        this.depiction = arrange(depiction);
        this.bounds = this.depiction.bounds();
        this.bondPaint = style.bondColour().hex();
        this.svg = new SceneBuilder(width(), height());
        this.annotationLayer = new AnnotationLayer(svg, annotations.style());
        for (BondPlacement bond : depiction.bonds())
        {
            bondsAt.computeIfAbsent(bond.first(), atom -> new ArrayList<>()).add(bond);
            bondsAt.computeIfAbsent(bond.second(), atom -> new ArrayList<>()).add(bond);
        }
        for (RingPlacement ring : depiction.rings())
        {
            Point middle = centreOf(ring);
            List<Integer> members = ring.atoms();
            for (int member = 0; member < members.size(); member++)
            {
                long pair = pairOf(members.get(member), members.get((member + 1) % members.size()));
                ringCentres.putIfAbsent(pair, middle);
                if (ring.circle())
                {
                    circled.add(pair);
                }
            }
        }
    }

    /**
     * Sets the fragments of a depiction out as the style asks.
     *
     * @param local a depiction whose fragments are each centred on the origin
     * @return the same depiction with the fragments moved apart
     */
    private Depiction arrange(Depiction local)
    {
        int count = local.fragments().size();
        // nothing to set apart
        if (count < 2)
        {
            return local;
        }

        FragmentArrangement arrangement = style.fragmentArrangement();
        // pixels per depiction unit
        double scale = style.bondLength();

        // each fragment's extent as {minX, minY, maxX, maxY} in depiction space, grown by how far its atoms reach
        double[][] extent = new double[count][];
        for (AtomPlacement atom : local.atoms())
        {
            double reach = clearances[atom.placementIndex()] / scale;
            Point at = atom.position();
            double[] box = extent[atom.fragment()];
            // first atom seen in this fragment
            if (box == null)
            {
                extent[atom.fragment()] = new double[] {at.x() - reach, at.y() - reach, at.x() + reach, at.y() + reach};
                continue;
            }
            box[0] = Math.min(box[0], at.x() - reach);
            box[1] = Math.min(box[1], at.y() - reach);
            box[2] = Math.max(box[2], at.x() + reach);
            box[3] = Math.max(box[3], at.y() + reach);
        }

        double gap = style.fragmentGap() / scale;
        // how far to move each fragment, as {dx, dy}
        double[][] offset = switch (arrangement)
        {
            case FragmentArrangement.Grid grid -> offsetsInRows(grid, extent, gap);
            case FragmentArrangement.Linear lines -> offsetsInTracks(lines, extent, gap);
        };

        List<AtomPlacement> moved = new ArrayList<>(local.atoms().size());
        for (AtomPlacement atom : local.atoms())
        {
            double[] by = offset[atom.fragment()];
            moved.add(atom.at(atom.position().translate(by[0], by[1])));
        }
        // bonds and rings refer to atoms by index, which have not changed
        return new Depiction(moved, local.bonds(), local.rings(), local.fragments());
    }

    /**
     * Sets fragments out in rows, each row's width shared between its cells by weight.
     *
     * @param grid the arrangement
     * @param extent each fragment's extent as {minX, minY, maxX, maxY}, or {@code null} if it has no atoms
     * @param gap the space between neighbouring cells and rows
     * @return how far to move each fragment, as {dx, dy}
     */
    private double[][] offsetsInRows(FragmentArrangement.Grid grid, double[][] extent, double gap)
    {
        int count = extent.length;
        FragmentArrangement.GridSlot[] slot = new FragmentArrangement.GridSlot[count];
        int rows = 0;
        for (int fragment = 0; fragment < count; fragment++)
        {
            // a fragment with no atoms takes up no room
            if (extent[fragment] != null)
            {
                slot[fragment] = grid.slotOf(fragment);
                rows = Math.max(rows, slot[fragment].row() + 1);
            }
        }

        // every row is stretched to the widest width any of them needs
        double width = 0;
        double[] height = new double[rows];
        for (int fragment = 0; fragment < count; fragment++)
        {
            if (slot[fragment] == null)
            {
                continue;
            }
            FragmentArrangement.GridSlot at = slot[fragment];
            double[] box = extent[fragment];
            double needs = (box[2] - box[0]) * at.total() / at.width() + gap * (at.columnSpan() - 1);
            width = Math.max(width, needs);
            height[at.row()] = Math.max(height[at.row()], box[3] - box[1]);
        }

        // where each row starts, stacked downwards (screen y) with a gap between
        double[] top = new double[rows];
        for (int row = 1; row < rows; row++)
        {
            top[row] = top[row - 1] + height[row - 1] + gap;
        }

        double[][] offset = new double[count][];
        for (int fragment = 0; fragment < count; fragment++)
        {
            if (slot[fragment] == null)
            {
                offset[fragment] = new double[2];
                continue;
            }
            FragmentArrangement.GridSlot at = slot[fragment];
            double[] box = extent[fragment];
            double shared = width - gap * (at.columnSpan() - 1);
            double cellLeft = shared * at.before() / at.total() + gap * at.column();
            double cellWidth = shared * at.width() / at.total();
            double dx = cellLeft + (cellWidth - (box[2] - box[0])) * style.gridAlignment().fraction() - box[0];
            double midY = -(top[at.row()] + height[at.row()] / 2); // screen y is downwards
            cells.add(new double[] {cellLeft, -(top[at.row()] + height[at.row()]), cellLeft + cellWidth, -top[at.row()]});
            offset[fragment] = new double[] {dx, midY - (box[1] + box[3]) / 2};
        }
        return offset;
    }

    /**
     * Sets fragments out on shared rows and columns, each as big as its widest or tallest fragment.
     *
     * @param arrangement the arrangement
     * @param extent each fragment's extent as {minX, minY, maxX, maxY}, or {@code null} if it has no atoms
     * @param gap the space between neighbouring rows and columns
     * @return how far to move each fragment, as {dx, dy}
     */
    private double[][] offsetsInTracks(FragmentArrangement.Linear arrangement, double[][] extent, double gap)
    {
        int count = extent.length;
        // where each fragment sits in the grid, and how much of it the fragment needs
        FragmentArrangement.Position[] cell = new FragmentArrangement.Position[count];
        int columns = 0;
        int rows = 0;
        List<double[]> wide = new ArrayList<>();
        List<double[]> tall = new ArrayList<>();
        for (int fragment = 0; fragment < count; fragment++)
        {
            double[] box = extent[fragment];
            // a fragment with no atoms takes up no room
            if (box == null)
            {
                continue;
            }
            FragmentArrangement.Position at = arrangement.positionOf(fragment);
            cell[fragment] = at;
            columns = Math.max(columns, at.column() + at.columnSpan());
            rows = Math.max(rows, at.row() + at.rowSpan());
            wide.add(new double[] {at.column(), at.columnSpan(), box[2] - box[0]});
            tall.add(new double[] {at.row(), at.rowSpan(), box[3] - box[1]});
        }
        double[] width = sizeTracks(columns, gap, wide);
        double[] height = sizeTracks(rows, gap, tall);

        // where each column starts, stacked left to right with a gap between
        double[] left = new double[columns + 1];
        for (int column = 0; column < columns; column++)
        {
            left[column + 1] = left[column] + width[column] + gap;
        }
        // where each row starts, stacked downwards (screen y) with a gap between
        double[] top = new double[rows + 1];
        for (int row = 0; row < rows; row++)
        {
            top[row + 1] = top[row] + height[row] + gap;
        }

        double[][] offset = new double[count][];
        for (int fragment = 0; fragment < count; fragment++)
        {
            FragmentArrangement.Position at = cell[fragment];
            if (at == null)
            {
                offset[fragment] = new double[2];
                continue;
            }
            double[] box = extent[fragment];
            // the cells' span, without the gap trailing the last one
            double regionWidth = left[at.column() + at.columnSpan()] - gap - left[at.column()];
            double regionHeight = top[at.row() + at.rowSpan()] - gap - top[at.row()];
            double dx = left[at.column()] + (regionWidth - (box[2] - box[0])) * style.gridAlignment().fraction() - box[0];
            double midY = -(top[at.row()] + regionHeight / 2); // screen y is downwards
            cells.add(new double[] {left[at.column()], -(top[at.row()] + regionHeight), left[at.column()] + regionWidth, -top[at.row()]});
            offset[fragment] = new double[] {dx, midY - (box[1] + box[3]) / 2};
        }
        return offset;
    }

    /**
     * Sizes a row or column of tracks so each fragment fits the tracks it covers.
     *
     * @param tracks how many tracks there are
     * @param gap the space between neighbouring tracks
     * @param items each fragment as {first track, how many it covers, size it needs}
     * @return how big each track is
     */
    private static double[] sizeTracks(int tracks, double gap, List<double[]> items)
    {
        double[] size = new double[tracks];
        // narrow items first, so that a spanning one only claims what the others leave short
        items.sort(Comparator.comparingDouble(item -> item[1]));
        for (double[] item : items)
        {
            int first = (int) item[0];
            int span = (int) item[1];
            double have = gap * (span - 1);
            for (int track = first; track < first + span; track++)
            {
                have += size[track];
            }
            if (item[2] > have)
            {
                double extra = (item[2] - have) / span;
                for (int track = first; track < first + span; track++)
                {
                    size[track] += extra;
                }
            }
        }
        return size;
    }

    /**
     * @param first one atom's index
     * @param second another atom's index
     * @return a key for the pair, whichever way round they are given
     */
    private static long pairOf(int first, int second)
    {
        return ((long) Math.min(first, second) << Integer.SIZE) | Math.max(first, second);
    }

    /**
     * @param ring a ring
     * @return the middle of its atoms, in depiction space
     */
    private Point centreOf(RingPlacement ring)
    {
        double x = 0;
        double y = 0;
        for (int member : ring.atoms())
        {
            x += depiction.atom(member).position().x();
            y += depiction.atom(member).position().y();
        }
        return new Point(x / ring.atoms().size(), y / ring.atoms().size());
    }

    /**
     * Draws a depiction in the default style.
     *
     * @param depiction the depiction to draw
     * @return the scene
     * @throws NullPointerException if {@code depiction} is {@code null}
     */
    public static Scene render(Depiction depiction)
    {
        return render(depiction, RenderStyle.defaults());
    }

    /**
     * Draws a depiction.
     *
     * @param depiction the depiction to draw
     * @param style how it should be drawn
     * @return the scene
     */
    public static Scene render(Depiction depiction, RenderStyle style)
    {
        return new SceneRenderer(depiction, style, null, Annotations.none()).draw();
    }

    /**
     * Draws a layout's depiction.
     *
     * @param layout the layout to draw
     * @param style how it should be drawn
     * @return the scene
     */
    public static Scene render(Layout layout, RenderStyle style)
    {
        return render(layout, style, Annotations.none());
    }

    /**
     * Draws a layout's depiction, restyling individual atoms and bonds.
     * <p>
     * Halos are drawn behind the structure and badges on top of it. A hydrogen the depiction made up takes the look of
     * the atom it is attached to, apart from its badge. With no annotations the scene is the same as without.
     *
     * @param layout the layout to draw
     * @param style how it should be drawn
     * @param annotations how to restyle the layout's atoms and bonds, which belong to its snapshot's molecule
     * @return the scene
     */
    public static Scene render(Layout layout, RenderStyle style, Annotations annotations)
    {
        return new SceneRenderer(layout.depiction(), style, layout.snapshot(), annotations).draw();
    }

    /**
     * @return the whole scene, bonds first so that atoms sit over the ends of them
     */
    private Scene draw()
    {
        style.background().ifPresent(svg::background);
        if (style.metadata())
        {
            svg.attribute("data-metadata", "true");
        }

        if (style.debugLayout())
        {
            drawDebugLayout();
        }
        drawHalos();
        for (BondPlacement bond : depiction.bonds())
        {
            drawBond(bond);
        }
        for (RingPlacement ring : depiction.rings())
        {
            if (ring.circle())
            {
                drawRingCircle(ring);
            }
        }
        for (AtomPlacement atom : depiction.atoms())
        {
            drawAtom(atom);
        }
        drawBadges();
        return svg.build();
    }

    /**
     * @param atom an atom of the depiction
     * @return how it is restyled; a hydrogen the depiction made up takes the look of the atom it is on, but not its badge
     */
    private AtomLook lookOf(AtomPlacement atom)
    {
        if (snapshot == null)
        {
            return AtomLook.NONE;
        }
        if (atom.sourceAtom().isPresent())
        {
            return annotations.lookOf(snapshot.getAtom(atom.sourceAtom().getAsInt()));
        }

        for (BondPlacement bond : bondsAt.getOrDefault(atom.placementIndex(), List.of()))
        {
            AtomPlacement parent = depiction.atom(bond.first() == atom.placementIndex() ? bond.second() : bond.first());
            if (parent.sourceAtom().isPresent())
            {
                AtomLook look = annotations.lookOf(snapshot.getAtom(parent.sourceAtom().getAsInt()));
                return new AtomLook(look.fill(), look.outline(), look.opacity(), look.saturation(), look.halo(), Optional.empty());
            }
        }
        return AtomLook.NONE;
    }

    /**
     * @param bond a bond of the depiction
     * @return how it is restyled; a bond to a hydrogen the depiction made up only takes on its atom's opacity
     */
    private BondLook lookOf(BondPlacement bond)
    {
        if (snapshot == null)
        {
            return BondLook.NONE;
        }
        if (bond.sourceBond().isPresent())
        {
            return annotations.lookOf(snapshot.getBond(bond.sourceBond().getAsInt()));
        }

        BondLook look = BondLook.NONE;
        for (int end : new int[] {bond.first(), bond.second()})
        {
            AtomPlacement atom = depiction.atom(end);
            if (atom.isGeneratedHydrogen() && lookOf(atom).opacity().isPresent())
            {
                look = look.withOpacity(lookOf(atom).opacity().getAsDouble());
            }
        }
        return look;
    }

    /**
     * @param atom an atom of the depiction
     * @return how far a halo around it reaches before it is scaled, in pixels
     */
    private double haloRadiusOf(AtomPlacement atom)
    {
        if (atom.vertex())
        {
            // a bare vertex has no symbol to surround, so its halo only needs to stand just proud of the bonds' halos
            return bondHaloWidth() * 0.6;
        }
        return Math.max(clearances[atom.placementIndex()], style.radiusOf(atom.element()));
    }

    /**
     * @return how thick a bond's halo is before it is scaled, in pixels
     */
    private double bondHaloWidth()
    {
        return Math.max(style.wedgeWidth(), 3 * style.bondGap());
    }

    /**
     * Draws the halos of the annotations, which go behind everything else.
     */
    private void drawHalos()
    {
        for (BondPlacement bond : depiction.bonds())
        {
            BondLook look = lookOf(bond);
            if (look.halo().isPresent())
            {
                Point from = toPage(depiction.atom(bond.first()).position());
                Point to = toPage(depiction.atom(bond.second()).position());
                annotationLayer.bondHalo(from, to, bondHaloWidth(), look.halo().get());
            }
        }
        for (AtomPlacement atom : depiction.atoms())
        {
            AtomLook look = lookOf(atom);
            if (look.halo().isPresent())
            {
                annotationLayer.atomHalo(toPage(atom.position()), haloRadiusOf(atom), look.halo().get());
            }
        }
    }

    /**
     * Draws the badges of the annotations, which go on top of everything else: a bond's at its middle, an atom's
     * up and to the right of it, and a bare vertex's directly over it.
     */
    private void drawBadges()
    {
        for (BondPlacement bond : depiction.bonds())
        {
            BondLook look = lookOf(bond);
            if (look.badge().isPresent())
            {
                Point from = toPage(depiction.atom(bond.first()).position());
                Point to = toPage(depiction.atom(bond.second()).position());
                annotationLayer.badge(from.plus(to.minus(from).scale(0.5)), look.badge().get());
            }
        }
        for (AtomPlacement atom : depiction.atoms())
        {
            AtomLook look = lookOf(atom);
            if (look.badge().isPresent())
            {
                Point centre = toPage(atom.position());
                if (atom.vertex())
                {
                    annotationLayer.badge(centre, look.badge().get());
                    continue;
                }
                double offset = haloRadiusOf(atom) * 0.75 + annotations.style().badgeFontSize() / 2;
                annotationLayer.badge(centre.plus(new Point(offset, -offset)), look.badge().get());
            }
        }
    }

    private void drawDebugLayout()
    {
        double scale = style.bondLength();
        // solid blue around every cell
        for (double[] cell : cells)
        {
            outline(cell, CELL_OUTLINE);
        }

        // as {minX, minY, maxX, maxY} in depiction space, by fragment
        Map<Integer, double[]> boxes = new TreeMap<>();
        for (AtomPlacement atom : depiction.atoms())
        {
            double reach = clearances[atom.placementIndex()] / scale;
            Point at = atom.position();
            double[] box = boxes.computeIfAbsent(atom.fragment(),
                fragment -> new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
            box[0] = Math.min(box[0], at.x() - reach);
            box[1] = Math.min(box[1], at.y() - reach);
            box[2] = Math.max(box[2], at.x() + reach);
            box[3] = Math.max(box[3], at.y() + reach);
        }

        // dashed red around every fragment
        for (double[] box : boxes.values())
        {
            outline(box, FRAGMENT_OUTLINE, 4, 3);
        }
    }

    /**
     * @param box the rectangle to outline, as {minX, minY, maxX, maxY} in depiction space
     * @param colour the line's colour
     * @param dashes how long its dashes and the gaps between them are, or nothing for a solid line
     */
    private void outline(double[] box, Colour colour, double... dashes)
    {
        double scale = style.bondLength();
        Point corner = toPage(new Point(box[0], box[3]));
        Element.Rect outline = svg.rect(corner, (box[2] - box[0]) * scale, (box[3] - box[1]) * scale)
            .fill("none").stroke(colour).strokeWidth(1);
        if (dashes.length > 0)
        {
            outline.dash(dashes);
        }
    }

    /**
     * @return how many pixels wide the document is
     */
    private double width()
    {
        return bounds.width() * style.bondLength() + 2 * (overhang + style.margin());
    }

    /**
     * @return how many pixels tall the document is
     */
    private double height()
    {
        return bounds.height() * style.bondLength() + 2 * (overhang + style.margin());
    }

    /**
     * Turns a point in depiction space into a point on the page.
     *
     * @param point where the thing sits in depiction space
     * @return where it sits on the page, in pixels
     */
    private Point toPage(Point point)
    {
        double inset = overhang + style.margin();
        return new Point(
            (point.x() - bounds.minX()) * style.bondLength() + inset,
            (bounds.maxY() - point.y()) * style.bondLength() + inset);
    }

    /**
     * Draws one bond, held off each of its atoms so that it does not run under a symbol.
     *
     * @param bond the bond to draw
     */
    private void drawBond(BondPlacement bond)
    {
        AtomPlacement firstAtom = depiction.atom(bond.first());
        AtomPlacement secondAtom = depiction.atom(bond.second());
        Point from = toPage(firstAtom.position());
        Point to = toPage(secondAtom.position());
        BondLook look = lookOf(bond);
        Point along = to.minus(from);
        double length = along.length();
        if (length <= 0)
        {
            return;
        }

        Point unit = along.scale(1 / length);
        Point start = from.plus(unit.scale(clearances[bond.first()] - reachOf(bond, bond.first(), unit)));
        Point end = to.minus(unit.scale(clearances[bond.second()] - reachOf(bond, bond.second(), unit.scale(-1))));
        if (end.minus(start).length() <= 0 || unit.x() * (end.x() - start.x()) + unit.y() * (end.y() - start.y()) <= 0)
        {
            // The two atoms are so close that their symbols already meet; there is no room for a line.
            return;
        }

        bondPaint = look.colour().map(colour -> colour.hex()).orElseGet(() -> paintFor(firstAtom, secondAtom, start, end));
        bondWidth = style.bondWidth() * look.widthScale().orElse(1);

        boolean grouped = style.metadata() || look.opacity().isPresent();
        if (style.metadata())
        {
            openBond(bond, start, end, look);
        }
        else if (grouped)
        {
            Looks.apply(svg.group(), look);
        }

        switch (bond.style())
        {
            case WEDGE -> drawWedge(start, end, unit);
            case HASH -> drawHash(start, end, unit);
            case PLAIN -> drawPlain(start, end, unit, bond);
        }

        if (grouped)
        {
            svg.close();
        }
    }

    /**
     * Opens the group a bond is drawn in, which says which bond of the molecule it is, and lays an
     * invisible, wider line along it so that it is easy to click.
     *
     * @param bond the bond being drawn
     * @param start where the line begins, in pixels
     * @param end where the line ends, in pixels
     * @param look how the bond is restyled
     */
    private void openBond(BondPlacement bond, Point start, Point end, BondLook look)
    {
        AtomPlacement first = depiction.atom(bond.first());
        AtomPlacement second = depiction.atom(bond.second());

        Element.Group group = svg.group().cssClass("bond")
            .attribute("data-bond", bond.sourceBond().isPresent() ? String.valueOf(bond.sourceBond().getAsInt()) : null)
            .attribute("data-order", bond.order().name().toLowerCase(Locale.ROOT))
            .attribute("data-style", bond.style().name().toLowerCase(Locale.ROOT))
            .attribute("data-first", String.valueOf(bond.first()))
            .attribute("data-second", String.valueOf(bond.second()))
            .attribute("data-first-atom", first.sourceAtom().isPresent() ? String.valueOf(first.sourceAtom().getAsInt()) : null)
            .attribute("data-second-atom", second.sourceAtom().isPresent() ? String.valueOf(second.sourceAtom().getAsInt()) : null)
            .attribute("data-fragment", String.valueOf(first.fragment()));
        Looks.apply(group, look);

        svg.line(start, end).stroke("transparent")
            .strokeWidth(Math.max(style.wedgeWidth(), 3 * style.bondGap()))
            .roundCaps();
    }

    /**
     * Draws the circle inside an aromatic ring.
     *
     * @param ring the ring
     */
    private void drawRingCircle(RingPlacement ring)
    {
        Point middle = toPage(centreOf(ring));
        List<Integer> members = ring.atoms();
        double apothem = 0;
        for (int member = 0; member < members.size(); member++)
        {
            Point from = toPage(depiction.atom(members.get(member)).position());
            Point to = toPage(depiction.atom(members.get((member + 1) % members.size())).position());
            apothem += middle.distanceTo(from.plus(to.minus(from).scale(0.5)));
        }
        apothem /= members.size();

        svg.circle(middle, apothem * RING_CIRCLE_SIZE)
            .fill("none").stroke(style.bondColour()).strokeWidth(style.bondWidth());
    }

    /**
     * @param bond a bond
     * @return the order to draw it with, which is single where a circle inside its ring stands for the aromaticity,
     * or where the bond lies outside every ring (as between the rings of biphenyl) and so cannot be delocalised
     */
    private BondOrder orderOf(BondPlacement bond)
    {
        if (bond.order() != BondOrder.AROMATIC)
        {
            return bond.order();
        }
        long pair = pairOf(bond.first(), bond.second());
        return circled.contains(pair) || !ringCentres.containsKey(pair) ? BondOrder.SINGLE : BondOrder.AROMATIC;
    }

    /**
     * Works out what a bond is painted with: the one bond colour, or, where a gradient was asked
     * for, a run from the colour of the atom at one end to the colour of the atom at the other.
     * <p>
     * The gradient turns quickly over the quarter nearest each end and then sits nearly still
     * across the middle half, so that each end reads as its own element while the bond as a whole
     * still looks of a piece.
     *
     * @param firstAtom the atom the bond starts at
     * @param secondAtom the atom the bond ends at
     * @param start where the line begins, in pixels
     * @param end where the line ends, in pixels
     * @return a paint to hand to {@code stroke} or {@code fill}
     */
    private String paintFor(AtomPlacement firstAtom, AtomPlacement secondAtom, Point start, Point end)
    {
        if (!style.bondGradient())
        {
            return style.bondColour().hex();
        }

        Colour from = style.colourOf(firstAtom.element());
        Colour to = style.colourOf(secondAtom.element());
        if (from.equals(to))
        {
            // Both ends agree, so a gradient would only be a longer way of writing one colour.
            return from.hex();
        }

        return gradient(from, to, start, end);
    }

    /**
     * Writes a gradient running the length of one bond, and names it.
     *
     * @param from the colour at the start
     * @param to the colour at the end
     * @param start where the line begins, in pixels
     * @param end where the line ends, in pixels
     * @return a reference to it, for {@code stroke} or {@code fill}
     */
    private String gradient(Colour from, Colour to, Point start, Point end)
    {
        return svg.linearGradient("bond", start, end, List.of(
            new Gradient.Stop(0, from),
            new Gradient.Stop(GRADIENT_SHOULDER, from.mix(to, 0.5 - GRADIENT_DRIFT)),
            new Gradient.Stop(1 - GRADIENT_SHOULDER, from.mix(to, 0.5 + GRADIENT_DRIFT)),
            new Gradient.Stop(1, to)));
    }

    /**
     * Works out how far a bond carries on past a bare vertex.
     *
     * @param bond the bond being drawn
     * @param atom the end of it to look at
     * @param outwards the direction the bond runs in, away from {@code atom}, of unit length
     * @return how many pixels past the atom the bond should carry on
     */
    private double reachOf(BondPlacement bond, int atom, Point outwards)
    {
        if (bond.style().isStereo() || clearances[atom] > 0)
        {
            // only a bare vertex has bonds meeting at a point; a symbol holds them apart anyway
            return 0;
        }

        Point centre = toPage(depiction.atom(atom).position());
        double reach = 0;
        for (BondPlacement other : bondsAt.get(atom))
        {
            double half = strayOf(other);
            if (other == bond || half <= 0)
            {
                continue;
            }

            int far = other.first() == atom ? other.second() : other.first();
            Point away = toPage(depiction.atom(far).position()).minus(centre);
            double length = away.length();
            if (length <= 0)
            {
                continue;
            }

            // how much of this bond's direction lies across the other's, which is what has to be covered
            double across = Math.abs(outwards.cross(away.scale(1 / length)));
            if (across < GRAZING)
            {
                // the two run so nearly along one another that their lines are already side by side
                continue;
            }
            reach = Math.max(reach, Math.min(half / across, FURTHEST_REACH * half));
        }
        return reach;
    }

    /**
     * @param bond a bond
     * @return how far its outermost line strays from its middle, in pixels, or {@code 0}
     */
    private double strayOf(BondPlacement bond)
    {
        if (bond.style().isStereo())
        {
            return 0;
        }
        return switch (orderOf(bond))
        {
            case SINGLE, TRIPLE -> 0;
            case DOUBLE, AROMATIC -> style.bondGap() / 2;
            case QUADRUPLE -> 1.5 * style.bondGap();
        };
    }

    /**
     * Draws a bond as the one to four parallel lines its order calls for.
     *
     * @param start where the line begins, in pixels
     * @param end where the line ends, in pixels
     * @param unit the direction it runs in, of unit length
     * @param bond the bond being drawn
     */
    private void drawPlain(Point start, Point end, Point unit, BondPlacement bond)
    {
        Point across = new Point(-unit.y(), unit.x());
        double gap = style.bondGap();

        switch (orderOf(bond))
        {
            case SINGLE -> line(start, end, false);
            case DOUBLE -> parallels(start, end, across, gap / 2, -gap / 2);
            case TRIPLE -> parallels(start, end, across, gap, 0, -gap);
            case QUADRUPLE -> parallels(start, end, across, 1.5 * gap, gap / 2, -gap / 2, -1.5 * gap);
            case AROMATIC ->
            {
                // the dashed line goes inside the ring, where there is one
                Point inside = across.scale(-1);
                Point ringCentre = ringCentres.get(pairOf(bond.first(), bond.second()));
                if (ringCentre != null)
                {
                    Point towards = toPage(ringCentre).minus(start);
                    if (towards.x() * across.x() + towards.y() * across.y() > 0)
                    {
                        inside = across;
                    }
                }
                line(start.minus(inside.scale(gap / 2)), end.minus(inside.scale(gap / 2)), false);
                line(start.plus(inside.scale(gap / 2)), end.plus(inside.scale(gap / 2)), true);
            }
        }
    }

    /**
     * Draws several copies of one line, each shifted sideways.
     *
     * @param start where the line begins, in pixels
     * @param end where the line ends, in pixels
     * @param across the sideways direction, of unit length
     * @param offsets how far sideways to shift each copy, in pixels
     */
    private void parallels(Point start, Point end, Point across, double... offsets)
    {
        for (double offset : offsets)
        {
            Point shift = across.scale(offset);
            line(start.plus(shift), end.plus(shift), false);
        }
    }

    /**
     * Draws a bond coming out of the page, as a triangle widening away from its stereocentre.
     *
     * @param start the narrow end, at the stereocentre, in pixels
     * @param end the wide end, in pixels
     * @param unit the direction it runs in, of unit length
     */
    private void drawWedge(Point start, Point end, Point unit)
    {
        Point across = new Point(-unit.y(), unit.x()).scale(style.wedgeWidth() / 2);
        Point left = end.plus(across);
        Point right = end.minus(across);

        svg.polygon(List.of(start, left, right))
            .fill(bondPaint).stroke(bondPaint).strokeWidth(bondWidth / 2).roundJoins();
    }

    /**
     * Draws a bond going into the page, as a ladder of marks widening away from its stereocentre.
     *
     * @param start the narrow end, at the stereocentre, in pixels
     * @param end the wide end, in pixels
     * @param unit the direction it runs in, of unit length
     */
    private void drawHash(Point start, Point end, Point unit)
    {
        Point across = new Point(-unit.y(), unit.x());
        double length = end.minus(start).length();
        int marks = Math.max(2, (int) Math.round(length / style.hashSpacing()));

        for (int mark = 0; mark <= marks; mark++)
        {
            // The first mark sits on the stereocentre itself, so it is drawn as a point rather than a line.
            double fraction = (double) mark / marks;
            Point centre = start.plus(unit.scale(length * fraction));
            Point half = across.scale(style.wedgeWidth() * fraction / 2);
            if (half.length() <= 0)
            {
                continue;
            }
            line(centre.plus(half), centre.minus(half), false);
        }
    }

    /**
     * Writes one straight line in the bond colour.
     *
     * @param from where it begins, in pixels
     * @param to where it ends, in pixels
     * @param dashed whether to break it up, as the inner line of an aromatic bond is
     */
    private void line(Point from, Point to, boolean dashed)
    {
        Element.Line line = svg.line(from, to).stroke(bondPaint).strokeWidth(bondWidth).roundCaps();
        if (dashed)
        {
            line.dash(AROMATIC_DASH, AROMATIC_DASH);
        }
    }

    /**
     * Draws one atom, either as its symbol or as a ball.
     *
     * @param atom the atom to draw
     */
    private void drawAtom(AtomPlacement atom)
    {
        AtomLook look = lookOf(atom);
        boolean grouped = style.metadata() || look.opacity().isPresent();
        if (style.metadata())
        {
            openAtom(atom, look);
        }
        else if (grouped)
        {
            Looks.apply(svg.group(), look);
        }

        if (style.atomShape() == AtomShape.CIRCLE)
        {
            drawCircle(atom, look);
        }
        else if (!atom.vertex())
        {
            // a bare vertex is the corner its bonds already draw, so there is nothing to write
            drawLabel(atom, look);
        }

        if (grouped)
        {
            svg.close();
        }
    }

    /**
     * Opens the group an atom is drawn in, which says which atom of the molecule it is, and lays an
     * invisible disc over it so that it is easy to click, even where it is a bare vertex.
     *
     * @param atom the atom being drawn
     * @param look how the atom is restyled
     */
    private void openAtom(AtomPlacement atom, AtomLook look)
    {
        Point centre = toPage(atom.position());

        Element.Group group = svg.group().cssClass("atom")
            .attribute("data-atom", atom.sourceAtom().isPresent() ? String.valueOf(atom.sourceAtom().getAsInt()) : null)
            .attribute("data-placement", String.valueOf(atom.placementIndex()))
            .attribute("data-fragment", String.valueOf(atom.fragment()))
            .attribute("data-element", atom.element())
            .attribute("data-isotope", atom.isotope().isPresent() ? String.valueOf(atom.isotope().getAsInt()) : null)
            .attribute("data-charge", String.valueOf(atom.charge()))
            .attribute("data-hydrogens", String.valueOf(atom.hydrogens()))
            .attribute("data-aromatic", String.valueOf(atom.aromatic()))
            .attribute("data-implicit", String.valueOf(atom.isGeneratedHydrogen()));
        Looks.apply(group, look);

        svg.circle(centre, Math.max(clearances[atom.placementIndex()], style.radiusOf(atom.element()))).fill("transparent");
    }

    /**
     * Draws an atom as a ball in its own colour, outlined so that a white hydrogen still shows up.
     *
     * @param atom the atom to draw
     * @param look how the atom is restyled
     */
    private void drawCircle(AtomPlacement atom, AtomLook look)
    {
        svg.circle(toPage(atom.position()), style.radiusOf(atom.element()))
            .fill(Looks.fill(look, style.colourOf(atom.element())))
            .stroke(Looks.outline(look, style.atomOutline()))
            .strokeWidth(style.atomOutlineWidth());
    }

    /**
     * Draws an atom as its symbol in its own colour, with its mass number above and before it, the
     * hydrogens gathered into it written after it, and its charge above and after that.
     * <p>
     * The symbol is outlined in the background's stead, which is what keeps a white hydrogen
     * readable on a white page.
     *
     * @param atom the atom to draw
     * @param look how the atom is restyled
     */
    private void drawLabel(AtomPlacement atom, AtomLook look)
    {
        Point centre = toPage(atom.position());

        Element.Text label = svg.text(centre)
            .attribute("font-family", style.fontFamily())
            .attribute("font-size", style.fontSize())
            .fill(Looks.fill(look, style.colourOf(atom.element())))
            .stroke(Looks.outline(look, style.atomOutline()))
            .strokeWidth(style.atomOutlineWidth())
            .attribute("paint-order", "stroke")
            .attribute("text-anchor", "middle")
            .attribute("dominant-baseline", "central");

        List<Run> runs = runsOf(atom);
        if (runs.size() == 1)
        {
            // one plain run needs no mark-up of its own, which keeps a simple label simple
            label.content(runs.getFirst().text());
        }
        else
        {
            double baseline = 0;
            for (Run run : runs)
            {
                label.span(run.text(),
                    Attribute.of("font-size", style.fontSize() * run.scale()),
                    Attribute.of("dy", run.rise() - baseline));
                baseline = run.rise();
            }
        }
    }

    /**
     * Breaks a label into the pieces it is written in, in the order they are written.
     *
     * @param atom the atom to write
     * @return its mass number, symbol, hydrogens and charge, as far as each applies
     */
    private List<Run> runsOf(AtomPlacement atom)
    {
        double superscript = -style.fontSize() * SUPERSCRIPT_RISE;
        double subscript = style.fontSize() * SUBSCRIPT_DROP;

        List<Run> runs = new ArrayList<>();
        if (style.isotopes() && atom.isotope().isPresent())
        {
            runs.add(new Run(Integer.toString(atom.isotope().getAsInt()), style.superscriptScale(), superscript));
        }
        runs.add(new Run(atom.element(), 1, 0));
        if (atom.hydrogens() > 0)
        {
            runs.add(new Run("H", 1, 0));
            if (atom.hydrogens() > 1)
            {
                runs.add(new Run(Integer.toString(atom.hydrogens()), style.superscriptScale(), subscript));
            }
        }
        if (style.charges() && atom.charge() != 0)
        {
            runs.add(new Run(chargeOf(atom.charge()), style.superscriptScale(), superscript));
        }
        return runs;
    }

    /**
     * One piece of a label, written in one go.
     *
     * @param text what it says
     * @param scale how big it is written, as a fraction of the font size
     * @param rise how far it sits off the line, in pixels, negative being upwards
     */
    private record Run(String text, double scale, double rise)
    {
    }

    /**
     * @param charge a net formal charge, which is never zero here
     * @return how it is written beside a symbol, e.g. {@code "+"}, {@code "2-"}
     */
    private static String chargeOf(int charge)
    {
        String sign = charge > 0 ? "+" : "-";
        int size = Math.abs(charge);
        return size == 1 ? sign : size + sign;
    }

    /**
     * Measures a label as it is actually written, superscripts and all, since the whole run is
     * centred on the atom and so a mass number pushes the symbol aside rather than overhanging it.
     *
     * @param atom the atom to measure
     * @return how many pixels wide its label is
     */
    private double labelWidthOf(AtomPlacement atom)
    {
        double characters = 0;
        for (Run run : runsOf(atom))
        {
            characters += run.scale() * run.text().length();
        }
        return CHARACTER_WIDTH * style.fontSize() * characters;
    }

    /**
     * @param atom the atom to measure
     * @return how many pixels of clear space it wants around it
     */
    private double clearanceOf(AtomPlacement atom)
    {
        if (style.atomShape() == AtomShape.CIRCLE)
        {
            return style.radiusOf(atom.element()) + style.atomOutlineWidth() / 2;
        }
        if (atom.vertex())
        {
            // bonds have to meet at a bare vertex, since the corner they make is all that marks it
            return 0;
        }
        return Math.max(labelWidthOf(atom) / 2, style.fontSize() / 2) + style.labelPadding();
    }
}
