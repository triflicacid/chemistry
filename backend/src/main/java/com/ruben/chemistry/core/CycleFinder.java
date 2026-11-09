package com.ruben.chemistry.core;

import com.ruben.chemistry.util.IdentitySet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the cycles of a {@link Molecule}.
 */
final class CycleFinder
{
    private CycleFinder()
    {
    }

    /**
     * A cycle.
     *
     * @param atoms the cycle's atoms, by reference identity
     * @param bonds the cycle's bonds, by reference identity
     */
    record Cycle(Set<Atom> atoms, Set<Bond> bonds)
    {
    }

    /**
     * The cycles of a molecule.
     *
     * @param relevant every relevant cycle, i.e., the union of all minimum cycle bases, so independent of atom order
     * @param basis the cycles of one minimum cycle basis; where ties allow several, which one this is depends on atom order
     */
    record Cycles(List<Cycle> relevant, List<Cycle> basis)
    {
    }

    /**
     * One shortest path from a root atom.
     *
     * @param bonds the indices of the path's bonds
     * @param atoms the indices of the path's atoms, other than the root
     */
    private record Path(BitSet bonds, BitSet atoms)
    {
    }

    /**
     * Finds every relevant cycle: those that cannot be written as the XOR of strictly shorter cycles. This is the
     * union of all minimum cycle bases, so it is the same whichever order the atoms and bonds were given in.
     * One minimum cycle basis is picked out of them too.
     * <p>
     * Every relevant cycle is in the Horton set (a shortest path from a root atom to each end of a bond, plus that bond),
     * so those candidates are generated and then filtered by GF(2) elimination, shortest first.
     *
     * @param molecule the molecule to search
     * @return the relevant cycles, and a minimum cycle basis among them
     */
    static Cycles analyse(Molecule molecule)
    {
        // strip acyclic leaves repeatedly, so substituents are never searched
        Map<Atom, Integer> degree = new IdentityHashMap<>();
        Set<Atom> stripped = IdentitySet.of();
        Deque<Atom> leaves = new ArrayDeque<>();
        for (Atom atom : molecule.atoms())
        {
            degree.put(atom, molecule.bondsOf(atom).size());
            if (molecule.bondsOf(atom).size() <= 1)
            {
                leaves.add(atom);
            }
        }
        while (!leaves.isEmpty())
        {
            Atom leaf = leaves.poll();
            // an atom may be queued twice, once as a leaf and again if it loses its last remaining bond
            if (stripped.add(leaf))
            {
                for (Bond bond : molecule.bondsOf(leaf))
                {
                    // removing the leaf may leave its neighbour as a new leaf
                    Atom other = bond.getOther(leaf);
                    if (!stripped.contains(other) && degree.merge(other, -1, Integer::sum) <= 1)
                    {
                        leaves.add(other);
                    }
                }
            }
        }

        // number the surviving atoms and bonds, so a path or cycle can be a BitSet of indices
        Map<Atom, Integer> atomIndex = new IdentityHashMap<>();
        for (Atom atom : molecule.atoms())
        {
            if (!stripped.contains(atom))
            {
                atomIndex.put(atom, atomIndex.size());
            }
        }
        List<Bond> bondList = molecule.bonds().stream()
            .filter(bond -> atomIndex.containsKey(bond.first()) && atomIndex.containsKey(bond.second()))
            .toList();
        Map<Bond, Integer> bondIndex = new IdentityHashMap<>();
        for (int i = 0; i < bondList.size(); i++)
        {
            bondIndex.put(bondList.get(i), i);
        }

        // the same cycle turns up from several roots, so collect into a set to drop duplicates
        Set<BitSet> candidates = new HashSet<>();
        for (Atom root : atomIndex.keySet())
        {
            collectHortonCycles(molecule, root, atomIndex, bondList, bondIndex, candidates);
        }

        // shortest first, so each cycle is only ever compared against shorter ones
        List<BitSet> byLength = new ArrayList<>(candidates);
        byLength.sort(Comparator.comparingInt(BitSet::cardinality));

        // basis of every cycle shorter than the group being tested, keyed by lowest set bit
        Map<Integer, BitSet> basis = new HashMap<>();
        Map<BitSet, Cycle> relevantByBonds = new HashMap<>();
        List<Cycle> relevant = new ArrayList<>();
        List<Cycle> minimumBasis = new ArrayList<>();
        int i = 0;
        while (i < byLength.size())
        {
            // find the group of candidates sharing this length, [i, end)
            int length = byLength.get(i).cardinality();
            int end = i;
            while (end < byLength.size() && byLength.get(end).cardinality() == length)
            {
                end++;
            }

            // test the whole group against shorter cycles only, before any of it joins the basis,
            // otherwise equally short cycles would rule each other out
            for (int j = i; j < end; j++)
            {
                BitSet cycle = byLength.get(j);
                // anything left after eliminating the shorter cycles means it is not a combination of them
                if (!reduce(cycle, basis).isEmpty())
                {
                    Cycle built = buildCycle(cycle, bondList);
                    relevantByBonds.put(cycle, built);
                    relevant.add(built);
                }
            }
            // each relevant cycle that is independent of the basis so far also joins the minimum cycle basis;
            // reducing again here, as the basis grows, is what drops the redundant ones among equally short cycles
            for (int j = i; j < end; j++)
            {
                BitSet reduced = reduce(byLength.get(j), basis);
                if (!reduced.isEmpty())
                {
                    basis.put(reduced.nextSetBit(0), reduced);
                    minimumBasis.add(relevantByBonds.get(byLength.get(j)));
                }
            }
            i = end;
        }
        return new Cycles(List.copyOf(relevant), List.copyOf(minimumBasis));
    }

    /**
     * Builds a standalone {@link Molecule} from exactly one cycle's atoms and bonds.
     *
     * @param cycle the cycle
     * @return a sub-molecule containing just that cycle's bonds and their endpoint atoms
     */
    static Molecule toMolecule(Cycle cycle)
    {
        Molecule.Builder builder = new Molecule.Builder();
        // add each atom once; bonds are added afterwards, as both their atoms must already be present
        Set<Atom> added = IdentitySet.of();
        for (Bond bond : cycle.bonds())
        {
            if (added.add(bond.first()))
            {
                builder.addAtom(bond.first());
            }
            if (added.add(bond.second()))
            {
                builder.addAtom(bond.second());
            }
        }
        cycle.bonds().forEach(builder::addBond);
        return builder.build();
    }

    /**
     * @param bondIndices a cycle's bonds, as indices into {@code bondList}
     * @param bondList the bonds, indexed
     * @return the cycle's atoms and bonds
     */
    private static Cycle buildCycle(BitSet bondIndices, List<Bond> bondList)
    {
        Set<Atom> cycleAtoms = IdentitySet.of();
        Set<Bond> cycleBonds = IdentitySet.of();
        bondIndices.stream().forEach(index ->
        {
            Bond bond = bondList.get(index);
            cycleBonds.add(bond);
            // each atom is on two of the cycle's bonds, so the set absorbs the repeat
            cycleAtoms.add(bond.first());
            cycleAtoms.add(bond.second());
        });
        return new Cycle(cycleAtoms, cycleBonds);
    }

    /**
     * @param vector a vector over GF(2)
     * @param basis independent vectors keyed by their lowest set bit
     * @return a copy of {@code vector} with the basis eliminated from it; empty if {@code vector} is in the basis' span
     */
    private static BitSet reduce(BitSet vector, Map<Integer, BitSet> basis)
    {
        BitSet result = (BitSet) vector.clone();
        while (!result.isEmpty())
        {
            // cancel the lowest set bit with the basis vector that has it as its own lowest bit, if there is one
            BitSet pivot = basis.get(result.nextSetBit(0));
            if (pivot == null)
            {
                // nothing can cancel this bit, so the vector is independent of the basis
                break;
            }
            result.xor(pivot);
        }
        return result;
    }

    /**
     * Adds every simple cycle made of a bond and shortest paths from {@code root} to each of its ends.
     *
     * @param molecule the molecule being searched
     * @param root the atom to search from
     * @param atomIndex each searchable atom's index; atoms absent from it are not searched
     * @param bondList the searchable bonds, indexed
     * @param bondIndex each bond's index in {@code bondList}
     * @param cycles collects each cycle as the set of its bond indices
     */
    private static void collectHortonCycles(Molecule molecule, Atom root, Map<Atom, Integer> atomIndex, List<Bond> bondList, Map<Bond, Integer> bondIndex, Set<BitSet> cycles)
    {
        // BFS, recording every bond that reaches an atom by a shortest path: ties give several paths to one atom
        Map<Atom, Integer> distance = new IdentityHashMap<>();
        Map<Atom, List<Bond>> shortestIn = new IdentityHashMap<>();
        Map<Atom, List<Path>> paths = new IdentityHashMap<>();
        Deque<Atom> queue = new ArrayDeque<>();
        distance.put(root, 0);
        // the root is reached by the empty path
        paths.put(root, List.of(new Path(new BitSet(), new BitSet())));
        queue.add(root);
        while (!queue.isEmpty())
        {
            Atom current = queue.poll();
            if (current != root)
            {
                // everything one step closer is already reached, so its paths are complete:
                // extend each of them by the bond onwards to this atom
                List<Path> reaching = new ArrayList<>();
                for (Bond bond : shortestIn.get(current))
                {
                    for (Path prefix : paths.get(bond.getOther(current)))
                    {
                        BitSet pathBonds = (BitSet) prefix.bonds().clone();
                        BitSet pathAtoms = (BitSet) prefix.atoms().clone();
                        pathBonds.set(bondIndex.get(bond));
                        pathAtoms.set(atomIndex.get(current));
                        reaching.add(new Path(pathBonds, pathAtoms));
                    }
                }
                paths.put(current, reaching);
            }

            for (Bond bond : molecule.bondsOf(current))
            {
                Atom neighbour = bond.getOther(current);
                // never walk into a stripped atom, as it cannot be on a cycle
                if (!atomIndex.containsKey(neighbour))
                {
                    continue;
                }
                Integer known = distance.get(neighbour);
                if (known == null)
                {
                    // first time reaching neighbour, so this is a shortest way in
                    distance.put(neighbour, distance.get(current) + 1);
                    shortestIn.computeIfAbsent(neighbour, k -> new ArrayList<>()).add(bond);
                    queue.add(neighbour);
                }
                else if (known == distance.get(current) + 1)
                {
                    // reached before, but at the same distance: another shortest way in
                    shortestIn.get(neighbour).add(bond);
                }
            }
        }

        // close a cycle with each bond, by joining a shortest path to each of its ends
        for (int closingIndex = 0; closingIndex < bondList.size(); closingIndex++)
        {
            Bond closing = bondList.get(closingIndex);
            List<Path> toFirst = paths.get(closing.first());
            List<Path> toSecond = paths.get(closing.second());
            // the bond is in another fragment, which this root cannot reach
            if (toFirst == null || toSecond == null)
            {
                continue;
            }
            for (Path first : toFirst)
            {
                for (Path second : toSecond)
                {
                    // the paths meeting only at the root, and neither using the closing bond, makes a simple cycle
                    if (!first.atoms().intersects(second.atoms()) && !first.bonds().get(closingIndex) && !second.bonds().get(closingIndex))
                    {
                        BitSet cycle = (BitSet) first.bonds().clone();
                        cycle.or(second.bonds());
                        cycle.set(closingIndex);
                        cycles.add(cycle);
                    }
                }
            }
        }
    }
}
