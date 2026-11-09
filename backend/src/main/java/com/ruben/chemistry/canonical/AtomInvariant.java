package com.ruben.chemistry.canonical;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.Stereochemistry;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Everything about one atom that does not depend on which atoms surround it.
 *
 * @param element the atom's element symbol
 * @param aromatic whether the atom is aromatic
 * @param charge the atom's formal charge
 * @param isotope the atom's mass number, or {@code -1} where it declares none
 * @param hydrogenCount how many hydrogens the atom carries in total, implicit ones included
 * @param degree how many bonds the atom has
 * @param inRing whether any of those bonds is a ring bond
 * @param stereoRole the parts the atom plays in its molecule's declared stereochemistry, {@link Stereochemistry#roleOf}
 */
public record AtomInvariant(
    String element,
    boolean aromatic,
    int charge,
    int isotope,
    int hydrogenCount,
    int degree,
    boolean inRing,
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
        .thenComparing(AtomInvariant::inRing)
        .thenComparingInt(AtomInvariant::stereoRole);

    /**
     * @param molecule the molecule {@code atom} belongs to
     * @param ringBonds {@code molecule}'s ring bonds, passed in so that one pass over a molecule need only find them once
     * @param atom the atom to describe
     * @param stereoRole the parts {@code atom} plays in the declared stereochemistry being honoured ({@link Stereochemistry#roleOf}), or zero if ignoring stereochemistry
     * @return {@code atom}'s own properties
     */
    public static AtomInvariant of(Molecule molecule, Set<Bond> ringBonds, Atom atom, int stereoRole)
    {
        List<Bond> bonds = molecule.bondsOf(atom);
        boolean inRing = bonds.stream().anyMatch(ringBonds::contains);
        return new AtomInvariant(
            atom.element(),
            atom.aromatic(),
            atom.charge(),
            atom.isotope().orElse(-1),
            molecule.totalHydrogenCount(atom),
            bonds.size(),
            inRing,
            stereoRole);
    }

    @Override
    public int compareTo(AtomInvariant other)
    {
        return ORDER.compare(this, other);
    }
}
