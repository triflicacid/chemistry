package com.ruben.chemistry.server.render;

import com.ruben.chemistry.render.Point;
import com.ruben.chemistry.render.scene.Attribute;
import com.ruben.chemistry.render.scene.Element;
import com.ruben.chemistry.render.scene.Gradient;
import com.ruben.chemistry.render.scene.Scene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link Scene} into plain values to write as JSON.
 */
public final class SceneJson
{
    private SceneJson()
    {
    }

    /**
     * @param scene the scene
     * @return it as maps and lists
     */
    public static Map<String, Object> of(Scene scene)
    {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("width", scene.width());
        json.put("height", scene.height());
        json.put("background", scene.background().map(colour -> colour.hex()).orElse(null));
        json.put("attributes", attributes(scene.attributes()));
        json.put("gradients", scene.gradients().stream().map(SceneJson::gradient).toList());
        json.put("elements", scene.elements().stream().map(SceneJson::element).toList());
        return json;
    }

    private static Map<String, Object> gradient(Gradient gradient)
    {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", gradient.id());
        json.put("from", point(gradient.from()));
        json.put("to", point(gradient.to()));
        json.put("stops", gradient.stops().stream().map(stop ->
        {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("offset", stop.offset());
            entry.put("colour", stop.colour().hex());
            return entry;
        }).toList());
        return json;
    }

    private static Map<String, Object> element(Element<?> element)
    {
        Map<String, Object> json = new LinkedHashMap<>();
        switch (element)
        {
            case Element.Line line ->
            {
                json.put("type", "line");
                json.put("from", point(line.from()));
                json.put("to", point(line.to()));
            }
            case Element.Circle circle ->
            {
                json.put("type", "circle");
                json.put("centre", point(circle.centre()));
                json.put("radius", circle.radius());
            }
            case Element.Rect rect ->
            {
                json.put("type", "rect");
                json.put("corner", point(rect.corner()));
                json.put("width", rect.width());
                json.put("height", rect.height());
            }
            case Element.Polygon polygon ->
            {
                json.put("type", "polygon");
                json.put("points", polygon.points().stream().map(SceneJson::point).toList());
            }
            case Element.Text text ->
            {
                json.put("type", "text");
                json.put("at", point(text.at()));
                json.put("runs", text.runs().stream().map(run ->
                {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("text", run.text());
                    entry.put("attributes", run.span() ? attributes(run.attributes()) : null);
                    return entry;
                }).toList());
            }
            case Element.Group group ->
            {
                json.put("type", "group");
                json.put("children", group.children().stream().map(SceneJson::element).toList());
            }
            case Element.Title title ->
            {
                json.put("type", "title");
                json.put("text", title.text());
            }
            case Element.Description description ->
            {
                json.put("type", "description");
                json.put("text", description.text());
            }
        }
        json.put("attributes", attributes(element.attributes()));
        return json;
    }

    private static Map<String, Object> point(Point point)
    {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("x", point.x());
        json.put("y", point.y());
        return json;
    }

    private static Map<String, Object> attributes(List<Attribute> attributes)
    {
        Map<String, Object> json = new LinkedHashMap<>();
        for (Attribute attribute : attributes)
        {
            json.put(attribute.name(), attribute.value() instanceof List<?> list ? new ArrayList<>(list) : attribute.value());
        }
        return json;
    }
}
