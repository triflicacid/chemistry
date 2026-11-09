package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Chirality;

import java.util.List;
import java.util.Optional;

/**
 * Reads the tetrahedral parity so a layout can check it against the parity the molecule declares.
 * <p>
 * A drawn stereocentre is a three-dimensional arrangement: its four ligands sit where the page
 * puts them, lifted out of the plane by whatever {@link BondStyle} each bond carries. Considering the
 * page as the {@code xy} plane of a right-handed frame, with {@code z} pointing out of the page
 * towards the viewer, the parity is the sign of the volume the four ligands span.
 * <p>
 * {@link Chirality#ANTICLOCKWISE} means that, viewed from the first ligand, the other three run
 * anticlockwise. Taking the first ligand straight out of the page and the other three evenly
 * around the plane below it in anticlockwise order gives a negative signed volume, so a negative
 * volume is what an anticlockwise parity looks like.
 */
final class Parity
{
    /** How far out of the plane a bond leaving it carries its ligand, as a fraction of a bond length. */
    static final double STEREO_DEPTH = 0.5;

    /** How many ligands a tetrahedral parity describes the arrangement of. */
    static final int LIGANDS = 4;

    /** The volume below which an arrangement is too nearly flat to read a parity from. */
    private static final double FLAT = 1e-9;

    private Parity()
    {
    }

    /**
     * @param style how a bond leaves the plane of the page
     * @return how far out of the plane that bond carries its ligand
     */
    static double depthOf(BondStyle style)
    {
        return switch (style)
        {
            case PLAIN -> 0;
            case WEDGE -> STEREO_DEPTH;
            case HASH -> -STEREO_DEPTH;
        };
    }

    /**
     * @param centre where the stereocentre sits
     * @param ligands where its four ligands sit, in the order the parity is declared against
     * @param depths how far out of the plane each ligand is carried, in the same order
     * @return the parity the arrangement shows, or empty if it is too nearly flat to read
     * @throws IllegalArgumentException if there are not exactly {@link #LIGANDS} ligands and depths
     */
    static Optional<Chirality> read(Point centre, List<Point> ligands, double[] depths)
    {
        double volume = signedVolume(centre, ligands, depths);
        if (Math.abs(volume) <= FLAT)
        {
            return Optional.empty();
        }
        return Optional.of(volume < 0 ? Chirality.ANTICLOCKWISE : Chirality.CLOCKWISE);
    }

    /**
     * @param centre where the stereocentre sits
     * @param ligands where its four ligands sit, in the order the parity is declared against
     * @param depths how far out of the plane each ligand is carried, in the same order
     * @return the signed volume the four ligands span, measured from the first
     * @throws IllegalArgumentException if there are not exactly {@link #LIGANDS} ligands and depths
     */
    static double signedVolume(Point centre, List<Point> ligands, double[] depths)
    {
        if (ligands.size() != LIGANDS || depths.length != LIGANDS)
        {
            throw new IllegalArgumentException("A tetrahedral parity needs " + LIGANDS
                + " ligands, but was given " + ligands.size() + " positions and " + depths.length + " depths");
        }

        double[][] offsets = new double[LIGANDS][];
        for (int ligand = 0; ligand < LIGANDS; ligand++)
        {
            Point position = ligands.get(ligand);
            offsets[ligand] = new double[] {position.x() - centre.x(), position.y() - centre.y(), depths[ligand]};
        }

        // the volume is measured from the first ligand, which is the one the parity is viewed from
        double[] first = difference(offsets[1], offsets[0]);
        double[] second = difference(offsets[2], offsets[0]);
        double[] third = difference(offsets[3], offsets[0]);
        return dot(first, cross(second, third));
    }

    /**
     * @param from the vector to subtract from
     * @param subtrahend the vector to subtract
     * @return their component-wise difference
     */
    private static double[] difference(double[] from, double[] subtrahend)
    {
        return new double[] {from[0] - subtrahend[0], from[1] - subtrahend[1], from[2] - subtrahend[2]};
    }

    /**
     * @param first one vector
     * @param second the other vector
     * @return their cross product
     */
    private static double[] cross(double[] first, double[] second)
    {
        return new double[] {
            first[1] * second[2] - first[2] * second[1],
            first[2] * second[0] - first[0] * second[2],
            first[0] * second[1] - first[1] * second[0]};
    }

    /**
     * @param first one vector
     * @param second the other vector
     * @return their dot product
     */
    private static double dot(double[] first, double[] second)
    {
        return first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
    }
}
