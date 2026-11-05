package com.ruben.chemistry.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Assigns each atom of a {@link Molecule} a dense {@code 0..atomCount-1} rank, based solely
 * on each atom's own properties and its position in the bond graph. Two atoms that play the
 * same structural role always end up with the same rank, regardless of the order atoms and
 * bonds were added to the molecule.
 * <p>
 * Uses Morgan-style iterative refinement (atoms are split into classes by invariant, then
 * repeatedly re-split by their neighbours' classes until the partition stops getting finer),
 * followed by deterministic tie-breaking for any atoms refinement alone can't distinguish.
 */
public final class CanonicalRanker implements Ranker
{
    @Override
    public Ranking rank(Molecule molecule)
    {
        List<Atom> atoms = new ArrayList<>();
        for (Atom atom : molecule.atoms())
        {
            atoms.add(atom);
        }

        Set<Bond> ringBonds = findRingBonds(molecule);

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
     * Repeatedly re-splits atoms by their neighbours' classes until the number of
     * distinct classes stops growing.
     */
    private Map<Atom, Integer> refineToStability(Molecule molecule, List<Atom> atoms, Map<Atom, Integer> classOf)
    {
        int distinctClasses = countDistinct(classOf);
        while (true)
        {
            Map<Atom, Integer> current = classOf;
            Map<Atom, Integer> refined = reclassify(atoms, current, atom -> neighbourInvariant(molecule, atom, current));
            int refinedCount = countDistinct(refined);
            classOf = refined;
            if (refinedCount == distinctClasses)
            {
                return classOf;
            }
            distinctClasses = refinedCount;
        }
    }

    /**
     * Splits any remaining tied classes one atom at a time, re-refining after each split so it
     * propagates outwards, until every atom has a unique class.
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
            classOf = refineToStability(molecule, atoms, split);
        }
        return classOf;
    }

    private Atom firstTiedAtom(List<Atom> atoms, Map<Atom, Integer> classOf)
    {
        Map<Integer, Integer> countByClass = new TreeMap<>();
        for (Atom atom : atoms)
        {
            countByClass.merge(classOf.get(atom), 1, Integer::sum);
        }
        for (Atom atom : atoms)
        {
            if (countByClass.get(classOf.get(atom)) > 1)
            {
                return atom;
            }
        }
        throw new IllegalStateException("No tied class remains to split");
    }

    /**
     * Re-splits atoms by combining their current class with a further invariant, so
     * classes only ever get finer, never merge back together. Distinct combined
     * signatures are sorted to assign dense, deterministic class numbers.
     */
    private Map<Atom, Integer> reclassify(List<Atom> atoms, Map<Atom, Integer> previous, Function<Atom, Object> invariantOf)
    {
        Map<Atom, String> signatureOf = new IdentityHashMap<>();
        for (Atom atom : atoms)
        {
            signatureOf.put(atom, previous.get(atom) + "|" + invariantOf.apply(atom));
        }

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

    private int countDistinct(Map<Atom, Integer> classOf)
    {
        return Set.copyOf(classOf.values()).size();
    }

    private AtomKey initialInvariant(Molecule molecule, Set<Bond> ringBonds, Atom atom)
    {
        int degree = 0;
        boolean inRing = false;
        for (Bond bond : molecule.bondsOf(atom))
        {
            degree++;
            if (ringBonds.contains(bond))
            {
                inRing = true;
            }
        }
        return new AtomKey(atom.element(), atom.aromatic(), atom.charge(), atom.isotope().orElse(-1), atom.hydrogenCount().orElse(-1), degree, inRing);
    }

    private Object neighbourInvariant(Molecule molecule, Atom atom, Map<Atom, Integer> classOf)
    {
        List<String> neighbours = new ArrayList<>();
        for (Bond bond : molecule.bondsOf(atom))
        {
            neighbours.add(bond.order().ordinal() + ":" + classOf.get(bond.getOther(atom)));
        }
        Collections.sort(neighbours);
        return neighbours;
    }

    /**
     * Finds every bond that lies on some cycle:
     * a bond is a bridge, and so excluded here, if its child
     * subtree has no back-edge reaching back to or above the bond's other endpoint.
     *
     * @return the molecule's ring bonds, by reference identity
     */
    private Set<Bond> findRingBonds(Molecule molecule)
    {
        Set<Bond> ringBonds = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Atom> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Atom, Integer> discoveryTime = new IdentityHashMap<>();
        Map<Atom, Integer> low = new IdentityHashMap<>();
        AtomicInteger clock = new AtomicInteger(); // single shared counter, incremented as the DFS below visits each atom

        for (Atom atom : molecule.atoms())
        {
            if (!visited.contains(atom))
            {
                findRingBonds(molecule, atom, null, ringBonds, visited, discoveryTime, low, clock);
            }
        }
        return ringBonds;
    }

    private void findRingBonds(Molecule molecule, Atom atom, Bond arrivingBond, Set<Bond> ringBonds, Set<Atom> visited, Map<Atom, Integer> discoveryTime, Map<Atom, Integer> low, AtomicInteger clock)
    {
        visited.add(atom);
        discoveryTime.put(atom, clock.get());
        low.put(atom, clock.get());
        clock.incrementAndGet();

        for (Bond bond : molecule.bondsOf(atom))
        {
            if (bond == arrivingBond)
            {
                continue;
            }

            Atom neighbour = bond.getOther(atom);
            if (!visited.contains(neighbour))
            {
                findRingBonds(molecule, neighbour, bond, ringBonds, visited, discoveryTime, low, clock);
                low.put(atom, Math.min(low.get(atom), low.get(neighbour)));
                if (low.get(neighbour) <= discoveryTime.get(atom))
                {
                    ringBonds.add(bond);
                }
            }
            else
            {
                // back edge: neighbour is an ancestor, so this bond closes a cycle
                low.put(atom, Math.min(low.get(atom), discoveryTime.get(neighbour)));
                ringBonds.add(bond);
            }
        }
    }

    /**
     * An atom's structural invariant, ignoring its neighbours: everything used to
     * split atoms into initial classes before neighbour-based refinement begins.
     */
    private record AtomKey(String element, boolean aromatic, int charge, int isotope, int hydrogenCount, int degree, boolean inRing)
    {
    }
}
