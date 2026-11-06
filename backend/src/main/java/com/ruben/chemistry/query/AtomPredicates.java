package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;

/**
 * Factory methods for common {@link AtomPredicate}s.
 */
public final class AtomPredicates
{
    private AtomPredicates()
    {
    }

    /**
     * @return a predicate matching any atom at all (the SMARTS {@code *} wildcard)
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
     * @return a predicate matching an atom with at least one ring bond (the SMARTS {@code R} primitive)
     */
    public static AtomPredicate inRing()
    {
        return (molecule, atom) -> isInRing(molecule, atom);
    }

    /**
     * @return a predicate matching an atom with no ring bond (the SMARTS {@code !R} primitive)
     */
    public static AtomPredicate notInRing()
    {
        return (molecule, atom) -> !isInRing(molecule, atom);
    }

    private static boolean isInRing(Molecule molecule, Atom atom)
    {
        for (Bond bond : molecule.bondsOf(atom))
        {
            if (molecule.ringBonds().contains(bond))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @param count the required number of explicit (heavy-atom) bonds
     * @return a predicate matching an atom with exactly that many bonds (the SMARTS {@code D} primitive)
     */
    public static AtomPredicate degree(int count)
    {
        return (molecule, atom) -> degreeOf(molecule, atom) == count;
    }

    private static int degreeOf(Molecule molecule, Atom atom)
    {
        int degree = 0;
        for (Bond ignored : molecule.bondsOf(atom))
        {
            degree++;
        }
        return degree;
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
     * @param template the atom to match exactly
     * @return a predicate matching an atom with the same element, aromaticity, charge, isotope, hydrogens etc.
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
