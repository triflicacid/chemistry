package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.linalg.Matrix;

import java.util.List;

public final class OrbitalComposition
{
    /** The kinds of function an orbital's share is split into: s, p, and d or higher. */
    public static final int KINDS = 3;

    private OrbitalComposition()
    {
    }

    /**
     * The Mulliken gross population of an orbital: each basis function's coefficient times its overlap with the orbital, which adds up
     * to one over all functions for a normalised orbital, gathered by the atom the function is on and by its kind.
     *
     * @param orbitals the calculation the orbital is of
     * @param channel the set of orbitals it is in
     * @param orbital which orbital of the set
     * @param sites how many atoms there are
     * @return for each atom, how much of the orbital is in its s, its p, and its d or higher functions
     * @throws IllegalArgumentException if the orbital is not in the set
     */
    public static double[][] gross(MolecularOrbitals orbitals, MolecularOrbitals.Channel channel, int orbital, int sites)
    {
        if (orbital < 0 || orbital >= channel.energies().length)
        {
            throw new IllegalArgumentException("there is no orbital " + orbital);
        }

        double[] c = channel.coefficients().column(orbital);
        Matrix overlap = orbitals.overlap();
        List<ContractedGaussian> functions = orbitals.basis().functions();
        double[][] shares = new double[sites][KINDS];
        for (int mu = 0; mu < c.length; mu++)
        {
            double sc = 0.0;
            for (int nu = 0; nu < c.length; nu++)
            {
                sc += overlap.get(mu, nu) * c[nu];
            }
            int kind = Math.min(KINDS - 1, functions.get(mu).angular().lx() + functions.get(mu).angular().ly() + functions.get(mu).angular().lz());
            shares[orbitals.basis().siteOfFunction().get(mu)][kind] += c[mu] * sc;
        }
        return shares;
    }

    /**
     * How much each orbital of a molecule is in each orbital of the whole that contains it, which is the overlap of the two. The whole
     * is made of the same basis functions at the same places as the molecule, so the molecule's orbital is a combination of some of the whole's.
     *
     * @param whole the calculation of everything together
     * @param wholeChannel a set of its orbitals
     * @param functions which of the whole's basis functions are the molecule's, in the order of the molecule's own
     * @param molecule the calculation of the molecule on its own
     * @param moleculeChannel a set of its orbitals
     * @return for each of the whole's orbitals and each of the molecule's, the overlap between them
     * @throws IllegalArgumentException if the molecule does not have as many basis functions as are said to be its
     */
    public static double[][] overlaps(MolecularOrbitals whole, MolecularOrbitals.Channel wholeChannel, List<Integer> functions, MolecularOrbitals molecule, MolecularOrbitals.Channel moleculeChannel)
    {
        if (functions.size() != molecule.basis().size())
        {
            throw new IllegalArgumentException("the molecule has " + molecule.basis().size() + " basis functions but " + functions.size() + " of the whole's are said to be its");
        }

        Matrix cWhole = wholeChannel.coefficients();
        Matrix cMolecule = moleculeChannel.coefficients();
        int wholeOrbitals = wholeChannel.energies().length;
        int moleculeOrbitals = moleculeChannel.energies().length;

        // the overlap of each of the molecule's functions with each of the whole's orbitals
        double[][] with = new double[functions.size()][wholeOrbitals];
        for (int mu = 0; mu < functions.size(); mu++)
        {
            for (int k = 0; k < wholeOrbitals; k++)
            {
                double sum = 0.0;
                for (int nu = 0; nu < cWhole.rows(); nu++)
                {
                    sum += whole.overlap().get(functions.get(mu), nu) * cWhole.get(nu, k);
                }
                with[mu][k] = sum;
            }
        }

        double[][] overlaps = new double[wholeOrbitals][moleculeOrbitals];
        for (int k = 0; k < wholeOrbitals; k++)
        {
            for (int j = 0; j < moleculeOrbitals; j++)
            {
                double sum = 0.0;
                for (int mu = 0; mu < functions.size(); mu++)
                {
                    sum += cMolecule.get(mu, j) * with[mu][k];
                }
                overlaps[k][j] = sum;
            }
        }
        return overlaps;
    }
}
