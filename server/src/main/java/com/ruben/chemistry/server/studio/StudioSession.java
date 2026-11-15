package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Hydrogens;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.space.Embedder;
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
import java.util.Set;

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
     */
    public record Applied(long version, Map<String, Vec3> moved)
    {
    }

    /**
     * One committed edit.
     *
     * @param before where each atom the edit touched was
     * @param after where each of them is after it
     */
    private record Edit(Map<String, Vec3> before, Map<String, Vec3> after)
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

    /** Atoms relaxation and optimisation must leave where they are. */
    private final Set<String> pinned = new LinkedHashSet<>();

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

        return build(sessionId, source.id(), source.molecule(), structure, source.lockedSites());
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
            record(uncommitted);
            uncommitted.clear();
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
        if (!uncommitted.isEmpty())
        {
            requireUnlocked(uncommitted.keySet());
            Map<String, Vec3> restored = new LinkedHashMap<>(uncommitted);
            geometry.putAll(restored);
            uncommitted.clear();
            return new Applied(++version, restored);
        }

        Edit edit = done.peekLast();
        if (edit == null)
        {
            throw new StudioEditException("NOTHING_TO_UNDO", "There is nothing to undo");
        }
        requireUnlocked(edit.before().keySet());

        done.removeLast();

        geometry.putAll(edit.before());
        undone.addLast(edit);
        return new Applied(++version, edit.before());
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
        done.addLast(edit);
        return new Applied(++version, edit.after());
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
        return !done.isEmpty() || !uncommitted.isEmpty();
    }

    /**
     * @return whether {@link #redo()} has anything to make again
     */
    public synchronized boolean canRedo()
    {
        return !undone.isEmpty();
    }

    /**
     * Files the moves since the last commit as one undoable edit.
     *
     * @param before where each atom moved since the last commit was when it started
     */
    private void record(Map<String, Vec3> before)
    {
        Map<String, Vec3> then = new LinkedHashMap<>();
        Map<String, Vec3> now = new LinkedHashMap<>();
        for (Map.Entry<String, Vec3> entry : before.entrySet())
        {
            Vec3 current = geometry.get(entry.getKey());
            if (!current.equals(entry.getValue()))
            {
                then.put(entry.getKey(), entry.getValue());
                now.put(entry.getKey(), current);
            }
        }

        if (then.isEmpty())
        {
            return;
        }

        done.addLast(new Edit(then, now));
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
     * @return how many mutations this session has applied, counting from 1
     */
    public long version()
    {
        return version;
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
     * @return the atoms relaxation must not move
     */
    public Set<String> pinned()
    {
        return Collections.unmodifiableSet(pinned);
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
