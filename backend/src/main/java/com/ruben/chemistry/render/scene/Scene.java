package com.ruben.chemistry.render.scene;

import com.ruben.chemistry.render.Colour;

import java.util.List;
import java.util.Optional;

/**
 * A drawing as shapes and text at coordinates in pixels, with the origin at the top left and y pointing down.
 *
 * @param width how wide it is
 * @param height how tall it is
 * @param background what fills it behind everything else, if anything does
 * @param attributes properties of the scene as a whole
 * @param gradients the gradients the elements may be painted with
 * @param elements what is drawn, back to front
 */
public record Scene(
    double width,
    double height,
    Optional<Colour> background,
    List<Attribute> attributes,
    List<Gradient> gradients,
    List<Element<?>> elements)
{
    /**
     * @param width how wide it is
     * @param height how tall it is
     * @param background what fills it behind everything else, or {@code null} if nothing does
     * @param attributes properties of the scene as a whole
     * @param gradients the gradients the elements may be painted with
     * @param elements what is drawn, back to front
     * @see #Scene(double, double, Optional, List, List, List)
     */
    Scene(double width, double height, Colour background, List<Attribute> attributes, List<Gradient> gradients, List<Element<?>> elements)
    {
        this(width, height, Optional.ofNullable(background), attributes, gradients, elements);
    }
}
