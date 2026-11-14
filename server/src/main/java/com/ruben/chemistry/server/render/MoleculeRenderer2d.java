package com.ruben.chemistry.server.render;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.query.Match;
import com.ruben.chemistry.render.FragmentArrangement;
import com.ruben.chemistry.render.MoleculeLayout;
import com.ruben.chemistry.render.ProjectedLayout;
import com.ruben.chemistry.render.SceneRenderer;
import com.ruben.chemistry.render.Structure2D;
import com.ruben.chemistry.render.match.MatchAnnotator;
import com.ruben.chemistry.render.scene.Scene;
import com.ruben.chemistry.render.svg.SvgWriter;
import com.ruben.chemistry.server.match.MatchOptionsMapper;
import com.ruben.chemistry.server.match.MatchResult;
import com.ruben.chemistry.server.molecule.MoleculeException;
import com.ruben.chemistry.space.Camera;
import com.ruben.chemistry.space.Structure3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MoleculeRenderer2d
{
    /**
     * @param molecule the molecule to draw
     * @param options how to draw it
     * @return the drawing, with the snapshot it was laid out from
     * @throws MoleculeException if an option is invalid or the molecule cannot be laid out
     */
    public Render2d render2d(Molecule molecule, Render2dOptions options)
    {
        Render2dOptionsMapper.Settings settings = Render2dOptionsMapper.map(options);

        try
        {
            Structure2D layout = MoleculeLayout.layout(molecule, settings.depiction());
            Scene scene = SceneRenderer.render(layout, settings.style());
            return new Render2d(scene, SvgWriter.write(scene), layout);
        }
        catch (IllegalArgumentException | IllegalStateException unsupported)
        {
            throw new MoleculeException("CANNOT_RENDER", unsupported.getMessage());
        }
    }

    /**
     * @param molecule the molecule to draw
     * @param result the matches of a pattern in {@code molecule}, to highlight
     * @param options how to draw the molecule
     * @return the drawing, which has no scenes if the pattern does not match
     * @throws MoleculeException if an option is invalid or the molecule cannot be laid out
     */
    public Match2dRender renderMatches2d(Molecule molecule, MatchResult result, Render2dOptions options)
    {
        Render2dOptionsMapper.Settings settings = Render2dOptionsMapper.map(options);
        MatchOptionsMapper.Matching matching = result.matching();

        try
        {
            List<Match> shown = result.shown();
            if (shown.isEmpty())
            {
                return new Match2dRender(matching.merged(), 0, 0, List.of());
            }

            Structure2D layout = MoleculeLayout.layout(molecule, settings.depiction());
            List<Scene> scenes = new ArrayList<>();
            if (matching.merged())
            {
                scenes.add(SceneRenderer.render(layout, settings.style(), MatchAnnotator.annotate(shown, molecule, matching.style())));
            }
            else
            {
                for (Match each : shown)
                {
                    scenes.add(SceneRenderer.render(layout, settings.style(), MatchAnnotator.annotate(each, molecule, matching.style())));
                }
            }
            return new Match2dRender(matching.merged(), result.total(), shown.size(), scenes);
        }
        catch (IllegalArgumentException | IllegalStateException unsupported)
        {
            throw new MoleculeException("CANNOT_RENDER", unsupported.getMessage());
        }
    }

    /**
     * @param molecule the molecule to draw
     * @param structure the molecule in space
     * @param conformer which conformer of {@code structure} to draw
     * @param camera where to look from, or null to choose the clearest direction
     * @param options how to draw it
     * @param only the atoms whose disconnected parts to draw, or null to draw every part
     * @return the drawing, with the snapshot it was laid out from
     * @throws MoleculeException if an option is invalid or the molecule cannot be drawn from that conformer
     */
    public Render2d project2d(Molecule molecule, Structure3D structure, int conformer, Camera camera, Render2dOptions options, Set<Integer> only)
    {
        Render2dOptionsMapper.Settings settings = Render2dOptionsMapper.map(options);

        try
        {
            Structure2D layout;
            Scene scene;
            if (camera == null)
            {
                layout = ProjectedLayout.bestView(molecule, structure, conformer, settings.depiction(), only);
                scene = SceneRenderer.render(layout, settings.style());
            }
            else
            {
                layout = ProjectedLayout.fromCamera(molecule, structure, conformer, camera, settings.depiction(), only);
                scene = SceneRenderer.render(layout, settings.style().toBuilder().fragmentArrangement(new FragmentArrangement.AsPlaced()).build());
            }
            return new Render2d(scene, SvgWriter.write(scene), layout);
        }
        catch (IllegalArgumentException | IllegalStateException unsupported)
        {
            throw new MoleculeException("CANNOT_RENDER", unsupported.getMessage());
        }
    }
}
