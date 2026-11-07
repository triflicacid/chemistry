package com.ruben.chemistry.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * A {@link Ranker} that ranks each atom by its structural role within the molecule, so that
 * the same molecule built in a different order always yields the same ranks.
 * <p>
 * Ranks are derived by Morgan-style iterative refinement, with deterministic tie-breaking for
 * atoms that refinement cannot tell apart (such as the two ends of a symmetric molecule).
 */
public final class CanonicalRanker implements Ranker
{
    @Override
    public Ranking rank(Molecule molecule)
    {
        List<Atom> atoms = molecule.atoms();

        Set<Bond> ringBonds = molecule.ringBonds();

        // everyone starts in one class, then gets split by invariant, then refined by neighbours
        Map<Atom, Integer> classOf = new IdentityHashMap<>();
        for (Atom atom : atoms)
        {
            classOf.put(atom, 0);
        }
        classOf = reclassify(atoms, classOf, atom -> initialInvariant(molecule, ringBonds, atom));
        classOf = refineToStability(molecule, atoms, classOf);
        return new Ranking(breakTies(molecule, atoms, classOf));
    }

    /**
     * @param molecule the molecule being ranked
     * @param atoms the molecule's atoms
     * @param classOf each atom's current class
     * @return a classification that keeps every distinction {@code classOf} already makes, and
     *     in which two atoms share a class iff they are bonded to the same classes in the same way.
     *     (May still contain ties.)
     */
    private Map<Atom, Integer> refineToStability(Molecule molecule, List<Atom> atoms, Map<Atom, Integer> classOf)
    {
        int distinctClasses = countDistinct(classOf);
        while (true)
        {
            // re-split by neighbour classes
            Map<Atom, Integer> current = classOf;
            Map<Atom, Integer> refined = reclassify(atoms, current, atom -> neighbourInvariant(molecule, atom, current));
            int refinedCount = countDistinct(refined);
            classOf = refined;
            // a pass that splits nothing means there is nothing left to split
            if (refinedCount == distinctClasses)
            {
                return classOf;
            }
            distinctClasses = refinedCount;
        }
    }

    /**
     * @param molecule the molecule being ranked
     * @param atoms the molecule's atoms
     * @param classOf each atom's current class, as settled by {@link #refineToStability}
     * @return a classification in which every atom has a class of its own.
     *     Two atoms that {@code classOf} holds apart are never put back together, though every class number is handed out afresh.
     */
    private Map<Atom, Integer> breakTies(Molecule molecule, List<Atom> atoms, Map<Atom, Integer> classOf)
    {
        while (countDistinct(classOf) < atoms.size())
        {
            // arbitrary but deterministic: the first still-tied atom in insertion order
            Atom toSplit = firstTiedAtom(atoms, classOf);
            int freshClass = Collections.max(classOf.values()) + 1;
            Map<Atom, Integer> split = new IdentityHashMap<>(classOf);
            split.put(toSplit, freshClass);
            // refine again, so pulling that one atom out also separates its now-distinguishable neighbours
            classOf = refineToStability(molecule, atoms, split);
        }
        return classOf;
    }

    /**
     * @param atoms the molecule's atoms
     * @param classOf each atom's current class
     * @return the first atom, in {@code atoms} order, that still shares its class with another atom
     * @throws IllegalStateException if every atom already has a class of its own
     */
    private Atom firstTiedAtom(List<Atom> atoms, Map<Atom, Integer> classOf)
    {
        // a class holding more than one atom is a tie, so count the members of each
        Map<Integer, Integer> countByClass = new TreeMap<>();
        for (Atom atom : atoms)
        {
            countByClass.merge(classOf.get(atom), 1, Integer::sum);
        }
        return atoms.stream()
            .filter(atom -> countByClass.get(classOf.get(atom)) > 1)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No tied class remains to split"));
    }

    /**
     * @param atoms the molecule's atoms
     * @param previous each atom's current class
     * @param invariantOf the further property to split each class by
     * @return a classification in which two atoms share a class only if they shared one in {@code previous} and agree on {@code invariantOf}
     */
    private Map<Atom, Integer> reclassify(List<Atom> atoms, Map<Atom, Integer> previous, Function<Atom, Object> invariantOf)
    {
        // pairing the old class with the new invariant is what keeps classes from merging back together
        Map<Atom, String> signatureOf = new IdentityHashMap<>();
        for (Atom atom : atoms)
        {
            signatureOf.put(atom, previous.get(atom) + "|" + invariantOf.apply(atom));
        }

        // sorted, so the class numbers handed out depend on the signatures and not on atom order
        TreeMap<String, Integer> classOfSignature = new TreeMap<>();
        for (String signature : signatureOf.values())
        {
            classOfSignature.putIfAbsent(signature, 0);
        }
        int nextClass = 0;
        for (String signature : classOfSignature.keySet())
        {
            classOfSignature.put(signature, nextClass++);
        }

        Map<Atom, Integer> result = new IdentityHashMap<>();
        for (Atom atom : atoms)
        {
            result.put(atom, classOfSignature.get(signatureOf.get(atom)));
        }
        return result;
    }

    /**
     * @param classOf each atom's class
     * @return the number of distinct classes in use
     */
    private int countDistinct(Map<Atom, Integer> classOf)
    {
        return Set.copyOf(classOf.values()).size();
    }

    /**
     * @param molecule the molecule being ranked
     * @param ringBonds the molecule's ring bonds
     * @param atom the atom to describe
     * @return {@code atom}'s own structural properties, ignoring its neighbours
     */
    private AtomKey initialInvariant(Molecule molecule, Set<Bond> ringBonds, Atom atom)
    {
        List<Bond> bonds = molecule.bondsOf(atom);
        boolean inRing = bonds.stream().anyMatch(ringBonds::contains);
        return new AtomKey(atom.element(), atom.aromatic(), atom.charge(), atom.isotope().orElse(-1), atom.hydrogenCount().orElse(-1), bonds.size(), inRing);
    }

    /**
     * @param molecule the molecule being ranked
     * @param atom the atom to describe
     * @param classOf each atom's current class
     * @return how {@code atom} is bonded to each of its neighbours' classes, in an order independent of how its bonds were added
     */
    private Object neighbourInvariant(Molecule molecule, Atom atom, Map<Atom, Integer> classOf)
    {
        return molecule.bondsOf(atom).stream()
            .map(bond -> bond.order().ordinal() + ":" + classOf.get(bond.getOther(atom)))
            .sorted()
            .toList();
    }

    /**
     * An atom's own structural properties, used to split atoms into their initial classes.
     */
    private record AtomKey(String element, boolean aromatic, int charge, int isotope, int hydrogenCount, int degree, boolean inRing)
    {
    }
}
