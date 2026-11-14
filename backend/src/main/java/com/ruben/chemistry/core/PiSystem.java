package com.ruben.chemistry.core;

import com.ruben.chemistry.util.IdentitySet;
import com.ruben.chemistry.util.MaximumMatching;


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * One conjugated π system of a {@link Molecule}: a ring, or a set of rings fused into a single
 * plane, with a Kékule assignment of its bonds if one exists.
 * <p>
 * A system is aromatic as written when its bonds can be assigned: every atom of it is planar, and
 * every atom needing a π bond has one.
 * Hückel's 4n+2 governs a single ring, so it cannot be asked of a fused system:
 * pyrene holds 16 π electrons, perylene 20 and coronene 24, and all three are aromatic.
 * 4n tells us a single ring is aromatic, but cannot be applied to the above.
 * <p>
 * Rings join into one system when they share a bond, which is what puts their atoms in one plane.
 * Rings sharing a single atom do not: that atom holds four σ bonds, so no π system runs around either of them.
 *
 * @param atoms the system's atoms, by reference identity, in the molecule's own order
 * @param bonds the bonds between them, by reference identity, in the molecule's own order
 * @param rings how many of the molecule's rings fused into the system
 * @param piElectrons the π electrons counted over {@link #atoms}
 * @param rimElectrons the π electrons counted around the system's rim, which is the circuit 4n+2
 *     and 4n are counts of, or empty if its rim is not a single circuit
 * @param kekule the bonds a Kékule assignment makes double, or empty if the system has no such
 *     assignment, which is what makes it unreadable as an aromatic structure
 * @param aromaticity what the system amounts to as written
 */
public record PiSystem(List<Atom> atoms, List<Bond> bonds, int rings, int piElectrons, OptionalInt rimElectrons, Optional<List<Bond>> kekule, Aromaticity aromaticity)
{
    /** What a system amounts to as written. */
    public enum Aromaticity
    {
        /** Its bonds can be assigned, so it stands as an aromatic system. */
        AROMATIC,

        /** A lone ring whose electrons come to 4n, for n of at least one. */
        ANTIAROMATIC,

        /** Its bonds cannot be assigned at all. */
        NONAROMATIC
    }

    /** Elements whose lone pair joins a π system when the atom has no π bond of its own, e.g. pyrrole's nitrogen. */
    private static final Set<String> LONE_PAIR_DONORS = Set.of("N", "O", "P", "S", "Se", "Te");

    /**
     * Elements that take a ring atom's π electrons out of the system through a double bond,
     * leaving it trigonal but lending nothing, e.g. the carbonyl of 2H-pyran-2-one.
     */
    private static final Set<String> WITHDRAWING = Set.of("N", "O", "S", "Se", "Te");

    /** Elements with an empty p orbital to offer a π system, e.g. borole's boron. */
    private static final Set<String> EMPTY_ORBITALS = Set.of("B", "Al");

    /** Elements that offer an empty p orbital rather than electrons, so a planar ring atom of one lends nothing. */
    private static final Set<String> GROUP_13 = Set.of("B", "Al", "Ga", "In", "Tl");

    /** Elements with one p electron to lend from a planar ring atom, e.g. benzene's carbon. */
    private static final Set<String> GROUP_14 = Set.of("C", "Si", "Ge", "Sn", "Pb");

    /** Elements that lend a lone pair from a planar ring atom only once σ bonds fill the rest, e.g. pyrrole's nitrogen against pyridine's. */
    private static final Set<String> GROUP_15 = Set.of("N", "P", "As", "Sb", "Bi");

    /** Elements that lend a lone pair from a planar ring atom, e.g. furan's oxygen. */
    private static final Set<String> GROUP_16 = Set.of("O", "S", "Se", "Te", "Po");

    /** The σ bonds a group 15 ring atom needs before its lone pair goes to the system rather than a bond. */
    private static final int GROUP_15_DONATING_SIGMA = 3;

    /** The atoms a π system needs to run around, since the shortest cycle is a triangle. */
    private static final int MINIMUM_RING_SIZE = 3;

    /** What an atom lends when it holds one p electron, and so needs a π bond to pair it with. */
    private static final int ONE_PI_ELECTRON = 1;

    /** Stands in for a rim electron count where there is no single rim to count. */
    private static final int NO_RIM = -1;

    /**
     * Finds every π system of a molecule.
     *
     * @param molecule the molecule to find them in
     * @return its π systems, unmodifiable, each with at least one ring and empty if it has none
     */
    public static List<PiSystem> of(Molecule molecule)
    {
        Map<Atom, Integer> lentBy = new IdentityHashMap<>();
        List<Atom> conjugated = new ArrayList<>();
        for (Atom atom : molecule.atoms())
        {
            OptionalInt lent = piElectronsOf(molecule, atom);
            if (lent.isPresent())
            {
                lentBy.put(atom, lent.getAsInt());
                conjugated.add(atom);
            }
        }

        List<PiSystem> systems = new ArrayList<>();
        for (List<Bond> component : cyclicComponentsOf(molecule, conjugated))
        {
            Set<Atom> members = IdentitySet.of();
            component.forEach(bond ->
            {
                members.add(bond.first());
                members.add(bond.second());
            });
            List<Atom> atoms = molecule.atoms().stream().filter(members::contains).toList();
            int electrons = atoms.stream().mapToInt(lentBy::get).sum();
            // the independent cycles of a connected subgraph, by Euler's formula
            int rings = component.size() - atoms.size() + 1;
            Optional<List<Bond>> kekule = kekuliseOf(molecule, members, lentBy);
            OptionalInt rim = rimElectronsOf(molecule, component, lentBy);
            systems.add(new PiSystem(atoms, component, rings, electrons, rim, kekule, verdictOf(kekule, rim)));
        }
        return List.copyOf(systems);
    }

    /**
     * @return whether this system's bonds can be assigned, which is what makes it readable as an aromatic system
     */
    public boolean isKekulisable()
    {
        return kekule.isPresent();
    }

    /**
     * @return whether this system stands as an aromatic system as written
     */
    public boolean isAromatic()
    {
        return aromaticity == Aromaticity.AROMATIC;
    }

    /**
     * @param atom an atom, by reference identity
     * @return whether it belongs to this system
     */
    public boolean contains(Atom atom)
    {
        return atoms.stream().anyMatch(member -> member == atom);
    }

    /**
     * Finds the cyclic biconnected components of the subgraph of atoms that can join a π system.
     * <p>
     * A biconnected component is a maximal set of bonds any two of which lie on a common cycle.
     *
     * @param molecule the molecule to search
     * @param conjugated the atoms that can join a π system, in the molecule's own order
     * @return one bond list per component that holds a cycle, each in the molecule's own order
     */
    private static List<List<Bond>> cyclicComponentsOf(Molecule molecule, List<Atom> conjugated)
    {
        Set<Atom> candidates = IdentitySet.copyOf(conjugated);
        Map<Atom, Integer> depthOf = new IdentityHashMap<>();
        Map<Atom, Integer> lowOf = new IdentityHashMap<>();
        Deque<Bond> stacked = new ArrayDeque<>();
        List<List<Bond>> components = new ArrayList<>();
        for (Atom atom : conjugated)
        {
            if (!depthOf.containsKey(atom))
            {
                walk(molecule, atom, candidates, depthOf, lowOf, stacked, components);
            }
        }
        return components.stream()
            .filter(component -> component.size() >= MINIMUM_RING_SIZE)
            .map(component -> orderedLike(molecule, component))
            .toList();
    }

    /**
     * @param molecule the molecule the bonds belong to
     * @param component a component's bonds
     * @return the same bonds in the molecule's own order
     */
    private static List<Bond> orderedLike(Molecule molecule, List<Bond> component)
    {
        Set<Bond> members = IdentitySet.copyOf(component);
        return molecule.bonds().stream().filter(members::contains).toList();
    }

    /**
     * Walks the subgraph from one atom, cutting a component off the bond stack at every atom none
     * of whose descendants reach back past it.
     *
     * @param molecule the molecule being searched
     * @param root the atom to walk from
     * @param candidates the atoms that can join a π system
     * @param depthOf how deep each atom was first reached, which this fills in
     * @param lowOf the shallowest depth each atom's descendants reach back to, which this fills in
     * @param stacked the bonds walked but not yet cut off into a component
     * @param components collects the components found
     */
    private static void walk(Molecule molecule, Atom root, Set<Atom> candidates, Map<Atom, Integer> depthOf,
        Map<Atom, Integer> lowOf, Deque<Bond> stacked, List<List<Bond>> components)
    {
        Deque<Step> pending = new ArrayDeque<>();
        depthOf.put(root, 0);
        lowOf.put(root, 0);
        pending.push(new Step(root, null, ringBondsOf(molecule, root, candidates).iterator()));
        while (!pending.isEmpty())
        {
            Step step = pending.peek();
            if (!step.remaining().hasNext())
            {
                pending.pop();
                Step parent = pending.peek();
                if (parent != null)
                {
                    cutOff(step, parent, depthOf, lowOf, stacked, components);
                }
                continue;
            }

            Bond bond = step.remaining().next();
            Atom next = bond.getOther(step.atom());
            if (bond == step.via())
            {
                continue;
            }
            if (depthOf.containsKey(next))
            {
                // an edge back to an atom already reached closes a cycle, so it joins this component
                if (depthOf.get(next) < depthOf.get(step.atom()))
                {
                    stacked.push(bond);
                    lowOf.merge(step.atom(), depthOf.get(next), Math::min);
                }
                continue;
            }
            stacked.push(bond);
            int depth = depthOf.get(step.atom()) + 1;
            depthOf.put(next, depth);
            lowOf.put(next, depth);
            pending.push(new Step(next, bond, ringBondsOf(molecule, next, candidates).iterator()));
        }
    }

    /**
     * Cuts a finished atom's component off the bond stack, if its descendants reach back no further than its parent.
     *
     * @param step the finished atom's step
     * @param parent its parent's step
     * @param depthOf how deep each atom was first reached
     * @param lowOf the shallowest depth each atom's descendants reach back to
     * @param stacked the bonds walked but not yet cut off
     * @param components collects the components found
     */
    private static void cutOff(Step step, Step parent, Map<Atom, Integer> depthOf, Map<Atom, Integer> lowOf, Deque<Bond> stacked, List<List<Bond>> components)
    {
        lowOf.merge(parent.atom(), lowOf.get(step.atom()), Math::min);
        if (lowOf.get(step.atom()) < depthOf.get(parent.atom()))
        {
            return;
        }

        List<Bond> component = new ArrayList<>();
        while (!stacked.isEmpty() && stacked.peek() != step.via())
        {
            component.add(stacked.pop());
        }
        if (!stacked.isEmpty())
        {
            component.add(stacked.pop());
        }
        components.add(component);
    }

    /**
     * @param molecule the molecule the atom belongs to
     * @param atom an atom of it
     * @param candidates the atoms that can join a π system
     * @return the atom's ring bonds that lead to another candidate
     */
    private static List<Bond> ringBondsOf(Molecule molecule, Atom atom, Set<Atom> candidates)
    {
        Set<Bond> ringBonds = molecule.ringBonds();
        return molecule.bondsOf(atom).stream()
            .filter(ringBonds::contains)
            .filter(bond -> candidates.contains(bond.getOther(atom)))
            .toList();
    }

    /** One atom on the walk's stack, with the bond it was reached by and its bonds still to try. */
    private record Step(Atom atom, Bond via, Iterator<Bond> remaining)
    {
    }

    /**
     * @param kekule the system's assignment, if it has one
     * @param rimElectrons the π electrons around its rim, if its rim is a single circuit
     * @return what the system amounts to as written
     */
    private static Aromaticity verdictOf(Optional<List<Bond>> kekule, OptionalInt rimElectrons)
    {
        if (kekule.isEmpty())
        {
            return Aromaticity.NONAROMATIC;
        }
        // 4n is a count of one circuit, and the circuit a fused system's electrons run around is
        // its rim: pyrene's rim holds fourteen of its sixteen, which is why it is aromatic though
        // sixteen is 4n. Where there is no single rim to count, nothing sound is left to take away
        // the aromaticity the assignment earned
        int rim = rimElectrons.orElse(NO_RIM);
        return rim >= 4 && rim % 4 == 0 ? Aromaticity.ANTIAROMATIC : Aromaticity.AROMATIC;
    }

    /**
     * Counts the π electrons around a system's rim.
     * <p>
     * A bond of the rim lies in one ring of the system, while one shared between two rings lies
     * inside it, so the rim is what is left once the shared bonds are taken away. That is the
     * circuit Hückel's rule counts: the whole system's electrons are a count of no circuit at all
     * once any of its atoms lies inside the rim rather than on it.
     *
     * @param molecule the molecule the system belongs to
     * @param bonds the system's bonds
     * @param lentBy what each atom lends
     * @return the electrons around the rim, or empty if the rim is not a single circuit
     */
    private static OptionalInt rimElectronsOf(Molecule molecule, List<Bond> bonds, Map<Atom, Integer> lentBy)
    {
        List<Bond> rim = bonds.stream().filter(bond -> ringsHolding(molecule, bond) == 1).toList();
        Set<Atom> atoms = IdentitySet.of();
        rim.forEach(bond ->
        {
            atoms.add(bond.first());
            atoms.add(bond.second());
        });

        // a circuit passes through each of its atoms once, so it holds two of their bonds apiece
        // and has as many bonds as atoms
        if (rim.isEmpty() || rim.size() != atoms.size())
        {
            return OptionalInt.empty();
        }
        for (Atom atom : atoms)
        {
            if (rim.stream().filter(bond -> bond.first() == atom || bond.second() == atom).count() != 2)
            {
                return OptionalInt.empty();
            }
        }
        // every atom having two leaves one circuit or several, and only one of them is a rim
        return isOneCircuit(rim, atoms) ? OptionalInt.of(atoms.stream().mapToInt(lentBy::get).sum()) : OptionalInt.empty();
    }

    /**
     * @param rim the rim's bonds, each atom of which holds two of them
     * @param atoms the atoms they join
     * @return whether they make one circuit rather than several
     */
    private static boolean isOneCircuit(List<Bond> rim, Set<Atom> atoms)
    {
        Set<Atom> reached = IdentitySet.of();
        Deque<Atom> pending = new ArrayDeque<>();
        Atom start = atoms.iterator().next();
        reached.add(start);
        pending.push(start);
        while (!pending.isEmpty())
        {
            Atom atom = pending.pop();
            for (Bond bond : rim)
            {
                if (bond.first() != atom && bond.second() != atom)
                {
                    continue;
                }
                Atom other = bond.getOther(atom);
                if (reached.add(other))
                {
                    pending.push(other);
                }
            }
        }
        return reached.size() == atoms.size();
    }

    /**
     * @param molecule the molecule the bond belongs to
     * @param bond a bond of it
     * @return how many of the molecule's rings hold the bond
     */
    private static int ringsHolding(Molecule molecule, Bond bond)
    {
        return (int) molecule.rings().stream().filter(ring -> ring.bonds().stream()
            .anyMatch(member -> member.matches(bond.first(), bond.second()))).count();
    }

    /**
     * Assigns the system's bonds, by matching every atom that needs a π bond with a neighbour that
     * needs one too. Each matched pair is a double bond, so an assignment is a perfect matching over
     * those atoms: benzene's six carbons each need one, and the three ring bonds that match them
     * alternately are its {@code C1=CC=CC=C1}. There are two such matchings, which are its two
     * Kekulé structures.
     * <p>
     * Matching each atom once is what the chemistry asks for from either end: a double bond belongs
     * to exactly the two atoms it joins, and an atom cannot hold two of them in the one plane.
     * <p>
     * An atom lending a lone pair or lending nothing needs no π bond and is no part of the matching
     * at all, so pyrrole's is two bonds over its four carbons, its nitrogen taking no part. An odd
     * number of atoms needing one cannot be paired: the matching strands exactly one, and that atom
     * is where the unpaired electron sits, as it does among phenalenyl's thirteen carbons.
     *
     * @param molecule the molecule the system belongs to
     * @param members the system's atoms
     * @param lentBy what each atom lends
     * @return the bonds the assignment makes double, or empty if there is no assignment
     */
    private static Optional<List<Bond>> kekuliseOf(Molecule molecule, Set<Atom> members, Map<Atom, Integer> lentBy)
    {
        List<Atom> needing = members.stream().filter(atom -> lentBy.get(atom) == ONE_PI_ELECTRON).toList();
        Map<Atom, Integer> indexOf = new IdentityHashMap<>();
        for (int index = 0; index < needing.size(); index++)
        {
            indexOf.put(needing.get(index), index);
        }

        List<List<Integer>> neighbours = new ArrayList<>();
        for (Atom atom : needing)
        {
            neighbours.add(molecule.bondsOf(atom).stream()
                .map(bond -> indexOf.get(bond.getOther(atom)))
                .filter(Objects::nonNull)
                .toList());
        }

        int[] partnerOf = MaximumMatching.of(neighbours);
        if (!MaximumMatching.isPerfect(partnerOf))
        {
            return Optional.empty();
        }

        List<Bond> doubled = new ArrayList<>();
        for (int index = 0; index < needing.size(); index++)
        {
            if (index < partnerOf[index])
            {
                doubled.add(molecule.bondBetween(needing.get(index), needing.get(partnerOf[index])).orElseThrow());
            }
        }
        return Optional.of(List.copyOf(doubled));
    }

    /**
     * Counts what one atom lends to a π system running through it: two for a lone pair, one for a
     * p electron, and none for an atom that is planar but has nothing to give.
     * <p>
     * A double bond counts towards the system when it is itself a ring bond, which is what makes a
     * Kekulé fusion bond part of the system it lies in rather than something hanging off it.
     * <p>
     * What this counts is what the atom would lend; the atom need not be in a ring, nor in a system.
     * Summed over a system's own atoms, it is that system's {@link #piElectrons()}.
     *
     * @param molecule the molecule the atom belongs to
     * @param atom an atom of it, by reference identity
     * @return the number of π electrons it lends, or empty if it cannot join a π system at all
     */
    public static OptionalInt piElectronsOf(Molecule molecule, Atom atom)
    {
        Set<Bond> ringBonds = molecule.ringBonds();
        int doubleBondsInRings = 0;
        int aromaticBondsInRings = 0;
        Atom doubleBondedOutside = null;
        for (Bond bond : molecule.bondsOf(atom))
        {
            boolean inRing = ringBonds.contains(bond);
            switch (bond.order())
            {
                case DOUBLE ->
                {
                    if (inRing)
                    {
                        doubleBondsInRings++;
                    }
                    else
                    {
                        doubleBondedOutside = bond.getOther(atom);
                    }
                }
                case AROMATIC ->
                {
                    if (inRing)
                    {
                        aromaticBondsInRings++;
                    }
                }
                // a triple or quadruple bond is more than a trigonal atom can hold, in a ring or out of it
                case TRIPLE, QUADRUPLE ->
                {
                    return OptionalInt.empty();
                }
                default ->
                {
                }
            }
        }

        // two π bonds on the one atom are perpendicular, so they cannot both lie in the system
        if (doubleBondsInRings > 1 || (doubleBondsInRings == 1 && doubleBondedOutside != null))
        {
            return OptionalInt.empty();
        }
        // an electronegative partner holds the pair outside the system, as 2H-pyran-2-one's carbonyl does
        if (doubleBondedOutside != null)
        {
            return WITHDRAWING.contains(doubleBondedOutside.element()) ? OptionalInt.of(0) : OptionalInt.empty();
        }
        // an aromatic bond asserts the atom is planar, which settles what it has left to lend
        if (aromaticBondsInRings > 0)
        {
            return lentWhenPlanar(molecule, atom);
        }
        return doubleBondsInRings == 1 ? OptionalInt.of(1) : lentWithoutPiBond(atom);
    }

    /**
     * Counts what a ring atom written as aromatic lends to a π system.
     * <p>
     * Aromatic notation asserts the atom is planar, so what it lends follows from its group and how
     * many σ bonds it holds: an atom whose σ bonds leave a lone pair spare lends the pair, and one
     * with a p electron free lends that.
     *
     * @param molecule the molecule the atom belongs to
     * @param atom a ring atom with at least one aromatic ring bond
     * @return the number of π electrons it lends, or empty if its element is not one a π system is known for
     */
    private static OptionalInt lentWhenPlanar(Molecule molecule, Atom atom)
    {
        int sigmaBonds = molecule.bondsOf(atom).size() + molecule.totalHydrogenCount(atom);
        OptionalInt neutral = lentWhenNeutralAndPlanar(atom.element(), sigmaBonds);
        if (neutral.isEmpty())
        {
            return OptionalInt.empty();
        }
        // a positive charge is an electron gone from the atom, and a negative one an electron arrived
        return OptionalInt.of(Math.max(0, neutral.getAsInt() - atom.charge()));
    }

    /**
     * @param element a planar ring atom's element symbol
     * @param sigmaBonds how many σ bonds it holds, its hydrogens counted
     * @return the number of π electrons an uncharged atom of that element lends
     */
    private static OptionalInt lentWhenNeutralAndPlanar(String element, int sigmaBonds)
    {
        if (GROUP_13.contains(element))
        {
            return OptionalInt.of(0);
        }
        if (GROUP_14.contains(element))
        {
            return OptionalInt.of(1);
        }
        if (GROUP_15.contains(element))
        {
            return OptionalInt.of(sigmaBonds >= GROUP_15_DONATING_SIGMA ? 2 : 1);
        }
        return GROUP_16.contains(element) ? OptionalInt.of(2) : OptionalInt.empty();
    }

    /**
     * Counts what a ring atom with no π bond of its own lends to a π system.
     *
     * @param atom a ring atom with only single bonds
     * @return the number of π electrons it lends, or empty if it cannot join the system
     */
    private static OptionalInt lentWithoutPiBond(Atom atom)
    {
        // a positive charge is the sign of the pair having gone elsewhere, leaving nothing to lend
        if (atom.charge() <= 0 && LONE_PAIR_DONORS.contains(atom.element()))
        {
            return OptionalInt.of(2);
        }
        if (atom.charge() < 0)
        {
            return OptionalInt.of(2);
        }
        if (atom.charge() > 0 || EMPTY_ORBITALS.contains(atom.element()))
        {
            return OptionalInt.of(0);
        }
        return OptionalInt.empty();
    }
}
