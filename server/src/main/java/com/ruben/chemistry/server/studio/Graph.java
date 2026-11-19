package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.space.Vec3;

import java.util.Map;
import java.util.Set;

/**
 * A session's graph.
 *
 * @param molecule the working graph
 * @param atomIds what each of its atoms is called
 * @param bondIds what each of its bonds is called, by the pair of its atoms' names
 * @param geometry where each atom is, so that an atom added or deleted comes back or goes with the graph
 */
record Graph(Molecule molecule, Map<Atom, String> atomIds, Map<Set<String>, String> bondIds, Map<String, Vec3> geometry)
{
    /**
     * @param id an atom's identifier
     * @return the atom
     * @throws StudioEditException with {@code UNKNOWN_ID} if the graph holds no such atom
     */
    Atom atomNamed(String id)
    {
        for (Atom atom : molecule.atoms())
        {
            if (id != null && id.equals(atomIds.get(atom)))
            {
                return atom;
            }
        }
        throw new StudioEditException("UNKNOWN_ID", "This session holds no atom '" + id + "'");
    }

    /**
     * @param id a bond's identifier
     * @return the bond
     * @throws StudioEditException with {@code UNKNOWN_ID} if the graph holds no such bond
     */
    Bond bondNamed(String id)
    {
        for (Bond bond : molecule.bonds())
        {
            if (id != null && id.equals(bondIds.get(endpoints(atomIds, bond))))
            {
                return bond;
            }
        }
        throw new StudioEditException("UNKNOWN_ID", "This session holds no bond '" + id + "'");
    }

    /**
     * @param atom an atom of the graph, by reference
     * @return where it is
     */
    Vec3 positionOf(Atom atom)
    {
        return geometry.get(atomIds.get(atom));
    }

    /**
     * @param atomIds what each atom is called
     * @param bond a bond between two of them
     * @return the pair of names a bond is identified by
     * @throws IllegalArgumentException if either end of the bond has no name
     */
    static Set<String> endpoints(Map<Atom, String> atomIds, Bond bond)
    {
        String first = atomIds.get(bond.first());
        String second = atomIds.get(bond.second());
        if (first == null || second == null)
        {
            throw new IllegalArgumentException("The bond joins an atom this session does not hold: " + bond);
        }

        return Set.of(first, second);
    }
}
