package com.ruben.chemistry.space;

/**
 * Finds the flat view of a system in space that reads best as a drawing.
 */
public final class StructureViews
{
    private StructureViews()
    {
    }

    /**
     * @param positions where each atom is, as x, y and z of each atom in turn
     * @param bonds the bonds, as pairs of atom indices
     * @return the view that keeps atoms apart and bonds evenly long, as x and y of each atom in turn
     * @throws IllegalArgumentException if {@code positions} is not a whole number of atoms
     */
    public static double[] best(double[] positions, int[][] bonds)
    {
        if (positions.length % 3 != 0)
        {
            throw new IllegalArgumentException("Positions come as x, y and z, but there are " + positions.length + " numbers");
        }
        int count = positions.length / 3;
        if (bonds.length == 0)
        {
            // nothing to scale or compare views by, so look straight down the z axis
            double[] flat = new double[count * 2];
            for (int atom = 0; atom < count; atom++)
            {
                flat[atom * 2] = positions[atom * 3];
                flat[atom * 2 + 1] = positions[atom * 3 + 1];
            }
            return flat;
        }
        return new StructureProjector(count, bonds, 1).bestViewOf(positions);
    }

    /**
     * @param positions where each atom is, as x, y and z of each atom in turn
     * @param bonds the bonds, as pairs of atom indices
     * @return the view {@link #best} finds, as x, y and depth of each atom in turn, the depth being how far the atom is
     *     from the viewer at the same scale as x and y
     * @throws IllegalArgumentException if {@code positions} is not a whole number of atoms
     */
    public static double[] bestWithDepth(double[] positions, int[][] bonds)
    {
        if (positions.length % 3 != 0)
        {
            throw new IllegalArgumentException("Positions come as x, y and z, but there are " + positions.length + " numbers");
        }
        int count = positions.length / 3;
        if (bonds.length == 0)
        {
            double[] flat = new double[count * 3];
            for (int atom = 0; atom < count; atom++)
            {
                flat[atom * 3] = positions[atom * 3];
                flat[atom * 3 + 1] = positions[atom * 3 + 1];
            }
            return flat;
        }
        return new StructureProjector(count, bonds, 1).bestViewWithDepthOf(positions);
    }
}
