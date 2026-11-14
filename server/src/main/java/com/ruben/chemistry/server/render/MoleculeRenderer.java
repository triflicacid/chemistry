package com.ruben.chemistry.server.render;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.render.FragmentArrangement;
import com.ruben.chemistry.render.Structure2D;
import com.ruben.chemistry.smiles.SmilesParser;
import com.ruben.chemistry.render.MoleculeLayout;
import com.ruben.chemistry.render.ProjectedLayout;
import com.ruben.chemistry.render.SceneRenderer;
import com.ruben.chemistry.space.Camera;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.render.svg.SvgWriter;
import com.ruben.chemistry.render.scene.Scene;

import com.ruben.chemistry.query.Match;
import com.ruben.chemistry.query.Query;
import com.ruben.chemistry.query.SubstructureMatcher;
import com.ruben.chemistry.render.match.MatchAnnotator;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.query.MatchLabels;
import com.ruben.chemistry.render.match.MatchStyle;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MoleculeRenderer
{
    /** The longest SMILES string accepted. */
    public static final int MAX_SMILES_LENGTH = 5_000;

    /**
     * A molecule read from SMILES.
     *
     * @param molecule the molecule
     * @param source where its atoms and bonds are written in the SMILES it was read from
     */
    public record Parsed(Molecule molecule, MoleculeMetadata.Notation source)
    {
    }

    /**
     * @param smiles a molecule, written in SMILES
     * @return the molecule, with where each of its atoms and bonds is written in {@code smiles}
     * @throws RenderException if the SMILES is too long or invalid
     */
    public Parsed parse(String smiles)
    {
        if (smiles.length() > MAX_SMILES_LENGTH)
        {
            throw new RenderException("SMILES_TOO_LONG", "The SMILES string is longer than " + MAX_SMILES_LENGTH + " characters");
        }

        try
        {
            // parsed here rather than by Molecule.fromSmiles, which does not keep where anything was written
            SmilesParser parser = new SmilesParser(smiles);
            Molecule molecule = parser.parse();
            return new Parsed(molecule, new MoleculeMetadata.Notation(smiles, parser.atomSpans(), parser.bondSpans()));
        }
        catch (InvalidNotationException invalid)
        {
            throw new RenderException("INVALID_SMILES", invalid.getMessage());
        }
    }

    /**
     * @param molecule the molecule to draw
     * @param options how to draw it
     * @return the drawing, with the snapshot it was laid out from
     * @throws RenderException if an option is invalid or the molecule cannot be laid out
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
            throw new RenderException("CANNOT_RENDER", unsupported.getMessage());
        }
    }

    /**
     * @param molecule the molecule to draw
     * @param smarts the pattern whose matches to highlight
     * @param options how to draw the molecule
     * @param match how to show the matches
     * @return the drawing, which has no scenes if the pattern does not match
     * @throws RenderException if the pattern or an option is invalid, or the molecule cannot be laid out
     */
    public Match2dRender renderMatches2d(Molecule molecule, String smarts, Render2dOptions options, MatchOptions match)
    {
        Render2dOptionsMapper.Settings settings = Render2dOptionsMapper.map(options);
        Render2dOptionsMapper.Matching matching = Render2dOptionsMapper.map(match);

        Found found = find(molecule, smarts, matching);

        try
        {
            List<Match> shown = found.shown();
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
            return new Match2dRender(matching.merged(), found.total(), shown.size(), scenes);
        }
        catch (IllegalArgumentException | IllegalStateException unsupported)
        {
            throw new RenderException("CANNOT_RENDER", unsupported.getMessage());
        }
    }

    /**
     * @param molecule the molecule to search
     * @param smarts the pattern to find
     * @param match how to find and show the matches
     * @return where the pattern matches, for a client to highlight in a drawing of its own
     * @throws RenderException if the pattern or an option is invalid
     */
    public Match3dData findMatches3d(Molecule molecule, String smarts, MatchOptions match)
    {
        Render2dOptionsMapper.Matching matching = Render2dOptionsMapper.map(match);
        Found found = find(molecule, smarts, matching);
        MatchStyle style = matching.style();

        Map<Atom, Integer> atomIndices = new IdentityHashMap<>();
        for (int i = 0; i < molecule.atoms().size(); i++)
        {
            atomIndices.put(molecule.atoms().get(i), i);
        }
        Map<Bond, Integer> bondIndices = new IdentityHashMap<>();
        for (int i = 0; i < molecule.bonds().size(); i++)
        {
            bondIndices.put(molecule.bonds().get(i), i);
        }

        List<Match3dData.Highlight> highlights = new ArrayList<>();
        for (int i = 0; i < found.shown().size(); i++)
        {
            Match each = found.shown().get(i);
            Set<Atom> atoms = each.atoms();
            List<Integer> bonds = new ArrayList<>();
            for (Bond bond : molecule.bonds())
            {
                if (atoms.contains(bond.first()) && atoms.contains(bond.second()))
                {
                    bonds.add(bondIndices.get(bond));
                }
            }

            List<Match3dData.Label> atomLabels = new ArrayList<>();
            List<Match3dData.Label> bondLabels = new ArrayList<>();
            if (style.labels())
            {
                MatchLabels labels = MatchLabels.of(each, molecule);
                labels.atoms().forEach((atom, text) -> atomLabels.add(new Match3dData.Label(atomIndices.get(atom), text)));
                labels.bonds().forEach((bond, text) -> bondLabels.add(new Match3dData.Label(bondIndices.get(bond), text)));
            }
            highlights.add(new Match3dData.Highlight(
                    style.colourOfMatch(i).hex(),
                    atoms.stream().map(atomIndices::get).sorted().toList(),
                    bonds,
                    atomLabels,
                    bondLabels));
        }
        return new Match3dData(matching.merged(), found.total(), highlights.size(), style.dimRest(), style.recolour(), style.labels(), highlights);
    }

    /**
     * @param total how many matches the pattern has
     * @param shown the matches to show, the first of them
     */
    private record Found(int total, List<Match> shown)
    {
    }

    private static Found find(Molecule molecule, String smarts, Render2dOptionsMapper.Matching matching)
    {
        Query query;
        try
        {
            query = Query.fromSmarts(smarts);
        }
        catch (InvalidNotationException invalid)
        {
            throw new RenderException("INVALID_SMARTS", invalid.getMessage());
        }

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);
        List<Match> found = matching.allMatches() ? matcher.findMatches(query) : matcher.findDistinctMatches(query);
        return new Found(found.size(), found.subList(0, Math.min(found.size(), matching.maxMatches())));
    }

    /**
     * @param molecule the molecule to draw
     * @param structure the molecule in space
     * @param conformer which conformer of {@code structure} to draw
     * @param camera where to look from, or null to choose the clearest direction
     * @param options how to draw it
     * @param only the atoms whose disconnected parts to draw, or null to draw every part
     * @return the drawing, with the snapshot it was laid out from
     * @throws RenderException if an option is invalid or the molecule cannot be drawn from that conformer
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
            throw new RenderException("CANNOT_RENDER", unsupported.getMessage());
        }
    }
}
