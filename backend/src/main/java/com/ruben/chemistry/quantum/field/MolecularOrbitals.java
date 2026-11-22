package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.quantum.basis.BasisSet;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.quantum.population.RhfMullikenCharges;
import com.ruben.chemistry.quantum.population.RohfMullikenCharges;
import com.ruben.chemistry.quantum.population.UhfMullikenCharges;
import com.ruben.chemistry.quantum.scf.RestrictedOpenShellScfResult;
import com.ruben.chemistry.quantum.scf.UnrestrictedScfResult;

import java.util.List;

/**
 * The molecular orbitals of a Hartree-Fock calculation of any kind.
 * <p>
 * A restricted calculation has one set of orbitals for both spins, as has a restricted open-shell one, though its orbitals
 * are not all filled the same: some hold two electrons and some one. An unrestricted calculation has a set for each spin.
 *
 * @param basis the basis functions the orbitals are combinations of, placed on the molecule, in bohr
 * @param overlap the overlap of each basis function with each other, which the orbitals are normalised with
 * @param density the density matrix of all the electrons, both spins together, over the basis functions
 * @param nuclei the nuclei as the point charges the electrons see, which are the effective charges where a core is replaced by a potential, in bohr
 * @param channels the sets of orbitals: one, or two for an unrestricted calculation, the spin-up set first
 * @param totalEnergy the total energy of the calculation, in hartree
 * @param iterations how many Fock builds it took
 */
public record MolecularOrbitals(BasisSet basis, Matrix overlap, Matrix density, List<PointCharge> nuclei, List<Channel> channels, double totalEnergy, int iterations)
{
    /** The name of the one set of orbitals that both spins share. */
    public static final String RESTRICTED = "restricted";

    /** The name of the set of spin-up orbitals of an unrestricted calculation. */
    public static final String ALPHA = "alpha";

    /** The name of the set of spin-down orbitals of an unrestricted calculation. */
    public static final String BETA = "beta";

    public MolecularOrbitals
    {
        channels = List.copyOf(channels);
    }

    /**
     * One set of orbitals.
     *
     * @param spin {@link #RESTRICTED}, {@link #ALPHA} or {@link #BETA}
     * @param energies the orbital energies in ascending order, in hartree
     * @param coefficients the orbitals as columns over the basis functions, in the order of the energies
     * @param occupancy how many electrons each orbital holds, which is two, one or none
     */
    public record Channel(String spin, double[] energies, Matrix coefficients, int[] occupancy)
    {
        /**
         * @return the index of the highest orbital that holds electrons, or -1 if none does
         */
        public int homo()
        {
            int homo = -1;
            for (int index = 0; index < occupancy.length; index++)
            {
                if (occupancy[index] > 0)
                {
                    homo = index;
                }
            }
            return homo;
        }
    }

    /**
     * @param calculation a closed-shell restricted calculation
     * @return its orbitals, the lowest half of the electrons' worth filled
     */
    public static MolecularOrbitals of(RhfMullikenCharges.Calculation calculation)
    {
        var result = calculation.result();
        int electrons = (int) Math.round(result.density().times(result.overlap()).trace());
        int[] occupancy = new int[result.orbitalEnergies().length];
        for (int index = 0; index < electrons / 2; index++)
        {
            occupancy[index] = 2;
        }
        return new MolecularOrbitals(calculation.basis(), result.overlap(), result.density(), calculation.nuclei(), List.of(new Channel(RESTRICTED, result.orbitalEnergies(), result.coefficients(), occupancy)),
                result.totalEnergy(), result.iterations());
    }

    /**
     * @param calculation an unrestricted calculation
     * @return its orbitals, a set for each spin
     */
    public static MolecularOrbitals of(UhfMullikenCharges.Calculation calculation)
    {
        UnrestrictedScfResult result = calculation.result();
        return new MolecularOrbitals(calculation.basis(), result.overlap(), result.alphaDensity().plus(result.betaDensity()), calculation.nuclei(),
                List.of(
                        new Channel(ALPHA, result.alphaOrbitalEnergies(), result.alphaCoefficients(), filled(result.alphaOrbitalEnergies().length, 0, result.alphaElectrons())),
                        new Channel(BETA, result.betaOrbitalEnergies(), result.betaCoefficients(), filled(result.betaOrbitalEnergies().length, 0, result.betaElectrons()))),
                result.totalEnergy(), result.iterations());
    }

    /**
     * @param calculation a restricted open-shell calculation
     * @return its one set of orbitals, the closed ones holding two electrons, the open ones one, and the rest none
     */
    public static MolecularOrbitals of(RohfMullikenCharges.Calculation calculation)
    {
        RestrictedOpenShellScfResult result = calculation.result();
        int[] occupancy = new int[result.orbitalEnergies().length];
        for (int index = 0; index < result.alphaElectrons(); index++)
        {
            occupancy[index] = index < result.closedOrbitals() ? 2 : 1;
        }
        return new MolecularOrbitals(calculation.basis(), result.overlap(), result.alphaDensity().plus(result.betaDensity()), calculation.nuclei(),
                List.of(new Channel(RESTRICTED, result.orbitalEnergies(), result.coefficients(), occupancy)),
                result.totalEnergy(), result.iterations());
    }

    /**
     * @return the density matrix of the spin density, the electrons of spin up less those of spin down, over the basis functions; which is
     *     zero throughout for a closed-shell calculation, and for a restricted open-shell one is made of the singly occupied orbitals alone
     */
    public Matrix spinDensity()
    {
        int size = density.rows();
        Matrix spin = new Matrix(size, size);
        for (Channel channel : channels)
        {
            for (int orbital = 0; orbital < channel.occupancy().length; orbital++)
            {
                int held = channel.occupancy()[orbital];
                double weight = switch (channel.spin())
                {
                    case ALPHA -> held;
                    case BETA -> -held;
                    default -> held == 1 ? 1 : 0;
                };
                if (weight == 0.0)
                {
                    continue;
                }

                double[] column = channel.coefficients().column(orbital);
                for (int row = 0; row < size; row++)
                {
                    for (int other = 0; other < size; other++)
                    {
                        spin.set(row, other, spin.get(row, other) + weight * column[row] * column[other]);
                    }
                }
            }
        }
        return spin;
    }

    /**
     * @param spin which set of orbitals
     * @return that set, or {@code null} if there is no such set
     */
    public Channel channel(String spin)
    {
        return channels.stream().filter(channel -> channel.spin().equals(spin)).findFirst().orElse(null);
    }

    /**
     * @param size how many orbitals there are
     * @param from the first filled orbital
     * @param count how many are filled, each with one electron
     * @return the occupancy of each orbital
     */
    private static int[] filled(int size, int from, int count)
    {
        int[] occupancy = new int[size];
        for (int index = from; index < from + count; index++)
        {
            occupancy[index] = 1;
        }
        return occupancy;
    }
}
