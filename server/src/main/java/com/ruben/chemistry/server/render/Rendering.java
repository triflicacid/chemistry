package com.ruben.chemistry.server.render;

import com.ruben.chemistry.render.Layout;

/**
 * One drawing of a molecule.
 *
 * @param svg the SVG document
 * @param layout the layout the drawing was laid out from
 */
public record Rendering(String svg, Layout layout)
{
}
