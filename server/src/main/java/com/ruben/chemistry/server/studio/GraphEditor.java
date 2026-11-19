package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.Valences;
import com.ruben.chemistry.space.AtomPlacement;
import com.ruben.chemistry.space.Vec3;
import com.ruben.chemistry.util.IdentitySet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Works out the edits that change a graph's atoms and bonds.
 */
final class GraphEditor
{
    /**
     * An edit of the graph.
     *
     * @param after the graph the edit makes
     * @param label what the edit is called in the history
     * @param touched the atoms the edit changed, which refuse it being undone or redone if locked since
     * @param atoms the atoms it added, by name
     * @param bonds the bonds it added, by name
     */
    record Result(Graph after, String label, Set<String> touched, List<String> atoms, List<String> bonds)
    {
    }

    /**
     * A bond while a graph is being rebuilt.
     *
     * @param first one end
     * @param second the other
     * @param order its order
     * @param original the bond of the graph being replaced that this is, or {@code null} for one that is new
     */
    private record Joint(Atom first, Atom second, BondOrder order, Bond original)
    {
    }

    private final Graph graph;
    private final Set<String> locked;
    private final Names names;
    private final Runnable requireIdle;

    /**
     * @param graph the graph to edit, which is not changed
     * @param locked the atoms that refuse an edit
     * @param names where the names of new atoms and bonds come from
     * @param requireIdle checks that no edit of positions is in progress, throwing {@link StudioEditException} if one is
     */
    GraphEditor(Graph graph, Set<String> locked, Names names, Runnable requireIdle)
    {
        this.graph = graph;
        this.locked = locked;
        this.names = names;
        this.requireIdle = requireIdle;
    }

    /**
     * Changes an atom's non-geometric properties.
     *
     * @param atomId the atom to change
     * @param element its new element symbol, or {@code null} to leave it as it is
     * @param charge its new formal charge, where 0 is no charge, or {@code null} to leave it as it is
     * @param isotope its new mass number, where 0 is natural abundance, or {@code null} to leave it as it is
     * @return the edit, or empty if the atom already is as asked
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom the graph does not hold, {@code LOCKED} if it
     *     is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if the element is not one, the
     *     isotope is negative, or nothing was asked for
     */
    Optional<Result> setAtom(String atomId, String element, Integer charge, Integer isotope)
    {
        Atom atom = graph.atomNamed(atomId);
        if (element == null && charge == null && isotope == null)
        {
            throw new StudioEditException("INVALID_EDIT", "Nothing was asked to change: give an element, a charge or an isotope");
        }
        if (element != null && !Elements.isSymbol(element))
        {
            throw new StudioEditException("INVALID_EDIT", "'" + element + "' is not an element symbol");
        }
        if (isotope != null && isotope < 0)
        {
            throw new StudioEditException("INVALID_EDIT", "An isotope's mass number cannot be negative, but was " + isotope);
        }
        requireUnlocked(List.of(atomId));
        requireIdle.run();

        Atom replacement = Atom.builder(element != null ? element : atom.element())
                .aromatic(atom.aromatic())
                .charge(charge != null ? charge : atom.charge())
                .isotope(isotope != null ? (isotope == 0 ? OptionalInt.empty() : OptionalInt.of(isotope)) : atom.isotope())
                .hydrogenCount(atom.hydrogenCount())
                .hydrogenIsotopes(atom.hydrogenIsotopes())
                .atomClass(atom.atomClass())
                .build();
        if (replacement.equals(atom))
        {
            return Optional.empty();
        }

        Map<Atom, String> renamed = new IdentityHashMap<>(graph.atomIds());
        renamed.put(replacement, renamed.remove(atom));
        Graph after = new Graph(replacing(atom, replacement), renamed, graph.bondIds(), new LinkedHashMap<>(graph.geometry()));
        return Optional.of(new Result(after, "Change atom " + atomId, Set.of(atomId), List.of(), List.of()));
    }

    /**
     * Adds an atom.
     *
     * @param element the new atom's element symbol
     * @param attachToId the atom to bond it to, or {@code null} for a free atom
     * @param order the order of the bond to {@code attachToId}, or {@code null} for single
     * @param position where to put the atom, in angstroms in the molecule's own frame
     * @return the edit
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom the graph does not hold, {@code LOCKED} if the
     *     atom to attach to is locked, {@code BUSY} if an edit is in progress, {@code INVALID_EDIT} if the element is
     *     not one, and {@code INVALID_MESSAGE} if a free atom has no finite position
     */
    Result addAtom(String element, String attachToId, BondOrder order, Vec3 position)
    {
        requireIdle.run();
        if (element == null || !Elements.isSymbol(element))
        {
            throw new StudioEditException("INVALID_EDIT", "'" + element + "' is not an element symbol");
        }

        Atom target = null;
        if (attachToId != null)
        {
            target = graph.atomNamed(attachToId);
            requireUnlocked(List.of(attachToId));
        }

        Vec3 where;
        if (position != null)
        {
            if (!Double.isFinite(position.x()) || !Double.isFinite(position.y()) || !Double.isFinite(position.z()))
            {
                throw new StudioEditException("INVALID_MESSAGE", "The position of the new atom is not a finite number");
            }
            where = position;
        }
        else if (target != null)
        {
            where = new AtomPlacement(graph.molecule(), graph::positionOf).positionFor(target, element);
        }
        else
        {
            throw new StudioEditException("INVALID_MESSAGE", "A free atom needs a position");
        }

        Draft draft = new Draft();
        Atom fresh = Atom.builder(element).hydrogenCount(0).build();
        String freshId = draft.add(fresh, where);
        List<String> bonds = new ArrayList<>();
        List<String> touched = new ArrayList<>(List.of(freshId));
        if (target != null)
        {
            bonds.add(draft.join(target, fresh, order == null ? BondOrder.SINGLE : order));
            touched.add(attachToId);
        }

        return draft.result("Add atom", touched, List.of(freshId), bonds);
    }

    /**
     * Bonds two atoms. The atoms stay where they are.
     *
     * @param firstId one atom
     * @param secondId the other
     * @param order the bond's order, or {@code null} for single
     * @return the edit
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom the graph does not hold, {@code LOCKED} if either
     *     is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if they are one atom or already bonded
     */
    Result addBond(String firstId, String secondId, BondOrder order)
    {
        requireIdle.run();
        Atom first = graph.atomNamed(firstId);
        Atom second = graph.atomNamed(secondId);
        if (first == second)
        {
            throw new StudioEditException("INVALID_EDIT", "An atom cannot be bonded to itself");
        }
        if (graph.molecule().bondBetween(first, second).isPresent())
        {
            throw new StudioEditException("INVALID_EDIT", "Atoms " + firstId + " and " + secondId + " are already bonded");
        }
        requireUnlocked(List.of(firstId, secondId));

        Draft draft = new Draft();
        String bond = draft.join(first, second, order == null ? BondOrder.SINGLE : order);
        return draft.result("Add bond", List.of(firstId, secondId), List.of(), List.of(bond));
    }

    /**
     * Gives bonds an order.
     *
     * @param bondIds the bonds to change
     * @param order their new order
     * @return the edit, or empty if every bond already has that order
     * @throws StudioEditException with {@code UNKNOWN_ID} for a bond the graph does not hold, {@code LOCKED} if an atom of
     *     a bond that would change is locked, {@code BUSY} if an edit is in progress, {@code INVALID_EDIT} if no bond is named,
     *     and {@code INVALID_MESSAGE} if there is no order
     */
    Optional<Result> setBondOrders(Collection<String> bondIds, BondOrder order)
    {
        requireIdle.run();
        if (order == null)
        {
            throw new StudioEditException("INVALID_MESSAGE", "An order is required");
        }
        if (bondIds.isEmpty())
        {
            throw new StudioEditException("INVALID_EDIT", "No bond was named to change");
        }

        List<Bond> changing = bondIds.stream().distinct().map(graph::bondNamed).filter(bond -> bond.order() != order).toList();
        if (changing.isEmpty())
        {
            return Optional.empty();
        }

        Set<String> ends = endsOf(changing);
        requireUnlocked(ends);

        Draft draft = new Draft();
        changing.forEach(bond -> draft.reorder(draft.jointOf(bond), order));
        return Optional.of(draft.result(changing.size() == 1 ? "Set bond order" : "Set bond orders", ends, List.of(), List.of()));
    }

    /**
     * Deletes atoms and the bonds they are in.
     *
     * @param ids the atoms to delete
     * @param hydrogens whether to also delete each hydrogen that is bonded only to a deleted atom
     * @return the edit
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom the graph does not hold, {@code LOCKED} if any
     *     atom that would go is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if none is named
     */
    Result deleteAtoms(Collection<String> ids, boolean hydrogens)
    {
        requireIdle.run();
        if (ids.isEmpty())
        {
            throw new StudioEditException("INVALID_EDIT", "No atom was named to delete");
        }

        Molecule molecule = graph.molecule();
        Set<Atom> going = IdentitySet.of();
        ids.forEach(id -> going.add(graph.atomNamed(id)));
        if (hydrogens)
        {
            for (Atom atom : List.copyOf(going))
            {
                for (Atom neighbour : molecule.neighboursOf(atom))
                {
                    if (neighbour.element().equals("H") && molecule.bondsOf(neighbour).size() == 1)
                    {
                        going.add(neighbour);
                    }
                }
            }
        }

        List<String> named = going.stream().map(graph.atomIds()::get).sorted().toList();
        requireUnlocked(named);

        Draft draft = new Draft();
        going.forEach(draft::remove);
        return draft.result(going.size() == 1 ? "Delete atom" : "Delete atoms", named, List.of(), List.of());
    }

    /**
     * Deletes bonds.
     *
     * @param ids the bonds to delete
     * @return the edit
     * @throws StudioEditException with {@code UNKNOWN_ID} for a bond the graph does not hold, {@code LOCKED} if an atom
     *     of any of them is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if none is named
     */
    Result deleteBonds(Collection<String> ids)
    {
        requireIdle.run();
        if (ids.isEmpty())
        {
            throw new StudioEditException("INVALID_EDIT", "No bond was named to delete");
        }

        List<Bond> going = ids.stream().distinct().map(graph::bondNamed).toList();
        Set<String> ends = endsOf(going);
        requireUnlocked(ends);

        Draft draft = new Draft();
        going.forEach(bond -> draft.remove(draft.jointOf(bond)));
        return draft.result(going.size() == 1 ? "Delete bond" : "Delete bonds", ends, List.of(), List.of());
    }

    /**
     * Gives atoms the hydrogens that fill their valence.
     *
     * @param ids the atoms to fill
     * @return the edit, or empty if none of the atoms lacks a hydrogen
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom the graph does not hold, {@code LOCKED} if an atom
     *     that would take a hydrogen is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if none is
     *     named or none is an atom whose valence is known
     */
    Optional<Result> fillValences(Collection<String> ids)
    {
        requireIdle.run();
        if (ids.isEmpty())
        {
            throw new StudioEditException("INVALID_EDIT", "No atom was named to fill");
        }

        List<Atom> named = ids.stream().distinct().map(graph::atomNamed).toList();
        List<Atom> known = named.stream().filter(atom -> Valences.areKnownFor(atom.element())).toList();
        if (known.isEmpty())
        {
            throw new StudioEditException("INVALID_EDIT", "None of the atoms is one whose valence is known, which only the organic subset is");
        }

        AtomPlacement placement = new AtomPlacement(graph.molecule(), graph::positionOf);
        Map<Atom, Integer> wanting = new IdentityHashMap<>();
        List<Atom> order = new ArrayList<>();
        for (Atom atom : known)
        {
            int missing = placement.hydrogensMissing(atom);
            if (missing > 0)
            {
                wanting.put(atom, missing);
                order.add(atom);
            }
        }
        if (order.isEmpty())
        {
            return Optional.empty();
        }
        requireUnlocked(order.stream().map(graph.atomIds()::get).toList());

        Draft draft = new Draft();
        List<String> atoms = new ArrayList<>();
        List<String> bonds = new ArrayList<>();
        List<String> touched = new ArrayList<>();
        for (Atom atom : order)
        {
            touched.add(graph.atomIds().get(atom));
            Vec3 centre = graph.positionOf(atom);
            double length = AtomPlacement.bondLength(atom.element(), "H");

            List<Vec3> arms = placement.armsOf(atom);
            for (int each = 0; each < wanting.get(atom); each++)
            {
                Vec3 direction = placement.hydrogenDirection(atom, arms);
                arms.add(direction);
                Atom hydrogen = Atom.builder("H").hydrogenCount(0).build();
                String hydrogenId = draft.add(hydrogen, centre.plus(direction.times(length)));
                atoms.add(hydrogenId);
                touched.add(hydrogenId);
                bonds.add(draft.join(atom, hydrogen, BondOrder.SINGLE));
            }
        }

        return Optional.of(draft.result("Fill valences", touched, atoms, bonds));
    }

    /**
     * @param bonds bonds of the graph
     * @return the names of the atoms at their ends
     */
    private Set<String> endsOf(Collection<Bond> bonds)
    {
        Set<String> ends = new LinkedHashSet<>();
        bonds.forEach(bond ->
        {
            ends.add(graph.atomIds().get(bond.first()));
            ends.add(graph.atomIds().get(bond.second()));
        });
        return ends;
    }

    /**
     * @param atomIds the atoms an edit would change
     * @throws StudioEditException with {@code LOCKED} if any of them is locked
     */
    private void requireUnlocked(Collection<String> atomIds)
    {
        for (String id : atomIds)
        {
            if (locked.contains(id))
            {
                throw new StudioEditException("LOCKED", "Atom " + id + " is locked against edits");
            }
        }
    }

    /**
     * Rebuilds the graph's molecule with one atom swapped for another.
     *
     * @param original an atom of the graph
     * @param replacement the atom to put in its place
     * @return the new molecule
     */
    private Molecule replacing(Atom original, Atom replacement)
    {
        Molecule molecule = graph.molecule();
        Map<Atom, Atom> swapped = new IdentityHashMap<>();
        Molecule.Builder builder = new Molecule.Builder();
        for (Atom atom : molecule.atoms())
        {
            Atom now = atom == original ? replacement : atom;
            swapped.put(atom, now);
            builder.addAtom(now);
        }
        for (Bond bond : molecule.bonds())
        {
            builder.addBond(swapped.get(bond.first()), swapped.get(bond.second()), bond.order());
        }
        for (Atom atom : molecule.atoms())
        {
            molecule.chiralityOf(atom).ifPresent(chirality -> builder.setChirality(swapped.get(atom), chirality));
        }
        for (Bond bond : molecule.bonds())
        {
            molecule.doubleBondStereoOf(bond).ifPresent(stereo -> builder.setDoubleBondStereo(swapped.get(bond.first()), swapped.get(bond.second()), stereo));
        }
        return builder.build();
    }

    /**
     * A copy of the graph being changed, which becomes the graph if the change is made.
     */
    private final class Draft
    {
        private final Molecule source = graph.molecule();
        private final List<Atom> atoms = new ArrayList<>(source.atoms());
        private final Map<Atom, String> ids = new IdentityHashMap<>(graph.atomIds());
        private final Map<String, Vec3> positions = new LinkedHashMap<>(graph.geometry());
        private final Map<Set<String>, String> jointIds = new LinkedHashMap<>(graph.bondIds());
        private final List<Joint> joints = new ArrayList<>();
        private final Set<Atom> disturbed = IdentitySet.of();

        private Draft()
        {
            source.bonds().forEach(bond -> joints.add(new Joint(bond.first(), bond.second(), bond.order(), bond)));
        }

        /**
         * @param atom the atom to add
         * @param where where to put it
         * @return the name it was given, which is never one the session has used
         */
        String add(Atom atom, Vec3 where)
        {
            String id = names.nextAtom();
            atoms.add(atom);
            ids.put(atom, id);
            positions.put(id, where);
            return id;
        }

        /**
         * @param first one atom
         * @param second the other
         * @param order the bond's order
         * @return the name the bond was given, which is never one the session has used
         */
        String join(Atom first, Atom second, BondOrder order)
        {
            String id = names.nextBond();
            joints.add(new Joint(first, second, order, null));
            jointIds.put(Set.of(ids.get(first), ids.get(second)), id);
            disturbed.add(first);
            disturbed.add(second);
            return id;
        }

        /**
         * @param bond a bond of the graph being changed
         * @return what it is in the rebuilt graph
         */
        Joint jointOf(Bond bond)
        {
            return joints.stream().filter(joint -> joint.original() == bond).findFirst().orElseThrow();
        }

        /**
         * @param joint the bond to give a different order
         * @param order its new order
         */
        void reorder(Joint joint, BondOrder order)
        {
            joints.replaceAll(each -> each == joint ? new Joint(joint.first(), joint.second(), order, joint.original()) : each);
        }

        /**
         * @param joint the bond to take away
         */
        void remove(Joint joint)
        {
            joints.removeIf(each -> each == joint);
            jointIds.remove(Set.of(ids.get(joint.first()), ids.get(joint.second())));
            disturbed.add(joint.first());
            disturbed.add(joint.second());
        }

        /**
         * @param atom the atom to take away, with its bonds
         */
        void remove(Atom atom)
        {
            for (Joint joint : List.copyOf(joints))
            {
                if (joint.first() == atom || joint.second() == atom)
                {
                    remove(joint);
                }
            }
            atoms.removeIf(each -> each == atom);
            positions.remove(ids.remove(atom));
        }

        /**
         * @param label what the edit is called in the history
         * @param touched the atoms the edit changed
         * @param added the atoms it added, by name
         * @param addedBonds the bonds it added, by name
         * @return the edit that makes these changes
         */
        Result result(String label, Collection<String> touched, List<String> added, List<String> addedBonds)
        {
            return new Result(new Graph(build(), ids, jointIds, positions), label, new LinkedHashSet<>(touched), added, addedBonds);
        }

        /**
         * @return the molecule these changes make
         */
        private Molecule build()
        {
            Molecule.Builder builder = new Molecule.Builder();
            atoms.forEach(builder::addAtom);
            joints.forEach(joint -> builder.addBond(joint.first(), joint.second(), joint.order()));

            for (Atom atom : atoms)
            {
                if (!disturbed.contains(atom) && source.containsAtom(atom))
                {
                    source.chiralityOf(atom).ifPresent(chirality -> builder.setChirality(atom, chirality));
                }
            }
            for (Joint joint : joints)
            {
                Bond was = joint.original();
                if (was != null && was.order() == BondOrder.DOUBLE && joint.order() == BondOrder.DOUBLE
                        && !disturbed.contains(joint.first()) && !disturbed.contains(joint.second()))
                {
                    source.doubleBondStereoOf(was).ifPresent(stereo -> builder.setDoubleBondStereo(joint.first(), joint.second(), stereo));
                }
            }
            return builder.build();
        }
    }
}
