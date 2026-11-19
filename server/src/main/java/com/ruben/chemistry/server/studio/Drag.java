package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.server.studio.EditHistory.Edit;
import com.ruben.chemistry.server.studio.StudioSession.Move;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A drag movement falls under one committed edit.
 */
final class Drag
{
    /**
     * What cancelling a drag put back.
     *
     * @param moved where each atom the drag had moved is now
     * @param pose where the molecule now sits, or {@code null} if the drag had not moved it as a whole
     */
    record Cancelled(Map<String, Vec3> moved, Pose pose)
    {
    }

    private final Map<String, Vec3> geometry;
    private final Set<String> locked;

    /** Where the molecule sits in the scene (ref. molecule space) */
    private Pose pose;

    /** What the edit in progress is. */
    private String pendingLabel;

    /** Where each atom was before the uncommitted moves that have touched it. */
    private final Map<String, Vec3> uncommitted = new LinkedHashMap<>();

    /** Where the molecule sat before the uncommitted moves of it as a whole, or null if there have been none. */
    private Pose uncommittedPose;

    /**
     * @param geometry where each atom is, which the drag changes and the session reads
     * @param locked the atoms that refuse a move, which the session changes and the drag reads
     * @param pose where the molecule sits
     */
    Drag(Map<String, Vec3> geometry, Set<String> locked, Pose pose)
    {
        this.geometry = geometry;
        this.locked = locked;
        this.pose = pose;
    }

    /**
     * @return whether there are moves that have not been committed
     */
    boolean active()
    {
        return !uncommitted.isEmpty() || uncommittedPose != null;
    }

    /**
     * @return where the molecule sits in the scene
     */
    Pose pose()
    {
        return pose;
    }

    /**
     * Puts the molecule somewhere without making it part of a drag, as undoing or redoing an edit does.
     *
     * @param to where the molecule sits
     */
    void setPose(Pose to)
    {
        pose = to;
    }

    /**
     * @param id an atom's identifier
     * @return where the atom was before the edit in progress began, or where it is if the edit has not moved it
     */
    Vec3 baselineOf(String id)
    {
        Vec3 original = uncommitted.get(id);
        return original != null ? original : geometry.get(id);
    }

    /**
     * @param id an atom's identifier
     * @return whether the edit in progress has moved the atom
     */
    boolean hasMoved(String id)
    {
        return uncommitted.containsKey(id);
    }

    /**
     * Names the edit in progress, which is what it is called in the history when it is committed.
     *
     * @param label what the edit does, in a few words, or {@code null} to describe it by what it moved
     */
    void label(String label)
    {
        pendingLabel = label;
    }

    /**
     * Puts atoms where they are asked to go.
     *
     * @param moves the atoms to move; a later move of an atom overrides an earlier one
     * @return whether any atom was not already where it was sent
     * @throws StudioEditException with {@code UNKNOWN_ID} for an atom that is not held, {@code LOCKED} for one that is
     *     locked, and {@code INVALID_MESSAGE} for a position that is not finite; no atom is moved
     */
    boolean move(List<Move> moves)
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
        return moved;
    }

    /**
     * Puts the molecule somewhere in the scene, turning and moving it as one rigid body.
     *
     * @param to where to put it, as the full pose and not a change to the current one
     * @return whether the molecule was not already there
     * @throws StudioEditException with {@code INVALID_MESSAGE} if the pose is not finite or its rotation has no length
     */
    boolean transform(Pose to)
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
        return moved;
    }

    /**
     * Ends the drag and commits it.
     *
     * @return the edit, or empty if nothing ended up anywhere new
     */
    Optional<Edit> commit()
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
            return Optional.empty();
        }

        if (label == null)
        {
            label = then.isEmpty() ? "Move molecule" : then.size() == 1 ? "Move atom" : "Move atoms";
        }

        return Optional.of(new Edit(then, now, posed ? poseThen : null, posed ? pose : null, null, null, Set.of(), label, System.currentTimeMillis()));
    }

    /**
     * Puts back what the drag has moved and forgets it.
     *
     * @return what was put back
     * @throws StudioEditException with {@code LOCKED} if an atom the drag moved has been locked since
     */
    Cancelled cancel()
    {
        for (String id : uncommitted.keySet())
        {
            if (locked.contains(id))
            {
                throw new StudioEditException("LOCKED", "Atom " + id + " is locked against edits");
            }
        }

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
        return new Cancelled(restored, restoredPose);
    }
}
