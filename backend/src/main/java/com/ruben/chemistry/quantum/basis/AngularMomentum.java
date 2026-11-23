package com.ruben.chemistry.quantum.basis;

import java.util.ArrayList;
import java.util.List;

/**
 * The angular shape of a Cartesian Gaussian basis function.
 * <p>
 * With {@code (x, y, z)} measured from the function's own centre and {@code r^2 = x^2 + y^2 + z^2}, the
 * function is {@code x^lx * y^ly * z^lz * exp(-a r^2)}. The total {@code lx + ly + lz} is the shell: 0 is an s
 * function, 1 a p function, 2 a d function. For example {@code (1, 0, 0)} is p<sub>x</sub> and {@code (1, 1, 0)}
 * is d<sub>xy</sub>.
 *
 * @param lx how many factors of x, the displacement from the centre along the x axis
 * @param ly how many factors of y, the displacement from the centre along the y axis
 * @param lz how many factors of z, the displacement from the centre along the z axis
 */
public record AngularMomentum(int lx, int ly, int lz)
{
    public AngularMomentum
    {
        if (lx < 0 || ly < 0 || lz < 0)
        {
            throw new IllegalArgumentException("powers must not be negative: " + lx + ", " + ly + ", " + lz);
        }
    }

    /**
     * @return the total angular momentum, {@code lx + ly + lz}
     */
    public int total()
    {
        return lx + ly + lz;
    }

    /**
     * @param l the total angular momentum
     * @return every Cartesian component of that shell, with x varying slowest, so p is x, y, z and d is xx, xy, xz, yy, yz, zz
     * @throws IllegalArgumentException if {@code l} is negative
     */
    public static List<AngularMomentum> shell(int l)
    {
        if (l < 0)
        {
            throw new IllegalArgumentException("angular momentum must not be negative, was " + l);
        }
        List<AngularMomentum> components = new ArrayList<>();
        for (int lx = l; lx >= 0; lx--)
        {
            for (int ly = l - lx; ly >= 0; ly--)
            {
                components.add(new AngularMomentum(lx, ly, l - lx - ly));
            }
        }
        return List.copyOf(components);
    }
}
