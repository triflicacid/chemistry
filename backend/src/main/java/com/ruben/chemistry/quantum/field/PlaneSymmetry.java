package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.quantum.basis.AngularMomentum;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.quantum.linalg.SymmetricEigen;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells sigma orbitals from pi orbitals of a flat molecule by their symmetry in the plane of its atoms.
 * <p>
 * Reflecting a flat molecule in its own plane leaves a sigma orbital as it was and turns a pi orbital over, which is what makes a
 * pi orbital have a nodal plane there. The functions that turn over are the p functions that point out of the plane, so the share of an
 * orbital that is made of them is its pi character. The distinction means nothing for a molecule that is not flat.
 */
public final class PlaneSymmetry
{
    /** The most the atoms may lie off their plane, as a root mean square distance in ångström, for the molecule to count as flat. */
    public static final double MAX_FLATNESS = 0.35;

    private PlaneSymmetry()
    {
    }

    /**
     * The plane through some atoms that they lie closest to.
     *
     * @param normal the unit vector at right angles to it
     * @param flatness how far the atoms lie off it, as a root mean square distance in the unit of the positions
     */
    public record Plane(Vec3 normal, double flatness)
    {
        /**
         * @return whether the atoms lie flat enough for sigma and pi to mean something
         */
        public boolean flat()
        {
            return flatness <= MAX_FLATNESS;
        }
    }

    /**
     * @param positions where the atoms are, in ångström
     * @return the plane through them that they lie closest to, or {@code null} if there are fewer than three atoms or they lie on one line,
     *     where there is no one plane
     */
    public static Plane fit(List<Vec3> positions)
    {
        if (positions.size() < 3)
        {
            return null;
        }

        Vec3 centre = Vec3.ZERO;
        for (Vec3 position : positions)
        {
            centre = centre.plus(position);
        }
        centre = centre.times(1.0 / positions.size());

        // the plane's normal is the direction the atoms spread least in, which is the smallest axis of their covariance
        double[][] covariance = new double[3][3];
        for (Vec3 position : positions)
        {
            double[] d = {position.x() - centre.x(), position.y() - centre.y(), position.z() - centre.z()};
            for (int i = 0; i < 3; i++)
            {
                for (int j = 0; j < 3; j++)
                {
                    covariance[i][j] += d[i] * d[j] / positions.size();
                }
            }
        }

        SymmetricEigen eigen = SymmetricEigen.of(Matrix.of(covariance));
        // atoms on a line spread in one direction only, so any plane through the line fits equally and none is the plane
        if (eigen.values()[1] < 1.0e-6)
        {
            return null;
        }
        Vec3 normal = new Vec3(eigen.vectors().get(0, 0), eigen.vectors().get(1, 0), eigen.vectors().get(2, 0)).normalised();
        return new Plane(normal, Math.sqrt(Math.max(0.0, eigen.values()[0])));
    }

    /**
     * How much of an orbital is pi.
     *
     * @param orbitals the calculation the orbital is of
     * @param channel the set of orbitals it is in
     * @param orbital which orbital of the set
     * @param normal the unit vector at right angles to the molecule's plane, in the frame the basis functions are placed in
     * @return the share, from zero for a sigma orbital to one for a pi orbital
     * @throws IllegalArgumentException if the orbital is not in the set
     */
    public static double piShare(MolecularOrbitals orbitals, MolecularOrbitals.Channel channel, int orbital, Vec3 normal)
    {
        if (orbital < 0 || orbital >= channel.energies().length)
        {
            throw new IllegalArgumentException("there is no orbital " + orbital);
        }

        List<Integer> shells = pShells(orbitals);
        double[] c = channel.coefficients().column(orbital);
        double[] n = {normal.x(), normal.y(), normal.z()};

        // an orbital's part along the out-of-plane p function of each shell
        double[] amplitude = new double[shells.size()];
        for (int t = 0; t < shells.size(); t++)
        {
            int at = shells.get(t);
            amplitude[t] = n[0] * c[at] + n[1] * c[at + 1] + n[2] * c[at + 2];
        }

        // and what those parts make together, with the overlap of the functions they are of
        double share = 0.0;
        for (int t = 0; t < shells.size(); t++)
        {
            for (int u = 0; u < shells.size(); u++)
            {
                double between = 0.0;
                for (int i = 0; i < 3; i++)
                {
                    for (int j = 0; j < 3; j++)
                    {
                        between += n[i] * n[j] * orbitals.overlap().get(shells.get(t) + i, shells.get(u) + j);
                    }
                }
                share += amplitude[t] * amplitude[u] * between;
            }
        }
        return Math.clamp(share, 0.0, 1.0);
    }

    /**
     * @param orbitals a calculation
     * @return the index of the first function of each shell of p functions, whose three functions follow it as px, py and pz
     */
    private static List<Integer> pShells(MolecularOrbitals orbitals)
    {
        List<ContractedGaussian> functions = orbitals.basis().functions();
        List<Integer> shells = new ArrayList<>();
        for (int f = 0; f + 2 < functions.size(); f++)
        {
            if (is(functions.get(f), 1, 0, 0) && is(functions.get(f + 1), 0, 1, 0) && is(functions.get(f + 2), 0, 0, 1)
                    && functions.get(f).centre().equals(functions.get(f + 1).centre()) && functions.get(f).centre().equals(functions.get(f + 2).centre())
                    && functions.get(f).size() == functions.get(f + 1).size() && functions.get(f).size() == functions.get(f + 2).size())
            {
                shells.add(f);
                f += 2;
            }
        }
        return shells;
    }

    private static boolean is(ContractedGaussian function, int lx, int ly, int lz)
    {
        AngularMomentum angular = function.angular();
        return angular.lx() == lx && angular.ly() == ly && angular.lz() == lz;
    }
}
