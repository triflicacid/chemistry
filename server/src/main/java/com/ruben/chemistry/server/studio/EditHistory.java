package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
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
     * @return the edit an undo would take back, or {@code null} if there is none
     */
    Edit lastDone()
    {
        return done.peekLast();
    }

    /**
     * @return the edit a redo would make again, or {@code null} if there is none
     */
    Edit lastUndone()
    {
        return undone.peekLast();
    }

    /**
     * Moves the latest edit in effect to those that can be made again.
     *
     * @return the edit
     */
    Edit takeBack()
    {
        Edit edit = done.removeLast();
        undone.addLast(edit);
        return edit;
    }

    /**
     * Moves the latest undone edit back into effect.
     *
     * @return the edit
     */
    Edit makeAgain()
    {
        Edit edit = undone.removeLast();
        done.addLast(edit);
        return edit;
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
     * @param position how many edits would be in effect
     * @return the edits that undoing or redoing to there would cross, in the order they lie in the history
     */
    List<Edit> crossedTo(int position)
    {
        List<Edit> all = all();
        return position < done.size() ? all.subList(position, done.size()) : all.subList(done.size(), position);
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
     * Forgets the edits that could be made again.
     */
    void discardUndone()
    {
        undone.clear();
    }

    /**
     * Forgets every edit.
     */
    void clear()
    {
        done.clear();
        undone.clear();
    }
}
