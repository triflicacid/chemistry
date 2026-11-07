package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;

import java.util.Set;

/**
 * Factory methods for common {@link AtomPredicate}s.
 */
public final class AtomPredicates
{
    private AtomPredicates()
    {
    }

    /**
     * @return a predicate matching any atom at all
     */
    public static AtomPredicate any()
    {
        return (molecule, atom) -> true;
    }

    /**
     * @param element the element symbol to match, e.g. {@code "C"}
     * @return a predicate matching atoms of exactly that element
     */
    public static AtomPredicate element(String element)
    {
        return (molecule, atom) -> atom.element().equals(element);
    }

    /**
     * @param atomicNumber the atomic number to match, e.g. {@code 6} for carbon
     * @return a predicate matching atoms of exactly that element
     * @throws IllegalArgumentException if {@code atomicNumber} has no known element
     */
    public static AtomPredicate atomicNumber(int atomicNumber)
    {
        return element(Elements.symbolOf(atomicNumber));
    }

    /**
     * @param element the element symbol to match, e.g. {@code "C"}
     * @return a predicate matching aromatic atoms of exactly that element
     */
    public static AtomPredicate aromatic(String element)
    {
        return element(element).and((molecule, atom) -> atom.aromatic());
    }

    /**
     * @param charge the required formal charge
     * @return a predicate matching an atom with exactly that formal charge
     */
    public static AtomPredicate charge(int charge)
    {
        return (molecule, atom) -> atom.charge() == charge;
    }

    /**
     * @param isotope the required isotope mass number
     * @return a predicate matching an atom with exactly that isotope mass number
     */
    public static AtomPredicate isotope(int isotope)
    {
        return (molecule, atom) -> atom.isotope().isPresent() && atom.isotope().getAsInt() == isotope;
    }

    /**
     * @param atomClass the required atom class number
     * @return a predicate matching an atom with exactly that atom class number
     */
    public static AtomPredicate atomClass(int atomClass)
    {
        return (molecule, atom) -> atom.atomClass().isPresent() && atom.atomClass().getAsInt() == atomClass;
    }

    /**
     * @return a predicate matching an atom with at least one ring bond
     */
    public static AtomPredicate inRing()
    {
        return AtomPredicates::isInRing;
    }

    /**
     * @return a predicate matching an atom with no ring bond
     */
    public static AtomPredicate notInRing()
    {
        return (molecule, atom) -> !isInRing(molecule, atom);
    }

    /**
     * @param molecule the molecule being matched against
     * @param atom the atom to test
     * @return {@code true} if any of {@code atom}'s bonds lies on a ring
     */
    private static boolean isInRing(Molecule molecule, Atom atom)
    {
        Set<Bond> ringBonds = molecule.ringBonds();
        return molecule.bondsOf(atom).stream().anyMatch(ringBonds::contains);
    }

    /**
     * @param count the required number of rings the atom belongs to
     * @return a predicate matching an atom that belongs to exactly that many of {@link Molecule#rings()}
     */
    public static AtomPredicate ringCount(int count)
    {
        return (molecule, atom) -> molecule.ringCount(atom) == count;
    }

    /**
     * @param size the required ring size
     * @return a predicate matching an atom that belongs to one of {@link Molecule#rings()} of exactly that size
     */
    public static AtomPredicate ringSize(int size)
    {
        return (molecule, atom) -> molecule.ringSizes(atom).contains(size);
    }

    /**
     * @param count the required number of explicit (heavy-atom) bonds
     * @return a predicate matching an atom with exactly that many bonds (the SMARTS {@code D} primitive)
     */
    public static AtomPredicate degree(int count)
    {
        return (molecule, atom) -> molecule.bondsOf(atom).size() == count;
    }

    /**
     * @param count the required total hydrogen count
     * @return a predicate matching an atom with exactly that many hydrogens, explicit or inferred
     */
    public static AtomPredicate hydrogenCount(int count)
    {
        return (molecule, atom) -> molecule.totalHydrogenCount(atom) == count;
    }

    /**
     * @param count the required implicit hydrogen count
     * @return a predicate matching an atom with exactly that many inferred hydrogens
     */
    public static AtomPredicate implicitHydrogenCount(int count)
    {
        return (molecule, atom) -> molecule.implicitHydrogenCount(atom) == count;
    }

    /**
     * @param count the required total valence
     * @return a predicate matching an atom with exactly that total valence
     */
    public static AtomPredicate valence(int count)
    {
        return (molecule, atom) -> molecule.valence(atom) == count;
    }

    /**
     * @param count the required total connection count
     * @return a predicate matching an atom with exactly that many connections (bonds plus hydrogens)
     */
    public static AtomPredicate connectivity(int count)
    {
        return (molecule, atom) -> molecule.bondsOf(atom).size() + molecule.totalHydrogenCount(atom) == count;
    }

    /**
     * @param subPattern the nested pattern to test, anchored at its own first atom position
     * @return a predicate matching an atom if {@code subPattern} has some match with its
     *     first atom position bound to that atom.
     * @throws IllegalArgumentException if {@code subPattern} has no atoms
     */
    public static AtomPredicate recursive(Query subPattern)
    {
        QueryAtom anchorPosition = subPattern.anchorAtom()
            .orElseThrow(() -> new IllegalArgumentException("Recursive sub-pattern must have at least one atom"));
        return (molecule, atom) -> new SubstructureMatcher(molecule).matches(subPattern, anchorPosition, atom);
    }

    /**
     * @param template the atom to match against
     * @return a predicate matching an atom with the same element, aromaticity and charge as
     *     {@code template}, and the same isotope and hydrogen count as any {@code template}
     *     specifies
     */
    public static AtomPredicate exactly(Atom template)
    {
        return (molecule, atom) ->
            atom.element().equals(template.element())
                && atom.aromatic() == template.aromatic()
                && atom.charge() == template.charge()
                && (template.isotope().isEmpty() || atom.isotope().equals(template.isotope()))
                && (template.hydrogenCount().isEmpty() || atom.hydrogenCount().equals(template.hydrogenCount()));
    }
}
