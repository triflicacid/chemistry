package com.ruben.chemistry.canonical;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.Stereochemistry;

import java.util.Comparator;
import java.util.List;

/**
 * Everything about one atom that does not depend on which atoms surround it.
 *
 * @param element the atom's element symbol
 * @param aromatic whether the atom is aromatic
 * @param charge the atom's formal charge
 * @param isotope the atom's mass number, or {@code -1} where it declares none
 * @param hydrogenCount how many hydrogens the atom carries in total, implicit ones included
 * @param degree how many bonds the atom has
 * @param ringCount how many of the molecule's relevant cycles the atom lies in, or zero if it is in none
 * @param smallestRing the size of the smallest cycle the atom lies in, or zero if it is in none
 * @param stereoRole the parts the atom plays in its molecule's declared stereochemistry, {@link Stereochemistry#roleOf}
 */
public record AtomInvariant(
    String element,
    boolean aromatic,
    int charge,
    int isotope,
    int hydrogenCount,
    int degree,
    int ringCount,
    int smallestRing,
    int stereoRole) implements Comparable<AtomInvariant>
{
    /** The total ordering invariants, and so the atoms carrying them, are placed in. */
    private static final Comparator<AtomInvariant> ORDER = Comparator
        .comparing(AtomInvariant::element)
        .thenComparing(AtomInvariant::aromatic)
        .thenComparingInt(AtomInvariant::charge)
        .thenComparingInt(AtomInvariant::isotope)
        .thenComparingInt(AtomInvariant::hydrogenCount)
        .thenComparingInt(AtomInvariant::degree)
        .thenComparingInt(AtomInvariant::ringCount)
        .thenComparingInt(AtomInvariant::smallestRing)
        .thenComparingInt(AtomInvariant::stereoRole);

    /**
     * @param molecule the molecule {@code atom} belongs to
     * @param atom the atom to describe
     * @param stereoRole the parts {@code atom} plays in the declared stereochemistry being honoured ({@link Stereochemistry#roleOf}), or zero if ignoring stereochemistry
     * @return {@code atom}'s own properties
     */
    public static AtomInvariant of(Molecule molecule, Atom atom, int stereoRole)
    {
        List<Bond> bonds = molecule.bondsOf(atom);
        int smallestRing = molecule.ringSizes(atom).stream().mapToInt(Integer::intValue).min().orElse(0);
        return new AtomInvariant(
            atom.element(),
            atom.aromatic(),
            atom.charge(),
            atom.isotope().orElse(-1),
            molecule.totalHydrogenCount(atom),
            bonds.size(),
            molecule.ringCount(atom),
            smallestRing,
            stereoRole);
    }

    @Override
    public int compareTo(AtomInvariant other)
    {
        return ORDER.compare(this, other);
    }
}
