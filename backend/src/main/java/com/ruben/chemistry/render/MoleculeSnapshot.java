package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the indices a {@link Depiction} carries back to the {@link Molecule} it was drawn from.
 */
public final class MoleculeSnapshot
{
    private final Molecule origin;

    /** The origin's fragments, which share its atoms and bonds. */
    private final List<Molecule> fragments;

    /** The fragment each of the origin's atoms is in, by atom index. */
    private final int[] fragmentOfAtom;

    /** The origin's atoms' indices, by reference identity. */
    private final Map<Atom, Integer> atomIndices = new IdentityHashMap<>();

    /**
     * @param origin the molecule to take a snapshot of
     */
    private MoleculeSnapshot(Molecule origin)
    {
        this.origin = origin;
        this.fragments = origin.fragments();

        List<Atom> atoms = origin.atoms();
        for (int index = 0; index < atoms.size(); index++)
        {
            atomIndices.put(atoms.get(index), index);
        }

        // fragments share the origin's atoms, so identity is enough to say which fragment an atom is in
        this.fragmentOfAtom = new int[atoms.size()];
        for (int fragment = 0; fragment < fragments.size(); fragment++)
        {
            for (Atom atom : fragments.get(fragment).atoms())
            {
                fragmentOfAtom[atomIndices.get(atom)] = fragment;
            }
        }
    }

    /**
     * @param origin the molecule to take a snapshot of
     * @return a snapshot of it
     * @throws NullPointerException if {@code origin} is {@code null}
     */
    public static MoleculeSnapshot of(Molecule origin)
    {
        return new MoleculeSnapshot(Objects.requireNonNull(origin, "origin"));
    }

    /**
     * @return the molecule this is a snapshot of
     */
    public Molecule getOrigin()
    {
        return origin;
    }

    /**
     * @param atomIndex an index into {@link Molecule#atoms()}
     * @return that atom
     * @throws IndexOutOfBoundsException if there is no such atom
     */
    public Atom getAtom(int atomIndex)
    {
        return origin.atoms().get(requireAtom(atomIndex));
    }

    /**
     * @param bondIndex an index into {@link Molecule#bonds()}
     * @return that bond
     * @throws IndexOutOfBoundsException if there is no such bond
     */
    public Bond getBond(int bondIndex)
    {
        int count = origin.bonds().size();
        if (bondIndex < 0 || bondIndex >= count)
        {
            throw new IndexOutOfBoundsException("Bond index " + bondIndex + " is out of range, as there are " + count + " bonds");
        }
        return origin.bonds().get(bondIndex);
    }

    /**
     * @return how many connected fragments the molecule has
     */
    public int getFragmentCount()
    {
        return fragments.size();
    }

    /**
     * @param fragmentIndex a fragment's index
     * @return that fragment, which is the origin itself if the molecule has only the one
     * @throws IndexOutOfBoundsException if there is no such fragment
     */
    public Molecule getFragment(int fragmentIndex)
    {
        if (fragmentIndex < 0 || fragmentIndex >= fragments.size())
        {
            throw new IndexOutOfBoundsException("Fragment index " + fragmentIndex + " is out of range, as there are " + fragments.size() + " fragments");
        }
        return fragments.get(fragmentIndex);
    }

    /**
     * @param atomIndex an index into {@link Molecule#atoms()}
     * @return the index of the fragment that atom is in
     * @throws IndexOutOfBoundsException if there is no such atom
     */
    public int getFragmentIndexOf(int atomIndex)
    {
        return fragmentOfAtom[requireAtom(atomIndex)];
    }

    /**
     * @param atomIndex an index into {@link Molecule#atoms()}
     * @return the fragment that atom is in
     * @throws IndexOutOfBoundsException if there is no such atom
     */
    public Molecule getFragmentOf(int atomIndex)
    {
        return fragments.get(getFragmentIndexOf(atomIndex));
    }

    /**
     * @param bondIndex an index into {@link Molecule#bonds()}
     * @return the index of the fragment that bond is in
     * @throws IndexOutOfBoundsException if there is no such bond
     */
    public int getFragmentIndexOfBond(int bondIndex)
    {
        // a bond joins its atoms, so either end says which fragment it is in
        return fragmentOfAtom[atomIndices.get(getBond(bondIndex).first())];
    }

    /**
     * @param bondIndex an index into {@link Molecule#bonds()}
     * @return the fragment that bond is in
     * @throws IndexOutOfBoundsException if there is no such bond
     */
    public Molecule getFragmentOfBond(int bondIndex)
    {
        return fragments.get(getFragmentIndexOfBond(bondIndex));
    }

    /**
     * @param atomIndex an atom index to check
     * @return {@code atomIndex}
     * @throws IndexOutOfBoundsException if there is no such atom
     */
    private int requireAtom(int atomIndex)
    {
        if (atomIndex < 0 || atomIndex >= fragmentOfAtom.length)
        {
            throw new IndexOutOfBoundsException("Atom index " + atomIndex + " is out of range, as there are " + fragmentOfAtom.length + " atoms");
        }
        return atomIndex;
    }
}
