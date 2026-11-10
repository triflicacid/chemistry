package com.ruben.chemistry.render;

import com.ruben.chemistry.core.BondOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Draws a {@link Depiction} as an SVG document.
 */
public final class SvgRenderer
{
    /** How wide one character of a symbol is, as a fraction of the font size. */
    private static final double CHARACTER_WIDTH = 0.62;

    /** How far up a superscript is lifted, as a fraction of the font size. */
    private static final double SUPERSCRIPT_RISE = 0.45;

    /** How far down a subscript is dropped, as a fraction of the font size. */
    private static final double SUBSCRIPT_DROP = 0.2;

    /** How many decimal places a coordinate is written to, which is finer than any screen. */
    private static final int PLACES = 3;

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

    /** The body of the document being built up. */
    private final StringBuilder svg = new StringBuilder();

    /** The gradients the bonds drawn so far have asked for. */
    private final StringBuilder defs = new StringBuilder();

    /** What the bond being drawn is painted with: a plain colour, or one of the gradients. */
    private String bondPaint;

    /** How many gradients have been written, which is what keeps their names apart. */
    private int gradients;

    /**
     * @param depiction the depiction to draw
     * @param style how it should be drawn
     */
    private SvgRenderer(Depiction depiction, RenderStyle style)
    {
        this.depiction = depiction;
        this.style = style;
        this.bounds = depiction.bounds();
        this.clearances = new double[depiction.atoms().size()];
        double widest = 0;
        for (AtomPlacement atom : depiction.atoms())
        {
            double clearance = clearanceOf(atom);
            clearances[atom.index()] = clearance;
            widest = Math.max(widest, clearance);
        }
        this.overhang = widest;
        this.bondPaint = style.bondColour().hex();
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
     * @return a standalone SVG document
     * @throws NullPointerException if {@code depiction} is {@code null}
     */
    public static String render(Depiction depiction)
    {
        return render(depiction, RenderStyle.defaults());
    }

    /**
     * Draws a depiction.
     *
     * @param depiction the depiction to draw
     * @param style how it should be drawn
     * @return a standalone SVG document
     * @throws NullPointerException if either argument is {@code null}
     */
    public static String render(Depiction depiction, RenderStyle style)
    {
        Objects.requireNonNull(depiction, "depiction");
        Objects.requireNonNull(style, "style");
        return new SvgRenderer(depiction, style).draw();
    }

    /**
     * @return the whole document, bonds first so that atoms sit over the ends of them
     */
    private String draw()
    {
        double width = width();
        double height = height();

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

        StringBuilder document = new StringBuilder();
        document.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(number(width))
            .append("\" height=\"").append(number(height))
            .append("\" viewBox=\"0 0 ").append(number(width)).append(' ').append(number(height))
            .append("\">\n");

        if (!defs.isEmpty())
        {
            document.append("  <defs>\n").append(defs).append("  </defs>\n");
        }

        style.background().ifPresent(colour ->
            document.append("  <rect x=\"0\" y=\"0\" width=\"").append(number(width))
                .append("\" height=\"").append(number(height))
                .append("\" fill=\"").append(colour.hex()).append("\"/>\n"));

        return document.append(svg).append("</svg>\n").toString();
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

        bondPaint = paintFor(firstAtom, secondAtom, start, end);

        switch (bond.style())
        {
            case WEDGE -> drawWedge(start, end, unit);
            case HASH -> drawHash(start, end, unit);
            case PLAIN -> drawPlain(start, end, unit, bond);
        }
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

        svg.append("  <circle cx=\"").append(number(middle.x()))
            .append("\" cy=\"").append(number(middle.y()))
            .append("\" r=\"").append(number(apothem * RING_CIRCLE_SIZE))
            .append("\" fill=\"none\" stroke=\"").append(style.bondColour().hex())
            .append("\" stroke-width=\"").append(number(style.bondWidth()))
            .append("\"/>\n");
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
        String id = "bond" + gradients++;

        defs.append("    <linearGradient id=\"").append(id)
            .append("\" gradientUnits=\"userSpaceOnUse\" x1=\"").append(number(start.x()))
            .append("\" y1=\"").append(number(start.y()))
            .append("\" x2=\"").append(number(end.x()))
            .append("\" y2=\"").append(number(end.y())).append("\">\n");
        stop(0, from);
        stop(GRADIENT_SHOULDER, from.mix(to, 0.5 - GRADIENT_DRIFT));
        stop(1 - GRADIENT_SHOULDER, from.mix(to, 0.5 + GRADIENT_DRIFT));
        stop(1, to);
        defs.append("    </linearGradient>\n");

        return "url(#" + id + ")";
    }

    /**
     * Writes one stop of the gradient being built up.
     *
     * @param offset how far along the gradient it sits, from 0 to 1
     * @param colour the colour it holds
     */
    private void stop(double offset, Colour colour)
    {
        defs.append("      <stop offset=\"").append(number(offset))
            .append("\" stop-color=\"").append(colour.hex()).append("\"/>\n");
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

        svg.append("  <polygon points=\"")
            .append(number(start.x())).append(',').append(number(start.y())).append(' ')
            .append(number(left.x())).append(',').append(number(left.y())).append(' ')
            .append(number(right.x())).append(',').append(number(right.y()))
            .append("\" fill=\"").append(bondPaint)
            .append("\" stroke=\"").append(bondPaint)
            .append("\" stroke-width=\"").append(number(style.bondWidth() / 2))
            .append("\" stroke-linejoin=\"round\"/>\n");
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
        svg.append("  <line x1=\"").append(number(from.x()))
            .append("\" y1=\"").append(number(from.y()))
            .append("\" x2=\"").append(number(to.x()))
            .append("\" y2=\"").append(number(to.y()))
            .append("\" stroke=\"").append(bondPaint)
            .append("\" stroke-width=\"").append(number(style.bondWidth()))
            .append("\" stroke-linecap=\"round\"");
        if (dashed)
        {
            svg.append(" stroke-dasharray=\"").append(number(AROMATIC_DASH)).append(' ').append(number(AROMATIC_DASH)).append('"');
        }
        svg.append("/>\n");
    }

    /**
     * Draws one atom, either as its symbol or as a ball.
     *
     * @param atom the atom to draw
     */
    private void drawAtom(AtomPlacement atom)
    {
        if (style.atomShape() == AtomShape.CIRCLE)
        {
            drawCircle(atom);
        }
        else if (!atom.vertex())
        {
            // a bare vertex is the corner its bonds already draw, so there is nothing to write
            drawLabel(atom);
        }
    }

    /**
     * Draws an atom as a ball in its own colour, outlined so that a white hydrogen still shows up.
     *
     * @param atom the atom to draw
     */
    private void drawCircle(AtomPlacement atom)
    {
        Point centre = toPage(atom.position());
        svg.append("  <circle cx=\"").append(number(centre.x()))
            .append("\" cy=\"").append(number(centre.y()))
            .append("\" r=\"").append(number(style.radiusOf(atom.element())))
            .append("\" fill=\"").append(style.colourOf(atom.element()).hex())
            .append("\" stroke=\"").append(style.atomOutline().hex())
            .append("\" stroke-width=\"").append(number(style.atomOutlineWidth()))
            .append("\"/>\n");
    }

    /**
     * Draws an atom as its symbol in its own colour, with its mass number above and before it, the
     * hydrogens gathered into it written after it, and its charge above and after that.
     * <p>
     * The symbol is outlined in the background's stead, which is what keeps a white hydrogen
     * readable on a white page.
     *
     * @param atom the atom to draw
     */
    private void drawLabel(AtomPlacement atom)
    {
        Point centre = toPage(atom.position());

        svg.append("  <text x=\"").append(number(centre.x()))
            .append("\" y=\"").append(number(centre.y()))
            .append("\" font-family=\"").append(escape(style.fontFamily()))
            .append("\" font-size=\"").append(number(style.fontSize()))
            .append("\" fill=\"").append(style.colourOf(atom.element()).hex())
            .append("\" stroke=\"").append(style.atomOutline().hex())
            .append("\" stroke-width=\"").append(number(style.atomOutlineWidth()))
            .append("\" paint-order=\"stroke\" text-anchor=\"middle\" dominant-baseline=\"central\">");

        List<Run> runs = runsOf(atom);
        if (runs.size() == 1)
        {
            // one plain run needs no mark-up of its own, which keeps a simple label simple
            svg.append(escape(runs.getFirst().text()));
        }
        else
        {
            double baseline = 0;
            for (Run run : runs)
            {
                svg.append("<tspan font-size=\"").append(number(style.fontSize() * run.scale()))
                    .append("\" dy=\"").append(number(run.rise() - baseline)).append("\">")
                    .append(escape(run.text()))
                    .append("</tspan>");
                baseline = run.rise();
            }
        }

        svg.append("</text>\n");
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

    /**
     * @param value a measurement in pixels
     * @return it written out plainly, without a locale's grouping or an exponent
     */
    private static String number(double value)
    {
        double rounded = Math.round(value * Math.pow(10, PLACES)) / Math.pow(10, PLACES);
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < Long.MAX_VALUE)
        {
            return Long.toString((long) rounded);
        }
        return stripZeroes(String.format(Locale.ROOT, "%." + PLACES + "f", rounded));
    }

    /**
     * @param text a decimal number
     * @return it without any trailing zeroes after its point
     */
    private static String stripZeroes(String text)
    {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0')
        {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '.')
        {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * @param text some text to put into the document
     * @return it with every character XML gives a meaning to replaced by its entity
     */
    private static String escape(String text)
    {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int at = 0; at < text.length(); at++)
        {
            char character = text.charAt(at);
            switch (character)
            {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
