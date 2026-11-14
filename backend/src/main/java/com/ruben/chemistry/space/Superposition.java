package com.ruben.chemistry.space;

import java.util.List;

/**
 * Optimal rigid superposition of two point sets, after Horn: the best rotation is the
 * eigenvector of a 4x4 matrix built from the sets' covariance, and the eigenvalue gives the error.
 */
final class Superposition
{
    private Superposition()
    {
    }

    /**
     * @param first a set of points
     * @param second a set of points, as many as {@code first}, matched one to one
     * @return the smallest root-mean-square distance between them over every rotation and translation
     */
    static double rmsd(List<Vec3> first, List<Vec3> second)
    {
        int count = first.size();
        Vec3 centreA = centroid(first);
        Vec3 centreB = centroid(second);

        double[][] covariance = new double[3][3];
        double norms = 0;
        for (int at = 0; at < count; at++)
        {
            Vec3 a = first.get(at).minus(centreA);
            Vec3 b = second.get(at).minus(centreB);
            double[] p = {a.x(), a.y(), a.z()};
            double[] q = {b.x(), b.y(), b.z()};
            for (int row = 0; row < 3; row++)
            {
                for (int column = 0; column < 3; column++)
                {
                    covariance[row][column] += p[row] * q[column];
                }
            }
            norms += a.dot(a) + b.dot(b);
        }

        double sxx = covariance[0][0];
        double sxy = covariance[0][1];
        double sxz = covariance[0][2];
        double syx = covariance[1][0];
        double syy = covariance[1][1];
        double syz = covariance[1][2];
        double szx = covariance[2][0];
        double szy = covariance[2][1];
        double szz = covariance[2][2];
        double[][] matrix = {
            {sxx + syy + szz, syz - szy, szx - sxz, sxy - syx},
            {syz - szy, sxx - syy - szz, sxy + syx, szx + sxz},
            {szx - sxz, sxy + syx, -sxx + syy - szz, syz + szy},
            {sxy - syx, szx + sxz, syz + szy, -sxx - syy + szz}
        };

        double largest = largestEigenvalue(matrix);
        return Math.sqrt(Math.max(0, (norms - 2 * largest) / count));
    }

    private static Vec3 centroid(List<Vec3> points)
    {
        Vec3 sum = Vec3.ZERO;
        for (Vec3 point : points)
        {
            sum = sum.plus(point);
        }
        return sum.times(1.0 / points.size());
    }

    /**
     * Finds the largest eigenvalue of a small symmetric matrix by cyclic Jacobi rotations.
     */
    private static double largestEigenvalue(double[][] matrix)
    {
        int size = matrix.length;
        for (int sweep = 0; sweep < 60; sweep++)
        {
            double off = 0;
            for (int row = 0; row < size; row++)
            {
                for (int column = row + 1; column < size; column++)
                {
                    off += matrix[row][column] * matrix[row][column];
                }
            }
            if (off < 1e-22)
            {
                break;
            }
            for (int p = 0; p < size; p++)
            {
                for (int q = p + 1; q < size; q++)
                {
                    if (Math.abs(matrix[p][q]) < 1e-300)
                    {
                        continue;
                    }
                    double theta = (matrix[q][q] - matrix[p][p]) / (2 * matrix[p][q]);
                    double t = Math.signum(theta == 0 ? 1 : theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
                    double c = 1 / Math.sqrt(t * t + 1);
                    double s = t * c;
                    for (int k = 0; k < size; k++)
                    {
                        double kp = matrix[k][p];
                        double kq = matrix[k][q];
                        matrix[k][p] = c * kp - s * kq;
                        matrix[k][q] = s * kp + c * kq;
                    }
                    for (int k = 0; k < size; k++)
                    {
                        double pk = matrix[p][k];
                        double qk = matrix[q][k];
                        matrix[p][k] = c * pk - s * qk;
                        matrix[q][k] = s * pk + c * qk;
                    }
                }
            }
        }
        double largest = Double.NEGATIVE_INFINITY;
        for (int at = 0; at < size; at++)
        {
            largest = Math.max(largest, matrix[at][at]);
        }
        return largest;
    }
}
