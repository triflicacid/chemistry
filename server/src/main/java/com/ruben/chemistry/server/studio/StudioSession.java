package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Hydrogens;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.ValenceTable;
import com.ruben.chemistry.core.Valences;
import com.ruben.chemistry.server.molecule.Camera;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.server.molecule.StudioSnapshot;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.space.BondMotion;
import com.ruben.chemistry.space.ElementData;
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
import java.util.OptionalInt;
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

    /** How close, in angstroms, a bond edit's result must be to where an atom was for it to be put exactly there. */
    private static final double SNAP_DISTANCE = 1e-9;

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
     * The graph at one moment: what an edit of the graph must be able to put back.
     *
     * @param molecule the working graph
     * @param atomIds what each of its atoms is called
     * @param bondIds what each of its bonds is called, by the pair of its atoms' names
     * @param geometry where each atom is, so that an atom added or deleted comes back or goes with the graph
     */
    private record Graph(Molecule molecule, Map<Atom, String> atomIds, Map<Set<String>, String> bondIds, Map<String, Vec3> geometry)
    {
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
     * A bond while a graph is being rebuilt: its ends as the rebuilt graph will hold them.
     *
     * @param first one end
     * @param second the other end
     * @param order its order
     * @param original the bond of the graph being replaced that this is, or {@code null} for one that is new
     */
    private record Joint(Atom first, Atom second, BondOrder order, Bond original)
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
     * @param graphBefore the graph before the edit, or {@code null} if the edit did not change it
     * @param graphAfter the graph after the edit, or {@code null} under the same condition
     * @param graphAtoms the atoms whose attributes the edit changed, which refuse it being undone or redone if locked
     * @param label what the edit did, in a few words
     * @param at when the edit was made
     */
    private record Edit(
            Map<String, Vec3> before,
            Map<String, Vec3> after,
            Pose poseBefore,
            Pose poseAfter,
            Graph graphBefore,
            Graph graphAfter,
            Set<String> graphAtoms,
            String label,
            long at)
    {
        /**
         * @param undoing whether the edit is being taken back rather than made again
         * @return every atom the step would change, by position or attributes
         */
        Set<String> touched(boolean undoing)
        {
            Set<String> atoms = new LinkedHashSet<>(undoing ? before.keySet() : after.keySet());
            atoms.addAll(graphAtoms);
            return atoms;
        }
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
    /** The working graph, replaced whole by an edit of the graph; read through {@link #molecule()}. */
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

    /** What the edit in progress is, or null to describe it by what it moved. */
    private String pendingLabel;

    /** Where each atom was before the uncommitted moves that have touched it, so a commit can say what it replaced. */
    private final Map<String, Vec3> uncommitted = new LinkedHashMap<>();

    /** Where the molecule sat before the uncommitted moves of it as a whole, or null if there have been none. */
    private Pose uncommittedPose;

    /** Where the molecule sits in the scene; its atoms' coordinates are in the molecule's own frame, not this one. */
    private Pose pose = Pose.IDENTITY;

    /** Where the client's camera is, or null if it has never said. It is not part of the version, and is never undone. */
    private Camera camera;

    /** How many atom and bond names the session has handed out, so that a name is never used twice. */
    private int atomCounter;
    private int bondCounter;

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

        StudioSnapshot snapshot = source.studio();
        Structure3D structure = snapshot.structure();
        if (structure == null)
        {
            // kept on the stored molecule, so the viewer and a second session do not embed it again
            structure = Embedder.embed(source.molecule(), Embedder.Options.DEFAULT.withConformers(conformers));
            source.setStudio(snapshot.withStructure(structure));
        }

        StudioSession session = build(sessionId, source.id(), source.molecule(), structure, snapshot.lockedSites());
        session.pose = snapshot.pose();
        session.camera = snapshot.camera();
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
        // the screen is arranged as it was, though the molecule on it is not
        next.camera = camera;
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
        session.atomCounter = atoms.size();
        session.bondCounter = bonds.size();

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

        pendingLabel = "Optimise";
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
        return reshape(() -> BondMotion.withLength(molecule, bond, this::baselineOf, length, held), commit, "Set bond length");
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
        return reshape(() -> BondMotion.withTorsion(molecule, bond, from, to, this::baselineOf, degrees, held), commit, "Set torsion");
    }

    /**
     * Gives an atom's two bonds an angle, as one edit that can be undone, by swinging one of them and everything beyond
     * it about the atom; see {@link BondMotion#withAngle}.
     *
     * @param centreId the atom the angle is at
     * @param fromId one atom bonded to it
     * @param toId another atom bonded to it
     * @param degrees the angle to give it, in degrees
     * @param heldId {@code fromId} or {@code toId}, whose side stays where it is, or {@code null} to hold neither
     * @param commit whether this ends the edit it belongs to; a change that does not is part of a drag, which is one
     *     edit to undo however many of them it took
     * @return what moved and the version after it, which is the version the session was at if nothing moved
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold,
     *     {@code INVALID_EDIT} if the angle is not allowed, the atoms are not bonded as the angle needs, or a bond that
     *     would have to turn is in a ring, and {@code LOCKED} if an atom that would move is locked
     */
    public synchronized Applied setAngle(String centreId, String fromId, String toId, double degrees, String heldId, boolean commit)
    {
        Atom centre = atomNamed(centreId);
        Atom from = atomNamed(fromId);
        Atom to = atomNamed(toId);
        Atom held = heldId == null ? null : atomNamed(heldId);
        return reshape(() -> BondMotion.withAngle(molecule, centre, from, to, this::baselineOf, degrees, held), commit, "Set angle");
    }

    /**
     * Changes an atom's non-geometric properties as one edit that can be undone.
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
        Atom atom = atomNamed(atomId);
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
        if (!uncommitted.isEmpty() || uncommittedPose != null)
        {
            throw new StudioEditException("BUSY", "An edit is still in progress, so the atom cannot be changed yet");
        }

        Atom.Builder builder = Atom.builder(element != null ? element : atom.element())
                .aromatic(atom.aromatic())
                .charge(charge != null ? charge : atom.charge())
                .isotope(isotope != null ? (isotope == 0 ? OptionalInt.empty() : OptionalInt.of(isotope)) : atom.isotope())
                .hydrogenCount(atom.hydrogenCount())
                .hydrogenIsotopes(atom.hydrogenIsotopes())
                .atomClass(atom.atomClass());
        Atom replacement = builder.build();
        if (replacement.equals(atom))
        {
            return new Applied(version, Map.of(), null);
        }

        Graph before = graph();
        Molecule rebuilt = replacing(atom, replacement);
        Map<Atom, String> renamed = new IdentityHashMap<>(atomIds);
        renamed.put(replacement, renamed.remove(atom));
        restore(new Graph(rebuilt, renamed, bondIds, new LinkedHashMap<>(geometry)));

        file(new Edit(Map.of(), Map.of(), null, null, before, graph(), Set.of(atomId), "Change atom " + atomId, System.currentTimeMillis()));
        return new Applied(++version, Map.of(), null, true);
    }

    /**
     * Rebuilds the working graph with one atom swapped for another, keeping every atom's and bond's place in the order
     * they were in, so that declared stereochemistry reads the same.
     *
     * @param original an atom of the working graph
     * @param replacement the atom to put in its place
     * @return the new graph
     */
    private Molecule replacing(Atom original, Atom replacement)
    {
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
     * Adds an atom as one edit that can be undone. No hydrogen is added or removed to suit it.
     *
     * @param element the new atom's element symbol
     * @param attachToId the atom to bond it to, or {@code null} for a free atom; unless {@code position} says where, it is
     *     placed a bond length away in a direction that suits that atom's neighbours
     * @param order the order of the bond to {@code attachToId}, or {@code null} for single
     * @param position where to put the atom, in angstroms in the molecule's own frame, which is required for a free atom
     *     and otherwise replaces the place the session would have chosen
     * @return the edit, and the names of the atom and bond it made
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold, {@code LOCKED} if the
     *     atom to attach to is locked, {@code BUSY} if an edit is in progress, {@code INVALID_EDIT} if the element is
     *     not one, and {@code INVALID_MESSAGE} if a free atom has no finite position
     */
    public synchronized Created addAtom(String element, String attachToId, BondOrder order, Vec3 position)
    {
        requireIdle();
        if (element == null || !Elements.isSymbol(element))
        {
            throw new StudioEditException("INVALID_EDIT", "'" + element + "' is not an element symbol");
        }

        Atom target = null;
        if (attachToId != null)
        {
            target = atomNamed(attachToId);
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
            where = placementFor(target, element);
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

        return new Created(commit(draft, "Add atom", touched), List.of(freshId), bonds);
    }

    /**
     * Bonds two atoms, as one edit that can be undone. The atoms stay where they are.
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
        requireIdle();
        Atom first = atomNamed(firstId);
        Atom second = atomNamed(secondId);
        if (first == second)
        {
            throw new StudioEditException("INVALID_EDIT", "An atom cannot be bonded to itself");
        }
        if (molecule.bondBetween(first, second).isPresent())
        {
            throw new StudioEditException("INVALID_EDIT", "Atoms " + firstId + " and " + secondId + " are already bonded");
        }
        requireUnlocked(List.of(firstId, secondId));

        Draft draft = new Draft();
        String bond = draft.join(first, second, order == null ? BondOrder.SINGLE : order);
        return new Created(commit(draft, "Add bond", List.of(firstId, secondId)), List.of(), List.of(bond));
    }

    /**
     * Gives a bond an order, as one edit that can be undone. A double bond's declared stereo goes if it stops being double.
     *
     * @param bondId the bond to change
     * @param order its new order
     * @return what changed and the version after it, which is the version the session was at if the bond already had that order
     * @throws StudioEditException as {@link #setBondOrders}
     */
    public synchronized Applied setBondOrder(String bondId, BondOrder order)
    {
        return setBondOrders(List.of(bondId), order);
    }

    /**
     * Gives bonds an order, as one edit that can be undone however many there are. A bond that has that order already is left
     * alone, and a double bond's declared stereo goes if it stops being double.
     *
     * @param bondIds the bonds to change
     * @param order their new order
     * @return what changed and the version after it, which is the version the session was at if every bond had that order
     * @throws StudioEditException with {@code UNKNOWN_ID} for a bond this session does not hold, {@code LOCKED} if an atom of
     *     a bond that would change is locked, {@code BUSY} if an edit is in progress, {@code INVALID_EDIT} if no bond is named,
     *     and {@code INVALID_MESSAGE} if there is no order
     */
    public synchronized Applied setBondOrders(Collection<String> bondIds, BondOrder order)
    {
        requireIdle();
        if (order == null)
        {
            throw new StudioEditException("INVALID_MESSAGE", "An order is required");
        }
        if (bondIds.isEmpty())
        {
            throw new StudioEditException("INVALID_EDIT", "No bond was named to change");
        }

        List<Bond> changing = bondIds.stream().distinct().map(this::bondNamed).filter(bond -> bond.order() != order).toList();
        if (changing.isEmpty())
        {
            return new Applied(version, Map.of(), null);
        }

        Set<String> ends = new LinkedHashSet<>();
        changing.forEach(bond ->
        {
            ends.add(atomIds.get(bond.first()));
            ends.add(atomIds.get(bond.second()));
        });
        requireUnlocked(ends);

        Draft draft = new Draft();
        changing.forEach(bond -> draft.reorder(draft.jointOf(bond), order));
        return commit(draft, changing.size() == 1 ? "Set bond order" : "Set bond orders", ends);
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
        requireIdle();
        if (ids.isEmpty())
        {
            throw new StudioEditException("INVALID_EDIT", "No atom was named to delete");
        }

        Set<Atom> going = IdentitySet.of();
        ids.forEach(id -> going.add(atomNamed(id)));
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

        List<String> names = going.stream().map(atomIds::get).sorted().toList();
        requireUnlocked(names);

        Draft draft = new Draft();
        going.forEach(draft::remove);
        return commit(draft, going.size() == 1 ? "Delete atom" : "Delete atoms", names);
    }

    /**
     * Deletes multiple bonmds.
     *
     * @param ids the bonds to delete
     * @return what changed and the version after it
     * @throws StudioEditException with {@code UNKNOWN_ID} for a bond this session does not hold, {@code LOCKED} if an atom
     *     of any of them is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if none is named
     */
    public synchronized Applied deleteBonds(Collection<String> ids)
    {
        requireIdle();
        if (ids.isEmpty())
        {
            throw new StudioEditException("INVALID_EDIT", "No bond was named to delete");
        }

        List<Bond> going = ids.stream().distinct().map(this::bondNamed).toList();
        Set<String> ends = new LinkedHashSet<>();
        going.forEach(bond ->
        {
            ends.add(atomIds.get(bond.first()));
            ends.add(atomIds.get(bond.second()));
        });
        requireUnlocked(ends);

        Draft draft = new Draft();
        going.forEach(bond -> draft.remove(draft.jointOf(bond)));
        return commit(draft, going.size() == 1 ? "Delete bond" : "Delete bonds", ends);
    }

    /**
     * @throws StudioEditException with {@code BUSY} if an edit of positions is in progress, which a change of the graph would be filed with
     */
    private void requireIdle()
    {
        if (!uncommitted.isEmpty() || uncommittedPose != null)
        {
            throw new StudioEditException("BUSY", "An edit is still in progress, so the structure cannot be changed yet");
        }
    }

    /**
     * Makes a rebuilt graph the working one and files the change as one edit.
     *
     * @param draft the rebuilt graph
     * @param label what the edit is called in the history
     * @param touched the atoms the edit changed, which refuse it being undone or redone if locked since
     * @return the edit, which changed the graph
     */
    private Applied commit(Draft draft, String label, Collection<String> touched)
    {
        Graph before = graph();
        restore(new Graph(draft.build(), draft.ids, draft.jointIds, draft.positions));
        file(new Edit(Map.of(), Map.of(), null, null, before, graph(), new LinkedHashSet<>(touched), label, System.currentTimeMillis()));
        return new Applied(++version, Map.of(), null, true);
    }

    /**
     * Gives atoms the hydrogens that fill their valence.
     *
     * @param ids the atoms to fill
     * @return the edit and the hydrogens and bonds it made, which is nothing, and does not raise the version, if every atom was full already
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom this session does not hold, {@code LOCKED} if an atom
     *     that would take a hydrogen is locked, {@code BUSY} if an edit is in progress, and {@code INVALID_EDIT} if none is
     *     named or none is an atom whose valence is known
     */
    public synchronized Created fillValences(Collection<String> ids)
    {
        requireIdle();
        if (ids.isEmpty())
        {
            throw new StudioEditException("INVALID_EDIT", "No atom was named to fill");
        }

        List<Atom> named = ids.stream().distinct().map(this::atomNamed).toList();
        List<Atom> known = named.stream().filter(atom -> Valences.areKnownFor(atom.element())).toList();
        if (known.isEmpty())
        {
            throw new StudioEditException("INVALID_EDIT", "None of the atoms is one whose valence is known, which only the organic subset is");
        }

        Map<Atom, Integer> wanting = new IdentityHashMap<>();
        List<Atom> order = new ArrayList<>();
        for (Atom atom : known)
        {
            int missing = hydrogensMissing(atom);
            if (missing > 0)
            {
                wanting.put(atom, missing);
                order.add(atom);
            }
        }
        if (order.isEmpty())
        {
            return new Created(new Applied(version, Map.of(), null), List.of(), List.of());
        }
        requireUnlocked(order.stream().map(atomIds::get).toList());

        Draft draft = new Draft();
        List<String> atoms = new ArrayList<>();
        List<String> bonds = new ArrayList<>();
        List<String> touched = new ArrayList<>();
        for (Atom atom : order)
        {
            touched.add(atomIds.get(atom));
            Vec3 centre = positionOfAtom(atom);
            double length = ElementData.covalentRadius(ElementData.numberOf(atom.element())) + ElementData.covalentRadius(1);

            List<Vec3> arms = new ArrayList<>();
            for (Atom neighbour : molecule.neighboursOf(atom))
            {
                Vec3 arm = positionOfAtom(neighbour).minus(centre);
                if (arm.length() > 1e-6)
                {
                    arms.add(arm.normalised());
                }
            }

            for (int each = 0; each < wanting.get(atom); each++)
            {
                Vec3 direction = hydrogenDirection(atom, arms);
                arms.add(direction);
                Atom hydrogen = Atom.builder("H").hydrogenCount(0).build();
                String hydrogenId = draft.add(hydrogen, centre.plus(direction.times(length)));
                atoms.add(hydrogenId);
                touched.add(hydrogenId);
                bonds.add(draft.join(atom, hydrogen, BondOrder.SINGLE));
            }
        }

        return new Created(commit(draft, "Fill valences", touched), atoms, bonds);
    }

    /**
     * @param atom an atom of the working graph whose valence is known
     * @return how many hydrogens it takes to fill the smallest valence its charge allows that its bonds do not exceed
     */
    private int hydrogensMissing(Atom atom)
    {
        int spent = 0;
        int aromatic = 0;
        for (Bond bond : molecule.bondsOf(atom))
        {
            switch (bond.order())
            {
                case SINGLE -> spent += 1;
                case DOUBLE -> spent += 2;
                case TRIPLE -> spent += 3;
                case QUADRUPLE -> spent += 4;
                case AROMATIC -> aromatic++;
            }
        }
        // aromatic bonds are worth one and a half each, as the molecule counts them
        spent += Math.round(1.5f * aromatic);

        int required = spent;
        OptionalInt valence = ValenceTable.allowedFor(atom.element(), atom.charge()).stream().mapToInt(Integer::intValue).filter(each -> each >= required).findFirst();
        if (valence.isEmpty())
        {
            valence = Valences.toFill(atom.element(), required);
        }
        return valence.isPresent() ? Math.max(0, valence.getAsInt() - spent) : 0;
    }

    /**
     * Chooses where the next hydrogen of an atom goes: where its neighbours, and the hydrogens already placed on it, leave
     * room, which for an atom with a double bond and nothing else is in the plane of what is beyond that bond.
     *
     * @param atom the atom
     * @param arms unit vectors from the atom to each of its neighbours and the hydrogens already placed
     * @return a unit vector for the new bond
     */
    private Vec3 hydrogenDirection(Atom atom, List<Vec3> arms)
    {
        // the one arm is a bond of the atom's own, and not a hydrogen placed a moment ago on an atom with none
        if (arms.size() == 1 && molecule.bondsOf(atom).size() == 1)
        {
            Vec3 along = arms.getFirst();
            BondOrder order = molecule.bondsOf(atom).getFirst().order();
            if (order == BondOrder.TRIPLE)
            {
                return along.times(-1);
            }
            if (order == BondOrder.DOUBLE)
            {
                Vec3 across = acrossDoubleBond(atom, along);
                return along.times(-0.5).plus(across.times(Math.sqrt(3) / 2));
            }
        }
        return freeDirection(arms);
    }

    /**
     * @param atom an atom with one double bond and no other bond
     * @param along the unit vector from it along that bond
     * @return a unit vector at right angles to the bond, in the plane of what is bonded beyond it and on the side away from
     *     it, so that the atom's hydrogens and the other end's substituents lie in one plane; any at right angles if the
     *     other end has no other bond
     */
    private Vec3 acrossDoubleBond(Atom atom, Vec3 along)
    {
        Atom other = molecule.neighboursOf(atom).getFirst();
        for (Atom beyond : molecule.neighboursOf(other))
        {
            if (beyond != atom)
            {
                Vec3 toBeyond = positionOfAtom(beyond).minus(positionOfAtom(other));
                Vec3 flat = toBeyond.minus(along.times(toBeyond.dot(along)));
                if (flat.length() > 1e-6)
                {
                    return flat.normalised().times(-1);
                }
            }
        }
        return perpendicularTo(along);
    }

    /**
     * Chooses where to put a new atom bonded to an existing one: a bond length away, where the atom's neighbours leave
     * the most room. Up to three neighbours it takes the position of the next ideal tetrahedral or trigonal arm; more
     * than that it takes the direction furthest from them all.
     *
     * @param target the atom to attach to
     * @param element the new atom's element symbol
     * @return where to put it, in the molecule's own frame
     */
    private Vec3 placementFor(Atom target, String element)
    {
        Vec3 centre = positionOfAtom(target);
        List<Vec3> arms = new ArrayList<>();
        for (Atom neighbour : molecule.neighboursOf(target))
        {
            Vec3 arm = positionOfAtom(neighbour).minus(centre);
            if (arm.length() > 1e-6)
            {
                arms.add(arm.normalised());
            }
        }

        double length = ElementData.covalentRadius(ElementData.numberOf(target.element())) + ElementData.covalentRadius(ElementData.numberOf(element));
        return centre.plus(freeDirection(arms).times(length));
    }

    /**
     * @param arms unit vectors from an atom to each of its neighbours
     * @return a unit vector for a new bond from it
     */
    private static Vec3 freeDirection(List<Vec3> arms)
    {
        if (arms.isEmpty())
        {
            return new Vec3(1, 0, 0);
        }

        Vec3 first = arms.getFirst();
        if (arms.size() == 1)
        {
            // a tetrahedral angle from the one bond, which is cos 109.47 = -1/3
            return first.times(-1.0 / 3).plus(perpendicularTo(first).times(Math.sqrt(8.0) / 3));
        }

        Vec3 second = arms.get(1);
        Vec3 normal = first.cross(second);
        if (arms.size() == 2)
        {
            Vec3 back = first.plus(second).times(-1);
            if (normal.length() < 1e-6 || back.length() < 1e-6)
            {
                return perpendicularTo(first);
            }
            // arms wide apart are trigonal and leave the third place in their plane; closer ones are tetrahedral and leave two, one on each side
            if (first.dot(second) < -0.4)
            {
                return back.normalised();
            }
            return back.normalised().times(Math.sqrt(1.0 / 3)).plus(normal.normalised().times(Math.sqrt(2.0 / 3)));
        }

        if (arms.size() == 3)
        {
            Vec3 sum = first.plus(second).plus(arms.get(2));
            if (sum.length() > 0.1)
            {
                return sum.times(-1).normalised();
            }
            // three arms in a plane leave only the two directions at right angles to it
            return normal.length() < 1e-6 ? perpendicularTo(first) : normal.normalised();
        }

        // crowded: of many evenly spread directions, the one whose nearest arm is furthest away
        int samples = 256;
        Vec3 best = perpendicularTo(first);
        double bestRoom = -2;
        for (int sample = 0; sample < samples; sample++)
        {
            double y = 1 - 2 * (sample + 0.5) / samples;
            double radius = Math.sqrt(1 - y * y);
            double turn = sample * Math.PI * (3 - Math.sqrt(5));
            Vec3 candidate = new Vec3(Math.cos(turn) * radius, y, Math.sin(turn) * radius);
            double nearest = -2;
            for (Vec3 arm : arms)
            {
                nearest = Math.max(nearest, candidate.dot(arm));
            }
            if (-nearest > bestRoom)
            {
                bestRoom = -nearest;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * @param vector a unit vector
     * @return a unit vector at right angles to it
     */
    private static Vec3 perpendicularTo(Vec3 vector)
    {
        Vec3 axis = Math.abs(vector.x()) <= Math.abs(vector.y()) && Math.abs(vector.x()) <= Math.abs(vector.z())
                ? new Vec3(1, 0, 0)
                : Math.abs(vector.y()) <= Math.abs(vector.z()) ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        return vector.cross(axis).normalised();
    }

    /**
     * A copy of the working graph being changed, which becomes the working graph if the change is made. Declared
     * stereochemistry is kept only where nothing around it changed, because a declaration is relative to the order of an
     * atom's bonds, and it means nothing once one is added or taken away.
     */
    private final class Draft
    {
        private final Molecule source = molecule;
        private final List<Atom> atoms = new ArrayList<>(source.atoms());
        private final Map<Atom, String> ids = new IdentityHashMap<>(atomIds);
        private final Map<String, Vec3> positions = new LinkedHashMap<>(geometry);
        private final Map<Set<String>, String> jointIds = new LinkedHashMap<>(bondIds);
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
            String id = "a" + (++atomCounter);
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
            String id = "b" + (++bondCounter);
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
         * @return the molecule these changes make
         */
        Molecule build()
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

        List<Move> moves = new ArrayList<>();
        for (Atom atom : molecule.atoms())
        {
            Vec3 baseline = baselineOf(atom);
            Vec3 to = placed.get(atom);
            if (to == null && uncommitted.containsKey(atomIds.get(atom)))
            {
                to = baseline;
            }
            else if (to != null && to.distanceTo(baseline) < SNAP_DISTANCE)
            {
                // arithmetic leaves a trace of error, and an atom that has not moved must be exactly where it was
                to = baseline;
            }

            if (to != null && !to.equals(positionOfAtom(atom)))
            {
                moves.add(new Move(atomIds.get(atom), to));
            }
        }
        pendingLabel = label;
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
        String id = atomIds.get(atom);
        Vec3 original = uncommitted.get(id);
        return original != null ? original : geometry.get(id);
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
            pendingLabel = null;

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
        requireUnlocked(edit.touched(true));

        done.removeLast();

        geometry.putAll(edit.before());
        if (edit.poseBefore() != null)
        {
            pose = edit.poseBefore();
        }
        if (edit.graphBefore() != null)
        {
            restore(edit.graphBefore());
        }
        undone.addLast(edit);
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
        Edit edit = undone.peekLast();
        if (edit == null)
        {
            throw new StudioEditException("NOTHING_TO_REDO", "There is nothing to redo");
        }
        requireUnlocked(edit.touched(false));

        undone.removeLast();

        geometry.putAll(edit.after());
        if (edit.poseAfter() != null)
        {
            pose = edit.poseAfter();
        }
        if (edit.graphAfter() != null)
        {
            restore(edit.graphAfter());
        }
        done.addLast(edit);
        return new Applied(++version, edit.after(), edit.poseAfter(), edit.graphAfter() != null);
    }

    /**
     * @return what each edit did and how many of them are in effect
     */
    public synchronized History history()
    {
        List<HistoryEntry> entries = new ArrayList<>();
        done.forEach(edit -> entries.add(new HistoryEntry(edit.label(), edit.at())));
        List<Edit> later = new ArrayList<>(undone);
        Collections.reverse(later);
        later.forEach(edit -> entries.add(new HistoryEntry(edit.label(), edit.at())));
        return new History(entries, done.size());
    }

    /**
     * Forgets every edit, done or undone, leaving the atoms where they are. There is then nothing to undo or redo.
     *
     * @return the version after it, which moved no atom
     */
    public synchronized Applied clearHistory()
    {
        done.clear();
        undone.clear();
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
        int total = done.size() + undone.size();
        if (position < 0 || position > total)
        {
            throw new StudioEditException("INVALID_EDIT", "There is no point " + position + " in a history of " + total + " edits");
        }

        List<Edit> all = new ArrayList<>(done);
        List<Edit> later = new ArrayList<>(undone);
        Collections.reverse(later);
        all.addAll(later);

        int from = done.size();
        List<Edit> crossed = position < from ? all.subList(position, from) : all.subList(from, position);
        for (Edit edit : crossed)
        {
            requireUnlocked(edit.touched(position < from));
        }

        Map<String, Vec3> moved = new LinkedHashMap<>();
        Pose posed = null;
        boolean regraphed = false;
        if (!uncommitted.isEmpty() || uncommittedPose != null)
        {
            Applied cancelled = undo();
            moved.putAll(cancelled.moved());
            posed = cancelled.pose();
        }

        while (done.size() > position)
        {
            Applied step = undo();
            moved.putAll(step.moved());
            posed = step.pose() != null ? step.pose() : posed;
            regraphed |= step.graph();
        }
        while (done.size() < position)
        {
            Applied step = redo();
            moved.putAll(step.moved());
            posed = step.pose() != null ? step.pose() : posed;
            regraphed |= step.graph();
        }

        if (discard)
        {
            undone.clear();
        }

        moved.replaceAll((id, ignored) -> geometry.get(id));
        return new Applied(version, moved, posed == null ? null : pose, regraphed);
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

        String label = pendingLabel;
        pendingLabel = null;
        if (then.isEmpty() && !posed)
        {
            return;
        }

        if (label == null)
        {
            label = then.isEmpty() ? "Move molecule" : then.size() == 1 ? "Move atom" : "Move atoms";
        }

        file(new Edit(then, now, posed ? poseThen : null, posed ? pose : null, null, null, Set.of(), label, System.currentTimeMillis()));
    }

    /**
     * Adds a committed edit to the history, discarding what could have been redone and the edits too old to keep.
     *
     * @param edit the edit
     */
    private void file(Edit edit)
    {
        done.addLast(edit);
        undone.clear();
        while (done.size() > MAX_UNDO)
        {
            done.pollFirst();
        }
    }

    /**
     * @return the graph as it is now
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
        collapsed = null;
        structureShape = null;
        nodeAtoms = null;
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
     * @return where the client's camera is, or empty if it has never said
     */
    public synchronized Optional<Camera> camera()
    {
        return Optional.ofNullable(camera);
    }

    /**
     * Records where the client's camera is. This is how a screen is arranged and not an edit, so the version does not
     * change and there is nothing to undo.
     *
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
