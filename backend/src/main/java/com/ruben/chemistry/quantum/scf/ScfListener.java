package com.ruben.chemistry.quantum.scf;

/**
 * Told how a self-consistent-field calculation is getting on, from the thread running it.
 * <p>
 * A listener may stop the calculation by throwing an unchecked exception, such as
 * {@link java.util.concurrent.CancellationException}, which then propagates out of the calculation.
 */
public interface ScfListener
{
    /** A listener that is told nothing it needs to act on. */
    ScfListener NONE = new ScfListener()
    {
    };

    /** The stage of a calculation that works out the integrals over the basis functions. */
    String INTEGRALS = "integrals";

    /** The stage of a calculation that iterates the orbitals until they are self-consistent. */
    String ITERATING = "iterating";

    /**
     * Told when a calculation moves on to a stage.
     *
     * @param stage {@link #INTEGRALS} or {@link #ITERATING}
     */
    default void stage(String stage)
    {
    }

    /**
     * Told after each Fock build.
     *
     * @param iteration how many have been built, from 1
     * @param energy the total energy so far, in hartree
     * @param error the root-mean-square element of the Fock-density commutator, which is zero at self-consistency
     */
    default void iteration(int iteration, double energy, double error)
    {
    }
}
