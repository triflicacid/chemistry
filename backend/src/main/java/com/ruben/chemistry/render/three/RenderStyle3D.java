package com.ruben.chemistry.render.three;

import com.ruben.chemistry.render.Colour;

import java.util.EnumSet;
import java.util.Set;

/**
 * How a 3D layout is drawn.
 *
 * @param mode how atoms and bonds are shown
 * @param hydrogens whether hydrogens are drawn; they are kept when the molecule has nothing else
 * @param pixelsPerAngstrom how many pixels an angstrom is at the target's depth
 * @param margin the empty pixels around the drawing
 * @param background the colour behind the drawing, or null for none
 * @param depthCue whether things further away fade towards white
 * @param metadata whether atoms and bonds carry data-* attributes tracing them to the molecule
 * @param charges whether atoms with a formal charge are labelled with it
 * @param axes whether to draw the x (red), y (green) and z (blue) axes from the origin
 * @param debug which diagnostics to draw over the molecule; the layout must carry them
 * @param debugScale how many times the diagnostic tolerances to allow before something is drawn; 0 draws all
 */
public record RenderStyle3D(
        Mode mode,
        boolean hydrogens,
        double pixelsPerAngstrom,
        double margin,
        Colour background,
        boolean depthCue,
        boolean metadata,
        boolean charges,
        boolean axes,
        boolean cameraInfo,
        Set<Debug> debug,
        double debugScale)
{
    /** The ways to show a molecule. */
    public enum Mode
    {
        /** Small balls joined by thick sticks. */
        BALL_AND_STICK,
        /** Thick sticks, coloured by half-bond, with no balls. */
        STICKS,
        /** Thin lines only. */
        WIREFRAME,
        /** Atoms as spheres the size of their van der Waals radius, with no bonds. */
        SPACEFILL
    }

    /** What can be drawn over a molecule. */
    public enum Debug
    {
        /** Bonds whose length strays from ideal, labelled with it. */
        BONDS,
        /** Arcs at angles that stray from ideal, labelled with the angle. */
        ANGLES,
        /** The worst torsion about each bond whose torsion strays from ideal, labelled. */
        TORSIONS,
        /** Lines between atoms that are too close, labelled with the overlap. */
        CLASHES,
        /** Atoms tinted red by how strained they are. */
        STRAIN,
        /** Where lone pairs point. */
        LONE_PAIRS
    }

    public RenderStyle3D
    {
        if (mode == null)
        {
            throw new IllegalArgumentException("A mode is required");
        }
        if (!(pixelsPerAngstrom > 0))
        {
            throw new IllegalArgumentException("Pixels per angstrom must be positive, but was " + pixelsPerAngstrom);
        }
        if (!(margin >= 0))
        {
            throw new IllegalArgumentException("The margin cannot be negative, but was " + margin);
        }
        if (!(debugScale >= 0))
        {
            throw new IllegalArgumentException("The debug scale cannot be negative, but was " + debugScale);
        }
        debug = debug.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(debug));
    }

    /**
     * @return ball and stick with hydrogens and depth cueing, 50 pixels to the angstrom, and no diagnostics
     */
    public static RenderStyle3D defaults()
    {
        return new RenderStyle3D(Mode.BALL_AND_STICK, true, 50, 12, null, true, false, true, false, false, Set.of(), 1);
    }

    public RenderStyle3D withMode(Mode mode)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withHydrogens(boolean hydrogens)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withPixelsPerAngstrom(double pixelsPerAngstrom)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withMargin(double margin)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withBackground(Colour background)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withDepthCue(boolean depthCue)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withMetadata(boolean metadata)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withCharges(boolean charges)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withAxes(boolean axes)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withCameraInfo(boolean cameraInfo)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withDebug(Set<Debug> debug)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }

    public RenderStyle3D withDebugScale(double debugScale)
    {
        return new RenderStyle3D(mode, hydrogens, pixelsPerAngstrom, margin, background, depthCue, metadata, charges, axes, cameraInfo, debug, debugScale);
    }
}
