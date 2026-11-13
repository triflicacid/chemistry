package com.ruben.chemistry.server.render;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.render.ElementColours;
import com.ruben.chemistry.space.Diagnostics;
import com.ruben.chemistry.space.ElementData;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link Structure3D} into plain values to write as JSON.
 */
public final class Structure3DJson
{
    private Structure3DJson()
    {
    }

    /**
     * @param molecule the molecule the structure was embedded from
     * @param structure the structure
     * @return json
     */
    public static Map<String, Object> of(Molecule molecule, Structure3D structure)
    {
        List<Object> sites = new ArrayList<>();
        for (Structure3D.Site site : structure.sites())
        {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("index", site.index());
            json.put("element", site.element());
            json.put("isotope", site.isotope());
            json.put("atom", site.atom());
            json.put("implied", site.implied());
            json.put("charge", site.charge());
            json.put("colour", ElementColours.of(site.element()).hex());
            json.put("radius", ElementData.vanDerWaalsRadius(ElementData.numberOf(site.element())));
            sites.add(json);
        }

        List<Object> connections = new ArrayList<>();
        for (Structure3D.Connection connection : structure.connections())
        {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("first", connection.first());
            json.put("second", connection.second());
            json.put("order", connection.order().name());
            json.put("bond", connection.bond());
            connections.add(json);
        }

        List<Object> conformers = new ArrayList<>();
        for (int i = 0; i < structure.conformers().size(); i++)
        {
            Structure3D.Conformer conformer = structure.conformers().get(i);
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("strain", conformer.strain());
            json.put("positions", conformer.positions().stream().map(Structure3DJson::point).toList());
            json.put("diagnostics", Diagnostics.of(molecule, structure, i));
            conformers.add(json);
        }

        Map<String, Object> tolerances = new LinkedHashMap<>();
        tolerances.put("length", Diagnostics.LENGTH_TOLERANCE);
        tolerances.put("angle", Diagnostics.ANGLE_TOLERANCE);
        tolerances.put("torsion", Diagnostics.TORSION_TOLERANCE);
        tolerances.put("clash", Diagnostics.CLASH_TOLERANCE);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("sites", sites);
        json.put("connections", connections);
        json.put("tolerances", tolerances);
        json.put("conformers", conformers);
        return json;
    }

    private static List<Double> point(Vec3 position)
    {
        return List.of(position.x(), position.y(), position.z());
    }
}
