package com.ruben.chemistry.space;

/**
 * Arithmetic on vectors held as arrays of their components.
 */
public final class Vectors
{
    private Vectors()
    {
    }

    /**
     * @param first one vector
     * @param second another vector of the same length
     * @return their dot product
     */
    public static double dot(double[] first, double[] second)
    {
        double sum = 0;
        for (int at = 0; at < first.length; at++)
        {
            sum += first[at] * second[at];
        }
        return sum;
    }

    /**
     * @param first one vector of three components
     * @param second another
     * @return the vector at right angles to both, as long as the area they span
     */
    public static double[] cross(double[] first, double[] second)
    {
        return new double[] {
            first[1] * second[2] - first[2] * second[1],
            first[2] * second[0] - first[0] * second[2],
            first[0] * second[1] - first[1] * second[0]};
    }

    /**
     * @param vector a vector of three components, which must not be of no length
     * @return it, scaled to one long
     */
    public static double[] normalised(double[] vector)
    {
        double length = Math.sqrt(dot(vector, vector));
        return new double[] {vector[0] / length, vector[1] / length, vector[2] / length};
    }

    /**
     * @param x the points, as all components of each point in turn
     * @param dimensions how many components each point has
     * @param first the point to measure from
     * @param second the point to measure to
     * @return the vector from the second point to the first
     */
    public static double[] offset(double[] x, int dimensions, int first, int second)
    {
        double[] offset = new double[dimensions];
        for (int axis = 0; axis < dimensions; axis++)
        {
            offset[axis] = x[first * dimensions + axis] - x[second * dimensions + axis];
        }
        return offset;
    }

    /**
     * @param values some numbers
     * @return the largest of them, ignoring sign
     */
    public static double worst(double[] values)
    {
        double worst = 0;
        for (double value : values)
        {
            worst = Math.max(worst, Math.abs(value));
        }
        return worst;
    }
}
