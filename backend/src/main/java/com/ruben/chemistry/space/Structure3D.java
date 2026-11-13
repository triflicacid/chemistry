package com.ruben.chemistry.space;

import com.ruben.chemistry.core.BondOrder;

import java.util.List;

/**
 * A molecule in three dimensions.
 *
 * @param sites the nodes
 * @param connections the bonds between nodes
 * @param conformers the shapes found, lowest strain first
 */
public record Structure3D(List<Site> sites, List<Connection> connections, List<Conformer> conformers)
{
    /**
     * @param index the node's index, which the positions of every conformer are listed by
     * @param element the element symbol
     * @param isotope the mass number, or 0 if unspecified
     * @param atom the index of the molecule atom this is, or the atom an implied hydrogen is attached to
     * @param implied whether this is a hydrogen the molecule did not write as an atom
     * @param charge the formal charge, 0 for an implied hydrogen
     */
    public record Site(int index, String element, int isotope, int atom, boolean implied, int charge)
    {
    }

    /**
     * @param first the index of one node
     * @param second the index of the other node
     * @param order the bond order
     * @param bond the index of the molecule bond this is, or -1 for the bond to an implied hydrogen
     */
    public record Connection(int first, int second, BondOrder order, int bond)
    {
    }

    /**
     * @param strain how far the shape strays from the ideal bond lengths, angles and contacts; lower is better
     * @param positions where each node is, in angstroms, centred on the origin
     */
    public record Conformer(double strain, List<Vec3> positions)
    {
        /**
         * @return how far the farthest node lies from the origin, in angstroms
         */
        public double radius()
        {
            return positions.stream().mapToDouble(Vec3::length).max().orElse(0);
        }
    }
}
