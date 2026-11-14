package com.ruben.chemistry.validate;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;

import java.util.List;

/**
 * One thing a {@link Check} found in a molecule.
 *
 * @param rule what was found
 * @param message the finding in words, naming the atoms and counts it is about
 * @param atoms the atoms the finding is about, by reference identity, unmodifiable and possibly empty
 * @param bonds the bonds the finding is about, by reference identity, unmodifiable and possibly empty
 */
public record Finding(Rule rule, String message, List<Atom> atoms, List<Bond> bonds)
{
    public Finding
    {
        atoms = List.copyOf(atoms);
        bonds = List.copyOf(bonds);
    }

    /**
     * Creates a finding about one atom.
     *
     * @param rule what was found
     * @param atom the atom it is about
     * @param message the finding in words
     * @return the finding
     */
    public static Finding atAtom(Rule rule, Atom atom, String message)
    {
        return new Finding(rule, message, List.of(atom), List.of());
    }

    /**
     * Creates a finding about one bond.
     *
     * @param rule what was found
     * @param bond the bond it is about
     * @param message the finding in words
     * @return the finding
     */
    public static Finding atBond(Rule rule, Bond bond, String message)
    {
        return new Finding(rule, message, List.of(bond.first(), bond.second()), List.of(bond));
    }

    /**
     * Creates a finding about the molecule rather than any part of it.
     *
     * @param rule what was found
     * @param message the finding in words
     * @return the finding
     */
    public static Finding atMolecule(Rule rule, String message)
    {
        return new Finding(rule, message, List.of(), List.of());
    }

    /**
     * @return how much this finding ought to stop its caller
     */
    public Severity severity()
    {
        return rule.severity();
    }

    /**
     * @return what part of the molecule this finding is about
     */
    public Category category()
    {
        return rule.category();
    }
}
