package com.ruben.chemistry.render.scene;

import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.Point;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Builds a {@link Scene}.
 */
public final class SceneBuilder
{
    private final double width;
    private final double height;
    private final List<Attribute> attributes = new ArrayList<>();
    private final List<Gradient> gradients = new ArrayList<>();
    private final List<Element<?>> elements = new ArrayList<>();
    private final Deque<Element.Group> open = new ArrayDeque<>();
    private final Lock lock = new Lock();

    private Colour background;
    private int gradientCount;

    /**
     * @param width how wide the scene is, in pixels
     * @param height how tall the scene is, in pixels
     */
    public SceneBuilder(double width, double height)
    {
        this.width = width;
        this.height = height;
    }

    /**
     * Sets a property of the scene as a whole.
     *
     * @param name the attribute's name
     * @param value its value, or {@code null} to leave it out
     * @return this builder
     */
    public SceneBuilder attribute(String name, String value)
    {
        if (value != null)
        {
            attributes.add(Attribute.of(name, value));
        }
        return this;
    }

    /**
     * Fills the whole scene, behind everything else.
     *
     * @param colour the colour to fill it with
     * @return this builder
     */
    public SceneBuilder background(Colour colour)
    {
        this.background = colour;
        return this;
    }

    /**
     * @param text what to call the scene or the group it is added to, for a reader that cannot see it
     * @return the element
     */
    public Element.Title title(String text)
    {
        return add(new Element.Title(lock, text));
    }

    /**
     * @param text a longer account of the scene or the group it is added to, for a reader that cannot see it
     * @return the element
     */
    public Element.Description description(String text)
    {
        return add(new Element.Description(lock, text));
    }

    /**
     * @param from where the line begins
     * @param to where it ends
     * @return the line
     */
    public Element.Line line(Point from, Point to)
    {
        return add(new Element.Line(lock, from, to));
    }

    /**
     * @param centre the middle of the circle
     * @param radius how far it reaches from there
     * @return the circle
     */
    public Element.Circle circle(Point centre, double radius)
    {
        return add(new Element.Circle(lock, centre, radius));
    }

    /**
     * @param corner the corner with the smallest coordinates, which is the top left on the page
     * @param width how wide it is
     * @param height how tall it is
     * @return the rectangle
     */
    public Element.Rect rect(Point corner, double width, double height)
    {
        return add(new Element.Rect(lock, corner, width, height));
    }

    /**
     * @param points its corners, in order
     * @return the polygon
     * @throws IllegalArgumentException if there are fewer than three
     */
    public Element.Polygon polygon(List<Point> points)
    {
        return add(new Element.Polygon(lock, points));
    }

    /**
     * @param at where the text sits
     * @return the text, which has nothing written in it yet
     */
    public Element.Text text(Point at)
    {
        return add(new Element.Text(lock, at));
    }

    /**
     * Opens a group, which holds everything added until it is {@linkplain #close() closed}.
     *
     * @return the group
     */
    public Element.Group group()
    {
        Element.Group group = add(new Element.Group(lock));
        open.push(group);
        return group;
    }

    /**
     * Closes the group most recently opened.
     *
     * @return this builder
     * @throws IllegalStateException if no group is open
     */
    public SceneBuilder close()
    {
        if (open.isEmpty())
        {
            throw new IllegalStateException("There is no open group to close");
        }
        open.pop();
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
    public String linearGradient(String idPrefix, Point from, Point to, List<Gradient.Stop> stops)
    {
        String id = idPrefix + gradientCount;
        gradients.add(new Gradient(id, from, to, stops));
        gradientCount++;
        return "url(#" + id + ")";
    }

    /**
     * Finishes the scene. Any group still open is closed, and the elements can no longer be changed.
     *
     * @return the scene
     */
    public Scene build()
    {
        lock.build();
        return new Scene(width, height, background, attributes, gradients, elements);
    }

    private <E extends Element<?>> E add(E element)
    {
        lock.requireOpen();
        if (open.isEmpty())
        {
            elements.add(element);
        }
        else
        {
            open.peek().add(element);
        }
        return element;
    }

    /** Tells every element of a scene whether it has been built, after which it can not change. */
    static final class Lock
    {
        private boolean built;

        boolean isBuilt()
        {
            return built;
        }

        void build()
        {
            built = true;
        }

        void requireOpen()
        {
            if (built)
            {
                throw new IllegalStateException("The scene has been built, so nothing can be added");
            }
        }
    }
}
