package com.ruben.chemistry.quantum.interaction;

import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.basis.ElementBasis;
import com.ruben.chemistry.quantum.basis.Shell;
import com.ruben.chemistry.quantum.integrals.EcpCentre;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.quantum.scf.ClosedShellScf;
import com.ruben.chemistry.quantum.scf.ScfResult;
import com.ruben.chemistry.quantum.scf.ScfSettings;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Closed-shell Hartree-Fock interaction energies of two fragments, with the counterpoise correction of Boys and Bernardi.
 */
public final class Counterpoise
{
    private Counterpoise()
    {
    }

    /**
     * Runs the five calculations with {@link ScfSettings#defaults()}.
     *
     * @param first the first fragment, with positions in bohr
     * @param second the second fragment, with positions in bohr
     * @param library the basis sets and core potentials of the elements
     * @return the energies of the dimer and of each fragment with and without ghost functions
     * @throws IllegalArgumentException if an element is not in the library, or a fragment or the dimer does not have a closed-shell electron count
     * @throws IllegalStateException if a calculation does not converge
     */
    public static CounterpoiseResult interaction(Fragment first, Fragment second, BasisLibrary library)
    {
        return interaction(first, second, library, ScfSettings.defaults());
    }

    /**
     * @param first the first fragment, with positions in bohr
     * @param second the second fragment, with positions in bohr
     * @param library the basis sets and core potentials of the elements
     * @param settings the convergence controls for every calculation
     * @return the energies of the dimer and of each fragment with and without ghost functions
     * @throws IllegalArgumentException if an element is not in the library, or a fragment or the dimer does not have a closed-shell electron count
     * @throws IllegalStateException if a calculation does not converge
     */
    public static CounterpoiseResult interaction(Fragment first, Fragment second, BasisLibrary library, ScfSettings settings)
    {
        return new CounterpoiseResult(
                energy(first.plus(second), null, library, settings),
                energy(first, null, library, settings),
                energy(second, null, library, settings),
                energy(first, second, library, settings),
                energy(second, first, library, settings));
    }

    /**
     * @param real the atoms that carry nuclei and electrons
     * @param ghost the atoms that carry only basis functions, or {@code null} for none
     * @param library the basis sets and core potentials of the elements
     * @param settings the convergence controls
     * @return the Hartree-Fock energy of {@code real} in the basis functions of both, in hartree
     * @throws IllegalArgumentException if an element is not in the library or {@code real} does not have a closed-shell electron count
     * @throws IllegalStateException if the calculation does not converge
     */
    public static double energy(Fragment real, Fragment ghost, BasisLibrary library, ScfSettings settings)
    {
        List<ContractedGaussian> functions = new ArrayList<>();
        List<PointCharge> nuclei = new ArrayList<>();
        List<EcpCentre> cores = new ArrayList<>();
        int electrons = -real.charge();
        for (int i = 0; i < real.size(); i++)
        {
            ElementBasis element = library.element(real.elements().get(i));
            Vec3 position = real.positions().get(i);
            addFunctions(functions, element, position);
            int zeff = element.effectiveNuclearCharge();
            electrons += zeff;
            nuclei.add(new PointCharge(position, zeff));
            if (element.ecp() != null)
            {
                cores.add(new EcpCentre(position, element.ecp()));
            }
        }
        if (ghost != null)
        {
            for (int i = 0; i < ghost.size(); i++)
            {
                addFunctions(functions, library.element(ghost.elements().get(i)), ghost.positions().get(i));
            }
        }
        if (electrons % 2 != 0)
        {
            throw new IllegalArgumentException("closed-shell calculation needs an even number of electrons, the fragment has " + electrons);
        }

        ScfResult result = ClosedShellScf.run(functions, nuclei, cores, electrons, settings);
        if (!result.converged())
        {
            throw new IllegalStateException("SCF did not converge in " + result.iterations() + " iterations");
        }
        return result.totalEnergy();
    }

    private static void addFunctions(List<ContractedGaussian> functions, ElementBasis element, Vec3 position)
    {
        for (Shell shell : element.shells())
        {
            functions.addAll(shell.functionsAt(position));
        }
    }
}
