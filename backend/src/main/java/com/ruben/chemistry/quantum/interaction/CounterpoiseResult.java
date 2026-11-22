package com.ruben.chemistry.quantum.interaction;

/**
 * The energies behind a counterpoise-corrected interaction energy, all in hartree.
 *
 * @param dimer the energy of both fragments together, in the basis functions of both
 * @param firstAlone the energy of the first fragment in its own basis functions
 * @param secondAlone the energy of the second fragment in its own basis functions
 * @param firstInDimerBasis the energy of the first fragment with ghost functions of the second
 * @param secondInDimerBasis the energy of the second fragment with ghost functions of the first
 */
public record CounterpoiseResult(double dimer, double firstAlone, double secondAlone, double firstInDimerBasis, double secondInDimerBasis)
{
    /**
     * @return the dimer energy less the two fragment energies in their own basis functions, which includes the basis-set superposition error
     */
    public double uncorrected()
    {
        return dimer - firstAlone - secondAlone;
    }

    /**
     * @return the dimer energy less the two fragment energies in the full dimer basis, the counterpoise-corrected interaction energy
     */
    public double corrected()
    {
        return dimer - firstInDimerBasis - secondInDimerBasis;
    }

    /**
     * @return how much the correction raises the interaction energy, the superposition error that made the dimer look more bound
     */
    public double superpositionError()
    {
        return corrected() - uncorrected();
    }
}
