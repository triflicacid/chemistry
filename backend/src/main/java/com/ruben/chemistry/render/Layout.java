package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Molecule;

import java.util.NoSuchElementException;
import java.util.OptionalInt;

/**
 * A depiction of a molecule, together with the information needed to map its placements back to the molecule
 *
 * @param depiction the data describing what to draw, suitable for sending to a client
 * @param snapshot the server-side molecule data used to resolve source indices
 */
public record Layout(Depiction depiction, MoleculeSnapshot snapshot)
{
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