package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Hydrogens;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;
import com.ruben.chemistry.util.IdentitySet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StructureExport
{
    private final Molecule collapsed;
    private final Structure3D shape;
    private final List<String> nodeAtoms;
    private final double strain;

    private StructureExport(Molecule collapsed, Structure3D shape, List<String> nodeAtoms, double strain)
    {
        this.collapsed = collapsed;
        this.shape = shape;
        this.nodeAtoms = nodeAtoms;
        this.strain = strain;
    }

    /**
     * @param graph the graph to export
     * @param strain the strain to give the structure
     * @return the export, with the positions the graph has now standing in for the structure's conformer
     */
    static StructureExport of(Graph graph, double strain)
    {
        Molecule molecule = graph.molecule();
        Map<Atom, List<Atom>> absorbed = Hydrogens.absorbedBy(molecule);
        Set<Atom> gone = IdentitySet.of();
        absorbed.values().forEach(gone::addAll);
        List<Atom> kept = molecule.atoms().stream().filter(atom -> !gone.contains(atom)).toList();

        // the collapsed molecule lists the hydrogens it implies atom by atom, after every atom it holds
        List<String> order = new ArrayList<>();
        kept.forEach(atom -> order.add(graph.atomIds().get(atom)));
        for (Atom atom : kept)
        {
            absorbed.getOrDefault(atom, List.of()).forEach(hydrogen -> order.add(graph.atomIds().get(hydrogen)));
        }

        Molecule counted = Hydrogens.asCounts(molecule);
        Structure3D shape = Embedder.placed(counted, order.stream().map(graph.geometry()::get).toList(), strain);
        return new StructureExport(counted, shape, List.copyOf(order), strain);
    }

    /**
     * @return the graph's molecule with its hydrogens taken back into counts, which is how it is stored
     */
    Molecule collapsed()
    {
        return collapsed;
    }

    /**
     * @return the atom each site of {@link #structure} is, in site order
     */
    List<String> sites()
    {
        return nodeAtoms;
    }

    /**
     * @param geometry where each atom is now
     * @return the structure, whose positions are the ones given, not recentred
     */
    Structure3D structure(Map<String, Vec3> geometry)
    {
        List<Vec3> positions = new ArrayList<>(nodeAtoms.size());
        nodeAtoms.forEach(id -> positions.add(geometry.get(id)));

        return new Structure3D(shape.sites(), shape.connections(), List.of(new Structure3D.Conformer(strain, positions)));
    }

    /**
     * @param locked the atoms that are locked
     * @return the position in the structure of each locked atom
     */
    Set<Integer> lockedSites(Set<String> locked)
    {
        Set<Integer> sites = new LinkedHashSet<>();
        for (String id : locked)
        {
            int site = nodeAtoms.indexOf(id);
            if (site >= 0)
            {
                sites.add(site);
            }
        }
        return sites;
    }
}
