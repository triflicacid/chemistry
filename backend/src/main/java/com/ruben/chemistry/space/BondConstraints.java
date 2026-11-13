package com.ruben.chemistry.space;

/**
 * Holds a set of points so that every bond between them stays one bond length long, in however many dimensions the points have.
 */
public final class BondConstraints
{
    /** How far a bond's squared length may be from one for the bond to count as the right length. */
    private static final double SETTLED = 1e-13;

    /** How small a pivot has to be for the system to count as having no one answer. */
    private static final double SINGULAR = 1e-14;

    /** The least the system is damped by, which keeps it solvable where bonds pull in the same direction. */
    private static final double LEAST_RIDGE = 1e-12;

    /** The damping a restore starts at. */
    private static final double FIRST_RIDGE = 1e-6;

    /** The damping above which a restore gives up. */
    private static final double MOST_RIDGE = 1e8;

    /** How many rounds a restore may take. */
    private static final int ROUNDS = 200;

    private final int[][] bonds;
    private final int dimensions;

    /**
     * @param bonds the bonds, as pairs of point indices
     * @param dimensions how many components each point has
     */
    public BondConstraints(int[][] bonds, int dimensions)
    {
        this.bonds = bonds;
        this.dimensions = dimensions;
    }

    /**
     * @param x the points, as all components of each point in turn
     * @return how far each bond's squared length is from one
     */
    double[] residual(double[] x)
    {
        double[] residual = new double[bonds.length];
        for (int bond = 0; bond < bonds.length; bond++)
        {
            double[] offset = Vectors.offset(x, dimensions, bonds[bond][0], bonds[bond][1]);
            residual[bond] = Vectors.dot(offset, offset) - 1;
        }
        return residual;
    }

    /**
     * Moves the points, as little as it can, until every bond is one bond length long.
     *
     * @param x the points, which are changed
     * @return whether every bond ended up the right length
     */
    public boolean restore(double[] x)
    {
        double ridge = FIRST_RIDGE;
        double[] residual = residual(x);
        double size = Vectors.dot(residual, residual);
        for (int round = 0; round < ROUNDS; round++)
        {
            if (Vectors.worst(residual) < SETTLED)
            {
                return true;
            }
            double[] multipliers = multipliers(x, residual, ridge);
            if (multipliers != null)
            {
                // the smallest move that would cancel the errors were the lengths to change evenly over it
                double[] next = x.clone();
                moveBy(x, multipliers, next);
                double[] nextResidual = residual(next);
                double nextSize = Vectors.dot(nextResidual, nextResidual);
                if (nextSize < size)
                {
                    System.arraycopy(next, 0, x, 0, x.length);
                    residual = nextResidual;
                    size = nextSize;
                    // they did change evenly enough, so trust a longer move next round
                    ridge = Math.max(ridge / 10, LEAST_RIDGE);
                    continue;
                }
            }
            // the move overshot, so damp it and try a shorter one over which the lengths do change evenly
            ridge *= 10;
            if (ridge > MOST_RIDGE)
            {
                return false;
            }
        }
        return Vectors.worst(residual) < SETTLED;
    }

    /**
     * Takes out of a direction any part of it that would change a bond's length, leaving a direction the points can
     * move along without having to be put back.
     *
     * @param x the points
     * @param gradient a direction to move them in
     * @return that direction, less the part that would change a length
     */
    public double[] tangent(double[] x, double[] gradient)
    {
        double[] along = new double[bonds.length];
        for (int bond = 0; bond < bonds.length; bond++)
        {
            double[] offset = Vectors.offset(x, dimensions, bonds[bond][0], bonds[bond][1]);
            double[] rise = Vectors.offset(gradient, dimensions, bonds[bond][0], bonds[bond][1]);
            along[bond] = 2 * Vectors.dot(offset, rise);
        }
        double[] result = gradient.clone();
        double[] multipliers = multipliers(x, along, LEAST_RIDGE);
        if (multipliers != null)
        {
            moveBy(x, multipliers, result);
        }
        return result;
    }

    /**
     * Moves points along their bonds, by the given amount per bond.
     *
     * @param from the points whose bond directions to use, so that a whole move is made from the directions the points
     *     had before any of it
     * @param multipliers how far to move along each bond
     * @param into the points to move, which are changed
     */
    private void moveBy(double[] from, double[] multipliers, double[] into)
    {
        for (int bond = 0; bond < bonds.length; bond++)
        {
            int first = bonds[bond][0];
            int second = bonds[bond][1];
            for (int axis = 0; axis < dimensions; axis++)
            {
                double part = 2 * multipliers[bond]
                    * (from[first * dimensions + axis] - from[second * dimensions + axis]);
                into[first * dimensions + axis] -= part;
                into[second * dimensions + axis] += part;
            }
        }
    }

    /**
     * Works out how far to move along each bond to bring about a wanted change in the bond lengths.
     *
     * @param x the points
     * @param rhs the change wanted in each bond
     * @param ridge how much to damp the answer by, which shortens the move
     * @return how far to move along each bond, or {@code null} if there is no one answer
     */
    private double[] multipliers(double[] x, double[] rhs, double ridge)
    {
        // each row says how much moving along one bond changes every bond's length, with the wanted change last
        int size = bonds.length;
        double[][] gram = new double[size][size + 1];
        for (int row = 0; row < size; row++)
        {
            for (int column = row; column < size; column++)
            {
                double value = shared(x, bonds[row], bonds[column]);
                gram[row][column] = value;
                gram[column][row] = value;
            }
            gram[row][row] += ridge;
            gram[row][size] = rhs[row];
        }

        for (int pivot = 0; pivot < size; pivot++)
        {
            // the largest remaining entry makes the steadiest pivot to divide by
            int best = pivot;
            for (int row = pivot + 1; row < size; row++)
            {
                if (Math.abs(gram[row][pivot]) > Math.abs(gram[best][pivot]))
                {
                    best = row;
                }
            }
            if (Math.abs(gram[best][pivot]) < SINGULAR)
            {
                return null;
            }
            double[] swap = gram[pivot];
            gram[pivot] = gram[best];
            gram[best] = swap;
            for (int row = pivot + 1; row < size; row++)
            {
                double factor = gram[row][pivot] / gram[pivot][pivot];
                for (int column = pivot; column <= size; column++)
                {
                    gram[row][column] -= factor * gram[pivot][column];
                }
            }
        }

        // read the answers back out, last row first, now that each row has only its own unknown left
        double[] solution = new double[size];
        for (int row = size - 1; row >= 0; row--)
        {
            double sum = gram[row][size];
            for (int column = row + 1; column < size; column++)
            {
                sum -= gram[row][column] * solution[column];
            }
            solution[row] = sum / gram[row][row];
        }
        return solution;
    }

    /**
     * @param x the points
     * @param first a bond
     * @param second a bond
     * @return how much moving along one bond changes the other's length, which is nothing unless they share a point
     */
    private double shared(double[] x, int[] first, int[] second)
    {
        double total = 0;
        for (int end = 0; end < 2; end++)
        {
            for (int other = 0; other < 2; other++)
            {
                if (first[end] != second[other])
                {
                    continue;
                }
                double[] one = Vectors.offset(x, dimensions, first[end], first[1 - end]);
                double[] another = Vectors.offset(x, dimensions, second[other], second[1 - other]);
                total += 4 * Vectors.dot(one, another);
            }
        }
        return total;
    }
}
