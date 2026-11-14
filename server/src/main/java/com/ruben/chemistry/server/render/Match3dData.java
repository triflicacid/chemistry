package com.ruben.chemistry.server.render;

import java.util.List;

/**
 * Where a SMARTS pattern matches a molecule, for a client to highlight in a drawing of its own. Atoms and bonds are
 * numbered as in the molecule: the {@code data-atom} and {@code data-bond} of a drawing.
 *
 * @param merged whether every match is highlighted at once, rather than one at a time
 * @param total how many matches the pattern has
 * @param shown how many of them are in {@code matches}
 * @param dim how much everything outside a match is faded, from 0 to 1
 * @param recolour whether matched atoms are filled with the colour of their match
 * @param labels whether matched atoms and bonds are labelled
 * @param matches the matches shown; empty if there is none
 */
public record Match3dData(boolean merged, int total, int shown, double dim, boolean recolour, boolean labels, List<Highlight> matches)
{
    /**
     * @param colour the hex colour of the match
     * @param atoms the atoms in the match
     * @param bonds the bonds between those atoms
     * @param atomLabels the labels of the atoms, when labels are wanted
     * @param bondLabels the labels of the bonds, when labels are wanted
     */
    public record Highlight(String colour, List<Integer> atoms, List<Integer> bonds, List<Label> atomLabels, List<Label> bondLabels)
    {
    }

    /**
     * @param index the atom or bond labelled
     * @param text its label
     */
    public record Label(int index, String text)
    {
    }
}
