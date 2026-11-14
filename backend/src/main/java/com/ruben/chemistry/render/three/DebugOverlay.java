package com.ruben.chemistry.render.three;

import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.Point;
import com.ruben.chemistry.render.scene.Element;
import com.ruben.chemistry.render.scene.SceneBuilder;
import com.ruben.chemistry.space.Camera;
import com.ruben.chemistry.space.Diagnostics;
import com.ruben.chemistry.space.Projection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Draws a layout's {@link Diagnostics} over a molecule. */
final class DebugOverlay
{
    private static final Colour AMBER = Colour.of("#e67e22");
    private static final Colour RED = Colour.of("#c0392b");
    private static final Colour LONE = Colour.of("#2980b9");
    private static final Colour HOT = Colour.of("#e41a1c");

    /** The strain at which an atom is tinted as red as it will go. */
    private static final double HOT_STRAIN = 6;

    private static final double LABEL_SIZE = 10;

    private DebugOverlay()
    {
    }

    /**
     * @return how far each node is tinted towards red, from 0 to 1; all zero unless strain is shown
     */
    static double[] heat(Projection layout, RenderStyle3D style)
    {
        double[] heat = new double[layout.nodes().size()];
        if (style.debug().contains(RenderStyle3D.Debug.STRAIN) && layout.diagnostics() != null)
        {
            List<Double> strain = layout.diagnostics().atomStrain();
            for (int i = 0; i < heat.length; i++)
            {
                heat[i] = Math.min(1, strain.get(i) / HOT_STRAIN);
            }
        }
        return heat;
    }

    static Colour tinted(Colour colour, double heat)
    {
        return heat <= 0 ? colour : colour.mix(HOT, 0.8 * heat);
    }

    /**
     * @return where each lone pair's tip lands in the layout's units, x then y, for the pairs on shown nodes
     */
    static List<double[]> reach(Projection layout, RenderStyle3D style, boolean[] shown)
    {
        List<double[]> points = new ArrayList<>();
        if (!style.debug().contains(RenderStyle3D.Debug.LONE_PAIRS) || layout.diagnostics() == null)
        {
            return points;
        }
        for (Diagnostics.LonePair pair : layout.diagnostics().lonePairs())
        {
            Camera.Projected tip = layout.camera().project(pair.tip());
            if (shown[pair.node()] && tip.visible())
            {
                points.add(new double[] {tip.x(), tip.y()});
            }
        }
        return points;
    }

    static void draw(SceneBuilder scene, Projection layout, RenderStyle3D style, SceneRenderer3D.Frame frame, boolean[] shown)
    {
        Diagnostics diagnostics = layout.diagnostics();
        if (diagnostics == null || style.debug().isEmpty())
        {
            return;
        }

        double scale = style.debugScale();
        scene.group().cssClass("debug").attribute("pointer-events", "none");
        if (style.debug().contains(RenderStyle3D.Debug.CLASHES))
        {
            clashes(scene, layout, style, frame, shown, diagnostics, scale);
        }
        if (style.debug().contains(RenderStyle3D.Debug.ANGLES))
        {
            angles(scene, layout, style, frame, shown, diagnostics, scale);
        }
        if (style.debug().contains(RenderStyle3D.Debug.BONDS))
        {
            bonds(scene, layout, style, frame, shown, diagnostics, scale);
        }
        if (style.debug().contains(RenderStyle3D.Debug.TORSIONS))
        {
            torsions(scene, layout, style, frame, shown, diagnostics, scale);
        }
        if (style.debug().contains(RenderStyle3D.Debug.LONE_PAIRS))
        {
            lonePairs(scene, layout, style, frame, shown, diagnostics);
        }
        scene.close();
    }

    private static Colour severity(double ratio)
    {
        return AMBER.mix(RED, Math.clamp((ratio - 1) / 3, 0, 1));
    }

    private static Point at(Projection layout, SceneRenderer3D.Frame frame, RenderStyle3D style, int node)
    {
        return frame.point(layout.nodes().get(node), style.pixelsPerAngstrom());
    }

    private static void label(SceneBuilder scene, Point where, String text, Colour colour)
    {
        scene.text(where).content(text)
            .fill(colour)
            .stroke(Colour.WHITE)
            .strokeWidth(3)
            .attribute("paint-order", "stroke")
            .attribute("font-family", "sans-serif")
            .attribute("font-size", LABEL_SIZE)
            .attribute("text-anchor", "middle")
            .attribute("dominant-baseline", "central");
    }

    private static void clashes(SceneBuilder scene, Projection layout, RenderStyle3D style, SceneRenderer3D.Frame frame,
                                boolean[] shown, Diagnostics diagnostics, double scale)
    {
        for (Diagnostics.Clash clash : diagnostics.clashes())
        {
            if (!shown[clash.first()] || !shown[clash.second()] || clash.overlap() <= Diagnostics.CLASH_TOLERANCE * scale)
            {
                continue;
            }
            Colour colour = severity(clash.overlap() / Diagnostics.CLASH_TOLERANCE / 4);
            Point from = at(layout, frame, style, clash.first());
            Point to = at(layout, frame, style, clash.second());
            scene.line(from, to).stroke(colour).strokeWidth(1.5).dash(4, 3).roundCaps();
            label(scene, new Point((from.x() + to.x()) / 2, (from.y() + to.y()) / 2),
                String.format(Locale.ROOT, "%.2f Å", clash.distance()), colour);
        }
    }

    private static void bonds(SceneBuilder scene, Projection layout, RenderStyle3D style, SceneRenderer3D.Frame frame, boolean[] shown, Diagnostics diagnostics, double scale)
    {
        for (Diagnostics.Length length : diagnostics.lengths())
        {
            if (!shown[length.first()] || !shown[length.second()] || length.deviation() <= Diagnostics.LENGTH_TOLERANCE * scale)
            {
                continue;
            }
            Point from = at(layout, frame, style, length.first());
            Point to = at(layout, frame, style, length.second());
            Point side = sideways(from, to, 9);
            label(scene, new Point((from.x() + to.x()) / 2 + side.x(), (from.y() + to.y()) / 2 + side.y()),
                String.format(Locale.ROOT, "%.2f Å", length.actual()), severity(length.deviation() / Diagnostics.LENGTH_TOLERANCE));
        }
    }

    private static void angles(SceneBuilder scene, Projection layout, RenderStyle3D style, SceneRenderer3D.Frame frame, boolean[] shown, Diagnostics diagnostics, double scale)
    {
        double ppa = style.pixelsPerAngstrom();
        for (Diagnostics.Angle angle : diagnostics.angles())
        {
            if (!shown[angle.first()] || !shown[angle.middle()] || !shown[angle.second()]
                || angle.deviation() <= Diagnostics.ANGLE_TOLERANCE * scale)
            {
                continue;
            }

            Point centre = at(layout, frame, style, angle.middle());
            Point a = at(layout, frame, style, angle.first());
            Point b = at(layout, frame, style, angle.second());
            double lengthA = Math.hypot(a.x() - centre.x(), a.y() - centre.y());
            double lengthB = Math.hypot(b.x() - centre.x(), b.y() - centre.y());
            double radius = Math.min(0.4 * ppa * layout.nodes().get(angle.middle()).scale(), 0.45 * Math.min(lengthA, lengthB));
            if (radius < 4)
            {
                continue;
            }

            double start = Math.atan2(a.y() - centre.y(), a.x() - centre.x());
            double sweep = Math.atan2(b.y() - centre.y(), b.x() - centre.x()) - start;
            while (sweep > Math.PI)
            {
                sweep -= 2 * Math.PI;
            }
            while (sweep < -Math.PI)
            {
                sweep += 2 * Math.PI;
            }

            Colour colour = severity(angle.deviation() / Diagnostics.ANGLE_TOLERANCE);
            int steps = Math.max(4, (int) Math.ceil(Math.abs(sweep) / 0.2));
            Point previous = null;
            for (int step = 0; step <= steps; step++)
            {
                double turn = start + sweep * step / steps;
                Point point = new Point(centre.x() + radius * Math.cos(turn), centre.y() + radius * Math.sin(turn));
                if (previous != null)
                {
                    scene.line(previous, point).stroke(colour).strokeWidth(1.5).roundCaps();
                }
                previous = point;
            }

            double middle = start + sweep / 2;
            label(scene, new Point(centre.x() + (radius + 11) * Math.cos(middle), centre.y() + (radius + 11) * Math.sin(middle)),
                String.format(Locale.ROOT, "%.0f°", angle.actual()), colour);
        }
    }

    private static void torsions(SceneBuilder scene, Projection layout, RenderStyle3D style, SceneRenderer3D.Frame frame, boolean[] shown, Diagnostics diagnostics, double scale)
    {
        Map<Long, Diagnostics.Torsion> worst = new HashMap<>();
        for (Diagnostics.Torsion torsion : diagnostics.torsions())
        {
            if (!shown[torsion.first()] || !shown[torsion.second()] || !shown[torsion.third()] || !shown[torsion.fourth()]
                || torsion.deviation() <= Diagnostics.TORSION_TOLERANCE * scale)
            {
                continue;
            }
            long key = (long) Math.min(torsion.second(), torsion.third()) * layout.nodes().size()
                + Math.max(torsion.second(), torsion.third());
            worst.merge(key, torsion, (old, next) -> next.deviation() > old.deviation() ? next : old);
        }

        for (Diagnostics.Torsion torsion : worst.values())
        {
            Point from = at(layout, frame, style, torsion.second());
            Point to = at(layout, frame, style, torsion.third());
            Point side = sideways(from, to, -9);
            label(scene, new Point((from.x() + to.x()) / 2 + side.x(), (from.y() + to.y()) / 2 + side.y()),
                String.format(Locale.ROOT, "τ %.0f°", torsion.actual()), severity(torsion.deviation() / Diagnostics.TORSION_TOLERANCE));
        }
    }

    private static void lonePairs(SceneBuilder scene, Projection layout, RenderStyle3D style, SceneRenderer3D.Frame frame, boolean[] shown, Diagnostics diagnostics)
    {
        double ppa = style.pixelsPerAngstrom();
        for (Diagnostics.LonePair pair : diagnostics.lonePairs())
        {
            Camera.Projected seen = layout.camera().project(pair.tip());
            if (!shown[pair.node()] || !seen.visible())
            {
                continue;
            }
            Point from = at(layout, frame, style, pair.node());
            Point to = frame.point(seen.x(), seen.y(), ppa);
            scene.line(from, to).stroke(LONE).strokeWidth(1.2).dash(3, 2).roundCaps();
            Element.Circle lobe = scene.circle(to, Math.max(3, 0.12 * ppa * seen.scale()));
            lobe.fill(LONE).opacity(0.35).stroke(LONE).strokeWidth(1);
        }
    }

    /** A vector of the given length at right angles to the line between two points. */
    private static Point sideways(Point from, Point to, double length)
    {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double size = Math.hypot(dx, dy);
        return size < 1e-9 ? new Point(0, length) : new Point(-dy / size * length, dx / size * length);
    }
}
