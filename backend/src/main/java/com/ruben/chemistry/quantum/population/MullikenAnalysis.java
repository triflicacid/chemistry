package com.ruben.chemistry.quantum.population;

import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.quantum.scf.ScfResult;

import java.util.List;

/**
 * Mulliken population analysis: each electron in an overlap density {@code P_mu_nu S_mu_nu} is split equally
 * between the atoms its two basis functions sit on.
 */
public final class MullikenAnalysis
{
    private MullikenAnalysis()
    {
    }

    /**
     * @param result a converged calculation
     * @param siteOfFunction the index of the atom each basis function is centred on, in basis order
     * @param siteCount the number of atoms
     * @return the number of electrons assigned to each atom, {@code N_A = sum over mu in A, nu of P_mu_nu S_mu_nu}
     * @throws IllegalArgumentException if the site list does not match the basis or names an atom out of range
     */
    public static double[] populations(ScfResult result, List<Integer> siteOfFunction, int siteCount)
    {
        Matrix ps = result.density().times(result.overlap());
        if (siteOfFunction.size() != ps.rows())
        {
            throw new IllegalArgumentException("need a site for each of the " + ps.rows() + " basis functions, got " + siteOfFunction.size());
        }
        double[] populations = new double[siteCount];
        for (int mu = 0; mu < ps.rows(); mu++)
        {
            int site = siteOfFunction.get(mu);
            if (site < 0 || site >= siteCount)
            {
                throw new IllegalArgumentException("function " + mu + " is on site " + site + ", outside 0 to " + (siteCount - 1));
            }
            // the diagonal of P S is the sum over nu of P_mu_nu S_nu_mu: the electrons in mu, including half of each overlap it shares
            populations[site] += ps.get(mu, mu);
        }
        return populations;
    }

    /**
     * @param result a converged calculation
     * @param siteOfFunction the index of the atom each basis function is centred on, in basis order
     * @param nuclearCharges the charge each atom's electrons see, in electrons; the effective nuclear charge when core electrons are replaced
     * @return the charge on each atom, its nuclear charge minus its electron population
     * @throws IllegalArgumentException if the site list does not match the basis or names an atom out of range
     */
    public static double[] charges(ScfResult result, List<Integer> siteOfFunction, double[] nuclearCharges)
    {
        double[] populations = populations(result, siteOfFunction, nuclearCharges.length);
        double[] charges = new double[populations.length];
        for (int site = 0; site < charges.length; site++)
        {
            charges[site] = nuclearCharges[site] - populations[site];
        }
        return charges;
    }
}
