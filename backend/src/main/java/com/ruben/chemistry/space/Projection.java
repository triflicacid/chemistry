package com.ruben.chemistry.space;

import com.ruben.chemistry.core.BondOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Structure3D} as a camera sees it.
 *
 * @param nodes where each node lands, indexed as the structure's sites are
 * @param edges the bonds
 * @param camera the camera that projected them
 * @param diagnostics what the geometry says about itself, or null if it was not worked out
 */
public record Projection(List<Node> nodes, List<Edge> edges, Camera camera, Diagnostics diagnostics)
{
    /**
     * @param site the node
     * @param x how far right of the picture's middle, in angstroms
     * @param y how far above the picture's middle, in angstroms
     * @param depth how far in front of the camera along its line of sight
     * @param scale how much perspective scaled it, 1 at the target's depth and for an orthographic view
     * @param visible false if it is level with or behind the camera, so it is clipped
     */
    public record Node(Structure3D.Site site, double x, double y, double depth, double scale, boolean visible)
    {
    }

    /**
     * @param first the index of one node
     * @param second the index of the other
     * @param order the bond order
     * @param bond the index of the molecule bond this is, or -1 for the bond to an implied hydrogen
     */
    public record Edge(int first, int second, BondOrder order, int bond)
    {
    }

    /**
     * @param structure the structure to project
     * @param conformer the index of the conformer to project
     * @param camera where to view it from
     * @return its layout
     * @throws IllegalArgumentException if there is no such conformer, or the camera is inside the molecule
     */
    public static Projection of(Structure3D structure, int conformer, Camera camera)
    {
        return of(structure, conformer, camera, null);
    }

    /**
     * @param structure the structure to project
     * @param conformer the index of the conformer to project
     * @param camera where to view it from
     * @param diagnostics what the conformer's geometry says, to travel with the layout, or null
     * @return its layout
     * @throws IllegalArgumentException if there is no such conformer, or the camera is inside the molecule
     */
    public static Projection of(Structure3D structure, int conformer, Camera camera, Diagnostics diagnostics)
    {
        if (conformer < 0 || conformer >= structure.conformers().size())
        {
            throw new IllegalArgumentException("There is no conformer " + conformer + "; there are " + structure.conformers().size());
        }

        List<Vec3> positions = structure.conformers().get(conformer).positions();
        List<Node> nodes = new ArrayList<>(positions.size());
        for (Structure3D.Site site : structure.sites())
        {
            Camera.Projected seen = camera.project(positions.get(site.index()));
            nodes.add(new Node(site, seen.x(), seen.y(), seen.depth(), seen.scale(), seen.visible()));
        }

        List<Edge> edges = new ArrayList<>(structure.connections().size());
        for (Structure3D.Connection connection : structure.connections())
        {
            edges.add(new Edge(connection.first(), connection.second(), connection.order(), connection.bond()));
        }
        return new Projection(List.copyOf(nodes), List.copyOf(edges), camera, diagnostics);
    }
}
