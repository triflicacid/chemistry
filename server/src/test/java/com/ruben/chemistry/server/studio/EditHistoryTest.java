package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.server.studio.EditHistory.Edit;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EditHistoryTest
{
    private static final Vec3 HERE = new Vec3(0, 0, 0);
    private static final Vec3 THERE = new Vec3(1, 0, 0);

    /** Remembers what the history put into it, and refuses the atoms it is told are locked. */
    private static final class Recorder implements EditHistory.Target
    {
        final Map<String, Vec3> positions = new LinkedHashMap<>();
        final List<Pose> poses = new ArrayList<>();
        final List<Graph> graphs = new ArrayList<>();
        final Set<String> locked = new java.util.LinkedHashSet<>();

        @Override
        public void putPositions(Map<String, Vec3> moved)
        {
            positions.putAll(moved);
        }

        @Override
        public void setPose(Pose pose)
        {
            poses.add(pose);
        }

        @Override
        public void restoreGraph(Graph graph)
        {
            graphs.add(graph);
        }

        @Override
        public void requireUnlocked(Collection<String> atomIds)
        {
            atomIds.stream().filter(locked::contains).findFirst().ifPresent(id ->
            {
                throw new StudioEditException("LOCKED", "Atom " + id + " is locked");
            });
        }
    }

    private final Recorder target = new Recorder();

    private static Edit edit(String label)
    {
        return new Edit(Map.of(), Map.of(), null, null, null, null, Set.of(), label, 0);
    }

    private static Edit moving(String label, String atom)
    {
        return new Edit(Map.of(atom, HERE), Map.of(atom, THERE), null, null, null, null, Set.of(), label, 0);
    }

    private static List<String> labels(List<Edit> edits)
    {
        return edits.stream().map(Edit::label).toList();
    }

    @Test
    void should_hold_filed_edits_in_the_order_they_were_made()
    {
        EditHistory history = new EditHistory(10);
        history.file(edit("one"));
        history.file(edit("two"));

        assertThat(labels(history.all())).containsExactly("one", "two");
        assertThat(history.position()).isEqualTo(2);
    }

    @Test
    void should_undo_the_latest_edit_and_put_its_old_positions_back()
    {
        EditHistory history = new EditHistory(10);
        history.file(moving("one", "a1"));

        Edit undone = history.undo(target);

        assertThat(undone.label()).isEqualTo("one");
        assertThat(target.positions).containsEntry("a1", HERE);
        assertThat(history.position()).isZero();
        assertThat(history.canUndo()).isFalse();
        assertThat(history.canRedo()).isTrue();
    }

    @Test
    void should_redo_an_undone_edit_and_put_its_new_positions_back()
    {
        EditHistory history = new EditHistory(10);
        history.file(moving("one", "a1"));
        history.undo(target);

        history.redo(target);

        assertThat(target.positions).containsEntry("a1", THERE);
        assertThat(history.position()).isEqualTo(1);
        assertThat(history.canRedo()).isFalse();
    }

    @Test
    void should_restore_the_pose_and_graph_only_when_the_edit_changed_them()
    {
        EditHistory history = new EditHistory(10);
        history.file(edit("plain"));
        history.undo(target);

        assertThat(target.poses).isEmpty();
        assertThat(target.graphs).isEmpty();

        Pose before = Pose.IDENTITY;
        Pose after = new Pose(new Vec3(1, 2, 3), Pose.IDENTITY.rotation());
        Graph graph = new Graph(null, Map.of(), Map.of(), Map.of());
        history.file(new Edit(Map.of(), Map.of(), before, after, graph, graph, Set.of(), "posed", 0));
        history.undo(target);
        history.redo(target);

        assertThat(target.poses).containsExactly(before, after);
        assertThat(target.graphs).containsExactly(graph, graph);
    }

    @Test
    void should_refuse_to_undo_or_redo_when_there_is_nothing_to()
    {
        EditHistory history = new EditHistory(10);

        assertThatThrownBy(() -> history.undo(target)).isInstanceOf(StudioEditException.class).hasMessageContaining("nothing to undo");
        assertThatThrownBy(() -> history.redo(target)).isInstanceOf(StudioEditException.class).hasMessageContaining("nothing to redo");
    }

    @Test
    void should_leave_an_edit_in_effect_when_an_atom_it_would_change_is_locked()
    {
        EditHistory history = new EditHistory(10);
        history.file(moving("one", "a1"));
        target.locked.add("a1");

        assertThatThrownBy(() -> history.undo(target)).isInstanceOf(StudioEditException.class).extracting(refused -> ((StudioEditException) refused).code()).isEqualTo("LOCKED");

        assertThat(history.position()).isEqualTo(1);
        assertThat(target.positions).isEmpty();
    }

    @Test
    void should_forget_what_could_be_redone_when_a_new_edit_is_filed()
    {
        EditHistory history = new EditHistory(10);
        history.file(edit("one"));
        history.undo(target);

        history.file(edit("two"));

        assertThat(history.canRedo()).isFalse();
        assertThat(labels(history.all())).containsExactly("two");
    }

    @Test
    void should_drop_the_oldest_edit_past_its_limit()
    {
        EditHistory history = new EditHistory(2);
        history.file(edit("one"));
        history.file(edit("two"));
        history.file(edit("three"));

        assertThat(labels(history.all())).containsExactly("two", "three");
    }

    @Test
    void should_list_undone_edits_after_the_ones_in_effect_with_the_earliest_first()
    {
        EditHistory history = new EditHistory(10);
        history.file(edit("one"));
        history.file(edit("two"));
        history.file(edit("three"));
        history.undo(target);
        history.undo(target);

        assertThat(labels(history.all())).containsExactly("one", "two", "three");
        assertThat(history.size()).isEqualTo(3);
        assertThat(history.position()).isEqualTo(1);
    }

    @Test
    void should_rewind_by_undoing_the_edits_after_a_position()
    {
        EditHistory history = new EditHistory(10);
        history.file(moving("one", "a1"));
        history.file(moving("two", "a2"));
        history.file(moving("three", "a3"));

        EditHistory.Rewound rewound = history.rewind(1, false, target);

        assertThat(history.position()).isEqualTo(1);
        assertThat(rewound.steps()).isEqualTo(2);
        assertThat(rewound.moved()).containsOnlyKeys("a2", "a3");
        assertThat(rewound.graph()).isFalse();
        assertThat(rewound.pose()).isNull();
    }

    @Test
    void should_rewind_forwards_by_redoing_the_undone_edits()
    {
        EditHistory history = new EditHistory(10);
        history.file(moving("one", "a1"));
        history.file(moving("two", "a2"));
        history.rewind(0, false, target);

        EditHistory.Rewound rewound = history.rewind(2, false, target);

        assertThat(history.position()).isEqualTo(2);
        assertThat(rewound.steps()).isEqualTo(2);
        assertThat(target.positions).containsEntry("a1", THERE).containsEntry("a2", THERE);
    }

    @Test
    void should_forget_the_undone_edits_when_rewinding_with_discard()
    {
        EditHistory history = new EditHistory(10);
        history.file(edit("one"));
        history.file(edit("two"));

        history.rewind(1, true, target);

        assertThat(labels(history.all())).containsExactly("one");
        assertThat(history.canRedo()).isFalse();
    }

    @Test
    void should_refuse_a_position_the_history_does_not_have()
    {
        EditHistory history = new EditHistory(10);
        history.file(edit("one"));

        assertThatThrownBy(() -> history.validate(2, target)).isInstanceOf(StudioEditException.class).hasMessageContaining("no point 2");
        assertThatThrownBy(() -> history.validate(-1, target)).isInstanceOf(StudioEditException.class);
    }

    @Test
    void should_refuse_a_rewind_across_an_edit_of_a_locked_atom_before_changing_anything()
    {
        EditHistory history = new EditHistory(10);
        history.file(moving("one", "a1"));
        history.file(moving("two", "a2"));
        target.locked.add("a1");

        assertThatThrownBy(() -> history.validate(0, target)).isInstanceOf(StudioEditException.class).extracting(refused -> ((StudioEditException) refused).code()).isEqualTo("LOCKED");

        assertThat(history.position()).isEqualTo(2);
        assertThat(target.positions).isEmpty();
    }

    @Test
    void should_forget_everything_when_cleared()
    {
        EditHistory history = new EditHistory(10);
        history.file(edit("one"));
        history.undo(target);

        history.clear();

        assertThat(history.size()).isZero();
        assertThat(history.canUndo()).isFalse();
        assertThat(history.canRedo()).isFalse();
    }
}
