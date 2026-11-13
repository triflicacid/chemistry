package com.ruben.chemistry.space;

/**
 * Limited-memory BFGS, for finding the nearest low-strain arrangement of a {@link Strain}.
 */
final class Minimiser
{
    private static final int MEMORY = 8;

    /** The furthest, in angstroms, any one coordinate may move in a single step. */
    private static final double MAXIMUM_STEP = 0.4;

    private Minimiser()
    {
    }

    /**
     * @param strain what to minimise
     * @param x the starting coordinates, replaced by the result
     * @param iterations the most steps to take
     * @return the strain at the result
     */
    static double minimise(Strain strain, double[] x, int iterations)
    {
        int size = x.length;
        double[] gradient = new double[size];
        double energy = strain.evaluate(x, gradient);

        double[][] steps = new double[MEMORY][];
        double[][] changes = new double[MEMORY][];
        double[] rho = new double[MEMORY];
        int stored = 0;
        int oldest = 0;

        double[] direction = new double[size];
        double[] trial = new double[size];
        double[] trialGradient = new double[size];
        double[] alpha = new double[MEMORY];

        for (int iteration = 0; iteration < iterations; iteration++)
        {
            double norm = norm(gradient);
            if (norm < 1e-4)
            {
                break;
            }

            // two-loop recursion for the quasi-Newton direction
            for (int at = 0; at < size; at++)
            {
                direction[at] = -gradient[at];
            }
            for (int back = 0; back < stored; back++)
            {
                int slot = (oldest + stored - 1 - back) % MEMORY;
                alpha[slot] = rho[slot] * dot(steps[slot], direction);
                axpy(-alpha[slot], changes[slot], direction);
            }
            if (stored > 0)
            {
                int latest = (oldest + stored - 1) % MEMORY;
                double scale = dot(steps[latest], changes[latest]) / dot(changes[latest], changes[latest]);
                for (int at = 0; at < size; at++)
                {
                    direction[at] *= scale;
                }
            }
            else
            {
                double scale = 1 / Math.max(norm, 1);
                for (int at = 0; at < size; at++)
                {
                    direction[at] *= scale;
                }
            }
            for (int forward = 0; forward < stored; forward++)
            {
                int slot = (oldest + forward) % MEMORY;
                double beta = rho[slot] * dot(changes[slot], direction);
                axpy(alpha[slot] - beta, steps[slot], direction);
            }

            double slope = dot(direction, gradient);
            if (slope >= 0)
            {
                // not a descent direction, so forget the history and go downhill
                stored = 0;
                for (int at = 0; at < size; at++)
                {
                    direction[at] = -gradient[at] / Math.max(norm, 1);
                }
                slope = dot(direction, gradient);
            }

            double biggest = 0;
            for (double component : direction)
            {
                biggest = Math.max(biggest, Math.abs(component));
            }
            double step = biggest > MAXIMUM_STEP ? MAXIMUM_STEP / biggest : 1;

            double trialEnergy = energy;
            boolean moved = false;
            for (int attempt = 0; attempt < 30; attempt++)
            {
                for (int at = 0; at < size; at++)
                {
                    trial[at] = x[at] + step * direction[at];
                }
                trialEnergy = strain.evaluate(trial, trialGradient);
                if (trialEnergy <= energy + 1e-4 * step * slope)
                {
                    moved = true;
                    break;
                }
                step *= 0.5;
            }
            if (!moved)
            {
                if (stored == 0)
                {
                    break;
                }
                stored = 0;
                continue;
            }

            double[] stepTaken = new double[size];
            double[] gradientChange = new double[size];
            for (int at = 0; at < size; at++)
            {
                stepTaken[at] = trial[at] - x[at];
                gradientChange[at] = trialGradient[at] - gradient[at];
            }
            double curvature = dot(stepTaken, gradientChange);
            if (curvature > 1e-10)
            {
                int slot;
                if (stored < MEMORY)
                {
                    slot = (oldest + stored) % MEMORY;
                    stored++;
                }
                else
                {
                    slot = oldest;
                    oldest = (oldest + 1) % MEMORY;
                }
                steps[slot] = stepTaken;
                changes[slot] = gradientChange;
                rho[slot] = 1 / curvature;
            }

            double drop = energy - trialEnergy;
            System.arraycopy(trial, 0, x, 0, size);
            System.arraycopy(trialGradient, 0, gradient, 0, size);
            energy = trialEnergy;
            if (drop < 1e-9 * Math.max(1, energy) && norm < 1e-2)
            {
                break;
            }
        }
        return energy;
    }

    private static double dot(double[] a, double[] b)
    {
        double sum = 0;
        for (int at = 0; at < a.length; at++)
        {
            sum += a[at] * b[at];
        }
        return sum;
    }

    private static double norm(double[] a)
    {
        return Math.sqrt(dot(a, a));
    }

    private static void axpy(double factor, double[] x, double[] y)
    {
        for (int at = 0; at < y.length; at++)
        {
            y[at] += factor * x[at];
        }
    }
}
