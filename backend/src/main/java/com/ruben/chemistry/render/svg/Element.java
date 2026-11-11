package com.ruben.chemistry.render.svg;

import com.ruben.chemistry.render.Colour;

import java.util.Objects;

/**
 * One element of an SVG document.
 */
public final class Element
{
    private final String tag;
    private final boolean group;
    private final StringBuilder attributes = new StringBuilder();
    private final StringBuilder content = new StringBuilder();
    private boolean written;

    Element(String tag, boolean group)
    {
        this.tag = tag;
        this.group = group;
    }

    /**
     * @param name the attribute's name
     * @param value its value, which is escaped, or {@code null} to leave the attribute out
     * @return this element
     * @throws IllegalStateException if the element has been written
     */
    public Element attribute(String name, String value)
    {
        requireOpen();
        if (value != null)
        {
            attributes.append(' ').append(name).append("=\"").append(SvgWriter.escape(value)).append('"');
        }
        return this;
    }

    /**
     * @param name the attribute's name
     * @param value its value, a measurement in pixels
     * @return this element
     * @throws IllegalStateException if the element has been written
     */
    public Element attribute(String name, double value)
    {
        return attribute(name, SvgWriter.number(value));
    }

    /**
     * @param name the class to give the element
     * @return this element
     */
    public Element cssClass(String name)
    {
        return attribute("class", name);
    }

    /**
     * @param colour what to fill the element with
     * @return this element
     */
    public Element fill(Colour colour)
    {
        return attribute("fill", colour.hex());
    }

    /**
     * @param paint what to fill the element with as an SVG string
     * @return this element
     */
    public Element fill(String paint)
    {
        return attribute("fill", paint);
    }

    /**
     * @param colour what to draw the element's outline with
     * @return this element
     */
    public Element stroke(Colour colour)
    {
        return attribute("stroke", colour.hex());
    }

    /**
     * @param paint what to draw the element's outline with, as SVG writes it; see {@link #fill(String)}
     * @return this element
     */
    public Element stroke(String paint)
    {
        return attribute("stroke", paint);
    }

    /**
     * @param width how thick the outline is, in pixels
     * @return this element
     */
    public Element strokeWidth(double width)
    {
        return attribute("stroke-width", width);
    }

    /**
     * @param lengths how long the dashes and the gaps between them are, in turn, in pixels
     * @return this element
     */
    public Element dash(double... lengths)
    {
        StringBuilder pattern = new StringBuilder();
        for (double length : lengths)
        {
            if (!pattern.isEmpty())
            {
                pattern.append(' ');
            }
            pattern.append(SvgWriter.number(length));
        }
        return attribute("stroke-dasharray", pattern.toString());
    }

    /**
     * @return this element, with the ends of its lines rounded
     */
    public Element roundCaps()
    {
        return attribute("stroke-linecap", "round");
    }

    /**
     * @return this element, with the corners of its outline rounded
     */
    public Element roundJoins()
    {
        return attribute("stroke-linejoin", "round");
    }

    /**
     * @param opacity how opaque it is, from 0 for invisible to 1 for solid
     * @return this element
     */
    public Element opacity(double opacity)
    {
        return attribute("opacity", opacity);
    }

    /**
     * Adds text to the element.
     *
     * @param text the text, which is escaped
     * @return this element
     * @throws IllegalStateException if the element has been written, or is a group
     */
    public Element content(String text)
    {
        requireOpen();
        requireNotGroup();
        content.append(SvgWriter.escape(text));
        return this;
    }

    /**
     * Adds a run of text with a look of its own to the element, which should be text.
     *
     * @param text the run, which is escaped
     * @param attributes the run's attributes as names and values in turn; a {@code null} value leaves one out
     * @return this element
     * @throws IllegalArgumentException if {@code attributes} does not pair names with values
     * @throws IllegalStateException if the element has been written, or is a group
     */
    public Element span(String text, String... attributes)
    {
        requireOpen();
        requireNotGroup();
        if (attributes.length % 2 != 0)
        {
            throw new IllegalArgumentException("Attributes come as names and values, but there were " + attributes.length);
        }
        content.append("<tspan");
        for (int at = 0; at < attributes.length; at += 2)
        {
            if (attributes[at + 1] != null)
            {
                content.append(' ').append(attributes[at]).append("=\"").append(SvgWriter.escape(attributes[at + 1])).append('"');
            }
        }
        content.append('>').append(SvgWriter.escape(text)).append("</tspan>");
        return this;
    }

    boolean isGroup()
    {
        return group;
    }

    void writeTo(StringBuilder out)
    {
        written = true;
        out.append("  <").append(tag).append(attributes);
        if (group)
        {
            out.append(">\n");
        }
        else if (content.isEmpty())
        {
            out.append("/>\n");
        }
        else
        {
            out.append('>').append(content).append("</").append(tag).append(">\n");
        }
    }

    private void requireOpen()
    {
        if (written)
        {
            throw new IllegalStateException("The <" + tag + "> has been written, so it can not change");
        }
    }

    private void requireNotGroup()
    {
        if (group)
        {
            throw new IllegalStateException("A group holds elements, not text");
        }
    }
}
