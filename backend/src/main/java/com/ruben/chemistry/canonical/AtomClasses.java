package com.ruben.chemistry.canonical;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.util.IdentityMap;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Each atom's class, which two atoms share exactly when nothing about the molecule tells them
 * apart. Asking whether an atom's substituents are all different is then asking whether they fall
 * in different classes.
 */
public final class AtomClasses
{
    private final Map<Atom, Integer> classByAtom;

    private final int classes;

    /**
     * @param atoms the classified atoms, indexed as {@code classOf} is
     * @param classOf each atom's class, by atom index
     * @param classes how many distinct classes {@code classOf} uses
     */
    AtomClasses(List<Atom> atoms, int[] classOf, int classes)
    {
        Map<Atom, Integer> byAtom = new IdentityHashMap<>();
        for (int atom = 0; atom < classOf.length; atom++)
        {
            byAtom.put(atoms.get(atom), classOf[atom]);
        }
        this.classByAtom = IdentityMap.copyOf(byAtom);
        this.classes = classes;
    }

    /**
     * @param atom an atom of the classified molecule, by reference identity
     * @return the atom's class
     * @throws IllegalArgumentException if {@code atom} was not part of the classified molecule
     */
    public int classOf(Atom atom)
    {
        Integer atomClass = classByAtom.get(atom);
        if (atomClass == null)
        {
            throw new IllegalArgumentException("Atom was not part of the classified molecule: " + atom);
        }
        return atomClass;
    }

    /**
     * @param first an atom of the classified molecule, by reference identity
     * @param second another, by reference identity
     * @return whether nothing about the molecule tells the two apart
     * @throws IllegalArgumentException if either atom was not part of the classified molecule
     */
    public boolean areAlike(Atom first, Atom second)
    {
        return classOf(first) == classOf(second);
    }

    /**
     * @return how many classes the molecule's atoms fall into, which is its atom count exactly when
     *     no two of them are alike
     */
    public int classCount()
    {
        return classes;
    }
}
