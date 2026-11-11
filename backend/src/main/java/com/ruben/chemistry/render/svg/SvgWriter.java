package com.ruben.chemistry.render.svg;

import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.Point;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds an SVG document.
 */
public final class SvgWriter
{
    /** How many decimal places a number is written to, which is finer than any screen. */
    private static final int PLACES = 3;

    private final double width;
    private final double height;

    /** Attributes of the {@code svg} tag beyond its size, already written out. */
    private final StringBuilder rootAttributes = new StringBuilder();

    /** What goes inside {@code defs}. */
    private final StringBuilder definitions = new StringBuilder();

    /** What follows the background. */
    private final StringBuilder body = new StringBuilder();

    private Colour background;
    private Element pending;
    private int depth;
    private int gradients;

    /**
     * @param width how wide the document is, in pixels
     * @param height how tall the document is, in pixels
     * @throws IllegalArgumentException if either is negative or not a number
     */
    public SvgWriter(double width, double height)
    {
        if (!(width >= 0) || !(height >= 0))
        {
            throw new IllegalArgumentException("A document can not have a negative size, but was " + width + " by " + height);
        }
        this.width = width;
        this.height = height;
    }

    /**
     * Sets an attribute of the document's {@code svg} tag.
     *
     * @param name the attribute's name
     * @param value its value, or {@code null} to leave it out
     * @return this writer
     */
    public SvgWriter attribute(String name, String value)
    {
        if (value != null)
        {
            rootAttributes.append(' ').append(name).append("=\"").append(escape(value)).append('"');
        }
        return this;
    }

    /**
     * Fills the whole document, behind everything else.
     *
     * @param colour the colour to fill it with
     * @return this writer
     */
    public SvgWriter background(Colour colour)
    {
        this.background = colour;
        return this;
    }

    /**
     * @param text what to call the document or the group it is written in, for a reader that cannot see it
     * @return the element, which holds the text
     */
    public Element title(String text)
    {
        return add(new Element("title", false)).content(text);
    }

    /**
     * @param text a longer account of the document or the group it is written in, for a reader that cannot see it
     * @return the element, which holds the text
     */
    public Element description(String text)
    {
        return add(new Element("desc", false)).content(text);
    }

    /**
     * @param from where the line begins
     * @param to where it ends
     * @return the line
     */
    public Element line(Point from, Point to)
    {
        return add(new Element("line", false))
            .attribute("x1", from.x()).attribute("y1", from.y())
            .attribute("x2", to.x()).attribute("y2", to.y());
    }

    /**
     * @param centre the middle of the circle
     * @param radius how far it reaches from there
     * @return the circle
     */
    public Element circle(Point centre, double radius)
    {
        return add(new Element("circle", false))
            .attribute("cx", centre.x()).attribute("cy", centre.y()).attribute("r", radius);
    }

    /**
     * @param corner the corner with the smallest coordinates, which is the top left on the page
     * @param width how wide it is
     * @param height how tall it is
     * @return the rectangle
     */
    public Element rect(Point corner, double width, double height)
    {
        return add(new Element("rect", false))
            .attribute("x", corner.x()).attribute("y", corner.y())
            .attribute("width", width).attribute("height", height);
    }

    /**
     * @param points its corners, in order
     * @return the polygon
     * @throws IllegalArgumentException if there are fewer than three
     */
    public Element polygon(List<Point> points)
    {
        if (points.size() < 3)
        {
            throw new IllegalArgumentException("A polygon needs at least three points, but had " + points.size());
        }
        StringBuilder corners = new StringBuilder();
        for (Point point : points)
        {
            if (!corners.isEmpty())
            {
                corners.append(' ');
            }
            corners.append(number(point.x())).append(',').append(number(point.y()));
        }
        return add(new Element("polygon", false)).attribute("points", corners.toString());
    }

    /**
     * Writes text.
     *
     * @param at where it sits
     * @return the text
     */
    public Element text(Point at)
    {
        return add(new Element("text", false)).attribute("x", at.x()).attribute("y", at.y());
    }

    /**
     * Opens a group, which holds everything written until it is {@linkplain #close() closed}.
     *
     * @return the group
     */
    public Element group()
    {
        return add(new Element("g", true));
    }

    /**
     * Closes the group most recently opened.
     *
     * @return this writer
     * @throws IllegalStateException if no group is open
     */
    public SvgWriter close()
    {
        flush();
        if (depth == 0)
        {
            throw new IllegalStateException("There is no open group to close");
        }
        depth--;
        body.append("  </g>\n");
        return this;
    }

    /**
     * Defines a gradient along a line, to paint with.
     *
     * @param idPrefix what the gradient's name starts with
     * @param from where the gradient begins
     * @param to where it ends
     * @param stops the colours along it, from its start to its end
     * @return a paint that refers to the gradient, for {@link Element#fill(String)} or {@link Element#stroke(String)}
     * @throws IllegalArgumentException if there are fewer than two stops
     */
    public String linearGradient(String idPrefix, Point from, Point to, List<Stop> stops)
    {
        if (stops.size() < 2)
        {
            throw new IllegalArgumentException("A gradient needs at least two stops, but had " + stops.size());
        }
        String id = idPrefix + gradients++;
        definitions.append("    <linearGradient id=\"").append(escape(id))
            .append("\" gradientUnits=\"userSpaceOnUse\" x1=\"").append(number(from.x()))
            .append("\" y1=\"").append(number(from.y()))
            .append("\" x2=\"").append(number(to.x()))
            .append("\" y2=\"").append(number(to.y())).append("\">\n");
        for (Stop stop : stops)
        {
            definitions.append("      <stop offset=\"").append(number(stop.offset()))
                .append("\" stop-color=\"").append(stop.colour().hex()).append("\"/>\n");
        }
        definitions.append("    </linearGradient>\n");
        return "url(#" + id + ")";
    }

    /**
     * Writes the document. Any group still open is closed.
     *
     * @return the document
     */
    public String render()
    {
        flush();
        StringBuilder document = new StringBuilder();
        document.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(number(width))
            .append("\" height=\"").append(number(height))
            .append("\" viewBox=\"0 0 ").append(number(width)).append(' ').append(number(height))
            .append('"').append(rootAttributes).append(">\n");
        if (!definitions.isEmpty())
        {
            document.append("  <defs>\n").append(definitions).append("  </defs>\n");
        }
        if (background != null)
        {
            document.append("  <rect x=\"0\" y=\"0\" width=\"").append(number(width))
                .append("\" height=\"").append(number(height))
                .append("\" fill=\"").append(background.hex()).append("\"/>\n");
        }
        document.append(body);
        document.append("  </g>\n".repeat(depth));
        return document.append("</svg>\n").toString();
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

    /**
     * @param element the element to write next
     * @return {@code element}, once whatever came before has been written
     */
    private Element add(Element element)
    {
        flush();
        pending = element;
        return element;
    }

    /** Writes the element waiting to be written, if there is one. */
    private void flush()
    {
        if (pending != null)
        {
            pending.writeTo(body);
            if (pending.isGroup())
            {
                depth++;
            }
            pending = null;
        }
    }

    /**
     * One colour along a gradient.
     *
     * @param offset how far along the gradient it sits, from 0 to 1
     * @param colour the colour there
     */
    public record Stop(double offset, Colour colour)
    {
        public Stop
        {
            if (!(offset >= 0) || !(offset <= 1))
            {
                throw new IllegalArgumentException("A stop has to sit between 0 and 1, but was at " + offset);
            }
            Objects.requireNonNull(colour, "colour");
        }
    }
}
