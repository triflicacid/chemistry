package com.ruben.chemistry.server.render;

import com.ruben.chemistry.render.Layout;
import com.ruben.chemistry.render.MoleculeSnapshot;
import com.ruben.chemistry.render.scene.Scene;

/**
 * One drawing of a molecule. The layout and its snapshot are the ones the SVG's indices refer to, so keep them together:
 * a later render makes a new layout and snapshot.
 *
 * @param scene what was drawn
 * @param svg the scene written as an SVG document
 * @param layout the layout the scene was drawn from
 */
public record Rendering(Scene scene, String svg, Layout layout)
{
    /**
     * @return the snapshot the layout's indices refer to
     */
    public MoleculeSnapshot snapshot()
    {
        return layout.snapshot();
    }
}
