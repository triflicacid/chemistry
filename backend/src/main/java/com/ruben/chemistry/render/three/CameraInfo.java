package com.ruben.chemistry.render.three;

import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.Point;
import com.ruben.chemistry.render.scene.SceneBuilder;
import com.ruben.chemistry.space.Camera;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Says where the camera is.
 */
public final class CameraInfo
{
    /** The height of the strip, in pixels. */
    static final double STRIP = 100;
    /** The least width the strip needs, in pixels. */
    static final double WIDTH = 340;

    private static final double ARM = 34;
    private static final double GIZMO_CENTRE = 52;
    private static final double LINE_HEIGHT = 15;
    private static final Colour[] COLOURS = {Colour.of("#e41a1c"), Colour.of("#2ca02c"), Colour.of("#1f4fd8")};
    private static final String[] NAMES = {"x", "y", "z"};

    private CameraInfo()
    {
    }

    private static String number(double value)
    {
        String text = String.format(Locale.ROOT, "%.2f", value);
        text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text.equals("-0") ? "0" : text;
    }

    private static String vector(Vec3 vector)
    {
        return number(vector.x()) + "," + number(vector.y()) + "," + number(vector.z());
    }

    /**
     * @param camera a camera
     * @return the render3d arguments that reproduce its view
     */
    public static String command(Camera camera)
    {
        return "--camera=" + vector(camera.eye()) + " --look-at=" + vector(camera.target())
            + " --up=" + vector(camera.up()) + " --fov=" + number(camera.fieldOfView());
    }

    static List<String> lines(Camera camera)
    {
        List<String> lines = new ArrayList<>();
        lines.add("eye     " + vector(camera.eye()));
        lines.add("target  " + vector(camera.target()));
        lines.add("up      " + vector(camera.up()));
        lines.add("yaw " + number(camera.yaw()) + "\u00b0  pitch " + number(camera.pitch())
            + "\u00b0  distance " + number(camera.distance()));
        lines.add(camera.fieldOfView() == 0 ? "orthographic" : "fov " + number(camera.fieldOfView()) + "\u00b0");
        return lines;
    }

    /** Notes the camera on the picture's root, so that anything reading the SVG can recover it. */
    static void annotate(SceneBuilder scene, Camera camera)
    {
        scene.attribute("data-camera-eye", vector(camera.eye()));
        scene.attribute("data-camera-target", vector(camera.target()));
        scene.attribute("data-camera-up", vector(camera.up()));
        scene.attribute("data-camera-fov", number(camera.fieldOfView()));
    }

    /** Draws the gizmo and readout in the strip whose top left is at (left, top). */
    static void draw(SceneBuilder scene, Camera camera, double left, double top)
    {
        double cx = left + GIZMO_CENTRE;
        double cy = top + STRIP / 2;
        scene.group().cssClass("camera-info").attribute("pointer-events", "none");

        Vec3[] arrows = new Vec3[3];
        Integer[] order = {0, 1, 2};
        for (int i = 0; i < 3; i++)
        {
            arrows[i] = camera.view(new Vec3(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0));
        }
        // furthest first, so nearer arrows cover them
        Arrays.sort(order, (a, b) -> Double.compare(arrows[b].z(), arrows[a].z()));
        for (int i : order)
        {
            arrow(scene, i, new Point(cx, cy), arrows[i]);
        }

        double textX = left + 2 * GIZMO_CENTRE + 12;
        List<String> lines = lines(camera);
        double y = cy - (lines.size() - 1) * LINE_HEIGHT / 2;
        for (String line : lines)
        {
            scene.text(new Point(textX, y)).content(line)
                .fill(Colour.of("#333333"))
                .attribute("font-family", "monospace")
                .attribute("font-size", 12)
                .attribute("xml:space", "preserve")
                .attribute("dominant-baseline", "central");
            y += LINE_HEIGHT;
        }
        scene.close();
    }

    /** One axis of the gizmo; an arrow pointing away from the viewer is drawn fainter. */
    private static void arrow(SceneBuilder scene, int axis, Point centre, Vec3 view)
    {
        Colour colour = COLOURS[axis];
        Point tip = new Point(centre.x() + view.x() * ARM, centre.y() - view.y() * ARM);
        double dx = tip.x() - centre.x();
        double dy = tip.y() - centre.y();
        double length = Math.hypot(dx, dy);

        scene.group().attribute("data-axis", NAMES[axis]).attribute("opacity", view.z() > 0 ? "0.5" : "1");
        scene.line(centre, tip).stroke(colour).strokeWidth(3).roundCaps();
        if (length > 1e-6)
        {
            double ux = dx / length;
            double uy = dy / length;
            double head = Math.min(9, length * 0.6);
            Point base = new Point(tip.x() - ux * head, tip.y() - uy * head);
            scene.polygon(List.of(tip,
                    new Point(base.x() - uy * head * 0.45, base.y() + ux * head * 0.45),
                    new Point(base.x() + uy * head * 0.45, base.y() - ux * head * 0.45)))
                .fill(colour).stroke(colour).strokeWidth(1).roundJoins();
        }
        double ux = length < 1e-6 ? 0 : dx / length;
        double uy = length < 1e-6 ? -1 : dy / length;
        scene.text(new Point(tip.x() + ux * 9, tip.y() + uy * 9)).content(NAMES[axis])
            .fill(colour)
            .attribute("font-family", "sans-serif")
            .attribute("font-weight", "bold")
            .attribute("font-size", 12)
            .attribute("text-anchor", "middle")
            .attribute("dominant-baseline", "central");
        scene.close();
    }
}
