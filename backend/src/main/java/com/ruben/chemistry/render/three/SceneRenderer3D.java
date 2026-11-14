package com.ruben.chemistry.render.three;

import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.ElementColours;
import com.ruben.chemistry.render.Point;
import com.ruben.chemistry.render.annotate.Annotations;
import com.ruben.chemistry.render.annotate.AtomLook;
import com.ruben.chemistry.render.annotate.BondLook;
import com.ruben.chemistry.render.annotate.Halo;
import com.ruben.chemistry.render.scene.AnnotationLayer;
import com.ruben.chemistry.render.scene.Looks;
import com.ruben.chemistry.render.scene.Scene;
import com.ruben.chemistry.render.scene.SceneBuilder;
import com.ruben.chemistry.space.Camera;
import com.ruben.chemistry.space.ElementData;
import com.ruben.chemistry.space.Projection;
import com.ruben.chemistry.space.Structure3D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Turns a {@link Projection} into a {@link Scene}.
 */
public final class SceneRenderer3D
{
    private static final Colour OUTLINE = Colour.of("#222222");
    private static final double DEPTH_FADE = 0.45;
    private static final double BALL_FRACTION = 0.25;
    private static final double LONE_ATOM_RADIUS = 0.2;

    /** How thick a halo around a bond is at least, in angstroms. */
    private static final double HALO_UNIT = 0.2;
    private static final double HALO_GROWTH = 1.3;
    private static final double HALO_PADDING = 0.8;

    private SceneRenderer3D()
    {
    }

    private record Part(double depth, Consumer<SceneBuilder> draw)
    {
    }

    /**
     * @param layout what to draw
     * @param style how to draw it
     * @return the scene
     */
    public static Scene render(Projection layout, RenderStyle3D style)
    {
        return render(layout, style, Annotations.none(), null);
    }

    /**
     * @param layout what to draw
     * @param style how to draw it
     * @param annotations how to restyle the atoms and bonds of {@code molecule}
     * @param molecule the molecule the layout's structure was embedded from, which {@code annotations} refer to
     * @return the scene
     * @throws IllegalArgumentException if the layout has no diagnostics but debug overlays are asked for
     */
    public static Scene render(Projection layout, RenderStyle3D style, Annotations annotations, Molecule molecule)
    {
        List<Projection.Node> nodes = layout.nodes();
        AtomLook[] atomLooks = atomLooks(nodes, annotations, molecule);
        Map<Projection.Edge, BondLook> bondLooks = bondLooks(layout.edges(), nodes, atomLooks, annotations, molecule);
        if (!style.debug().isEmpty() && layout.diagnostics() == null)
        {
            throw new IllegalArgumentException("Debug overlays need a layout built with diagnostics");
        }
        double[] heat = DebugOverlay.heat(layout, style);
        boolean anyHeavy = nodes.stream().anyMatch(node -> !node.site().element().equals("H"));
        boolean[] shown = new boolean[nodes.size()];
        for (int i = 0; i < shown.length; i++)
        {
            shown[i] = nodes.get(i).visible() && (style.hydrogens() || !anyHeavy || !nodes.get(i).site().element().equals("H"));
        }

        List<Projection.Edge> edges = new ArrayList<>();
        boolean[] bonded = new boolean[nodes.size()];
        for (Projection.Edge edge : layout.edges())
        {
            if (shown[edge.first()] && shown[edge.second()])
            {
                edges.add(edge);
                bonded[edge.first()] = true;
                bonded[edge.second()] = true;
            }
        }

        double ppa = style.pixelsPerAngstrom();
        boolean balls = style.mode() == RenderStyle3D.Mode.BALL_AND_STICK || style.mode() == RenderStyle3D.Mode.SPACEFILL;
        double lineWidth = switch (style.mode())
        {
            case BALL_AND_STICK -> 0.14 * ppa;
            case STICKS -> 0.3 * ppa;
            default -> 1.5;
        };
        double haloUnit = Math.max(lineWidth, HALO_UNIT * ppa);
        double[] radius = new double[nodes.size()];
        double[] haloRadius = new double[nodes.size()];
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minDepth = Double.POSITIVE_INFINITY;
        double maxDepth = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < nodes.size(); i++)
        {
            if (!shown[i])
            {
                continue;
            }

            Projection.Node node = nodes.get(i);
            double vdw = ElementData.vanDerWaalsRadius(ElementData.numberOf(node.site().element()));
            double angstroms = switch (style.mode())
            {
                case SPACEFILL -> vdw;
                case BALL_AND_STICK -> BALL_FRACTION * vdw;
                default -> bonded[i] ? 0 : LONE_ATOM_RADIUS;
            };
            radius[i] = angstroms * node.scale() * ppa;
            double extent = radius[i];
            if (atomLooks[i].halo().isPresent())
            {
                haloRadius[i] = Math.max(radius[i], 0.75 * haloUnit * node.scale()) * HALO_GROWTH;
                extent = Math.max(extent, haloRadius[i] * atomLooks[i].halo().get().radiusScale());
            }
            minX = Math.min(minX, node.x() * ppa - extent);
            maxX = Math.max(maxX, node.x() * ppa + extent);
            minY = Math.min(minY, node.y() * ppa - extent);
            maxY = Math.max(maxY, node.y() * ppa + extent);
            minDepth = Math.min(minDepth, node.depth());
            maxDepth = Math.max(maxDepth, node.depth());
            if (style.charges() && node.site().charge() != 0)
            {
                double reach = chargeOffset(radius[i], ppa) + chargeWidth(node.site().charge(), ppa);
                maxX = Math.max(maxX, node.x() * ppa + reach);
                maxY = Math.max(maxY, node.y() * ppa + chargeOffset(radius[i], ppa) + chargeFont(ppa) / 2);
            }
        }
        for (double[] tip : DebugOverlay.reach(layout, style, shown))
        {
            double pad = 0.12 * ppa;
            minX = Math.min(minX, tip[0] * ppa - pad);
            maxX = Math.max(maxX, tip[0] * ppa + pad);
            minY = Math.min(minY, tip[1] * ppa - pad);
            maxY = Math.max(maxY, tip[1] * ppa + pad);
        }
        List<Camera.Projected> axisEnds = style.axes() ? AxesOverlay.ends(layout) : List.of();
        for (Camera.Projected end : axisEnds)
        {
            double pad = 0.3 * ppa;
            minX = Math.min(minX, end.x() * ppa - pad);
            maxX = Math.max(maxX, end.x() * ppa + pad);
            minY = Math.min(minY, end.y() * ppa - pad);
            maxY = Math.max(maxY, end.y() * ppa + pad);
        }
        if (minX > maxX)
        {
            minX = maxX = minY = maxY = minDepth = maxDepth = 0;
        }

        double margin = style.margin() + (style.mode() == RenderStyle3D.Mode.WIREFRAME ? lineWidth : lineWidth / 2);
        double width = maxX - minX + 2 * margin;
        double height = maxY - minY + 2 * margin;
        double offsetX = 0;
        if (style.cameraInfo())
        {
            offsetX = Math.max(0, (CameraInfo.WIDTH - width) / 2);
            width = Math.max(width, CameraInfo.WIDTH);
            height += CameraInfo.STRIP;
        }
        Frame frame = new Frame(minX, maxY, margin, offsetX, minDepth, maxDepth, style.depthCue());

        SceneBuilder builder = new SceneBuilder(width, height);
        builder.title("Molecule in 3D");
        CameraInfo.annotate(builder, layout.camera());
        if (style.background() != null)
        {
            builder.background(style.background());
        }
        if (style.metadata())
        {
            builder.attribute("data-metadata", "true");
        }

        List<Part> parts = new ArrayList<>();
        AnnotationLayer halos = new AnnotationLayer(builder, annotations.style());
        Map<Projection.Edge, Point> inward = inward(edges, nodes, frame, ppa);
        if (style.mode() != RenderStyle3D.Mode.SPACEFILL)
        {
            for (Projection.Edge edge : edges)
            {
                Projection.Node a = nodes.get(edge.first());
                Projection.Node b = nodes.get(edge.second());
                BondLook look = bondLooks.get(edge);
                double depth = (a.depth() + b.depth()) / 2 - 1e-6;
                double thickness = lineWidth * look.widthScale().orElse(1);
                parts.add(new Part(depth, scene -> bond(scene, edge, a, b, style, frame, thickness, inward.get(edge), look)));
                look.halo().ifPresent(halo -> parts.add(new Part(depth + 1e-7, scene ->
                    halos.bondHalo(frame.point(a, ppa), frame.point(b, ppa), spanOf(edge.order(), thickness) + HALO_PADDING * haloUnit, halo))));
            }
        }
        for (int i = 0; i < nodes.size(); i++)
        {
            if (shown[i] && radius[i] > 0)
            {
                Projection.Node node = nodes.get(i);
                double r = radius[i];
                AtomLook look = atomLooks[i];
                parts.add(new Part(node.depth(), scene -> atom(scene, node, r, style, frame, balls, heat[node.site().index()], look)));
            }
            if (shown[i] && haloRadius[i] > 0)
            {
                Projection.Node node = nodes.get(i);
                double reach = haloRadius[i] * atomLooks[i].halo().get().radiusScale();
                // a sphere's halo is as deep as its far side, so bonds to atoms behind it are drawn over it
                double depth = node.depth() + reach / (ppa * node.scale());
                Point centre = frame.point(node, ppa);
                Halo halo = atomLooks[i].halo().get();
                parts.add(new Part(depth, scene -> halos.atomHalo(centre, haloRadius[node.site().index()], halo)));
            }
        }

        if (style.axes())
        {
            for (AxesOverlay.Piece piece : AxesOverlay.pieces(layout))
            {
                parts.add(new Part(axisDepth(piece, nodes, shown, radius, ppa), scene -> piece.drawer().draw(scene, frame, ppa)));
            }
        }

        parts.sort(Comparator.comparingDouble(Part::depth).reversed());
        for (Part part : parts)
        {
            part.draw().accept(builder);
        }
        if (style.charges())
        {
            for (int i = 0; i < nodes.size(); i++)
            {
                if (shown[i] && nodes.get(i).site().charge() != 0)
                {
                    charge(builder, nodes.get(i), radius[i], style, frame);
                }
            }
        }
        DebugOverlay.draw(builder, layout, style, frame, shown);
        badges(builder, annotations, nodes, edges, atomLooks, bondLooks, shown, haloRadius, haloUnit, frame, ppa);
        if (style.cameraInfo())
        {
            CameraInfo.draw(builder, layout.camera(), 0, height - CameraInfo.STRIP);
        }
        return builder.build();
    }

    private static AtomLook[] atomLooks(List<Projection.Node> nodes, Annotations annotations, Molecule molecule)
    {
        AtomLook[] looks = new AtomLook[nodes.size()];
        for (int i = 0; i < looks.length; i++)
        {
            Structure3D.Site site = nodes.get(i).site();
            if (molecule == null)
            {
                looks[i] = AtomLook.NONE;
                continue;
            }

            AtomLook look = annotations.lookOf(molecule.atoms().get(site.atom()));
            looks[i] = site.implied()
                ? new AtomLook(look.fill(), look.outline(), look.opacity(), look.saturation(), look.halo(), Optional.empty())
                : look;
        }
        return looks;
    }

    /** A bond to a hydrogen the molecule did not write takes on only the opacity of the hydrogen. */
    private static Map<Projection.Edge, BondLook> bondLooks(
            List<Projection.Edge> edges,
            List<Projection.Node> nodes,
            AtomLook[] atomLooks,
            Annotations annotations,
            Molecule molecule)
    {
        Map<Projection.Edge, BondLook> looks = new HashMap<>();
        for (Projection.Edge edge : edges)
        {
            BondLook look = BondLook.NONE;
            if (molecule != null && edge.bond() >= 0)
            {
                look = annotations.lookOf(molecule.bonds().get(edge.bond()));
            }
            else if (molecule != null)
            {
                int hydrogen = nodes.get(edge.first()).site().implied() ? edge.first() : edge.second();
                if (atomLooks[hydrogen].opacity().isPresent())
                {
                    look = look.withOpacity(atomLooks[hydrogen].opacity().getAsDouble());
                }
            }
            looks.put(edge, look);
        }
        return looks;
    }

    /** @return how wide the lines of a bond of this order are drawn across, in pixels */
    private static double spanOf(BondOrder order, double lineWidth)
    {
        int lines = linesOf(order);
        return lines > 1 ? (lines - 1) * Math.max(lineWidth * 1.7, 3) + lineWidth * 0.6 : lineWidth;
    }

    private static int linesOf(BondOrder order)
    {
        return switch (order)
        {
            case SINGLE -> 1;
            case DOUBLE, AROMATIC -> 2;
            case TRIPLE -> 3;
            case QUADRUPLE -> 4;
        };
    }

    /** Draws badges over everything else: an atom's at its upper right, and a bond's at its middle. */
    private static void badges(
            SceneBuilder builder,
            Annotations annotations,
            List<Projection.Node> nodes,
            List<Projection.Edge> edges,
            AtomLook[] atomLooks,
            Map<Projection.Edge, BondLook> bondLooks,
            boolean[] shown,
            double[] haloRadius,
            double haloUnit,
            Frame frame,
            double ppa)
    {
        AnnotationLayer layer = new AnnotationLayer(builder, annotations.style());
        for (Projection.Edge edge : edges)
        {
            bondLooks.get(edge).badge().ifPresent(badge ->
            {
                Point from = frame.point(nodes.get(edge.first()), ppa);
                Point to = frame.point(nodes.get(edge.second()), ppa);
                layer.badge(new Point((from.x() + to.x()) / 2, (from.y() + to.y()) / 2), badge);
            });
        }
        for (int i = 0; i < nodes.size(); i++)
        {
            if (shown[i] && atomLooks[i].badge().isPresent())
            {
                double reach = Math.max(haloRadius[i], 0.75 * haloUnit * nodes.get(i).scale());
                double offset = reach * 0.75 + annotations.style().badgeFontSize() / 2;
                Point centre = frame.point(nodes.get(i), ppa);
                layer.badge(new Point(centre.x() + offset, centre.y() - offset), atomLooks[i].badge().get());
            }
        }
    }

    private static double chargeFont(double ppa)
    {
        return Math.max(11, 0.28 * ppa);
    }

    private static double chargeOffset(double radius, double ppa)
    {
        return Math.max(radius, 0.15 * ppa) * 0.75 + 2;
    }

    private static double chargeWidth(int charge, double ppa)
    {
        return chargeFont(ppa) * 0.65 * chargeText(charge).length() + 2;
    }

    /** A charge as written in chemistry: +, \u2212, 2+, 3\u2212. */
    private static String chargeText(int charge)
    {
        String sign = charge > 0 ? "+" : "\u2212";
        int size = Math.abs(charge);
        return size == 1 ? sign : size + sign;
    }

    /** Floats a charge label at the upper right of its atom; it is drawn over everything, and is not part of the atom. */
    private static void charge(SceneBuilder scene, Projection.Node node, double radius, RenderStyle3D style, Frame frame)
    {
        double ppa = style.pixelsPerAngstrom();
        Point centre = frame.point(node, ppa);
        double offset = chargeOffset(radius, ppa);
        Colour colour = node.site().charge() > 0 ? Colour.of("#1f4fd8") : Colour.of("#c0392b");
        scene.text(new Point(centre.x() + offset, centre.y() - offset))
            .content(chargeText(node.site().charge()))
            .fill(colour)
            .stroke(Colour.WHITE)
            .strokeWidth(3)
            .attribute("paint-order", "stroke")
            .attribute("font-family", "sans-serif")
            .attribute("font-weight", "bold")
            .attribute("font-size", chargeFont(ppa))
            .attribute("dominant-baseline", "central")
            .cssClass("charge")
            .attribute("pointer-events", "none");
    }

    /** Converts layout units to pixels and picks depth-cued colours. */
    record Frame(double minX, double maxY, double margin, double offsetX, double minDepth, double maxDepth, boolean cue)
    {
        Point point(Projection.Node node, double ppa)
        {
            return point(node.x(), node.y(), ppa);
        }

        Point point(double x, double y, double ppa)
        {
            return new Point(margin + offsetX + x * ppa - minX, margin + maxY - y * ppa);
        }

        Colour tint(Colour colour, double depth)
        {
            if (!cue || maxDepth - minDepth < 1e-9)
            {
                return colour;
            }
            return colour.mix(Colour.WHITE, DEPTH_FADE * (depth - minDepth) / (maxDepth - minDepth));
        }
    }

    /** An axis piece inside an atom's silhouette but beyond its near surface is hidden, so it is drawn before the atom. */
    private static double axisDepth(AxesOverlay.Piece piece, List<Projection.Node> nodes, boolean[] shown,
                                    double[] radius, double ppa)
    {
        double depth = piece.depth();
        for (int i = 0; i < nodes.size(); i++)
        {
            if (!shown[i] || radius[i] <= 0)
            {
                continue;
            }
            Projection.Node node = nodes.get(i);
            double pixels = Math.hypot((piece.x() - node.x()) * ppa, (piece.y() - node.y()) * ppa);
            if (pixels >= radius[i])
            {
                continue;
            }
            double unit = node.scale() * ppa;
            double reach = radius[i] / unit;
            double front = node.depth() - Math.sqrt(reach * reach - (pixels / unit) * (pixels / unit));
            if (piece.depth() > front)
            {
                depth = Math.max(depth, node.depth() + 1e-6);
            }
        }
        return depth;
    }

    private static void atom(SceneBuilder scene, Projection.Node node, double radius, RenderStyle3D style, Frame frame, boolean balls, double heat, AtomLook look)
    {
        Point centre = frame.point(node, style.pixelsPerAngstrom());
        Colour colour = DebugOverlay.tinted(frame.tint(Looks.fill(look, ElementColours.of(node.site().element())), node.depth()), heat);
        var group = Looks.apply(scene.group().cssClass("atom"), look);
        if (style.metadata())
        {
            if (node.site().implied())
            {
                group.attribute("data-element", node.site().element());
            }
            else
            {
                group.attribute("data-atom", String.valueOf(node.site().atom()));
            }
        }
        scene.circle(centre, radius).fill(colour).stroke(Looks.outline(look, OUTLINE)).strokeWidth(balls ? 1 : 0.8);
        if (balls)
        {
            Point shine = new Point(centre.x() - radius * 0.3, centre.y() - radius * 0.3);
            scene.circle(shine, radius * 0.35).fill(Colour.WHITE).opacity(0.35).attribute("pointer-events", "none");
        }
        scene.close();
    }

    private static void bond(SceneBuilder scene, Projection.Edge edge, Projection.Node a, Projection.Node b, RenderStyle3D style, Frame frame, double lineWidth, Point inward, BondLook look)
    {
        double ppa = style.pixelsPerAngstrom();
        Point from = frame.point(a, ppa);
        Point to = frame.point(b, ppa);
        Point middle = new Point((from.x() + to.x()) / 2, (from.y() + to.y()) / 2);

        int lines = linesOf(edge.order());
        double each = lines > 1 ? lineWidth * 0.6 : lineWidth;
        double spacing = Math.max(lineWidth * 1.7, 3);
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double length = Math.hypot(dx, dy);
        double nx = length < 1e-9 ? 0 : -dy / length;
        double ny = length < 1e-9 ? 1 : dx / length;

        var group = Looks.apply(scene.group().cssClass("bond"), look);
        if (style.metadata())
        {
            if (edge.bond() >= 0)
            {
                group.attribute("data-bond", String.valueOf(edge.bond()));
            }
            else
            {
                group.attribute("data-order", edge.order().name().toLowerCase(Locale.ROOT));
            }
        }

        Colour first = frame.tint(Looks.stroke(look, ElementColours.of(a.site().element())), a.depth());
        Colour second = frame.tint(Looks.stroke(look, ElementColours.of(b.site().element())), b.depth());
        if (edge.order() == BondOrder.AROMATIC && length > 1e-9)
        {
            // the solid line is the bond itself; the dashed one sits on the ring side, so it never swaps places
            if (inward != null && nx * inward.x() + ny * inward.y() < 0)
            {
                nx = -nx;
                ny = -ny;
            }
            half(scene, from, middle, first, lineWidth * 0.8, null);
            half(scene, middle, to, second, lineWidth * 0.8, null);

            double inset = Math.min(spacing * 0.7, length * 0.2);
            double ux = dx / length;
            double uy = dy / length;
            Point start = new Point(from.x() + nx * spacing + ux * inset, from.y() + ny * spacing + uy * inset);
            Point end = new Point(to.x() + nx * spacing - ux * inset, to.y() + ny * spacing - uy * inset);
            Point centre = new Point((start.x() + end.x()) / 2, (start.y() + end.y()) / 2);
            double inner = Math.hypot(end.x() - start.x(), end.y() - start.y());
            double width = lineWidth * 0.5;
            // a whole number of dash-gap pairs, so both halves carry on from each other without a seam
            double pairs = Math.max(1, Math.round(inner / (width * 3.5)));
            double period = inner / pairs;
            half(scene, start, centre, first, width, new double[] {period / 2, 0});
            half(scene, centre, end, second, width, new double[] {period / 2, (inner / 2) % period});
            scene.close();
            return;
        }

        for (int k = 0; k < lines; k++)
        {
            double offset = (k - (lines - 1) / 2.0) * spacing;
            Point start = new Point(from.x() + nx * offset, from.y() + ny * offset);
            Point centre = new Point(middle.x() + nx * offset, middle.y() + ny * offset);
            Point end = new Point(to.x() + nx * offset, to.y() + ny * offset);
            half(scene, start, centre, first, each, null);
            half(scene, centre, end, second, each, null);
        }
        scene.close();
    }

    /** Draws half a bond; a dash is the length of both a dash and its gap, then where in that pattern the line starts. */
    private static void half(SceneBuilder scene, Point from, Point to, Colour colour, double width, double[] dash)
    {
        var line = scene.line(from, to).stroke(colour).strokeWidth(width);
        if (dash == null)
        {
            line.roundCaps();
            return;
        }
        line.dash(dash[0], dash[0]);
        if (dash[1] > 0)
        {
            line.attribute("stroke-dashoffset", dash[1]);
        }
    }

    /**
     * @return for each aromatic bond that lies in a ring of aromatic bonds, the way from the bond's middle
     *     towards the middle of the smallest such ring, in pixels
     */
    private static Map<Projection.Edge, Point> inward(List<Projection.Edge> edges, List<Projection.Node> nodes, Frame frame, double ppa)
    {
        Map<Integer, List<Projection.Edge>> around = new HashMap<>();
        for (Projection.Edge edge : edges)
        {
            if (edge.order() == BondOrder.AROMATIC)
            {
                around.computeIfAbsent(edge.first(), key -> new ArrayList<>()).add(edge);
                around.computeIfAbsent(edge.second(), key -> new ArrayList<>()).add(edge);
            }
        }

        Map<Projection.Edge, Point> inward = new HashMap<>();
        for (Projection.Edge edge : edges)
        {
            if (edge.order() != BondOrder.AROMATIC)
            {
                continue;
            }

            // the shortest way round from one end to the other without crossing the bond itself
            Map<Integer, Integer> before = new HashMap<>();
            before.put(edge.first(), -1);
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>(List.of(edge.first()));
            while (!queue.isEmpty() && !before.containsKey(edge.second()))
            {
                int node = queue.poll();
                for (Projection.Edge next : around.getOrDefault(node, List.of()))
                {
                    int other = next.first() == node ? next.second() : next.first();
                    if (next != edge && !before.containsKey(other))
                    {
                        before.put(other, node);
                        queue.add(other);
                    }
                }
            }
            if (!before.containsKey(edge.second()))
            {
                continue;
            }

            double x = 0;
            double y = 0;
            int count = 0;
            for (int node = edge.second(); node != -1; node = before.get(node))
            {
                Point point = frame.point(nodes.get(node), ppa);
                x += point.x();
                y += point.y();
                count++;
            }
            Point from = frame.point(nodes.get(edge.first()), ppa);
            Point to = frame.point(nodes.get(edge.second()), ppa);
            inward.put(edge, new Point(x / count - (from.x() + to.x()) / 2, y / count - (from.y() + to.y()) / 2));
        }
        return inward;
    }
}
