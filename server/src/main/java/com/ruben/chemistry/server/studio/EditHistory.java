package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A session's committed edits.
 */
final class EditHistory
{
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
    record Edit(
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
     * What an edit's changes are put into.
     */
    interface Target
    {
        /**
         * @param positions the atoms to move, and where to
         */
        void putPositions(Map<String, Vec3> positions);

        /**
         * @param pose where the molecule now sits
         */
        void setPose(Pose pose);

        /**
         * @param graph the graph to make the working one
         */
        void restoreGraph(Graph graph);

        /**
         * @param atomIds the atoms a step would change
         * @throws StudioEditException with {@code LOCKED} if any of them is locked
         */
        void requireUnlocked(Collection<String> atomIds);
    }

    /**
     * What going to a point in the history changed.
     *
     * @param moved the atoms the steps moved
     * @param pose where the molecule sat after the last step that moved it as a whole, or {@code null} if no step did
     * @param graph whether any step changed the graph
     * @param steps how many edits were undone or redone
     */
    record Rewound(Map<String, Vec3> moved, Pose pose, boolean graph, int steps)
    {
    }

    /** Committed edits, the latest last. */
    private final Deque<Edit> done = new ArrayDeque<>();

    /** Edits undone and not yet overtaken by a new one, the next to redo last. */
    private final Deque<Edit> undone = new ArrayDeque<>();

    private final int limit;

    /**
     * @param limit how many edits in effect to keep, the oldest going first
     */
    EditHistory(int limit)
    {
        this.limit = limit;
    }

    /**
     * Adds a committed edit, discarding what could have been redone and the edits too old to keep.
     *
     * @param edit the edit
     */
    void file(Edit edit)
    {
        done.addLast(edit);
        undone.clear();
        while (done.size() > limit)
        {
            done.pollFirst();
        }
    }

    /**
     * Takes back the latest edit in effect.
     *
     * @param target what to put the edit's changes back into
     * @return the edit
     * @throws StudioEditException with {@code NOTHING_TO_UNDO} if there is no edit to take back, and {@code LOCKED} if
     *     an atom it would change has been locked since, in which case the edit stays in effect
     */
    Edit undo(Target target)
    {
        Edit edit = done.peekLast();
        if (edit == null)
        {
            throw new StudioEditException("NOTHING_TO_UNDO", "There is nothing to undo");
        }
        target.requireUnlocked(edit.touched(true));

        done.removeLast();

        target.putPositions(edit.before());
        if (edit.poseBefore() != null)
        {
            target.setPose(edit.poseBefore());
        }
        if (edit.graphBefore() != null)
        {
            target.restoreGraph(edit.graphBefore());
        }
        undone.addLast(edit);
        return edit;
    }

    /**
     * Makes again the edit most recently undone.
     *
     * @param target what to put the edit's changes into
     * @return the edit
     * @throws StudioEditException with {@code NOTHING_TO_REDO} if no undone edit is waiting, and {@code LOCKED} if an
     *     atom it would change has been locked since, in which case the edit stays undone
     */
    Edit redo(Target target)
    {
        Edit edit = undone.peekLast();
        if (edit == null)
        {
            throw new StudioEditException("NOTHING_TO_REDO", "There is nothing to redo");
        }
        target.requireUnlocked(edit.touched(false));

        undone.removeLast();

        target.putPositions(edit.after());
        if (edit.poseAfter() != null)
        {
            target.setPose(edit.poseAfter());
        }
        if (edit.graphAfter() != null)
        {
            target.restoreGraph(edit.graphAfter());
        }
        done.addLast(edit);
        return edit;
    }

    /**
     * Checks that going to a point in the history is possible.
     *
     * @param position how many edits would be in effect
     * @param target what the steps would change
     * @throws StudioEditException with {@code INVALID_EDIT} if there is no such position, and {@code LOCKED} if an atom
     *     a step would change is locked
     */
    void validate(int position, Target target)
    {
        if (position < 0 || position > size())
        {
            throw new StudioEditException("INVALID_EDIT", "There is no point " + position + " in a history of " + size() + " edits");
        }

        boolean undoing = position < done.size();
        for (Edit edit : crossedTo(position))
        {
            target.requireUnlocked(edit.touched(undoing));
        }
    }

    /**
     * Undoes or redoes as many edits as it takes to have exactly {@code position} of them in effect.
     *
     * @param position how many edits to have in effect
     * @param discard whether to forget the edits after {@code position}, which can then not be made again
     * @param target what the steps change
     * @return what the steps changed
     */
    Rewound rewind(int position, boolean discard, Target target)
    {
        Map<String, Vec3> moved = new LinkedHashMap<>();
        Pose posed = null;
        boolean regraphed = false;
        int steps = 0;

        while (done.size() > position)
        {
            Edit edit = undo(target);
            moved.putAll(edit.before());
            posed = edit.poseBefore() != null ? edit.poseBefore() : posed;
            regraphed |= edit.graphBefore() != null;
            steps++;
        }
        while (done.size() < position)
        {
            Edit edit = redo(target);
            moved.putAll(edit.after());
            posed = edit.poseAfter() != null ? edit.poseAfter() : posed;
            regraphed |= edit.graphAfter() != null;
            steps++;
        }

        if (discard)
        {
            undone.clear();
        }

        return new Rewound(moved, posed, regraphed, steps);
    }

    /**
     * @return how many edits are in effect
     */
    int position()
    {
        return done.size();
    }

    /**
     * @return how many edits there are, counting the ones that have been undone
     */
    int size()
    {
        return done.size() + undone.size();
    }

    /**
     * @return every edit, the earliest first, counting the ones that have been undone
     */
    List<Edit> all()
    {
        List<Edit> all = new ArrayList<>(done);
        List<Edit> later = new ArrayList<>(undone);
        Collections.reverse(later);
        all.addAll(later);
        return all;
    }

    /**
     * @return whether there is an edit in effect to undo
     */
    boolean canUndo()
    {
        return !done.isEmpty();
    }

    /**
     * @return whether there is an undone edit to make again
     */
    boolean canRedo()
    {
        return !undone.isEmpty();
    }

    /**
     * Forgets every edit.
     */
    void clear()
    {
        done.clear();
        undone.clear();
    }

    /**
     * @param position how many edits would be in effect
     * @return the edits that undoing or redoing to there would cross, in the order they lie in the history
     */
    private List<Edit> crossedTo(int position)
    {
        List<Edit> all = all();
        return position < done.size() ? all.subList(position, done.size()) : all.subList(done.size(), position);
    }
}
