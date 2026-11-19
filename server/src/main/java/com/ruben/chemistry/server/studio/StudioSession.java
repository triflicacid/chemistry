package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Hydrogens;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.server.molecule.Camera;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.server.studio.EditHistory.Edit;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.space.BondMotion;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One open Molecular Studio workspace.
 * <p>
 * The session holds the state of the workspace and the lock that guards it. Working out a change of the graph is
 * {@link GraphEditor}'s, keeping the edits is {@link EditHistory}'s, and the session makes the change and files it.
 */
public final class StudioSession
{
    public static final int DEFAULT_CONFORMERS = 1;
    public static final int MAX_CONFORMERS = 20;
    public static final int MAX_UNDO = 200;
    private static final double SNAP_DISTANCE = 1e-9;
    private static final double MOVE_RESOLUTION = 1e-6;

    /**
     * One atom to put somewhere.
     *
     * @param atomId the atom to move
     * @param position where to put it, in angstroms
     */
    public record Move(String atomId, Vec3 position)
    {
    }

    /**
     * What an undo or redo did.
     *
     * @param version the version after it
     * @param moved where each atom it touched is now
     * @param pose where the molecule now sits, or {@code null} if the step did not move it as a whole
     * @param graph whether the step changed the graph
     */
    public record Applied(long version, Map<String, Vec3> moved, Pose pose, boolean graph)
    {
        /**
         * @param version the version after the step
         * @param moved where each atom it touched is now
         * @param pose where the molecule now sits, or {@code null} if the step did not move it as a whole
         */
        public Applied(long version, Map<String, Vec3> moved, Pose pose)
        {
            this(version, moved, pose, false);
        }
    }

    /**
     * What an edit that added atoms or bonds made.
     *
     * @param applied the edit, as for any other
     * @param atoms the atoms it added, by name
     * @param bonds the bonds it added, by name
     */
    public record Created(Applied applied, List<String> atoms, List<String> bonds)
    {
    }

    /**
     * The atoms' positions at one version, which a calculation can be made from without holding the session.
     *
     * @param version the version of the session the positions are of
     * @param positions where each atom is, in angstroms in the molecule's own frame, in the order of {@link #molecule()}'s atoms
     */
    public record Snapshot(long version, List<Vec3> positions)
    {
    }

    /**
     * What an optimisation is made from, taken at one version so that it can run without holding the session.
     *
     * @param version the version of the session this describes
     * @param structure the working geometry as the structure that saving stores, whose sites are numbered as {@code sites}
     * @param lockedSites the sites of {@code structure} that are locked, which the optimisation must leave where they are
     * @param sites the atom each site of {@code structure} is, in site order
     */
    public record OptimisationInput(long version, Structure3D structure, Set<Integer> lockedSites, List<String> sites)
    {
    }

    /**
     * One edit as the history lists it.
     *
     * @param label what the edit did, in a few words
     * @param at when the edit was made
     */
    public record HistoryEntry(String label, long at)
    {
    }

    /**
     * The session's history of edits.
     *
     * @param entries the edits, the earliest first, counting the ones that have been undone
     * @param position how many of the edits are in effect; the ones after it have been undone and can be made again
     */
    public record History(List<HistoryEntry> entries, int position)
    {
    }

    private final String sessionId;
    private final String sourceMoleculeId;
    private volatile Molecule molecule;
    private volatile Map<Atom, String> atomIds;
    private volatile Map<Set<String>, String> bondIds;
    private final Map<String, Vec3> geometry;
    private final Set<String> wasImplicit;
    private final double strain;

    /** Atoms and bonds that refuse an edit. */
    private final Set<String> locked = new LinkedHashSet<>();

    /** The molecule this session last saved, or null if it has saved none. */
    private String savedMoleculeId;

    /** The graph as saving stores it, or null until saving first asks for it and after the graph changes. */
    private StructureExport export;

    private final EditHistory edits = new EditHistory(MAX_UNDO);

    /** What the history puts an edit's changes into. */
    private final EditHistory.Target target = new EditHistory.Target()
    {
        @Override
        public void putPositions(Map<String, Vec3> positions)
        {
            geometry.putAll(positions);
        }

        @Override
        public void setPose(Pose to)
        {
            drag.setPose(to);
        }

        @Override
        public void restoreGraph(Graph graph)
        {
            restore(graph);
        }

        @Override
        public void requireUnlocked(Collection<String> atomIds)
        {
            StudioSession.this.requireUnlocked(atomIds);
        }
    };

    /** Where the atoms are, where the molecule sits, and the edit of them that is in progress. */
    private final Drag drag;

    /** Where the client's camera is, or null if it has never said. It is not part of the version, and is never undone. */
    private Camera camera;

    /** Where the names of atoms and bonds come from, so that a name is never used twice. */
    private final Names names;

    private long version = 1;
    private String smiles;
    private long smilesVersion;

    /**
     * @param sessionId what to call the session
     * @param sourceMoleculeId the stored molecule it is said to have come from
     * @param graph the graph to start with, which the session takes over
     * @param wasImplicit the atoms that are hydrogens the source molecule counted rather than wrote
     * @param strain the strain of the shape the session starts with
     * @param names where the names of atoms and bonds come from
     * @param locked the atoms to start with locked
     * @param pose where the molecule sits in the scene
     * @param camera where the client's camera is, or {@code null} if it has never said
     * @param version the version to start at
     */
    StudioSession(
            String sessionId,
            String sourceMoleculeId,
            Graph graph,
            Set<String> wasImplicit,
            double strain,
            Names names,
            Set<String> locked,
            Pose pose,
            Camera camera,
            long version)
    {
        this.sessionId = sessionId;
        this.sourceMoleculeId = sourceMoleculeId;
        this.molecule = graph.molecule();
        this.atomIds = graph.atomIds();
        this.bondIds = graph.bondIds();
        this.geometry = graph.geometry();
        this.wasImplicit = wasImplicit;
        this.strain = strain;
        this.names = names;
        this.locked.addAll(locked);
        this.drag = new Drag(geometry, this.locked, pose);
        this.camera = camera;
        this.version = version;
    }

    /**
     * Opens a workspace on a stored molecule.
     *
     * @param sessionId what to call the session
     * @param source the molecule to open
     * @param conformers how many conformers to embed if the molecule has no shape yet
     * @return the open session, at version 1
     * @throws IllegalArgumentException if {@code conformers} is outside 1 to {@link #MAX_CONFORMERS}
     * @throws IllegalStateException if no shape could be embedded, or the shape found does not describe the molecule's atoms one for one
     */
    public static StudioSession open(String sessionId, StoredMolecule source, int conformers)
    {
        return SessionFactory.open(sessionId, source, conformers);
    }

    /**
     * Makes a session of the same name on a different molecule.
     *
     * @param molecule the molecule to rebuild on, whose hydrogens may still be counts
     * @param conformers how many conformers to embed
     * @return the new session, one version on from this one, with this session's name and source
     * @throws IllegalArgumentException if {@code conformers} is outside 1 to {@link #MAX_CONFORMERS}
     * @throws IllegalStateException if no shape could be embedded, or the shape found does not describe the molecule's atoms one for one
     */
    public StudioSession rebuilt(Molecule molecule, int conformers)
    {
        return SessionFactory.rebuilt(this, molecule, conformers);
    }

    /**
     * Puts atoms where they are asked to go.
     *
     * @param moves the atoms to move; a later move of an atom overrides an earlier one
     * @param commit whether this ends the edit it belongs to
     * @return the version after the patch, which is the one before it if nothing was moved
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold,
     *     {@code LOCKED} for one that is locked, and {@code INVALID_MESSAGE} for a position that is not finite
     */
    public synchronized long move(List<Move> moves, boolean commit)
    {
        boolean moved = drag.move(moves);
        return finish(moved, commit);
    }

    /**
     * Puts the molecule somewhere in the scene, turning and moving it as one rigid body.
     *
     * @param to where to put it, as the full pose and not a change to the current one
     * @param commit whether this ends the edit it belongs to
     * @return the version after the change, which is the one before it if the molecule was already there
     * @throws StudioEditException with {@code INVALID_MESSAGE} if the pose is not finite or its rotation has no length
     */
    public synchronized long transform(Pose to, boolean commit)
    {
        boolean moved = drag.transform(to);
        return finish(moved, commit);
    }

    /**
     * Ends a change of positions: commits the drag if the change ends it, and counts the change as a version.
     *
     * @param moved whether the change moved anything
     * @param commit whether the change ends the edit it belongs to
     * @return the version after the change
     */
    private long finish(boolean moved, boolean commit)
    {
        if (commit)
        {
            drag.commit().ifPresent(edits::file);
        }

        if (moved)
        {
            version++;
        }

        return version;
    }

    /**
     * Locks or unlocks atoms.
     *
     * @param atomIds the atoms to lock or unlock
     * @param lock {@code true} to lock them, {@code false} to unlock them
     * @return the version after the change, which is the one before it if no atom changed state
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold
     */
    public synchronized long lock(Collection<String> atomIds, boolean lock)
    {
        for (String id : atomIds)
        {
            if (!geometry.containsKey(id))
            {
                throw new StudioEditException("UNKNOWN_ID", "This session holds no atom '" + id + "'");
            }
        }

        boolean changed = false;
        for (String id : atomIds)
        {
            changed |= lock ? locked.add(id) : locked.remove(id);
        }

        if (changed)
        {
            version++;
        }

        return version;
    }

    /**
     * Takes what an optimisation of the geometry as it is now needs.
     *
     * @return the geometry, what is locked in it, and the version it is at
     * @throws StudioEditException with {@code BUSY} if an edit is in progress
     */
    public synchronized OptimisationInput beginOptimisation()
    {
        if (editing())
        {
            throw new StudioEditException("BUSY", "An edit is still in progress, so the structure cannot be optimised yet");
        }

        StructureExport stored = export();
        return new OptimisationInput(version, stored.structure(geometry), Set.copyOf(stored.lockedSites(locked)), List.copyOf(stored.sites()));
    }

    /**
     * Puts the atoms where an optimisation of an earlier version left them.
     *
     * @param fromVersion the version the optimisation was made from
     * @param positions where the optimisation put each atom, by atom
     * @return what changed and the version after it
     */
    public synchronized Optional<Applied> optimised(long fromVersion, Map<String, Vec3> positions)
    {
        if (version != fromVersion)
        {
            return Optional.empty();
        }

        List<Move> moves = new ArrayList<>();
        positions.forEach((id, position) ->
        {
            Vec3 from = geometry.get(id);
            if (from != null && !locked.contains(id) && from.minus(position).length() > MOVE_RESOLUTION)
            {
                moves.add(new Move(id, position));
            }
        });

        if (moves.isEmpty())
        {
            return Optional.of(new Applied(version, Map.of(), null));
        }

        drag.label("Optimise");
        long next = move(moves, true);
        Map<String, Vec3> moved = new LinkedHashMap<>();
        moves.forEach(move -> moved.put(move.atomId(), move.position()));
        return Optional.of(new Applied(next, moved, null));
    }

    /**
     * Gives a bond a length.
     *
     * @param bondId the bond to change
     * @param length the length to give it, in angstroms
     * @param heldId the atom of the bond to leave where it is, or {@code null} to hold neither
     * @param commit whether this ends the edit it belongs to
     * @return what moved and the version after it, which is the version the session was at if nothing moved
     * @throws StudioEditException with {@code UNKNOWN_ID} for a bond or atom this session does not hold,
     *     {@code INVALID_EDIT} if the length is not allowed or the held atom is not on the bond, and {@code LOCKED} if
     *     an atom that would move is locked
     */
    public synchronized Applied setBondLength(String bondId, double length, String heldId, boolean commit)
    {
        Graph graph = view();
        Bond bond = graph.bondNamed(bondId);
        Atom held = heldId == null ? null : graph.atomNamed(heldId);
        return reshape(() -> BondMotion.withLength(molecule, bond, this::baselineOf, length, held), commit, "Set bond length");
    }

    /**
     * Gives a bond a torsion.
     *
     * @param bondId the bond to turn about
     * @param fromId an atom bonded to the bond's first atom, which the torsion is measured from
     * @param toId an atom bonded to the bond's second atom, which the torsion is measured to
     * @param degrees the torsion to give it, in degrees
     * @param heldId the atom of the bond whose side stays where it is, or {@code null} to hold neither
     * @param commit whether this ends the edit it belongs to
     * @return what moved and the version after it, which is the version the session was at if nothing moved
     * @throws StudioEditException with {@code UNKNOWN_ID} for a bond or atom this session does not hold,
     *     {@code INVALID_EDIT} if the bond is in a ring, the atoms measured between are not bonded one to each end, or
     *     the held atom is not on the bond, and {@code LOCKED} if an atom that would move is locked
     */
    public synchronized Applied setBondTorsion(String bondId, String fromId, String toId, double degrees, String heldId, boolean commit)
    {
        Graph graph = view();
        Bond bond = graph.bondNamed(bondId);
        Atom from = graph.atomNamed(fromId);
        Atom to = graph.atomNamed(toId);
        Atom held = heldId == null ? null : graph.atomNamed(heldId);
        return reshape(() -> BondMotion.withTorsion(molecule, bond, from, to, this::baselineOf, degrees, held), commit, "Set torsion");
    }

    /**
     * Gives an atom's two bonds an angle.
     *
     * @param centreId the atom the angle is at
     * @param fromId one atom bonded to it
     * @param toId another atom bonded to it
     * @param degrees the angle to give it, in degrees
     * @param heldId {@code fromId} or {@code toId}, whose side stays where it is, or {@code null} to hold neither
     * @param commit whether this ends the edit it belongs to
     * @return what moved and the version after it, which is the version the session was at if nothing moved
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold,
     *     {@code INVALID_EDIT} if the angle is not allowed, the atoms are not bonded as the angle needs, or a bond that
     *     would have to turn is in a ring, and {@code LOCKED} if an atom that would move is locked
     */
    public synchronized Applied setAngle(String centreId, String fromId, String toId, double degrees, String heldId, boolean commit)
    {
        Graph graph = view();
        Atom centre = graph.atomNamed(centreId);
        Atom from = graph.atomNamed(fromId);
        Atom to = graph.atomNamed(toId);
        Atom held = heldId == null ? null : graph.atomNamed(heldId);
        return reshape(() -> BondMotion.withAngle(molecule, centre, from, to, this::baselineOf, degrees, held), commit, "Set angle");
    }

    /**
     * Changes an atom's non-geometric properties.
     *
     * @param atomId the atom to change
     * @param element its new element symbol, or {@code null} to leave it as it is
     * @param charge its new formal charge, where 0 is no charge, or {@code null} to leave it as it is
     * @param isotope its new mass number, where 0 is natural abundance, or {@code null} to leave it as it is
     * @return what changed and the version after it
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold, {@code LOCKED} if it
     *     is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if the element is not one, the
     *     isotope is negative, or nothing was asked for
     */
    public synchronized Applied setAtom(String atomId, String element, Integer charge, Integer isotope)
    {
        return commit(editor().setAtom(atomId, element, charge, isotope));
    }

    /**
     * Adds an atom.
     *
     * @param element the new atom's element symbol
     * @param attachToId the atom to bond it to, or {@code null} for a free atom
     * @param order the order of the bond to {@code attachToId}, or {@code null} for single
     * @param position where to put the atom, in angstroms in the molecule's own frame
     * @return the edit, and the names of the atom and bond it made
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold, {@code LOCKED} if the
     *     atom to attach to is locked, {@code BUSY} if an edit is in progress, {@code INVALID_EDIT} if the element is
     *     not one, and {@code INVALID_MESSAGE} if a free atom has no finite position
     */
    public synchronized Created addAtom(String element, String attachToId, BondOrder order, Vec3 position)
    {
        return created(editor().addAtom(element, attachToId, order, position));
    }

    /**
     * Bonds two atoms. The atoms stay where they are.
     *
     * @param firstId one atom
     * @param secondId the other
     * @param order the bond's order, or {@code null} for single
     * @return the edit, and the name of the bond it made
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold, {@code LOCKED} if either
     *     is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if they are one atom or already bonded
     */
    public synchronized Created addBond(String firstId, String secondId, BondOrder order)
    {
        return created(editor().addBond(firstId, secondId, order));
    }

    /**
     * Gives a bond an order.
     *
     * @param bondId the bond to change
     * @param order its new order
     * @return what changed and the version after it
     * @throws StudioEditException as {@link #setBondOrders}
     */
    public synchronized Applied setBondOrder(String bondId, BondOrder order)
    {
        return setBondOrders(List.of(bondId), order);
    }

    /**
     * Gives bonds an order.
     *
     * @param bondIds the bonds to change
     * @param order their new order
     * @return what changed and the version after it
     * @throws StudioEditException with {@code UNKNOWN_ID} for a bond this session does not hold, {@code LOCKED} if an atom of
     *     a bond that would change is locked, {@code BUSY} if an edit is in progress, {@code INVALID_EDIT} if no bond is named,
     *     and {@code INVALID_MESSAGE} if there is no order
     */
    public synchronized Applied setBondOrders(Collection<String> bondIds, BondOrder order)
    {
        return commit(editor().setBondOrders(bondIds, order));
    }

    /**
     * Deletes atoms and the bonds they are in.
     *
     * @param ids the atoms to delete
     * @param hydrogens whether to also delete each hydrogen that is bonded only to a deleted atom
     * @return what changed and the version after it
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold, {@code LOCKED} if any
     *     atom that would go is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if none is named
     */
    public synchronized Applied deleteAtoms(Collection<String> ids, boolean hydrogens)
    {
        return commit(editor().deleteAtoms(ids, hydrogens));
    }

    /**
     * Deletes multiple bonds.
     *
     * @param ids the bonds to delete
     * @return what changed and the version after it
     * @throws StudioEditException with {@code UNKNOWN_ID} for a bond this session does not hold, {@code LOCKED} if an atom
     *     of any of them is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if none is named
     */
    public synchronized Applied deleteBonds(Collection<String> ids)
    {
        return commit(editor().deleteBonds(ids));
    }

    /**
     * Gives atoms the hydrogens that fill their valence.
     *
     * @param ids the atoms to fill
     * @return the edit
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold, {@code LOCKED} if an atom
     *     that would take a hydrogen is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if none is
     *     named or none is an atom whose valence is known
     */
    public synchronized Created fillValences(Collection<String> ids)
    {
        return created(editor().fillValences(ids));
    }

    /**
     * @return an editor of the graph as it is now
     */
    private GraphEditor editor()
    {
        return new GraphEditor(view(), locked, names, this::requireIdle);
    }

    /**
     * @return the graph as it is, which changes as the session does
     */
    private Graph view()
    {
        return new Graph(molecule, atomIds, bondIds, geometry);
    }

    /**
     * @return whether an edit of positions is in progress
     */
    private boolean editing()
    {
        return drag.active();
    }

    /**
     * @throws StudioEditException with {@code BUSY} if an edit of positions is in progress
     */
    private void requireIdle()
    {
        if (editing())
        {
            throw new StudioEditException("BUSY", "An edit is still in progress, so the structure cannot be changed yet");
        }
    }

    /**
     * @param edit an edit of the graph, or empty if it would change nothing
     * @return what the edit did, which is nothing if it was empty
     */
    private Applied commit(Optional<GraphEditor.Result> edit)
    {
        return edit.map(this::commit).orElseGet(() -> new Applied(version, Map.of(), null));
    }

    /**
     * @param edit an edit of the graph, or empty if it would change nothing
     * @return what the edit made, which is nothing if it was empty
     */
    private Created created(Optional<GraphEditor.Result> edit)
    {
        return edit.map(this::created).orElseGet(() -> new Created(new Applied(version, Map.of(), null), List.of(), List.of()));
    }

    /**
     * @param edit an edit of the graph
     * @return what the edit made
     */
    private Created created(GraphEditor.Result edit)
    {
        return new Created(commit(edit), edit.atoms(), edit.bonds());
    }

    /**
     * Makes the graph an edit worked out the working one and files the change as one edit.
     *
     * @param edit the edit
     * @return the edit, which changed the graph
     */
    private Applied commit(GraphEditor.Result edit)
    {
        Graph before = graph();
        restore(edit.after());
        edits.file(new Edit(Map.of(), Map.of(), null, null, before, graph(), edit.touched(), edit.label(), System.currentTimeMillis()));
        return new Applied(++version, Map.of(), null, true);
    }

    /**
     * Makes the moves a bond edit works out.
     * <p>
     * An edit is worked out from where the atoms were before the edit in progress began, not from where its earlier
     * changes have put them. Each change of a drag therefore says where the whole drag has got to and does not depend on
     * the one before, a length can be taken through zero and an angle through a straight line, and a drag that comes
     * back to where it began ends exactly there and files nothing. Atoms an earlier change of the drag moved that this one
     * does not are put back.
     *
     * @param plan works out where atoms go from {@link #baselineOf}, refusing by throwing
     *     {@link IllegalArgumentException}
     * @param commit whether this ends the edit it belongs to. A commit that moves nothing, because the edit before it
     *     in the same drag already put the atoms there, still ends that edit
     * @param label what the edit is called in the history
     * @return what moved, which is nothing if every atom was already where it was sent
     * @throws StudioEditException with {@code INVALID_EDIT} if the plan refused, and {@code LOCKED} if an atom that
     *     would move is locked
     */
    private Applied reshape(Supplier<Map<Atom, Vec3>> plan, boolean commit, String label)
    {
        Map<Atom, Vec3> placed;
        try
        {
            placed = plan.get();
        }
        catch (IllegalArgumentException refused)
        {
            throw new StudioEditException("INVALID_EDIT", refused.getMessage());
        }

        Graph graph = view();
        List<Move> moves = new ArrayList<>();
        for (Atom atom : molecule.atoms())
        {
            Vec3 baseline = baselineOf(atom);
            Vec3 to = placed.get(atom);
            if (to == null && drag.hasMoved(atomIds.get(atom)))
            {
                to = baseline;
            }
            else if (to != null && to.distanceTo(baseline) < SNAP_DISTANCE)
            {
                // arithmetic leaves a trace of error, and an atom that has not moved must be exactly where it was
                to = baseline;
            }

            if (to != null && !to.equals(graph.positionOf(atom)))
            {
                moves.add(new Move(atomIds.get(atom), to));
            }
        }
        drag.label(label);
        long next = move(moves, commit);
        Map<String, Vec3> moved = new LinkedHashMap<>();
        moves.forEach(move -> moved.put(move.atomId(), move.position()));
        return new Applied(next, moved, null);
    }

    /**
     * @param atom an atom of this session's molecule, by reference
     * @return where it was before the edit in progress began, or where it is if the edit has not moved it
     */
    private Vec3 baselineOf(Atom atom)
    {
        return drag.baselineOf(atomIds.get(atom));
    }

    /**
     * @param atomIds the atoms an edit would move
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
     * Takes back the latest committed edit. An edit still in progress is cancelled instead.
     *
     * @return the atoms it moved and the version after it
     * @throws StudioEditException with {@code NOTHING_TO_UNDO} if there is nothing to take back, and
     *     {@code LOCKED} if an atom it would move has been locked since, in which case the edit stays where it is
     */
    public synchronized Applied undo()
    {
        if (editing())
        {
            return cancelEdit();
        }

        Edit edit = edits.undo(target);
        return new Applied(++version, edit.before(), edit.poseBefore(), edit.graphBefore() != null);
    }

    /**
     * Makes again the edit most recently undone.
     *
     * @return the atoms it moved and the version after it
     * @throws StudioEditException with {@code NOTHING_TO_REDO} if no undone edit is waiting, and
     *     {@code LOCKED} if an atom it would move has been locked since, in which case the edit stays where it is
     */
    public synchronized Applied redo()
    {
        Edit edit = edits.redo(target);
        return new Applied(++version, edit.after(), edit.poseAfter(), edit.graphAfter() != null);
    }

    /**
     * Puts back what the edit in progress has moved, and forgets it.
     *
     * @return the atoms it moved and the version after it
     * @throws StudioEditException with {@code LOCKED} if an atom it moved has been locked since
     */
    private Applied cancelEdit()
    {
        Drag.Cancelled cancelled = drag.cancel();
        return new Applied(++version, cancelled.moved(), cancelled.pose());
    }

    /**
     * @return what each edit did and how many of them are in effect
     */
    public synchronized History history()
    {
        List<HistoryEntry> entries = edits.all().stream().map(edit -> new HistoryEntry(edit.label(), edit.at())).toList();
        return new History(entries, edits.position());
    }

    /**
     * Forgets every edit, done or undone, leaving the atoms where they are. There is then nothing to undo or redo.
     *
     * @return the version after it, which moved no atom
     */
    public synchronized Applied clearHistory()
    {
        edits.clear();
        return new Applied(++version, Map.of(), null);
    }

    /**
     * Undoes or redoes as many edits as it takes to have exactly {@code position} of them in effect.
     *
     * @param position how many edits to have in effect, from 0 to the length of {@link #history()}
     * @param discard whether to forget the edits after {@code position}, which can then not be made again
     * @return where the atoms the steps touched now are
     * @throws StudioEditException with {@code INVALID_EDIT} if there is no such position, and {@code LOCKED} if an
     *     atom a step would move has been locked, in which case nothing is changed
     */
    public synchronized Applied rewind(int position, boolean discard)
    {
        edits.validate(position, target);

        Map<String, Vec3> moved = new LinkedHashMap<>();
        Pose posed = null;
        if (editing())
        {
            Applied cancelled = cancelEdit();
            moved.putAll(cancelled.moved());
            posed = cancelled.pose();
        }

        EditHistory.Rewound rewound = edits.rewind(position, discard, target);
        version += rewound.steps();
        moved.putAll(rewound.moved());
        posed = rewound.pose() != null ? rewound.pose() : posed;

        moved.replaceAll((id, ignored) -> geometry.get(id));
        return new Applied(version, moved, posed == null ? null : drag.pose(), rewound.graph());
    }

    /**
     * @return the working graph with its hydrogens taken back into counts, which is how it is stored
     */
    public synchronized Molecule collapsed()
    {
        return export().collapsed();
    }

    /**
     * Describes the working geometry as the structure of {@link #collapsed()}, which is what saving stores.
     *
     * @return the structure, whose positions are the working positions, not recentred
     */
    public synchronized Structure3D structure()
    {
        return export().structure(geometry);
    }

    /**
     * Says which atoms are locked in the terms a stored structure uses, which survive the session's own identifiers
     * being issued afresh.
     *
     * @return the position in {@link #structure()} of each locked atom
     */
    public synchronized Set<Integer> lockedSites()
    {
        return export().lockedSites(locked);
    }

    /**
     * @return the graph as saving stores it, worked out the first time it is needed after the graph changes
     */
    private StructureExport export()
    {
        if (export == null)
        {
            export = StructureExport.of(view(), strain);
        }
        return export;
    }

    /**
     * Notes the stored molecule that holds this session's work.
     *
     * @param moleculeId the stored molecule
     */
    public synchronized void saved(String moleculeId)
    {
        this.savedMoleculeId = moleculeId;
    }

    /**
     * @return whether {@link #undo()} has anything to take back
     */
    public synchronized boolean canUndo()
    {
        return edits.canUndo() || editing();
    }

    /**
     * @return whether {@link #redo()} has anything to make again
     */
    public synchronized boolean canRedo()
    {
        return edits.canRedo();
    }

    /**
     * @return the graph as it is now, copied so that it keeps its state as the session changes
     */
    private Graph graph()
    {
        return new Graph(molecule, new IdentityHashMap<>(atomIds), new LinkedHashMap<>(bondIds), new LinkedHashMap<>(geometry));
    }

    /**
     * Makes a graph the working one, and forgets what was worked out from the one it replaces.
     *
     * @param graph the graph to restore
     */
    private void restore(Graph graph)
    {
        molecule = graph.molecule();
        atomIds = new IdentityHashMap<>(graph.atomIds());
        bondIds = new LinkedHashMap<>(graph.bondIds());
        geometry.clear();
        geometry.putAll(graph.geometry());
        // a lock on an atom that is gone has nothing left to refuse
        locked.retainAll(geometry.keySet());
        export = null;
    }

    /**
     * @return what this session is called, for the lifetime of the session only
     */
    public String sessionId()
    {
        return sessionId;
    }

    /**
     * @return the ID of the stored molecule this session was opened from
     */
    public String sourceMoleculeId()
    {
        return sourceMoleculeId;
    }

    /**
     * @return the ID of the molecule this session last saved, or {@code null} if it has saved none
     */
    public String savedMoleculeId()
    {
        return savedMoleculeId;
    }

    /**
     * @return the working graph, whose hydrogens are all atoms
     */
    public Molecule molecule()
    {
        return molecule;
    }

    /**
     * @return the atoms' positions as they are now, with the version they are at
     */
    public synchronized Snapshot snapshot()
    {
        return new Snapshot(version, molecule.atoms().stream().map(atom -> geometry.get(atomIds.get(atom))).toList());
    }

    /**
     * @return how many mutations this session has applied, counting from 1
     */
    public synchronized long version()
    {
        return version;
    }

    /**
     * @return where the molecule sits in the scene
     */
    public synchronized Pose pose()
    {
        return drag.pose();
    }

    /**
     * @return where the client's camera is, or empty if it has never said
     */
    public synchronized Optional<Camera> camera()
    {
        return Optional.ofNullable(camera);
    }

    /**
     * @param camera where the camera is and how it looks
     */
    public synchronized void setCamera(Camera camera)
    {
        this.camera = camera;
    }

    /**
     * @return the strain of the shape this session was opened with
     */
    public double strain()
    {
        return strain;
    }

    /**
     * @return the SMILES of the working graph, in its own atom order, with its hydrogens taken
     *     back into counts
     */
    public String smiles()
    {
        if (smiles == null || smilesVersion != version)
        {
            smiles = SmilesWriter.write(Hydrogens.asCounts(molecule));
            smilesVersion = version;
        }

        return smiles;
    }

    /**
     * @param atom an atom of this session's molecule, by reference
     * @return what this session calls it
     * @throws IllegalArgumentException if the atom is not part of this session
     */
    public String atomId(Atom atom)
    {
        String id = atomIds.get(atom);
        if (id == null)
        {
            throw new IllegalArgumentException("The atom is not part of this session: " + atom);
        }

        return id;
    }

    /**
     * @param bond a bond of this session's molecule
     * @return what this session calls it
     * @throws IllegalArgumentException if either of its atoms is not part of this session
     */
    public String bondId(Bond bond)
    {
        String id = bondIds.get(Graph.endpoints(atomIds, bond));
        if (id == null)
        {
            throw new IllegalArgumentException("The bond is not part of this session: " + bond);
        }

        return id;
    }

    /**
     * @param atomId an atom of this session
     * @return where it is, in angstroms
     * @throws IllegalArgumentException if this session holds no such atom
     */
    public Vec3 positionOf(String atomId)
    {
        Vec3 position = geometry.get(atomId);
        if (position == null)
        {
            throw new IllegalArgumentException("This session holds no atom '" + atomId + "'");
        }

        return position;
    }

    /**
     * @param atomId an atom of this session
     * @return whether it is a hydrogen the source molecule counted rather than wrote
     */
    public boolean wasImplicit(String atomId)
    {
        return wasImplicit.contains(atomId);
    }

    /**
     * @return the atoms and bonds that refuse an edit
     */
    public Set<String> locked()
    {
        return Collections.unmodifiableSet(locked);
    }
}
