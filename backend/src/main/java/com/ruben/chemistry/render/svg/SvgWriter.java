package com.ruben.chemistry.render.svg;

import com.ruben.chemistry.render.Point;
import com.ruben.chemistry.render.scene.Attribute;
import com.ruben.chemistry.render.scene.Element;
import com.ruben.chemistry.render.scene.Gradient;
import com.ruben.chemistry.render.scene.Scene;

import java.util.List;
import java.util.Locale;

/**
 * Writes a {@link Scene} as an SVG document.
 */
public final class SvgWriter
{
    /** How many decimal places a number is written to, which is finer than any screen. */
    private static final int PLACES = 3;

    private final StringBuilder out = new StringBuilder();

    private SvgWriter()
    {
    }

    /**
     * @param scene the scene to write
     * @return a standalone SVG document
     */
    public static String write(Scene scene)
    {
        return new SvgWriter().document(scene);
    }

    /**
     * @param value a measurement in pixels
     * @return it written out as the writer writes every number
     */
    public static String number(double value)
    {
        double rounded = Math.round(value * Math.pow(10, PLACES)) / Math.pow(10, PLACES);
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < Long.MAX_VALUE)
        {
            return Long.toString((long) rounded);
        }
        return stripZeroes(String.format(Locale.ROOT, "%." + PLACES + "f", rounded));
    }

    private String document(Scene scene)
    {
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(number(scene.width()))
            .append("\" height=\"").append(number(scene.height()))
            .append("\" viewBox=\"0 0 ").append(number(scene.width())).append(' ').append(number(scene.height()))
            .append('"');
        attributes(scene.attributes());
        out.append(">\n");

        if (!scene.gradients().isEmpty())
        {
            out.append("  <defs>\n");
            scene.gradients().forEach(this::gradient);
            out.append("  </defs>\n");
        }
        scene.background().ifPresent(colour -> out
            .append("  <rect x=\"0\" y=\"0\" width=\"").append(number(scene.width()))
            .append("\" height=\"").append(number(scene.height()))
            .append("\" fill=\"").append(colour.hex()).append("\"/>\n"));

        scene.elements().forEach(this::element);
        return out.append("</svg>\n").toString();
    }

    private void gradient(Gradient gradient)
    {
        out.append("    <linearGradient id=\"").append(escape(gradient.id()))
            .append("\" gradientUnits=\"userSpaceOnUse\" x1=\"").append(number(gradient.from().x()))
            .append("\" y1=\"").append(number(gradient.from().y()))
            .append("\" x2=\"").append(number(gradient.to().x()))
            .append("\" y2=\"").append(number(gradient.to().y())).append("\">\n");
        for (Gradient.Stop stop : gradient.stops())
        {
            out.append("      <stop offset=\"").append(number(stop.offset()))
                .append("\" stop-color=\"").append(stop.colour().hex()).append("\"/>\n");
        }
        out.append("    </linearGradient>\n");
    }

    private void element(Element<?> element)
    {
        switch (element)
        {
            case Element.Line line -> leaf("line", line,
                "x1", line.from().x(), "y1", line.from().y(), "x2", line.to().x(), "y2", line.to().y());
            case Element.Circle circle -> leaf("circle", circle,
                "cx", circle.centre().x(), "cy", circle.centre().y(), "r", circle.radius());
            case Element.Rect rect -> leaf("rect", rect,
                "x", rect.corner().x(), "y", rect.corner().y(), "width", rect.width(), "height", rect.height());
            case Element.Polygon polygon -> polygon(polygon);
            case Element.Text text -> text(text);
            case Element.Group group -> group(group);
            case Element.Title title -> textual("title", title, title.text());
            case Element.Description description -> textual("desc", description, description.text());
        }
    }

    /** Writes an element with nothing inside it, whose geometry is given as names and numbers in turn. */
    private void leaf(String tag, Element<?> element, Object... geometry)
    {
        out.append("  <").append(tag);
        for (int at = 0; at < geometry.length; at += 2)
        {
            out.append(' ').append(geometry[at]).append("=\"").append(number((Double) geometry[at + 1])).append('"');
        }
        attributes(element.attributes());
        out.append("/>\n");
    }

    private void polygon(Element.Polygon polygon)
    {
        StringBuilder corners = new StringBuilder();
        for (Point point : polygon.points())
        {
            if (!corners.isEmpty())
            {
                corners.append(' ');
            }
            corners.append(number(point.x())).append(',').append(number(point.y()));
        }
        out.append("  <polygon points=\"").append(corners).append('"');
        attributes(polygon.attributes());
        out.append("/>\n");
    }

    private void text(Element.Text text)
    {
        out.append("  <text x=\"").append(number(text.at().x())).append("\" y=\"").append(number(text.at().y())).append('"');
        attributes(text.attributes());
        if (text.runs().isEmpty())
        {
            out.append("/>\n");
            return;
        }
        out.append('>');
        for (Element.Text.Run run : text.runs())
        {
            if (run.span())
            {
                out.append("<tspan");
                attributes(run.attributes());
                out.append('>').append(escape(run.text())).append("</tspan>");
            }
            else
            {
                out.append(escape(run.text()));
            }
        }
        out.append("</text>\n");
    }

    private void group(Element.Group group)
    {
        out.append("  <g");
        attributes(group.attributes());
        out.append(">\n");
        group.children().forEach(this::element);
        out.append("  </g>\n");
    }

    private void textual(String tag, Element<?> element, String text)
    {
        out.append("  <").append(tag);
        attributes(element.attributes());
        if (text.isEmpty())
        {
            out.append("/>\n");
        }
        else
        {
            out.append('>').append(escape(text)).append("</").append(tag).append(">\n");
        }
    }

    private void attributes(List<Attribute> attributes)
    {
        for (Attribute attribute : attributes)
        {
            out.append(' ').append(attribute.name()).append("=\"").append(valueOf(attribute.value())).append('"');
        }
    }

    private static String valueOf(Object value)
    {
        if (value instanceof Double number)
        {
            return number(number);
        }
        if (value instanceof List<?> numbers)
        {
            StringBuilder list = new StringBuilder();
            for (Object item : numbers)
            {
                if (!list.isEmpty())
                {
                    list.append(' ');
                }
                list.append(number((Double) item));
            }
            return list.toString();
        }
        return escape((String) value);
    }

    /**
     * @param text some text to put into the document
     * @return it with every character XML gives a meaning to replaced by its entity
     */
    static String escape(String text)
    {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int at = 0; at < text.length(); at++)
        {
            char character = text.charAt(at);
            switch (character)
            {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    /**
     * @param text a decimal number
     * @return it without any trailing zeroes after its point
     */
    private static String stripZeroes(String text)
    {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0')
        {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '.')
        {
            end--;
        }
        return text.substring(0, end);
    }
}
