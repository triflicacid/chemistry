package com.ruben.chemistry.core;

import com.ruben.chemistry.util.DisjointSet;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits a {@link Molecule} into its connected fragments, i.e. the disconnected structures written
 * either side of a {@code .} in SMILES.
 */
final class MoleculeFragmenter
{
    private MoleculeFragmenter()
    {
    }

    /**
     * Separates a molecule into its connected fragments.
     * <p>
     * Atoms and bonds are shared with {@code molecule}, so ordering, stereochemical constraints etc are all preserved.
     *
     * @param molecule the molecule to separate
     * @return one sub-molecule per connected fragment
     */
    static List<Molecule> fragment(Molecule molecule)
    {
        List<Atom> atoms = molecule.atoms();
        if (atoms.isEmpty())
        {
            return List.of();
        }

        Map<Atom, Integer> indices = indexAtoms(atoms);
        DisjointSet groups = joinBondedAtoms(molecule, indices);

        // keying by the group's root leaves the fragments ordered by their first atom, as the atoms
        // are walked in the order the molecule holds them
        Map<Integer, Molecule.Builder> builders = new LinkedHashMap<>();
        for (Atom atom : atoms)
        {
            builders.computeIfAbsent(groups.find(indices.get(atom)), root -> new Molecule.Builder()).addAtom(atom);
        }

        if (builders.size() == 1)
        {
            return List.of(molecule);
        }

        for (Bond bond : molecule.bonds())
        {
            // a bond joins its atoms, so either endpoint identifies the fragment it belongs to
            builderOf(builders, groups, indices, bond.first()).addBond(bond);
        }

        copyStereochemistry(molecule, builders, groups, indices);

        return builders.values().stream().map(Molecule.Builder::build).toList();
    }

    /**
     * @param molecule the molecule being separated
     * @param builders each fragment's builder, keyed by its group's root
     * @param groups which atoms are connected, over the atom indices
     * @param indices each atom's index
     */
    private static void copyStereochemistry(
        Molecule molecule,
        Map<Integer, Molecule.Builder> builders,
        DisjointSet groups,
        Map<Atom, Integer> indices)
    {
        for (Atom atom : molecule.atoms())
        {
            molecule.chiralityOf(atom)
                .ifPresent(chirality -> builderOf(builders, groups, indices, atom)
                        .setChirality(atom, chirality));
        }

        for (Bond bond : molecule.bonds())
        {
            molecule.doubleBondStereoOf(bond)
                    .ifPresent(stereo -> builderOf(builders, groups, indices, bond.first())
                            .setDoubleBondStereo(bond.first(), bond.second(), stereo));
        }
    }

    /**
     * @param builders each fragment's builder, keyed by its group's root
     * @param groups which atoms are connected, over the atom indices
     * @param indices each atom's index
     * @param atom the atom to locate, by reference identity
     * @return the builder of the fragment {@code atom} belongs to
     */
    private static Molecule.Builder builderOf(
        Map<Integer, Molecule.Builder> builders,
        DisjointSet groups,
        Map<Atom, Integer> indices,
        Atom atom)
    {
        return builders.get(groups.find(indices.get(atom)));
    }

    /**
     * @param atoms the atoms to index, in order
     * @return each atom's position in {@code atoms}, keyed by reference identity
     */
    private static Map<Atom, Integer> indexAtoms(List<Atom> atoms)
    {
        Map<Atom, Integer> indices = new IdentityHashMap<>();
        for (int index = 0; index < atoms.size(); index++)
        {
            indices.put(atoms.get(index), index);
        }
        return indices;
    }

    /**
     * @param molecule the molecule whose bonds to follow
     * @param indices each atom's index
     * @return the atom indices, with those reachable from one another merged into a single set
     */
    private static DisjointSet joinBondedAtoms(Molecule molecule, Map<Atom, Integer> indices)
    {
        DisjointSet groups = new DisjointSet(indices.size());
        for (Bond bond : molecule.bonds())
        {
            groups.union(indices.get(bond.first()), indices.get(bond.second()));
        }
        return groups;
    }
}
