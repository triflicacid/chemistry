package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Hydrogens;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.space.BondMotion;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;
import com.ruben.chemistry.util.IdentitySet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
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
 */
public final class StudioSession
{
    /** How many conformers to embed when none is asked for. */
    public static final int DEFAULT_CONFORMERS = 1;

    /** The most conformers one message may ask to embed. */
    public static final int MAX_CONFORMERS = 20;

    /** The most committed edits kept to undo. */
    public static final int MAX_UNDO = 200;

    /** How far, in angstroms, an atom must be moved by a computed edit, such as an optimisation, for the move to count. */
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
     */
    public record Applied(long version, Map<String, Vec3> moved, Pose pose)
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
     * One committed edit.
     *
     * @param before where each atom the edit touched was
     * @param after where each of them is after it
     * @param poseBefore where the molecule sat before the edit, or {@code null} if the edit did not move it as a whole
     * @param poseAfter where it sat after the edit, or {@code null} under the same condition
     */
    private record Edit(Map<String, Vec3> before, Map<String, Vec3> after, Pose poseBefore, Pose poseAfter)
    {
    }

    private final String sessionId;
    private final String sourceMoleculeId;
    private final Molecule molecule;
    private final Map<Atom, String> atomIds;
    private final Map<Set<String>, String> bondIds;
    private final Map<String, Vec3> geometry;
    private final Set<String> wasImplicit;
    private final double strain;

    /** Atoms and bonds that refuse an edit. */
    private final Set<String> locked = new LinkedHashSet<>();

    /** The molecule this session last saved, or null if it has saved none. */
    private String savedMoleculeId;

    /** The working graph with its hydrogens counted, or null until saving first asks for it. */
    private Molecule collapsed;

    /** The structure of {@link #collapsed}, whose conformer is a placeholder, or null until saving first asks for it. */
    private Structure3D structureShape;

    /** The atom each node of {@link #collapsed}'s structure is, in node order. */
    private List<String> nodeAtoms;

    /** Committed edits, the latest last. */
    private final Deque<Edit> done = new ArrayDeque<>();

    /** Edits undone and not yet overtaken by a new one, the next to redo last. */
    private final Deque<Edit> undone = new ArrayDeque<>();

    /** Where each atom was before the uncommitted moves that have touched it, so a commit can say what it replaced. */
    private final Map<String, Vec3> uncommitted = new LinkedHashMap<>();

    /** Where the molecule sat before the uncommitted moves of it as a whole, or null if there have been none. */
    private Pose uncommittedPose;

    /** Where the molecule sits in the scene; its atoms' coordinates are in the molecule's own frame, not this one. */
    private Pose pose = Pose.IDENTITY;

    private long version = 1;
    private String smiles;
    private long smilesVersion;

    private StudioSession(
            String sessionId,
            String sourceMoleculeId,
            Molecule molecule,
            Map<Atom, String> atomIds,
            Map<Set<String>, String> bondIds,
            Map<String, Vec3> geometry,
            Set<String> wasImplicit,
            double strain)
    {
        this.sessionId = sessionId;
        this.sourceMoleculeId = sourceMoleculeId;
        this.molecule = molecule;
        this.atomIds = atomIds;
        this.bondIds = bondIds;
        this.geometry = geometry;
        this.wasImplicit = wasImplicit;
        this.strain = strain;
    }

    /**
     * Opens a workspace on a stored molecule, making its counted hydrogens atoms and seeding the
     * working geometry from the molecule's lowest-strain shape.
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
        if (conformers < 1 || conformers > MAX_CONFORMERS)
        {
            throw new IllegalArgumentException("conformers must be 1 to " + MAX_CONFORMERS + ", but was " + conformers);
        }

        Structure3D structure = source.latestStructure3d().orElse(null);
        if (structure == null)
        {
            // kept on the stored molecule, so the viewer and a second session do not embed it again
            structure = Embedder.embed(source.molecule(), Embedder.Options.DEFAULT.withConformers(conformers));
            source.setLatestStructure3d(structure);
        }

        StudioSession session = build(sessionId, source.id(), source.molecule(), structure, source.lockedSites());
        session.pose = source.pose();
        return session;
    }

    /**
     * Makes a session of the same name on a different molecule.
     *
     * @param molecule the molecule to rebuild on, whose hydrogens may still be counts
     * @param conformers how many conformers to embed
     * @return the new session, one version on from this one, with this session's name and source; it has saved
     *     nothing, because the molecule this session saved describes a structure that no longer exists
     * @throws IllegalArgumentException if {@code conformers} is outside 1 to {@link #MAX_CONFORMERS}
     * @throws IllegalStateException if no shape could be embedded, or the shape found does not describe the molecule's atoms one for one
     */
    public StudioSession rebuilt(Molecule molecule, int conformers)
    {
        if (conformers < 1 || conformers > MAX_CONFORMERS)
        {
            throw new IllegalArgumentException("conformers must be 1 to " + MAX_CONFORMERS + ", but was " + conformers);
        }

        Structure3D structure = Embedder.embed(molecule, Embedder.Options.DEFAULT.withConformers(conformers));
        StudioSession next = build(sessionId, sourceMoleculeId, molecule, structure, Set.of());
        next.version = version + 1;
        return next;
    }

    /**
     * @param sessionId what to call the session
     * @param sourceMoleculeId the stored molecule it is said to have come from
     * @param source the molecule to work on, whose hydrogens may still be counts
     * @param structure a shape of {@code source}
     * @param lockedSites the sites of {@code structure} to start with locked, by position; any past its end are ignored
     * @return a session at version 1
     * @throws IllegalStateException if the structure holds no conformer, or does not describe the molecule's atoms one for one
     */
    private static StudioSession build(String sessionId, String sourceMoleculeId, Molecule source, Structure3D structure, Set<Integer> lockedSites)
    {
        Molecule working = Hydrogens.asAtoms(source);
        if (structure.conformers().isEmpty())
        {
            throw new IllegalStateException("The molecule's structure holds no conformer to open");
        }

        Structure3D.Conformer conformer = structure.conformers().getFirst();
        List<Atom> atoms = working.atoms();
        if (conformer.positions().size() != atoms.size())
        {
            throw new IllegalStateException("The structure describes " + conformer.positions().size() + " sites, but the molecule has " + atoms.size() + " atoms");
        }

        int written = source.atomCount();
        Map<Atom, String> atomIds = new IdentityHashMap<>();
        Map<String, Vec3> geometry = new LinkedHashMap<>();
        Set<String> wasImplicit = new LinkedHashSet<>();
        for (int atom = 0; atom < atoms.size(); atom++)
        {
            String id = "a" + (atom + 1);
            atomIds.put(atoms.get(atom), id);
            geometry.put(id, conformer.positions().get(atom));

            if (atom >= written)
            {
                wasImplicit.add(id);
            }
        }

        List<Bond> bonds = working.bonds();
        Map<Set<String>, String> bondIds = new LinkedHashMap<>();
        for (int bond = 0; bond < bonds.size(); bond++)
        {
            bondIds.put(endpoints(atomIds, bonds.get(bond)), "b" + (bond + 1));
        }

        StudioSession session = new StudioSession(sessionId, sourceMoleculeId, working, atomIds, bondIds, geometry, wasImplicit, conformer.strain());

        // a site's atom is named by its position, as every atom is above
        lockedSites.stream().filter(site -> site >= 0 && site < atoms.size()).sorted().forEach(site -> session.locked.add("a" + (site + 1)));
        return session;
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
        for (Move move : moves)
        {
            if (!geometry.containsKey(move.atomId()))
            {
                throw new StudioEditException("UNKNOWN_ID", "This session holds no atom '" + move.atomId() + "'");
            }
            if (locked.contains(move.atomId()))
            {
                throw new StudioEditException("LOCKED", "Atom " + move.atomId() + " is locked against edits");
            }

            Vec3 to = move.position();
            if (!Double.isFinite(to.x()) || !Double.isFinite(to.y()) || !Double.isFinite(to.z()))
            {
                throw new StudioEditException("INVALID_MESSAGE", "The position of atom " + move.atomId() + " is not a finite number");
            }
        }

        boolean moved = false;
        for (Move move : moves)
        {
            Vec3 from = geometry.get(move.atomId());
            if (!from.equals(move.position()))
            {
                uncommitted.putIfAbsent(move.atomId(), from);
                geometry.put(move.atomId(), move.position());
                moved = true;
            }
        }

        if (commit)
        {
            record();
        }

        if (moved)
        {
            version++;
        }

        return version;
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
        if (!to.isFinite())
        {
            throw new StudioEditException("INVALID_MESSAGE", "The pose is not a finite number");
        }

        Pose unit;
        try
        {
            unit = to.normalised();
        }
        catch (IllegalStateException noRotation)
        {
            throw new StudioEditException("INVALID_MESSAGE", "The rotation has no length, so it is not a rotation");
        }

        boolean moved = !unit.equals(pose);
        if (moved)
        {
            if (uncommittedPose == null)
            {
                uncommittedPose = pose;
            }
            pose = unit;
        }

        if (commit)
        {
            record();
        }

        if (moved)
        {
            version++;
        }

        return version;
    }

    /**
     * Locks or unlocks atoms. A locked atom refuses every edit that would move it, including an undo or redo.
     * Locks are not part of the edit history, so they are never undone.
     *
     * @param atomIds the atoms to lock or unlock; one already in the requested state is left as it is
     * @param lock {@code true} to lock them, {@code false} to unlock them
     * @return the version after the change, which is the one before it if no atom changed state
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold, in which case
     *     none of the atoms is changed
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
     * @throws StudioEditException with {@code BUSY} if an edit is in progress, whose moves an optimisation would
     *     otherwise be filed with
     */
    public synchronized OptimisationInput beginOptimisation()
    {
        if (!uncommitted.isEmpty() || uncommittedPose != null)
        {
            throw new StudioEditException("BUSY", "An edit is still in progress, so the structure cannot be optimised yet");
        }

        prepareStructure();
        return new OptimisationInput(version, structure(), Set.copyOf(lockedSites()), List.copyOf(nodeAtoms));
    }

    /**
     * Puts the atoms where an optimisation of an earlier version left them, as one edit that can be undone. Locked
     * atoms are left where they are however far the optimisation moved them.
     *
     * @param fromVersion the version the optimisation was made from
     * @param positions where the optimisation put each atom, by atom
     * @return what changed and the version after it, which is the version the session was at if no unlocked atom
     *     moved; empty, changing nothing, if the session has moved on from {@code fromVersion} so that the result
     *     describes a structure that no longer exists
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

        long next = move(moves, true);
        Map<String, Vec3> moved = new LinkedHashMap<>();
        moves.forEach(move -> moved.put(move.atomId(), move.position()));
        return Optional.of(new Applied(next, moved, null));
    }

    /**
     * Gives a bond a length, as one edit that can be undone. The atoms move along the bond, and the atoms beyond them go
     * with them if the bond is not in a ring; see {@link BondMotion#withLength}.
     *
     * @param bondId the bond to change
     * @param length the length to give it, in angstroms
     * @param heldId the atom of the bond to leave where it is, or {@code null} to hold neither
     * @param commit whether this ends the edit it belongs to; a change that does not is part of a drag, which is one
     *     edit to undo however many of them it took
     * @return what moved and the version after it, which is the version the session was at if nothing moved
     * @throws StudioEditException with {@code UNKNOWN_ID} for a bond or atom this session does not hold,
     *     {@code INVALID_EDIT} if the length is not allowed or the held atom is not on the bond, and {@code LOCKED} if
     *     an atom that would move is locked
     */
    public synchronized Applied setBondLength(String bondId, double length, String heldId, boolean commit)
    {
        Bond bond = bondNamed(bondId);
        Atom held = heldId == null ? null : atomNamed(heldId);
        return reshape(() -> BondMotion.withLength(molecule, bond, this::positionOfAtom, length, held), commit);
    }

    /**
     * Gives a bond a torsion, as one edit that can be undone, by turning one side of it about the bond; see
     * {@link BondMotion#withTorsion}.
     *
     * @param bondId the bond to turn about
     * @param fromId an atom bonded to the bond's first atom, which the torsion is measured from
     * @param toId an atom bonded to the bond's second atom, which the torsion is measured to
     * @param degrees the torsion to give it, in degrees
     * @param heldId the atom of the bond whose side stays where it is, or {@code null} to hold neither
     * @param commit whether this ends the edit it belongs to; a change that does not is part of a drag, which is one
     *     edit to undo however many of them it took
     * @return what moved and the version after it, which is the version the session was at if nothing moved
     * @throws StudioEditException with {@code UNKNOWN_ID} for a bond or atom this session does not hold,
     *     {@code INVALID_EDIT} if the bond is in a ring, the atoms measured between are not bonded one to each end, or
     *     the held atom is not on the bond, and {@code LOCKED} if an atom that would move is locked
     */
    public synchronized Applied setBondTorsion(String bondId, String fromId, String toId, double degrees, String heldId, boolean commit)
    {
        Bond bond = bondNamed(bondId);
        Atom from = atomNamed(fromId);
        Atom to = atomNamed(toId);
        Atom held = heldId == null ? null : atomNamed(heldId);
        return reshape(() -> BondMotion.withTorsion(molecule, bond, from, to, this::positionOfAtom, degrees, held), commit);
    }

    /**
     * Makes the moves a bond edit works out.
     *
     * @param plan works out where atoms go, refusing by throwing {@link IllegalArgumentException}
     * @param commit whether this ends the edit it belongs to. A commit that moves nothing, because the edit before it
     *     in the same drag already put the atoms there, still ends that edit
     * @return what moved, which is nothing if every atom was already where it was sent
     * @throws StudioEditException with {@code INVALID_EDIT} if the plan refused, and {@code LOCKED} if an atom that
     *     would move is locked
     */
    private Applied reshape(Supplier<Map<Atom, Vec3>> plan, boolean commit)
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

        List<Move> moves = new ArrayList<>();
        for (Atom atom : molecule.atoms())
        {
            Vec3 to = placed.get(atom);
            if (to != null && positionOfAtom(atom).minus(to).length() > MOVE_RESOLUTION)
            {
                moves.add(new Move(atomIds.get(atom), to));
            }
        }
        long next = move(moves, commit);
        Map<String, Vec3> moved = new LinkedHashMap<>();
        moves.forEach(move -> moved.put(move.atomId(), move.position()));
        return new Applied(next, moved, null);
    }

    /**
     * @param atom an atom of this session's molecule, by reference
     * @return where it is
     */
    private Vec3 positionOfAtom(Atom atom)
    {
        return geometry.get(atomIds.get(atom));
    }

    /**
     * @param id an atom's identifier
     * @return the atom
     * @throws StudioEditException with {@code UNKNOWN_ID} if this session holds no such atom
     */
    private Atom atomNamed(String id)
    {
        for (Atom atom : molecule.atoms())
        {
            if (id != null && id.equals(atomIds.get(atom)))
            {
                return atom;
            }
        }
        throw new StudioEditException("UNKNOWN_ID", "This session holds no atom '" + id + "'");
    }

    /**
     * @param id a bond's identifier
     * @return the bond
     * @throws StudioEditException with {@code UNKNOWN_ID} if this session holds no such bond
     */
    private Bond bondNamed(String id)
    {
        for (Bond bond : molecule.bonds())
        {
            if (id != null && id.equals(bondId(bond)))
            {
                return bond;
            }
        }
        throw new StudioEditException("UNKNOWN_ID", "This session holds no bond '" + id + "'");
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
        if (!uncommitted.isEmpty() || uncommittedPose != null)
        {
            requireUnlocked(uncommitted.keySet());
            Map<String, Vec3> restored = new LinkedHashMap<>(uncommitted);
            geometry.putAll(restored);
            uncommitted.clear();

            Pose restoredPose = uncommittedPose;
            if (restoredPose != null)
            {
                pose = restoredPose;
                uncommittedPose = null;
            }
            return new Applied(++version, restored, restoredPose);
        }

        Edit edit = done.peekLast();
        if (edit == null)
        {
            throw new StudioEditException("NOTHING_TO_UNDO", "There is nothing to undo");
        }
        requireUnlocked(edit.before().keySet());

        done.removeLast();

        geometry.putAll(edit.before());
        if (edit.poseBefore() != null)
        {
            pose = edit.poseBefore();
        }
        undone.addLast(edit);
        return new Applied(++version, edit.before(), edit.poseBefore());
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
        Edit edit = undone.peekLast();
        if (edit == null)
        {
            throw new StudioEditException("NOTHING_TO_REDO", "There is nothing to redo");
        }
        requireUnlocked(edit.after().keySet());

        undone.removeLast();

        geometry.putAll(edit.after());
        if (edit.poseAfter() != null)
        {
            pose = edit.poseAfter();
        }
        done.addLast(edit);
        return new Applied(++version, edit.after(), edit.poseAfter());
    }

    /**
     * @return the working graph with its hydrogens taken back into counts, which is how it is stored
     */
    public synchronized Molecule collapsed()
    {
        prepareStructure();
        return collapsed;
    }

    /**
     * Describes the working geometry as the structure of {@link #collapsed()}, which is what saving stores.
     *
     * @return the structure, whose positions are the working positions, not recentred
     */
    public synchronized Structure3D structure()
    {
        prepareStructure();

        List<Vec3> positions = new ArrayList<>(nodeAtoms.size());
        nodeAtoms.forEach(id -> positions.add(geometry.get(id)));

        return new Structure3D(
                structureShape.sites(),
                structureShape.connections(),
                List.of(new Structure3D.Conformer(strain, positions)));
    }

    /** Works out, the first time it is needed, what saving stores that does not change as atoms move. */
    private void prepareStructure()
    {
        if (collapsed != null)
        {
            return;
        }

        Map<Atom, List<Atom>> absorbed = Hydrogens.absorbedBy(molecule);
        Set<Atom> gone = IdentitySet.of();
        absorbed.values().forEach(gone::addAll);
        List<Atom> kept = molecule.atoms().stream().filter(atom -> !gone.contains(atom)).toList();

        // the collapsed molecule lists the hydrogens it implies atom by atom, after every atom it holds
        List<String> order = new ArrayList<>();
        kept.forEach(atom -> order.add(atomIds.get(atom)));
        for (Atom atom : kept)
        {
            absorbed.getOrDefault(atom, List.of()).forEach(hydrogen -> order.add(atomIds.get(hydrogen)));
        }

        Molecule counted = Hydrogens.asCounts(molecule);
        structureShape = Embedder.placed(counted, order.stream().map(geometry::get).toList(), strain);
        nodeAtoms = List.copyOf(order);
        collapsed = counted;
    }

    /**
     * Says which atoms are locked in the terms a stored structure uses, which survive the session's own identifiers
     * being issued afresh.
     *
     * @return the position in {@link #structure()} of each locked atom
     */
    public synchronized Set<Integer> lockedSites()
    {
        prepareStructure();
        Set<Integer> sites = new LinkedHashSet<>();
        for (String id : locked)
        {
            int site = nodeAtoms.indexOf(id);
            if (site >= 0)
            {
                sites.add(site);
            }
        }
        return sites;
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
        return !done.isEmpty() || !uncommitted.isEmpty() || uncommittedPose != null;
    }

    /**
     * @return whether {@link #redo()} has anything to make again
     */
    public synchronized boolean canRedo()
    {
        return !undone.isEmpty();
    }

    /**
     * Files the moves since the last commit, of atoms and of the molecule as a whole, as one undoable edit. An edit
     * that ended where it began is not filed.
     */
    private void record()
    {
        Map<String, Vec3> then = new LinkedHashMap<>();
        Map<String, Vec3> now = new LinkedHashMap<>();
        for (Map.Entry<String, Vec3> entry : uncommitted.entrySet())
        {
            Vec3 current = geometry.get(entry.getKey());
            if (!current.equals(entry.getValue()))
            {
                then.put(entry.getKey(), entry.getValue());
                now.put(entry.getKey(), current);
            }
        }

        Pose poseThen = uncommittedPose;
        boolean posed = poseThen != null && !poseThen.equals(pose);
        uncommitted.clear();
        uncommittedPose = null;

        if (then.isEmpty() && !posed)
        {
            return;
        }

        done.addLast(new Edit(then, now, posed ? poseThen : null, posed ? pose : null));
        undone.clear();
        while (done.size() > MAX_UNDO)
        {
            done.pollFirst();
        }
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
        return pose;
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
        String id = bondIds.get(endpoints(atomIds, bond));
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

    /**
     * @param atomIds what each atom is called
     * @param bond a bond between two of them
     * @return the pair of names a bond is identified by
     */
    private static Set<String> endpoints(Map<Atom, String> atomIds, Bond bond)
    {
        String first = atomIds.get(bond.first());
        String second = atomIds.get(bond.second());
        if (first == null || second == null)
        {
            throw new IllegalArgumentException("The bond joins an atom this session does not hold: " + bond);
        }

        return Set.of(first, second);
    }
}
