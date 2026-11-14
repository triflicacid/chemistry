package com.ruben.chemistry.server.render;

import com.ruben.chemistry.render.scene.Scene;

import java.util.List;

/**
 * A molecule drawn with the matches of a SMARTS pattern highlighted.
 *
 * @param merged whether there is one scene with every match highlighted, rather than a scene per match
 * @param total how many matches the pattern has
 * @param shown how many of them were drawn
 * @param scenes what was drawn; empty if there is no match
 */
public record Match2dRender(boolean merged, int total, int shown, List<Scene> scenes)
{
}
