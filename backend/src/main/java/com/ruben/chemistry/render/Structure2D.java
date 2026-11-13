package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Molecule;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalInt;

/**
 * A depiction of a molecule, together with the information needed to map its placements back to the molecule
 *
 * @param depiction the data describing what to draw, suitable for sending to a client
 * @param snapshot the server-side molecule data used to resolve source indices
 */
public record Structure2D(Depiction depiction, MoleculeSnapshot snapshot)
{
    /**
     * Gathers drawings of one molecule into one, each as a set of fragments of their own so that they are arranged
     * as the parts of a disconnected molecule are.
     *
     * @param layouts the drawings, each of the same molecule
     * @return the combined layout, which is {@code layouts}'s only element if there is just the one, and which resolves atoms
     *     and bonds as the first does
     * @throws IllegalArgumentException if there are no layouts
     */
    public static Structure2D sideBySide(List<Structure2D> layouts)
    {
        if (layouts.isEmpty())
        {
            throw new IllegalArgumentException("There is nothing to put side by side");
        }
        if (layouts.size() == 1)
        {
            return layouts.get(0);
        }

        List<AtomPlacement> atoms = new ArrayList<>();
        List<BondPlacement> bonds = new ArrayList<>();
        List<RingPlacement> rings = new ArrayList<>();
        List<FragmentFrame> fragments = new ArrayList<>();
        for (Structure2D layout : layouts)
        {
            Depiction depiction = layout.depiction();
            int atomOffset = atoms.size();
            int fragmentOffset = fragments.size();
            for (AtomPlacement atom : depiction.atoms())
            {
                atoms.add(atom.withIndex(atom.placementIndex() + atomOffset).inFragment(atom.fragment() + fragmentOffset));
            }
            for (BondPlacement bond : depiction.bonds())
            {
                bonds.add(new BondPlacement(bond.first() + atomOffset, bond.second() + atomOffset, bond.order(), bond.style(), bond.sourceBond()));
            }
            for (RingPlacement ring : depiction.rings())
            {
                rings.add(new RingPlacement(ring.atoms().stream().map(atom -> atom + atomOffset).toList(), ring.circle()));
            }
            for (FragmentFrame frame : depiction.fragments())
            {
                fragments.add(new FragmentFrame(frame.index() + fragmentOffset, frame.bounds()));
            }
        }
        return new Structure2D(new Depiction(atoms, bonds, rings, fragments), layouts.get(0).snapshot());
    }

    /**
     * Finds the atom placement created from the given molecule atom
     *
     * @param atomIndex an index into {@link Molecule#atoms()}
     * @return the placement whose source atom has the given index
     * @throws NoSuchElementException if no placement refers to that atom
     */
    public AtomPlacement placementOfAtom(int atomIndex)
    {
        return depiction.atoms().stream()
                .filter(placement -> placement.sourceAtom().equals(OptionalInt.of(atomIndex)))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No placement came from atom " + atomIndex));
    }

    /**
     * Finds the bond placement created from the given molecule bond
     *
     * @param bondIndex an index into {@link Molecule#bonds()}
     * @return the placement whose source bond has the given index
     * @throws NoSuchElementException if no placement refers to that bond
     */
    public BondPlacement placementOfBond(int bondIndex)
    {
        return depiction.bonds().stream()
                .filter(placement -> placement.sourceBond().equals(OptionalInt.of(bondIndex)))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No placement came from bond " + bondIndex));
    }
}
