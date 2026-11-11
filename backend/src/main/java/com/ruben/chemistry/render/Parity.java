package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Chirality;

import java.util.List;
import java.util.Optional;

/**
 * Reads the tetrahedral parity shown by a stereocentre's drawn ligands.
 * <p>
 * Uses signed volume in a right-handed coordinate system, with the page as the {@code xy} plane
 * and {@code z} pointing towards the viewer.
 * {@link Chirality#ANTICLOCKWISE} corresponds to negative volume when viewed from the first ligand
 */
final class Parity
{
    /** The out-of-plane distance of a wedge or hash, as a fraction of bond length */
    static final double STEREO_DEPTH = 0.5;

    /** The number of ligands in a tetrahedral parity */
    static final int LIGANDS = 4;

    /** The minimum volume needed to determine a parity */
    private static final double FLAT = 1e-9;

    private Parity()
    {
    }

    /**
     * @param style how the bond leaves the page
     * @return the ligand's signed distance from the page
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
     * Reads the parity shown by four ligand placements
     *
     * @param centre the stereocentre's position
     * @param ligands the ligand positions, in declared parity order
     * @param depths their signed distances from the page, in the same order
     * @return the shown parity, or empty if the arrangement is too nearly flat
     * @throws IllegalArgumentException if there are not exactly four ligands and depths
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
     * Calculates the signed volume spanned by four ligands, measured from the first
     *
     * @param centre the stereocentre's position
     * @param ligands the ligand positions, in declared parity order
     * @param depths their signed distances from the page, in the same order
     * @return the signed volume, whose sign determines the parity
     * @throws IllegalArgumentException if there are not exactly four ligands and depths
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

        // measure the volume from the first ligand
        double[] first = difference(offsets[1], offsets[0]);
        double[] second = difference(offsets[2], offsets[0]);
        double[] third = difference(offsets[3], offsets[0]);
        return dot(first, cross(second, third));
    }

    /**
     * @param from the vector to subtract from
     * @param subtrahend the vector to subtract
     * @return the component-wise difference
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