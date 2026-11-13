package com.ruben.chemistry.render.scene;

import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Something in a {@link Scene}: a shape, some text, or a group of other elements.
 * <p>
 * Coordinates are in pixels, with the origin at the top left and y pointing down. An element can be styled until the
 * {@link SceneBuilder} that made it builds the scene.
 *
 * @param <T> the kind of element, so that styling an element hands back that same kind
 */
public abstract sealed class Element<T extends Element<T>>
    permits Element.Line, Element.Circle, Element.Rect, Element.Polygon, Element.Text, Element.Group, Element.Title, Element.Description
{
    private final List<Attribute> attributes = new ArrayList<>();
    private final SceneBuilder.Lock lock;

    Element(SceneBuilder.Lock lock)
    {
        this.lock = lock;
    }

    /**
     * @return the properties the element has been given beyond its geometry, in the order they were given
     */
    public List<Attribute> attributes()
    {
        return List.copyOf(attributes);
    }

    /**
     * @param name the attribute's name
     * @param value its value, or {@code null} to leave the attribute out
     * @return this element
     * @throws IllegalStateException if the scene has been built
     */
    public T attribute(String name, String value)
    {
        if (value != null)
        {
            add(Attribute.of(name, value));
        }
        return self();
    }

    /**
     * @param name the attribute's name
     * @param value its value, a measurement in pixels
     * @return this element
     * @throws IllegalStateException if the scene has been built
     */
    public T attribute(String name, double value)
    {
        add(Attribute.of(name, value));
        return self();
    }

    /**
     * @param name the class to give the element
     * @return this element
     */
    public T cssClass(String name)
    {
        return attribute("class", name);
    }

    /**
     * @param colour what to fill the element with
     * @return this element
     */
    public T fill(Colour colour)
    {
        return attribute("fill", colour.hex());
    }

    /**
     * @param paint what to fill the element with as SVG writes it, such as {@code none}, or a gradient's reference
     * @return this element
     */
    public T fill(String paint)
    {
        return attribute("fill", paint);
    }

    /**
     * @param colour what to draw the element's outline with
     * @return this element
     */
    public T stroke(Colour colour)
    {
        return attribute("stroke", colour.hex());
    }

    /**
     * @param paint what to draw the element's outline with; see {@link #fill(String)}
     * @return this element
     */
    public T stroke(String paint)
    {
        return attribute("stroke", paint);
    }

    /**
     * @param width how thick the outline is, in pixels
     * @return this element
     */
    public T strokeWidth(double width)
    {
        return attribute("stroke-width", width);
    }

    /**
     * @param lengths how long the dashes and the gaps between them are, in turn, in pixels
     * @return this element
     */
    public T dash(double... lengths)
    {
        add(Attribute.of("stroke-dasharray", lengths));
        return self();
    }

    /**
     * @return this element, with the ends of its lines rounded
     */
    public T roundCaps()
    {
        return attribute("stroke-linecap", "round");
    }

    /**
     * @return this element, with the corners of its outline rounded
     */
    public T roundJoins()
    {
        return attribute("stroke-linejoin", "round");
    }

    /**
     * @param opacity how opaque it is, from 0 for invisible to 1 for solid
     * @return this element
     */
    public T opacity(double opacity)
    {
        return attribute("opacity", opacity);
    }

    @SuppressWarnings("unchecked")
    final T self()
    {
        return (T) this;
    }

    final void requireOpen()
    {
        if (lock.isBuilt())
        {
            throw new IllegalStateException("The scene has been built, so its elements can not change");
        }
    }

    private void add(Attribute attribute)
    {
        requireOpen();
        attributes.add(attribute);
    }

    /**
     * A straight line.
     */
    public static final class Line extends Element<Line>
    {
        private final Point from;
        private final Point to;

        Line(SceneBuilder.Lock lock, Point from, Point to)
        {
            super(lock);
            this.from = Objects.requireNonNull(from, "from");
            this.to = Objects.requireNonNull(to, "to");
        }

        /** @return where the line begins */
        public Point from()
        {
            return from;
        }

        /** @return where the line ends */
        public Point to()
        {
            return to;
        }
    }

    /**
     * A circle.
     */
    public static final class Circle extends Element<Circle>
    {
        private final Point centre;
        private final double radius;

        Circle(SceneBuilder.Lock lock, Point centre, double radius)
        {
            super(lock);
            this.centre = Objects.requireNonNull(centre, "centre");
            this.radius = radius;
        }

        /** @return the middle of the circle */
        public Point centre()
        {
            return centre;
        }

        /** @return how far the circle reaches from its middle */
        public double radius()
        {
            return radius;
        }
    }

    /**
     * An upright rectangle.
     */
    public static final class Rect extends Element<Rect>
    {
        private final Point corner;
        private final double width;
        private final double height;

        Rect(SceneBuilder.Lock lock, Point corner, double width, double height)
        {
            super(lock);
            this.corner = Objects.requireNonNull(corner, "corner");
            this.width = width;
            this.height = height;
        }

        /** @return the corner with the smallest coordinates, which is the top left on the page */
        public Point corner()
        {
            return corner;
        }

        /** @return how wide it is */
        public double width()
        {
            return width;
        }

        /** @return how tall it is */
        public double height()
        {
            return height;
        }
    }

    /**
     * A closed shape with straight sides.
     */
    public static final class Polygon extends Element<Polygon>
    {
        private final List<Point> points;

        Polygon(SceneBuilder.Lock lock, List<Point> points)
        {
            super(lock);
            if (points.size() < 3)
            {
                throw new IllegalArgumentException("A polygon needs at least three points, but had " + points.size());
            }
            this.points = List.copyOf(points);
        }

        /** @return its corners, in order */
        public List<Point> points()
        {
            return points;
        }
    }

    /**
     * Some text, which is either plain or a series of runs each with a look of its own.
     */
    public static final class Text extends Element<Text>
    {
        private final Point at;
        private final List<Run> runs = new ArrayList<>();

        Text(SceneBuilder.Lock lock, Point at)
        {
            super(lock);
            this.at = Objects.requireNonNull(at, "at");
        }

        /** @return where the text sits, which {@code text-anchor} and {@code dominant-baseline} say how to align to */
        public Point at()
        {
            return at;
        }

        /** @return what is written, in order */
        public List<Run> runs()
        {
            return List.copyOf(runs);
        }

        /**
         * Adds plain text.
         *
         * @param text the text
         * @return this text
         * @throws IllegalStateException if the scene has been built
         */
        public Text content(String text)
        {
            requireOpen();
            runs.add(new Run(text, List.of(), false));
            return this;
        }

        /**
         * Adds a run of text with a look of its own.
         *
         * @param text the run
         * @param attributes the run's own properties
         * @return this text
         * @throws IllegalStateException if the scene has been built
         */
        public Text span(String text, Attribute... attributes)
        {
            requireOpen();
            runs.add(new Run(text, List.of(attributes), true));
            return this;
        }

        /**
         * A piece of a {@link Text}.
         *
         * @param text what is written
         * @param attributes the run's own properties, which a plain run has none of
         * @param span whether the run has a look of its own, rather than being plain text
         */
        public record Run(String text, List<Attribute> attributes, boolean span)
        {
            public Run
            {
                Objects.requireNonNull(text, "text");
                attributes = List.copyOf(attributes);
            }
        }
    }

    /**
     * Holds the elements written until it is closed, so that they can be styled and given data together.
     */
    public static final class Group extends Element<Group>
    {
        private final List<Element<?>> children = new ArrayList<>();

        Group(SceneBuilder.Lock lock)
        {
            super(lock);
        }

        /** @return what the group holds, in order */
        public List<Element<?>> children()
        {
            return List.copyOf(children);
        }

        void add(Element<?> child)
        {
            children.add(child);
        }
    }

    /**
     * What to call the scene or the group it is written in, for a reader that cannot see it.
     */
    public static final class Title extends Element<Title>
    {
        private final String text;

        Title(SceneBuilder.Lock lock, String text)
        {
            super(lock);
            this.text = Objects.requireNonNull(text, "text");
        }

        /** @return the title */
        public String text()
        {
            return text;
        }
    }

    /**
     * A longer account of the scene or the group it is written in, for a reader that cannot see it.
     */
    public static final class Description extends Element<Description>
    {
        private final String text;

        Description(SceneBuilder.Lock lock, String text)
        {
            super(lock);
            this.text = Objects.requireNonNull(text, "text");
        }

        /** @return the description */
        public String text()
        {
            return text;
        }
    }
}
