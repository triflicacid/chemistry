package com.ruben.chemistry.space;

import com.ruben.chemistry.core.BondOrder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

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
     * @param atoms indices of molecule atoms
     * @return this, keeping only the connected parts that hold any of the atoms; nodes are renumbered in order
     * @throws IllegalArgumentException if none of the atoms is in the structure
     */
    public Structure3D restrictedTo(Set<Integer> atoms)
    {
        int count = sites.size();
        List<List<Integer>> adjacent = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            adjacent.add(new ArrayList<>());
        }
        for (Connection connection : connections)
        {
            adjacent.get(connection.first()).add(connection.second());
            adjacent.get(connection.second()).add(connection.first());
        }
        boolean[] kept = new boolean[count];
        Deque<Integer> pending = new ArrayDeque<>();
        for (Site site : sites)
        {
            if (!site.implied() && atoms.contains(site.atom()) && !kept[site.index()])
            {
                kept[site.index()] = true;
                pending.add(site.index());
            }
        }
        if (pending.isEmpty())
        {
            throw new IllegalArgumentException("None of the atoms is in the structure");
        }
        while (!pending.isEmpty())
        {
            for (int next : adjacent.get(pending.poll()))
            {
                if (!kept[next])
                {
                    kept[next] = true;
                    pending.add(next);
                }
            }
        }

        int[] renumbered = new int[count];
        List<Site> keptSites = new ArrayList<>();
        for (Site site : sites)
        {
            if (kept[site.index()])
            {
                renumbered[site.index()] = keptSites.size();
                keptSites.add(new Site(keptSites.size(), site.element(), site.isotope(), site.atom(), site.implied(), site.charge()));
            }
        }
        List<Connection> keptConnections = connections.stream()
                .filter(connection -> kept[connection.first()] && kept[connection.second()])
                .map(connection -> new Connection(renumbered[connection.first()], renumbered[connection.second()], connection.order(), connection.bond()))
                .toList();
        List<Conformer> keptConformers = new ArrayList<>();
        for (Conformer conformer : conformers)
        {
            List<Vec3> positions = new ArrayList<>();
            for (int i = 0; i < count; i++)
            {
                if (kept[i])
                {
                    positions.add(conformer.positions().get(i));
                }
            }
            keptConformers.add(new Conformer(conformer.strain(), positions));
        }
        return new Structure3D(keptSites, keptConnections, keptConformers);
    }

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
