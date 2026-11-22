package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.space.Vec3;

import java.util.List;

/**
 * A regular grid of points in space.
 *
 * @param origin the point with all indices zero, in ångström
 * @param spacing the distance between neighbouring points, in ångström
 * @param nx the number of points along x
 * @param ny the number of points along y
 * @param nz the number of points along z
 */
public record FieldGrid(Vec3 origin, double spacing, int nx, int ny, int nz)
{
    public FieldGrid
    {
        if (nx < 2 || ny < 2 || nz < 2)
        {
            throw new IllegalArgumentException("a grid needs at least two points along each axis");
        }
        if (!(spacing > 0.0) || !Double.isFinite(spacing))
        {
            throw new IllegalArgumentException("spacing must be positive, was " + spacing);
        }
    }

    /**
     * Makes the grid that holds some points and a margin round them.
     *
     * @param points the positions to hold, in ångström
     * @param margin how far beyond the points the grid reaches, in ångström
     * @param longestSide the most points along the longest side, which sets how fine the grid is for a large molecule
     * @param finest the least spacing, in ångström, however small the molecule is
     * @return the grid
     * @throws IllegalArgumentException if there are no points
     */
    public static FieldGrid around(List<Vec3> points, double margin, int longestSide, double finest)
    {
        if (points.isEmpty())
        {
            throw new IllegalArgumentException("a grid needs something to hold");
        }

        double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Vec3 point : points)
        {
            double[] at = {point.x(), point.y(), point.z()};
            for (int axis = 0; axis < 3; axis++)
            {
                low[axis] = Math.min(low[axis], at[axis] - margin);
                high[axis] = Math.max(high[axis], at[axis] + margin);
            }
        }

        double longest = Math.max(high[0] - low[0], Math.max(high[1] - low[1], high[2] - low[2]));
        double spacing = Math.max(finest, longest / (longestSide - 1));
        return new FieldGrid(new Vec3(low[0], low[1], low[2]), spacing,
                count(high[0] - low[0], spacing), count(high[1] - low[1], spacing), count(high[2] - low[2], spacing));
    }

    private static int count(double extent, double spacing)
    {
        return (int) Math.ceil(extent / spacing) + 1;
    }

    /**
     * @return how many points the grid has
     */
    public int size()
    {
        return nx * ny * nz;
    }

    /**
     * @param i the index along x
     * @param j the index along y
     * @param k the index along z
     * @return where that point is, in ångström
     */
    public Vec3 pointAt(int i, int j, int k)
    {
        return new Vec3(origin.x() + i * spacing, origin.y() + j * spacing, origin.z() + k * spacing);
    }

    /**
     * @param i the index along x
     * @param j the index along y
     * @param k the index along z
     * @return the position of the point in a list of values that runs along x fastest, then y, then z
     */
    public int indexOf(int i, int j, int k)
    {
        return (k * ny + j) * nx + i;
    }
}
