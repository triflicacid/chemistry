package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Molecule;

import java.util.NoSuchElementException;
import java.util.OptionalInt;

/**
 * A laid-out molecule, together with what resolves its indices back to the molecule.
 * <p>
 * Two kinds of index meet here. A depiction's own are <em>placement</em> indices, which say where something
 * is in {@link Depiction#atoms()} or {@link Depiction#bonds()}. The molecule's are <em>atom</em> and
 * <em>bond</em> indices, which say where something is in {@link Molecule#atoms()} or {@link Molecule#bonds()}.
 *
 * @param depiction what to draw, which is plain data fit to send to a client
 * @param snapshot the lookup behind {@code depiction}'s indices, which stays on the server
 */
public record Layout(Depiction depiction, MoleculeSnapshot snapshot)
{
    /**
     * @param atomIndex an index into {@link Molecule#atoms()}
     * @return where that atom was placed
     * @throws NoSuchElementException if no atom placement came from that atom
     */
    public AtomPlacement placementOfAtom(int atomIndex)
    {
        return depiction.atoms().stream()
            .filter(placement -> placement.sourceAtom().equals(OptionalInt.of(atomIndex)))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("No placement came from atom " + atomIndex));
    }

    /**
     * @param bondIndex an index into {@link Molecule#bonds()}
     * @return where that bond was placed
     * @throws NoSuchElementException if no bond placement came from that bond
     */
    public BondPlacement placementOfBond(int bondIndex)
    {
        return depiction.bonds().stream()
            .filter(placement -> placement.sourceBond().equals(OptionalInt.of(bondIndex)))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("No placement came from bond " + bondIndex));
    }
}
