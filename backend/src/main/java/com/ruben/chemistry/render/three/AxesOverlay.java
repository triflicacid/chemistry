package com.ruben.chemistry.render.three;

import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.Point;
import com.ruben.chemistry.render.scene.SceneBuilder;
import com.ruben.chemistry.space.Camera;
import com.ruben.chemistry.space.Projection;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The x, y and z axes from the origin, each a shaft ending in a cone, in red, green and blue.
 * <p>
 * Each axis is cut into short pieces that are depth-sorted with the atoms and bonds, so an axis
 * passes behind what is nearer to the camera and in front of what is further.
 */
final class AxesOverlay
{
    private static final double LENGTH = 2.5;
    private static final double CONE_LENGTH = 0.5;
    private static final double CONE_RADIUS = 0.18;
    private static final double SHAFT_WIDTH = 0.06;
    private static final int SEGMENTS = 16;

    private static final Colour[] COLOURS = {Colour.of("#e41a1c"), Colour.of("#2ca02c"), Colour.of("#1f4fd8")};
    private static final String[] NAMES = {"x", "y", "z"};

    /** Draws a piece of an axis once the picture's frame is known. */
    interface Drawer
    {
        void draw(SceneBuilder scene, SceneRenderer3D.Frame frame, double ppa);
    }

    /** A piece of an axis, and how far it lies from the camera. */
    record Piece(double depth, double x, double y, Drawer drawer)
    {
    }

    private AxesOverlay()
    {
    }

    private static Vec3 direction(int axis)
    {
        return new Vec3(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
    }

    /** @return the projected ends of every axis, for sizing the picture */
    static List<Camera.Projected> ends(Projection layout)
    {
        List<Camera.Projected> ends = new ArrayList<>();
        for (Vec3 end : List.of(Vec3.ZERO, direction(0).times(LENGTH), direction(1).times(LENGTH), direction(2).times(LENGTH)))
        {
            Camera.Projected seen = layout.camera().project(end);
            if (seen.visible())
            {
                ends.add(seen);
            }
        }
        return ends;
    }

    static List<Piece> pieces(Projection layout)
    {
        Camera camera = layout.camera();
        List<Piece> pieces = new ArrayList<>();
        for (int i = 0; i < 3; i++)
        {
            int axis = i;
            Vec3 along = direction(i);
            double shaft = LENGTH - CONE_LENGTH;

            for (int step = 0; step < SEGMENTS; step++)
            {
                Camera.Projected from = camera.project(along.times(shaft * step / SEGMENTS));
                Camera.Projected to = camera.project(along.times(shaft * (step + 1) / SEGMENTS));
                if (!from.visible() || !to.visible())
                {
                    continue;
                }
                pieces.add(new Piece((from.depth() + to.depth()) / 2, (from.x() + to.x()) / 2, (from.y() + to.y()) / 2, (scene, frame, ppa) -> {
                    group(scene, axis);
                    scene.line(frame.point(from.x(), from.y(), ppa), frame.point(to.x(), to.y(), ppa))
                        .stroke(COLOURS[axis])
                        .strokeWidth(Math.max(1.5, SHAFT_WIDTH * ppa * (from.scale() + to.scale()) / 2))
                        .roundCaps();
                    scene.close();
                }));
            }

            Camera.Projected base = camera.project(along.times(shaft));
            Camera.Projected tip = camera.project(along.times(LENGTH));
            if (!base.visible() || !tip.visible())
            {
                continue;
            }
            pieces.add(new Piece((base.depth() + tip.depth()) / 2, (base.x() + tip.x()) / 2, (base.y() + tip.y()) / 2, (scene, frame, ppa) -> {
                group(scene, axis);
                cone(scene, axis, frame.point(base.x(), base.y(), ppa), frame.point(tip.x(), tip.y(), ppa),
                    Math.max(3, CONE_RADIUS * ppa * base.scale()));
                scene.close();
            }));
        }
        return pieces;
    }

    private static void group(SceneBuilder scene, int axis)
    {
        scene.group().cssClass("axis").attribute("data-axis", NAMES[axis]).attribute("pointer-events", "none");
    }

    /** A cone seen from the side: a triangle across its base to its tip, with the axis's name beyond. */
    private static void cone(SceneBuilder scene, int axis, Point base, Point tip, double half)
    {
        Colour colour = COLOURS[axis];
        double dx = tip.x() - base.x();
        double dy = tip.y() - base.y();
        double length = Math.hypot(dx, dy);
        double nx = length < 1e-9 ? 1 : -dy / length;
        double ny = length < 1e-9 ? 0 : dx / length;
        scene.polygon(List.of(
                new Point(base.x() + nx * half, base.y() + ny * half),
                new Point(base.x() - nx * half, base.y() - ny * half),
                tip))
            .fill(colour).stroke(colour).strokeWidth(1).roundJoins();

        double ux = length < 1e-9 ? 0 : dx / length;
        double uy = length < 1e-9 ? -1 : dy / length;
        scene.text(new Point(tip.x() + ux * 9, tip.y() + uy * 9)).content(NAMES[axis])
            .fill(colour).stroke(Colour.WHITE).strokeWidth(3)
            .attribute("paint-order", "stroke")
            .attribute("font-family", "sans-serif").attribute("font-weight", "bold").attribute("font-size", 12)
            .attribute("text-anchor", "middle").attribute("dominant-baseline", "central");
    }
}
