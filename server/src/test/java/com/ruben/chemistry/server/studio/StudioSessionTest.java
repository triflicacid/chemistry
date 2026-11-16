package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.server.molecule.StudioSnapshot;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Quaternion;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class StudioSessionTest
{
    private final MoleculeStore molecules = new MoleculeStore();
    private final MoleculeParser parser = new MoleculeParser();

    @Test
    void makes_every_counted_hydrogen_an_atom_of_its_own()
    {
        StudioSession session = open("CCO");

        assertThat(session.molecule().atomCount()).isEqualTo(9);
        assertThat(session.molecule().atoms().stream().filter(atom -> atom.element().equals("H")).count()).isEqualTo(6);
        assertThat(session.molecule().bondCount()).isEqualTo(8);
    }

    @Test
    void tells_the_hydrogens_the_source_wrote_from_the_ones_it_counted()
    {
        StudioSession session = open("[2H]CO");

        List<String> counted = ids(session).stream().filter(session::wasImplicit).toList();

        // the deuterium is written as an atom; two hydrogens on the carbon and one on the
        // oxygen are not
        assertThat(session.molecule().atomCount()).isEqualTo(6);
        assertThat(counted).hasSize(3);
        assertThat(session.wasImplicit(session.atomId(session.molecule().atoms().getFirst()))).isFalse();
    }

    @Test
    void places_every_atom_of_the_molecule()
    {
        StudioSession session = open("CCO");

        Set<Vec3> positions = new HashSet<>();
        for (String id : ids(session))
        {
            positions.add(session.positionOf(id));
        }

        assertThat(positions).hasSize(9);
    }

    @Test
    void names_each_atom_and_bond_once()
    {
        StudioSession session = open("CCO");

        assertThat(new HashSet<>(ids(session))).hasSize(9);

        Set<String> bondIds = new HashSet<>();
        for (Bond bond : session.molecule().bonds())
        {
            bondIds.add(session.bondId(bond));
        }

        assertThat(bondIds).hasSize(8);
    }

    @Test
    void publishes_the_molecule_with_its_hydrogens_counted()
    {
        assertThat(open("CCO").smiles()).isEqualTo("CCO");
        assertThat(open("OC(=O)c1ccccc1").smiles()).isEqualTo("OC(=O)c1ccccc1");
        assertThat(open("N[C@@H](C)C(=O)O").smiles()).isEqualTo("N[C@@H](C)C(=O)O");
    }

    @Test
    void opens_at_version_one_having_saved_nothing()
    {
        StudioSession session = open("CCO");

        assertThat(session.version()).isEqualTo(1);
        assertThat(session.savedMoleculeId()).isNull();
        assertThat(session.locked()).isEmpty();
    }

    @Test
    void keeps_the_shape_it_embedded_on_the_molecule_it_opened()
    {
        StoredMolecule stored = store("CCO");
        assertThat(stored.studio().structure()).isNull();

        StudioSession session = StudioSession.open("session-1", stored, 1);

        assertThat(stored.studio().structure()).isNotNull();
        assertThat(session.sourceMoleculeId()).isEqualTo(stored.id());
    }

    @Test
    void opens_on_the_shape_the_molecule_already_has()
    {
        StoredMolecule stored = store("CCO");
        Structure3D structure = Embedder.embed(stored.molecule(), Embedder.Options.DEFAULT.withSeed(7));
        stored.setStudio(stored.studio().withStructure(structure));

        StudioSession session = StudioSession.open("session-1", stored, 1);

        List<Vec3> expected = structure.conformers().getFirst().positions();
        List<Atom> atoms = session.molecule().atoms();
        for (int atom = 0; atom < atoms.size(); atom++)
        {
            assertThat(session.positionOf(session.atomId(atoms.get(atom)))).isEqualTo(expected.get(atom));
        }

        assertThat(session.strain()).isEqualTo(structure.conformers().getFirst().strain());
    }

    @Test
    void refuses_a_conformer_count_it_will_not_embed()
    {
        StoredMolecule stored = store("CCO");

        assertThatThrownBy(() -> StudioSession.open("session-1", stored, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conformers");
        assertThatThrownBy(() -> StudioSession.open("session-1", stored, StudioSession.MAX_CONFORMERS + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuses_an_atom_or_bond_from_another_molecule()
    {
        StudioSession session = open("CCO");
        StudioSession other = open("CCO");
        Atom stranger = other.molecule().atoms().getFirst();

        assertThatThrownBy(() -> session.atomId(stranger)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> session.bondId(other.molecule().bonds().getFirst()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> session.positionOf("a99")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rebuilds_on_a_new_molecule_as_the_same_session_one_version_on()
    {
        StudioSession session = open("CCO");

        StudioSession rebuilt = session.rebuilt(parser.parse("CCN").molecule(), 1);

        assertThat(rebuilt.sessionId()).isEqualTo(session.sessionId());
        assertThat(rebuilt.sourceMoleculeId()).isEqualTo(session.sourceMoleculeId());
        assertThat(rebuilt.version()).isEqualTo(session.version() + 1);
        assertThat(rebuilt.smiles()).isEqualTo("CCN");
        assertThat(rebuilt.molecule().atomCount()).isEqualTo(10);
        assertThat(rebuilt.locked()).isEmpty();
    }

    @Test
    void rebuilds_having_saved_nothing()
    {
        StudioSession session = open("CCO");
        session.saved("some-molecule");

        StudioSession rebuilt = session.rebuilt(parser.parse("CCN").molecule(), 1);

        assertThat(rebuilt.savedMoleculeId()).isNull();
    }

    @Test
    void describes_its_geometry_as_the_structure_of_the_molecule_it_stores()
    {
        StudioSession session = open("CCO");
        String carbon = ids(session).getFirst();
        session.move(List.of(new StudioSession.Move(carbon, new Vec3(7, 8, 9))), true);

        Structure3D structure = session.structure();

        assertThat(session.collapsed().atomCount()).isEqualTo(3);
        assertThat(structure.sites()).hasSize(9);
        assertThat(structure.sites().stream().filter(Structure3D.Site::implied).count()).isEqualTo(6);
        assertThat(structure.conformers()).hasSize(1);
        assertThat(structure.conformers().getFirst().positions().getFirst()).isEqualTo(new Vec3(7, 8, 9));
    }

    @Test
    void reopens_on_the_geometry_it_described()
    {
        StudioSession session = open("CCO");
        session.move(List.of(new StudioSession.Move(ids(session).getFirst(), new Vec3(7, 8, 9))), true);
        StoredMolecule stored = molecules.add(com.ruben.chemistry.server.molecule.ParsedMolecule.written(session.collapsed()));
        stored.setStudio(stored.studio().withStructure(session.structure()));

        StudioSession reopened = StudioSession.open("again", stored, 1);

        assertThat(reopened.molecule().atomCount()).isEqualTo(9);
        for (String id : ids(session))
        {
            assertThat(reopened.positionOf(id)).isEqualTo(session.positionOf(id));
            assertThat(reopened.wasImplicit(id)).isEqualTo(session.wasImplicit(id));
        }
    }

    @Test
    void rebuilds_without_touching_the_session_it_replaces()
    {
        StudioSession session = open("CCO");

        session.rebuilt(parser.parse("CCN").molecule(), 1);

        assertThat(session.version()).isEqualTo(1);
        assertThat(session.smiles()).isEqualTo("CCO");
        assertThat(session.molecule().atomCount()).isEqualTo(9);
    }

    @Test
    void refuses_to_rebuild_with_a_conformer_count_it_will_not_embed()
    {
        StudioSession session = open("CCO");

        assertThatThrownBy(() -> session.rebuilt(parser.parse("CCN").molecule(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moves_atoms_and_bumps_the_version()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();

        long version = session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 2, 3))), true);

        assertThat(version).isEqualTo(2);
        assertThat(session.version()).isEqualTo(2);
        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 2, 3));
    }

    @Test
    void leaves_the_version_alone_when_nothing_moves()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();

        long version = session.move(List.of(new StudioSession.Move(atom, session.positionOf(atom))), true);

        assertThat(version).isEqualTo(1);
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void refuses_a_patch_whole_when_any_of_it_cannot_be_made()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        Vec3 before = session.positionOf(atom);

        assertThatThrownBy(() -> session.move(List.of(
                new StudioSession.Move(atom, new Vec3(1, 2, 3)),
                new StudioSession.Move("nope", Vec3.ZERO)), true))
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("UNKNOWN_ID"));
        assertThatThrownBy(() -> session.move(List.of(new StudioSession.Move(atom, new Vec3(Double.NaN, 0, 0))), true))
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("INVALID_MESSAGE"));

        assertThat(session.positionOf(atom)).isEqualTo(before);
        assertThat(session.version()).isEqualTo(1);
    }

    @Test
    void undoes_and_redoes_a_committed_move()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        Vec3 before = session.positionOf(atom);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 2, 3))), true);

        session.undo();
        assertThat(session.positionOf(atom)).isEqualTo(before);
        assertThat(session.canUndo()).isFalse();
        assertThat(session.canRedo()).isTrue();

        session.redo();
        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 2, 3));
        assertThat(session.canRedo()).isFalse();

        // an undo is a change too, so versions only ever grow
        assertThat(session.version()).isEqualTo(4);
    }

    @Test
    void undoes_a_drag_sent_as_several_patches_in_one_step()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        Vec3 before = session.positionOf(atom);

        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 0, 0))), false);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(2, 0, 0))), false);
        assertThat(session.canUndo()).isTrue();
        session.move(List.of(new StudioSession.Move(atom, new Vec3(3, 0, 0))), true);

        session.undo();

        assertThat(session.positionOf(atom)).isEqualTo(before);
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void goes_to_a_point_in_the_history_keeping_the_later_edits_to_redo()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        Vec3 start = session.positionOf(atom);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 0, 0))), true);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(2, 0, 0))), true);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(3, 0, 0))), true);

        StudioSession.Applied applied = session.rewind(1, false);

        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 0, 0));
        assertThat(applied.moved()).containsEntry(atom, new Vec3(1, 0, 0));
        assertThat(session.history().position()).isEqualTo(1);
        assertThat(session.history().entries()).extracting(StudioSession.HistoryEntry::label).containsExactly("Move atom", "Move atom", "Move atom");
        assertThat(session.history().entries()).allSatisfy(entry -> assertThat(entry.at()).isPositive());

        session.rewind(3, false);
        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(3, 0, 0));

        session.rewind(0, false);
        assertThat(session.positionOf(atom)).isEqualTo(start);
        assertThat(session.canRedo()).isTrue();
    }

    @Test
    void reverts_to_a_point_in_the_history_forgetting_the_later_edits()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 0, 0))), true);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(2, 0, 0))), true);

        session.rewind(1, true);

        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 0, 0));
        assertThat(session.canRedo()).isFalse();
        assertThat(session.history().entries()).hasSize(1);
    }

    @Test
    void refuses_a_point_that_is_not_in_the_history_and_a_move_over_a_locked_atom()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 0, 0))), true);

        assertThatThrownBy(() -> session.rewind(2, false))
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("INVALID_EDIT"));

        session.lock(List.of(atom), true);
        assertThatThrownBy(() -> session.rewind(0, false))
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("LOCKED"));
        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 0, 0));
    }

    @Test
    void clears_the_history_leaving_the_atoms_where_they_are()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 0, 0))), true);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(2, 0, 0))), true);
        session.undo();

        session.clearHistory();

        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 0, 0));
        assertThat(session.canUndo()).isFalse();
        assertThat(session.canRedo()).isFalse();
        assertThat(session.history().entries()).isEmpty();
    }

    @Test
    void cancels_an_edit_in_progress_instead_of_undoing_the_one_before()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 0, 0))), true);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(2, 0, 0))), false);

        session.undo();

        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 0, 0));
        assertThat(session.canUndo()).isTrue();
    }

    @Test
    void forgets_what_could_be_redone_once_a_new_edit_is_made()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 0, 0))), true);
        session.undo();

        session.move(List.of(new StudioSession.Move(atom, new Vec3(5, 0, 0))), true);

        assertThat(session.canRedo()).isFalse();
        assertThatThrownBy(session::redo)
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("NOTHING_TO_REDO"));
    }

    @Test
    void has_nothing_to_undo_when_nothing_has_been_done()
    {
        StudioSession session = open("CCO");

        assertThatThrownBy(session::undo)
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("NOTHING_TO_UNDO"));
    }

    @Test
    void locks_and_unlocks_atoms_one_version_on_each_time()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();

        assertThat(session.lock(List.of(atom), true)).isEqualTo(2);
        assertThat(session.locked()).containsExactly(atom);

        assertThat(session.lock(List.of(atom), true)).isEqualTo(2);
        assertThat(session.lock(List.of(atom), false)).isEqualTo(3);
        assertThat(session.locked()).isEmpty();
        assertThat(session.lock(List.of(atom), false)).isEqualTo(3);
    }

    @Test
    void refuses_to_move_a_locked_atom_until_it_is_unlocked()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        Vec3 before = session.positionOf(atom);
        session.lock(List.of(atom), true);

        assertThatThrownBy(() -> session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 2, 3))), true))
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("LOCKED"));
        assertThat(session.positionOf(atom)).isEqualTo(before);

        session.lock(List.of(atom), false);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 2, 3))), true);
        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 2, 3));
    }

    @Test
    void reopens_with_the_atoms_it_had_locked_still_locked()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        session.lock(List.of(ids.get(1), ids.getLast()), true);
        StoredMolecule stored = molecules.add(com.ruben.chemistry.server.molecule.ParsedMolecule.written(session.collapsed()));
        stored.setStudio(new StudioSnapshot(session.structure(), session.lockedSites(), Pose.IDENTITY, null));

        StudioSession reopened = StudioSession.open("again", stored, 1);

        assertThat(reopened.locked()).containsExactlyInAnyOrder(ids.get(1), ids.getLast());
        assertThat(reopened.version()).isEqualTo(1);
    }

    @Test
    void rebuilds_with_nothing_locked()
    {
        StudioSession session = open("CCO");
        session.lock(List.of(ids(session).getFirst()), true);

        assertThat(session.rebuilt(parser.parse("CC").molecule(), 1).locked()).isEmpty();
    }

    @Test
    void locks_none_of_the_atoms_when_one_is_unknown()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();

        assertThatThrownBy(() -> session.lock(List.of(atom, "nope"), true))
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("UNKNOWN_ID"));

        assertThat(session.locked()).isEmpty();
        assertThat(session.version()).isEqualTo(1);
    }

    @Test
    void refuses_an_undo_or_redo_that_would_move_a_locked_atom_and_keeps_the_edit()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        Vec3 before = session.positionOf(atom);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 2, 3))), true);
        session.lock(List.of(atom), true);

        assertThatThrownBy(session::undo)
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("LOCKED"));
        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 2, 3));

        session.lock(List.of(atom), false);
        session.undo();
        assertThat(session.positionOf(atom)).isEqualTo(before);

        session.lock(List.of(atom), true);
        assertThatThrownBy(session::redo)
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("LOCKED"));
        session.lock(List.of(atom), false);
        session.redo();
        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 2, 3));
    }

    @Test
    void takes_a_snapshot_of_the_positions_in_atom_order_at_the_version_they_are_at()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 2, 3))), true);

        StudioSession.Snapshot snapshot = session.snapshot();

        assertThat(snapshot.version()).isEqualTo(2);
        assertThat(snapshot.positions()).isEqualTo(ids(session).stream().map(session::positionOf).toList());
        assertThat(snapshot.positions().getFirst()).isEqualTo(new Vec3(1, 2, 3));
    }

    @Test
    void keeps_a_snapshot_as_it_was_when_the_session_moves_on()
    {
        StudioSession session = open("CCO");
        StudioSession.Snapshot before = session.snapshot();

        session.move(List.of(new StudioSession.Move(ids(session).getFirst(), new Vec3(9, 9, 9))), true);

        assertThat(before.version()).isEqualTo(1);
        assertThat(before.positions()).doesNotContain(new Vec3(9, 9, 9));
    }

    @Test
    void takes_what_an_optimisation_needs_at_the_version_it_is_at()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).get(3);
        session.lock(List.of(atom), true);

        StudioSession.OptimisationInput input = session.beginOptimisation();

        assertThat(input.version()).isEqualTo(2);
        assertThat(input.sites()).hasSize(9).contains(atom);
        assertThat(input.structure().sites()).hasSameSizeAs(input.sites());
        assertThat(input.lockedSites()).containsExactly(input.sites().indexOf(atom));
        assertThat(input.structure().conformers().getFirst().positions().get(input.sites().indexOf(atom))).isEqualTo(session.positionOf(atom));
    }

    @Test
    void refuses_to_take_an_optimisation_while_an_edit_is_in_progress()
    {
        StudioSession session = open("CCO");
        session.move(List.of(new StudioSession.Move(ids(session).getFirst(), new Vec3(1, 2, 3))), false);

        assertThatThrownBy(session::beginOptimisation)
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("BUSY"));
    }

    @Test
    void files_an_optimisation_as_one_edit_and_leaves_locked_atoms_where_they_are()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        session.lock(List.of(ids.get(1)), true);
        Vec3 held = session.positionOf(ids.get(1));

        StudioSession.Applied applied = session.optimised(2, Map.of(
                ids.get(0), new Vec3(1, 2, 3),
                ids.get(1), new Vec3(4, 5, 6),
                ids.get(2), new Vec3(7, 8, 9))).orElseThrow();

        assertThat(applied.version()).isEqualTo(3);
        assertThat(applied.moved()).containsOnlyKeys(ids.get(0), ids.get(2));
        assertThat(session.positionOf(ids.get(1))).isEqualTo(held);
        assertThat(session.canUndo()).isTrue();

        session.undo();
        assertThat(session.positionOf(ids.get(0))).isNotEqualTo(new Vec3(1, 2, 3));
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void ignores_an_optimisation_made_from_a_version_the_session_has_moved_on_from()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        Vec3 where = session.positionOf(atom);
        session.move(List.of(new StudioSession.Move(ids(session).get(1), new Vec3(1, 2, 3))), true);

        assertThat(session.optimised(1, Map.of(atom, new Vec3(7, 8, 9)))).isEmpty();

        assertThat(session.positionOf(atom)).isEqualTo(where);
        assertThat(session.version()).isEqualTo(2);
    }

    @Test
    void leaves_the_version_alone_when_an_optimisation_moves_nothing_that_matters()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        Vec3 where = session.positionOf(atom);

        StudioSession.Applied applied = session.optimised(1, Map.of(atom, where.plus(new Vec3(1e-9, 0, 0)))).orElseThrow();

        assertThat(applied.version()).isEqualTo(1);
        assertThat(applied.moved()).isEmpty();
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void starts_with_the_molecule_where_its_own_frame_is_the_scene()
    {
        assertThat(open("CCO").pose()).isEqualTo(Pose.IDENTITY);
    }

    @Test
    void moves_and_turns_the_molecule_without_changing_a_coordinate_inside_it()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        List<Vec3> before = ids.stream().map(session::positionOf).toList();
        Pose pose = new Pose(new Vec3(3, -2, 5), new Quaternion(1, 2, 3, 4).normalised());

        long version = session.transform(pose, true);

        assertThat(version).isEqualTo(2);
        assertThat(session.pose()).isEqualTo(pose);
        assertThat(ids.stream().map(session::positionOf).toList()).isEqualTo(before);
    }

    @Test
    void keeps_every_bond_length_the_same_in_the_scene_after_a_move()
    {
        StudioSession session = open("CCO");
        session.transform(new Pose(new Vec3(3, -2, 5), new Quaternion(1, 2, 3, 4).normalised()), true);

        for (Bond bond : session.molecule().bonds())
        {
            Vec3 first = session.positionOf(session.atomId(bond.first()));
            Vec3 second = session.positionOf(session.atomId(bond.second()));

            assertThat(session.pose().apply(first).distanceTo(session.pose().apply(second)))
                    .isCloseTo(first.distanceTo(second), org.assertj.core.api.Assertions.within(1e-9));
        }
    }

    @Test
    void scales_a_rotation_that_is_not_of_length_one()
    {
        StudioSession session = open("CCO");

        session.transform(new Pose(Vec3.ZERO, new Quaternion(0, 0, 0, 2)), true);

        assertThat(session.pose().rotation()).isEqualTo(Quaternion.IDENTITY);
    }

    @Test
    void leaves_the_version_alone_when_the_molecule_is_already_there()
    {
        StudioSession session = open("CCO");

        assertThat(session.transform(Pose.IDENTITY, true)).isEqualTo(1);
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void refuses_a_pose_that_is_not_a_rotation_or_not_finite()
    {
        StudioSession session = open("CCO");

        assertThatThrownBy(() -> session.transform(new Pose(Vec3.ZERO, new Quaternion(0, 0, 0, 0)), true))
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("INVALID_MESSAGE"));
        assertThatThrownBy(() -> session.transform(new Pose(new Vec3(Double.NaN, 0, 0), Quaternion.IDENTITY), true))
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("INVALID_MESSAGE"));

        assertThat(session.pose()).isEqualTo(Pose.IDENTITY);
        assertThat(session.version()).isEqualTo(1);
    }

    @Test
    void moves_a_molecule_with_locked_atoms_in_it()
    {
        StudioSession session = open("CCO");
        session.lock(List.of(ids(session).getFirst()), true);
        Pose pose = new Pose(new Vec3(1, 0, 0), Quaternion.IDENTITY);

        session.transform(pose, true);

        assertThat(session.pose()).isEqualTo(pose);
    }

    @Test
    void files_a_drag_of_the_molecule_as_one_edit_to_undo()
    {
        StudioSession session = open("CCO");
        session.transform(new Pose(new Vec3(1, 0, 0), Quaternion.IDENTITY), false);
        session.transform(new Pose(new Vec3(2, 0, 0), Quaternion.IDENTITY), false);
        session.transform(new Pose(new Vec3(3, 0, 0), Quaternion.IDENTITY), true);

        StudioSession.Applied undone = session.undo();

        assertThat(undone.pose()).isEqualTo(Pose.IDENTITY);
        assertThat(undone.moved()).isEmpty();
        assertThat(session.pose()).isEqualTo(Pose.IDENTITY);
        assertThat(session.canUndo()).isFalse();

        StudioSession.Applied redone = session.redo();

        assertThat(redone.pose().position()).isEqualTo(new Vec3(3, 0, 0));
        assertThat(session.pose()).isEqualTo(redone.pose());
    }

    @Test
    void cancels_a_drag_of_the_molecule_in_progress_when_undone()
    {
        StudioSession session = open("CCO");
        session.transform(new Pose(new Vec3(1, 0, 0), Quaternion.IDENTITY), false);

        assertThat(session.canUndo()).isTrue();
        StudioSession.Applied cancelled = session.undo();

        assertThat(cancelled.pose()).isEqualTo(Pose.IDENTITY);
        assertThat(session.pose()).isEqualTo(Pose.IDENTITY);
        assertThat(session.canUndo()).isFalse();
        assertThat(session.canRedo()).isFalse();
    }

    @Test
    void files_nothing_when_a_drag_of_the_molecule_ends_where_it_began()
    {
        StudioSession session = open("CCO");
        session.transform(new Pose(new Vec3(1, 0, 0), Quaternion.IDENTITY), false);
        session.transform(Pose.IDENTITY, true);

        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void undoes_an_atom_move_without_touching_where_the_molecule_sits()
    {
        StudioSession session = open("CCO");
        String atom = ids(session).getFirst();
        Vec3 before = session.positionOf(atom);
        session.move(List.of(new StudioSession.Move(atom, new Vec3(1, 2, 3))), true);
        Pose pose = new Pose(new Vec3(9, 0, 0), Quaternion.IDENTITY);
        session.transform(pose, true);

        session.undo();
        assertThat(session.pose()).isEqualTo(Pose.IDENTITY);
        assertThat(session.positionOf(atom)).isEqualTo(new Vec3(1, 2, 3));

        StudioSession.Applied second = session.undo();
        assertThat(second.pose()).isNull();
        assertThat(session.positionOf(atom)).isEqualTo(before);
    }

    @Test
    void reopens_where_the_molecule_was_left_and_rebuilds_back_at_the_origin()
    {
        StudioSession session = open("CCO");
        Pose pose = new Pose(new Vec3(1, 2, 3), new Quaternion(0, 0, 1, 1).normalised());
        session.transform(pose, true);
        StoredMolecule stored = molecules.add(com.ruben.chemistry.server.molecule.ParsedMolecule.written(session.collapsed()));
        stored.setStudio(new StudioSnapshot(session.structure(), Set.of(), session.pose(), null));

        StudioSession reopened = StudioSession.open("again", stored, 1);

        assertThat(reopened.pose()).isEqualTo(pose);
        assertThat(reopened.rebuilt(parser.parse("CC").molecule(), 1).pose()).isEqualTo(Pose.IDENTITY);
    }

    @Test
    void should_give_a_bond_a_length_as_one_edit_that_can_be_undone()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        String bond = session.bondId(session.molecule().bonds().getFirst());
        double before = session.positionOf(ids.get(0)).distanceTo(session.positionOf(ids.get(1)));

        StudioSession.Applied applied = session.setBondLength(bond, 2.0, ids.get(0), true);

        assertThat(session.positionOf(ids.get(0)).distanceTo(session.positionOf(ids.get(1)))).isCloseTo(2.0, within(1e-9));
        assertThat(applied.version()).isEqualTo(2);
        assertThat(applied.moved()).doesNotContainKey(ids.get(0)).containsKey(ids.get(1));
        assertThat(session.canUndo()).isTrue();

        session.undo();
        assertThat(session.positionOf(ids.get(0)).distanceTo(session.positionOf(ids.get(1)))).isCloseTo(before, within(1e-9));
    }

    @Test
    void should_carry_everything_beyond_a_bond_along_when_it_changes_length()
    {
        StudioSession session = open("CCO");
        Map<String, Double> lengths = bondLengths(session);
        String bond = session.bondId(session.molecule().bonds().getFirst());

        session.setBondLength(bond, 1.9, ids(session).get(0), true);

        // only the bond that was changed has a different length
        Map<String, Double> after = bondLengths(session);
        lengths.forEach((id, length) -> assertThat(after.get(id)).isCloseTo(id.equals(bond) ? 1.9 : length, within(1e-9)));
    }

    @Test
    void should_refuse_a_length_edit_that_would_move_a_locked_atom_but_not_one_on_the_held_side()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        String bond = session.bondId(session.molecule().bonds().getFirst());
        session.lock(List.of(ids.get(2)), true);

        assertThat(code(() -> session.setBondLength(bond, 2.0, ids.get(0), true))).isEqualTo("LOCKED");

        session.lock(List.of(ids.get(2)), false);
        session.lock(List.of(ids.get(0)), true);
        session.setBondLength(bond, 2.0, ids.get(0), true);
        assertThat(session.canUndo()).isTrue();
    }

    @Test
    void should_change_an_atoms_element_keeping_its_id_its_bonds_and_every_hydrogen()
    {
        StudioSession session = open("CCO");
        String oxygen = ids(session).get(2);
        List<String> bonds = session.molecule().bonds().stream().map(session::bondId).toList();
        Vec3 where = session.positionOf(oxygen);

        StudioSession.Applied applied = session.setAtom(oxygen, "N", null, null);

        assertThat(applied.graph()).isTrue();
        assertThat(applied.version()).isEqualTo(2);
        assertThat(session.molecule().atoms().get(2).element()).isEqualTo("N");
        assertThat(ids(session).get(2)).isEqualTo(oxygen);
        assertThat(session.molecule().bonds().stream().map(session::bondId).toList()).isEqualTo(bonds);
        assertThat(session.molecule().atomCount()).isEqualTo(9);
        assertThat(session.positionOf(oxygen)).isEqualTo(where);
        assertThat(session.smiles()).isNotEqualTo("CCO");
    }

    @Test
    void should_add_change_and_remove_a_charge_and_an_isotope()
    {
        StudioSession session = open("CCO");
        String carbon = ids(session).get(0);

        session.setAtom(carbon, null, 1, 13);
        assertThat(session.molecule().atoms().getFirst().charge()).isEqualTo(1);
        assertThat(session.molecule().atoms().getFirst().isotope()).hasValue(13);

        session.setAtom(carbon, null, -2, 14);
        assertThat(session.molecule().atoms().getFirst().charge()).isEqualTo(-2);
        assertThat(session.molecule().atoms().getFirst().isotope()).hasValue(14);

        // zero is how a charge or an isotope is taken away
        session.setAtom(carbon, null, 0, 0);
        assertThat(session.molecule().atoms().getFirst().charge()).isZero();
        assertThat(session.molecule().atoms().getFirst().isotope()).isEmpty();
        assertThat(session.molecule().atoms().getFirst().element()).isEqualTo("C");
    }

    @Test
    void should_accept_an_atom_that_is_chemically_odd()
    {
        StudioSession session = open("CCO");
        String hydrogen = ids(session).get(3);

        session.setAtom(hydrogen, null, 3, 999);

        assertThat(session.molecule().atoms().get(3).charge()).isEqualTo(3);
        assertThat(session.smiles()).isNotBlank();
        assertThat(session.collapsed().atoms()).isNotEmpty();
    }

    @Test
    void should_undo_and_redo_an_attribute_edit_as_one_step_that_keeps_the_atoms_id()
    {
        StudioSession session = open("CCO");
        String oxygen = ids(session).get(2);
        String before = session.smiles();

        session.setAtom(oxygen, "S", null, null);
        String edited = session.smiles();

        StudioSession.Applied undone = session.undo();
        assertThat(undone.graph()).isTrue();
        assertThat(session.molecule().atoms().get(2).element()).isEqualTo("O");
        assertThat(ids(session).get(2)).isEqualTo(oxygen);
        assertThat(session.smiles()).isEqualTo(before);
        assertThat(session.collapsed().atoms().stream().map(Atom::element)).doesNotContain("S");
        assertThat(session.canUndo()).isFalse();

        StudioSession.Applied redone = session.redo();
        assertThat(redone.graph()).isTrue();
        assertThat(session.molecule().atoms().get(2).element()).isEqualTo("S");
        assertThat(session.smiles()).isEqualTo(edited);
        assertThat(session.collapsed().atoms().stream().map(Atom::element)).contains("S");
    }

    @Test
    void should_keep_position_edits_either_side_of_an_attribute_edit_undoable_in_order()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        Vec3 start = session.positionOf(ids.get(0));
        Vec3 moved = new Vec3(start.x() + 1, start.y(), start.z());

        session.move(List.of(new StudioSession.Move(ids.get(0), moved)), true);
        session.setAtom(ids.get(2), "N", null, null);
        session.rewind(0, false);

        assertThat(session.positionOf(ids.get(0))).isEqualTo(start);
        assertThat(session.molecule().atoms().get(2).element()).isEqualTo("O");

        StudioSession.Applied forward = session.rewind(2, false);
        assertThat(forward.graph()).isTrue();
        assertThat(session.positionOf(ids.get(0))).isEqualTo(moved);
        assertThat(session.molecule().atoms().get(2).element()).isEqualTo("N");
    }

    @Test
    void should_leave_the_version_alone_when_an_attribute_edit_changes_nothing()
    {
        StudioSession session = open("CCO");

        StudioSession.Applied applied = session.setAtom(ids(session).get(2), "O", 0, 0);

        assertThat(applied.version()).isEqualTo(1);
        assertThat(applied.graph()).isFalse();
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void should_refuse_an_attribute_edit_it_cannot_represent_or_that_touches_a_locked_atom()
    {
        StudioSession session = open("CCO");
        String oxygen = ids(session).get(2);

        assertThat(code(() -> session.setAtom("nope", "N", null, null))).isEqualTo("UNKNOWN_ID");
        assertThat(code(() -> session.setAtom(oxygen, "Zz", null, null))).isEqualTo("INVALID_EDIT");
        assertThat(code(() -> session.setAtom(oxygen, null, null, -1))).isEqualTo("INVALID_EDIT");
        assertThat(code(() -> session.setAtom(oxygen, null, null, null))).isEqualTo("INVALID_EDIT");

        session.lock(List.of(oxygen), true);
        long locked = session.version();
        assertThat(code(() -> session.setAtom(oxygen, "N", null, null))).isEqualTo("LOCKED");
        assertThat(session.version()).isEqualTo(locked);
    }

    @Test
    void should_refuse_to_undo_an_attribute_edit_of_an_atom_that_has_been_locked_since()
    {
        StudioSession session = open("CCO");
        String oxygen = ids(session).get(2);
        session.setAtom(oxygen, "N", null, null);
        session.lock(List.of(oxygen), true);

        assertThat(code(session::undo)).isEqualTo("LOCKED");
        assertThat(session.molecule().atoms().get(2).element()).isEqualTo("N");
    }

    @Test
    void should_refuse_an_attribute_edit_while_a_drag_is_in_progress()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        Vec3 at = session.positionOf(ids.get(0));
        session.move(List.of(new StudioSession.Move(ids.get(0), new Vec3(at.x() + 1, at.y(), at.z()))), false);

        assertThat(code(() -> session.setAtom(ids.get(2), "N", null, null))).isEqualTo("BUSY");
    }

    @Test
    void should_add_a_free_atom_where_it_is_put_and_undo_and_redo_it_with_the_same_name()
    {
        StudioSession session = open("CCO");
        Vec3 where = new Vec3(5, 5, 5);

        StudioSession.Created created = session.addAtom("N", null, null, where);
        String added = created.atoms().getFirst();

        assertThat(created.bonds()).isEmpty();
        assertThat(created.applied().graph()).isTrue();
        assertThat(session.molecule().atomCount()).isEqualTo(10);
        assertThat(session.molecule().bondCount()).isEqualTo(8);
        assertThat(session.positionOf(added)).isEqualTo(where);
        assertThat(session.smiles()).contains(".");
        assertThat(session.molecule().totalHydrogenCount(session.molecule().atoms().getLast())).isZero();

        session.undo();
        assertThat(session.molecule().atomCount()).isEqualTo(9);
        assertThat(session.smiles()).isEqualTo("CCO");

        session.redo();
        assertThat(ids(session)).contains(added);
        assertThat(session.positionOf(added)).isEqualTo(where);
    }

    @Test
    void should_attach_a_new_atom_a_bond_length_away_by_the_order_asked_and_leave_every_hydrogen()
    {
        StudioSession session = open("CCO");
        String oxygen = ids(session).get(2);
        long hydrogens = session.molecule().atoms().stream().filter(atom -> atom.element().equals("H")).count();

        StudioSession.Created created = session.addAtom("C", oxygen, BondOrder.DOUBLE, null);
        String added = created.atoms().getFirst();

        assertThat(created.bonds()).hasSize(1);
        assertThat(session.molecule().atoms().stream().filter(atom -> atom.element().equals("H")).count()).isEqualTo(hydrogens);
        Bond bond = session.molecule().bonds().getLast();
        assertThat(session.bondId(bond)).isEqualTo(created.bonds().getFirst());
        assertThat(bond.order()).isEqualTo(BondOrder.DOUBLE);
        assertThat(session.positionOf(oxygen).distanceTo(session.positionOf(added))).isBetween(1.2, 1.7);
    }

    @Test
    void should_leave_a_new_atom_clear_of_its_neighbours_wherever_it_is_attached()
    {
        // methane's carbon, a carbon with one neighbour, and one with two, each fully crowded or not
        for (String smiles : List.of("C", "CC", "C=O", "CCO", "C(C)(C)(C)C", "c1ccccc1"))
        {
            StudioSession session = open(smiles);
            String target = ids(session).get(0);
            List<String> neighbours = session.molecule().neighboursOf(session.molecule().atoms().getFirst()).stream().map(session::atomId).toList();

            String added = session.addAtom("N", target, null, null).atoms().getFirst();

            for (String neighbour : neighbours)
            {
                assertThat(session.positionOf(added).distanceTo(session.positionOf(neighbour)))
                        .as("%s: distance from %s", smiles, neighbour).isGreaterThan(1.2);
            }
        }
    }

    @Test
    void should_never_use_a_name_twice_even_after_an_undo()
    {
        StudioSession session = open("CCO");

        String first = session.addAtom("N", null, null, new Vec3(5, 0, 0)).atoms().getFirst();
        session.undo();
        String second = session.addAtom("N", null, null, new Vec3(5, 0, 0)).atoms().getFirst();

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void should_bond_two_atoms_where_they_stand_and_refuse_a_second_bond_between_them()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        Vec3 first = session.positionOf(ids.get(0));

        StudioSession.Created created = session.addBond(ids.get(0), ids.get(2), BondOrder.SINGLE);

        assertThat(created.bonds()).hasSize(1);
        assertThat(session.molecule().bondCount()).isEqualTo(9);
        assertThat(session.positionOf(ids.get(0))).isEqualTo(first);

        assertThat(code(() -> session.addBond(ids.get(0), ids.get(2), null))).isEqualTo("INVALID_EDIT");
        assertThat(code(() -> session.addBond(ids.get(0), ids.get(0), null))).isEqualTo("INVALID_EDIT");

        session.undo();
        assertThat(session.molecule().bondCount()).isEqualTo(8);
    }

    @Test
    void should_change_a_bond_order_keeping_the_bond_and_its_name()
    {
        StudioSession session = open("CCO");
        String bond = session.bondId(session.molecule().bonds().getFirst());

        StudioSession.Applied applied = session.setBondOrder(bond, BondOrder.DOUBLE);

        assertThat(applied.graph()).isTrue();
        assertThat(session.molecule().bonds().getFirst().order()).isEqualTo(BondOrder.DOUBLE);
        assertThat(session.bondId(session.molecule().bonds().getFirst())).isEqualTo(bond);

        long version = session.version();
        assertThat(session.setBondOrder(bond, BondOrder.DOUBLE).version()).isEqualTo(version);

        session.undo();
        assertThat(session.molecule().bonds().getFirst().order()).isEqualTo(BondOrder.SINGLE);
    }

    @Test
    void should_delete_an_atom_and_its_bonds_but_leave_its_hydrogens_unless_asked()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        Vec3 where = session.positionOf(ids.get(1));

        session.deleteAtoms(List.of(ids.get(1)), false);

        // the middle carbon's two hydrogens stay, now bonded to nothing
        assertThat(session.molecule().atomCount()).isEqualTo(8);
        assertThat(session.molecule().bondCount()).isEqualTo(8 - 4);
        assertThat(ids(session)).doesNotContain(ids.get(1)).contains(ids.get(6), ids.get(7));

        session.undo();
        assertThat(ids(session)).isEqualTo(ids);
        assertThat(session.positionOf(ids.get(1))).isEqualTo(where);
        assertThat(session.molecule().bondCount()).isEqualTo(8);

        session.deleteAtoms(List.of(ids.get(1)), true);
        assertThat(session.molecule().atomCount()).isEqualTo(6);
        assertThat(ids(session)).doesNotContain(ids.get(1), ids.get(6), ids.get(7));
    }

    @Test
    void should_delete_bonds_and_leave_their_atoms()
    {
        StudioSession session = open("CCO");
        String bond = session.bondId(session.molecule().bonds().getFirst());

        session.deleteBonds(List.of(bond));

        assertThat(session.molecule().atomCount()).isEqualTo(9);
        assertThat(session.molecule().bondCount()).isEqualTo(7);
        assertThat(session.molecule().isConnected()).isFalse();

        session.undo();
        assertThat(session.molecule().bondCount()).isEqualTo(8);
        assertThat(session.bondId(session.molecule().bonds().getFirst())).isEqualTo(bond);
    }

    @Test
    void should_still_write_and_store_a_structure_that_is_chemically_nonsense()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);

        // a carbon with a fifth bond, a hydrogen bonded to two atoms, and a bond between two hydrogens
        session.addAtom("N", ids.get(0), BondOrder.TRIPLE, null);
        session.addBond(ids.get(3), ids.get(2), BondOrder.SINGLE);
        session.addBond(ids.get(3), ids.get(7), BondOrder.DOUBLE);
        session.addAtom("H", null, null, new Vec3(9, 9, 9));

        assertThat(session.smiles()).isNotBlank();
        assertThat(session.collapsed().atomCount()).isPositive();
        assertThat(session.structure().conformers()).hasSize(1);
        assertThat(session.structure().conformers().getFirst().positions()).hasSize(session.structure().sites().size());
    }

    @Test
    void should_drop_stereo_around_an_edit_and_keep_it_elsewhere()
    {
        String alanine = "N[C@@H](C)C(=O)O";
        StudioSession untouched = open(alanine);
        StudioSession far = open(alanine);
        StudioSession disturbed = open(alanine);

        // a bond to a new atom far from the stereocentre
        String carboxylOxygen = ids(far).get(4);
        far.addAtom("C", carboxylOxygen, null, null);
        // and one at the stereocentre itself, which leaves its chirality with nothing to be relative to
        disturbed.addAtom("C", ids(disturbed).get(1), null, null);

        assertThat(untouched.smiles()).contains("@");
        assertThat(far.smiles()).contains("@");
        assertThat(disturbed.smiles()).doesNotContain("@");
        assertThat(disturbed.collapsed().atomCount()).isPositive();
    }

    @Test
    void should_refuse_a_graph_edit_that_touches_a_locked_atom_or_names_what_is_not_there()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        String bond = session.bondId(session.molecule().bonds().getFirst());
        session.lock(List.of(ids.get(0)), true);
        long version = session.version();

        assertThat(code(() -> session.addAtom("N", ids.get(0), null, null))).isEqualTo("LOCKED");
        assertThat(code(() -> session.addBond(ids.get(0), ids.get(2), null))).isEqualTo("LOCKED");
        assertThat(code(() -> session.setBondOrder(bond, BondOrder.DOUBLE))).isEqualTo("LOCKED");
        assertThat(code(() -> session.deleteAtoms(List.of(ids.get(0)), false))).isEqualTo("LOCKED");
        assertThat(code(() -> session.deleteBonds(List.of(bond)))).isEqualTo("LOCKED");
        // a hydrogen carried with an atom is refused too, when it is the one that is locked
        session.lock(List.of(ids.get(0)), false);
        session.lock(List.of(ids.get(6)), true);
        assertThat(code(() -> session.deleteAtoms(List.of(ids.get(1)), true))).isEqualTo("LOCKED");

        assertThat(code(() -> session.addAtom("Zz", null, null, new Vec3(0, 0, 0)))).isEqualTo("INVALID_EDIT");
        assertThat(code(() -> session.addAtom("N", "nope", null, null))).isEqualTo("UNKNOWN_ID");
        assertThat(code(() -> session.addAtom("N", null, null, null))).isEqualTo("INVALID_MESSAGE");
        assertThat(code(() -> session.deleteBonds(List.of("nope")))).isEqualTo("UNKNOWN_ID");
        assertThat(code(() -> session.deleteAtoms(List.of(), false))).isEqualTo("INVALID_EDIT");
        assertThat(session.version()).isEqualTo(version + 2);
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void should_refuse_to_undo_a_graph_edit_of_an_atom_locked_since()
    {
        StudioSession session = open("CCO");
        String added = session.addAtom("N", null, null, new Vec3(5, 0, 0)).atoms().getFirst();
        session.lock(List.of(added), true);

        assertThat(code(session::undo)).isEqualTo("LOCKED");
        assertThat(ids(session)).contains(added);
    }

    @Test
    void should_refuse_a_graph_edit_while_a_drag_is_in_progress()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        Vec3 at = session.positionOf(ids.get(0));
        session.move(List.of(new StudioSession.Move(ids.get(0), new Vec3(at.x() + 1, at.y(), at.z()))), false);

        assertThat(code(() -> session.addAtom("N", null, null, new Vec3(5, 0, 0)))).isEqualTo("BUSY");
        assertThat(code(() -> session.deleteAtoms(List.of(ids.get(1)), false))).isEqualTo("BUSY");
    }

    @Test
    void should_keep_moves_either_side_of_a_graph_edit_undoable_in_order()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        Vec3 start = session.positionOf(ids.get(0));
        Vec3 moved = new Vec3(start.x() + 1, start.y(), start.z());

        session.move(List.of(new StudioSession.Move(ids.get(0), moved)), true);
        String added = session.addAtom("N", ids.get(2), null, null).atoms().getFirst();
        session.move(List.of(new StudioSession.Move(added, new Vec3(7, 7, 7))), true);

        session.undo();
        assertThat(session.positionOf(added)).isNotEqualTo(new Vec3(7, 7, 7));
        session.undo();
        assertThat(ids(session)).doesNotContain(added);
        assertThat(session.positionOf(ids.get(0))).isEqualTo(moved);
        session.undo();
        assertThat(session.positionOf(ids.get(0))).isEqualTo(start);

        session.rewind(3, false);
        assertThat(session.positionOf(added)).isEqualTo(new Vec3(7, 7, 7));
        assertThat(session.positionOf(ids.get(0))).isEqualTo(moved);
    }

    @Test
    void should_refuse_a_bond_edit_that_names_something_it_does_not_hold_or_is_not_allowed()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        String bond = session.bondId(session.molecule().bonds().getFirst());
        long version = session.version();

        assertThat(code(() -> session.setBondLength("b99", 1.5, null, true))).isEqualTo("UNKNOWN_ID");
        assertThat(code(() -> session.setBondLength(bond, 1.5, "a99", true))).isEqualTo("UNKNOWN_ID");
        assertThat(code(() -> session.setBondLength(bond, 50, null, true))).isEqualTo("INVALID_EDIT");
        assertThat(code(() -> session.setBondLength(bond, Double.NaN, null, true))).isEqualTo("INVALID_EDIT");
        assertThat(code(() -> session.setBondLength(bond, 1.5, ids.get(2), true))).isEqualTo("INVALID_EDIT");
        assertThat(session.version()).isEqualTo(version);
    }

    @Test
    void should_leave_the_version_alone_when_a_bond_already_has_the_length_asked_for()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        String bond = session.bondId(session.molecule().bonds().getFirst());
        double length = session.positionOf(ids.get(0)).distanceTo(session.positionOf(ids.get(1)));

        StudioSession.Applied applied = session.setBondLength(bond, length, null, true);

        assertThat(applied.version()).isEqualTo(1);
        assertThat(applied.moved()).isEmpty();
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void should_file_a_drag_of_a_bond_as_one_edit_however_many_changes_it_took()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        String bond = session.bondId(session.molecule().bonds().getFirst());
        Map<String, Double> before = bondLengths(session);

        session.setBondLength(bond, 1.7, ids.get(0), false);
        session.setBondLength(bond, 1.9, ids.get(0), false);

        // the last of the drag puts nothing further anywhere, and still ends the edit
        StudioSession.Applied ended = session.setBondLength(bond, 1.9, ids.get(0), true);
        assertThat(ended.moved()).isEmpty();
        assertThat(session.canUndo()).isTrue();
        assertThat(bondLengths(session).get(bond)).isCloseTo(1.9, within(1e-9));

        session.undo();
        assertThat(session.canUndo()).isFalse();
        bondLengths(session).forEach((id, length) -> assertThat(length).isCloseTo(before.get(id), within(1e-9)));
    }

    @Test
    void should_file_a_drag_of_a_torsion_as_one_edit_to_undo()
    {
        StudioSession session = open("CCO");
        Atom first = session.molecule().atoms().get(0);
        Atom second = session.molecule().atoms().get(1);
        String bond = session.bondId(session.molecule().bondBetween(first, second).orElseThrow());
        String from = session.atomId(session.molecule().neighboursOf(first).stream().filter(atom -> atom.element().equals("H")).findFirst().orElseThrow());
        String to = session.atomId(session.molecule().atoms().get(2));
        String held = session.atomId(first);
        double start = Vec3.torsion(session.positionOf(from), session.positionOf(held), session.positionOf(session.atomId(second)), session.positionOf(to));

        session.setBondTorsion(bond, from, to, start + 40, held, false);
        session.setBondTorsion(bond, from, to, start + 80, held, false);
        session.setBondTorsion(bond, from, to, start + 30, held, false);
        session.setBondTorsion(bond, from, to, start + 30, held, true);

        session.undo();

        assertThat(session.canUndo()).isFalse();
        assertThat(Vec3.torsion(session.positionOf(from), session.positionOf(held), session.positionOf(session.atomId(second)), session.positionOf(to)))
                .isCloseTo(start, within(1e-9));
    }

    @Test
    void should_work_each_change_of_a_drag_out_from_where_the_drag_began()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        String bond = session.bondId(session.molecule().bonds().getFirst());
        Vec3 held = session.positionOf(ids.get(0));
        Vec3 start = session.positionOf(ids.get(1));

        // through zero to the other side and back, none of it committed, each change saying where the whole drag has got to
        session.setBondLength(bond, 2.0, ids.get(0), false);
        session.setBondLength(bond, -0.8, ids.get(0), false);
        assertThat(session.positionOf(ids.get(1)).distanceTo(held)).isCloseTo(0.8, within(1e-9));
        assertThat(session.positionOf(ids.get(1)).minus(held).dot(start.minus(held))).isLessThan(0);

        session.setBondLength(bond, 1.2, ids.get(0), true);
        assertThat(session.positionOf(ids.get(1)).distanceTo(held)).isCloseTo(1.2, within(1e-9));
        assertThat(session.positionOf(ids.get(1)).minus(held).dot(start.minus(held))).isGreaterThan(0);

        session.undo();
        assertThat(session.positionOf(ids.get(1))).isEqualTo(start);
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void should_file_nothing_when_a_drag_comes_back_to_where_it_began()
    {
        StudioSession session = open("CCO");
        Atom first = session.molecule().atoms().get(0);
        Atom second = session.molecule().atoms().get(1);
        String bond = session.bondId(session.molecule().bondBetween(first, second).orElseThrow());
        String from = session.atomId(session.molecule().neighboursOf(first).stream().filter(atom -> atom.element().equals("H")).findFirst().orElseThrow());
        String to = session.atomId(session.molecule().atoms().get(2));
        String held = session.atomId(first);
        double start = Vec3.torsion(session.positionOf(from), session.positionOf(held), session.positionOf(session.atomId(second)), session.positionOf(to));

        session.setBondTorsion(bond, from, to, start + 40, held, false);
        session.setBondTorsion(bond, from, to, start + 80, held, false);
        session.setBondTorsion(bond, from, to, start, held, false);
        session.setBondTorsion(bond, from, to, start, held, true);

        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void should_put_back_atoms_an_earlier_change_of_a_drag_moved_that_a_later_one_does_not()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        String bond = session.bondId(session.molecule().bonds().getFirst());
        Vec3 start = session.positionOf(ids.get(1));

        // holding the first atom moves the second and what is beyond it, and holding the second moves the first and what is beyond it
        session.setBondLength(bond, 2.0, ids.get(0), false);
        session.setBondLength(bond, 2.0, ids.get(1), false);

        assertThat(session.positionOf(ids.get(1))).isEqualTo(start);
        assertThat(session.positionOf(ids.get(0)).distanceTo(start)).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void should_swing_an_angle_past_a_straight_line_and_treat_a_whole_turn_more_as_the_same()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);

        session.setAngle(ids.get(1), ids.get(0), ids.get(2), 1, ids.get(0), true);
        Vec3 one = session.positionOf(ids.get(2));
        session.undo();

        session.setAngle(ids.get(1), ids.get(0), ids.get(2), 361, ids.get(0), true);
        assertThat(session.positionOf(ids.get(2)).distanceTo(one)).isLessThan(1e-9);
        session.undo();

        session.setAngle(ids.get(1), ids.get(0), ids.get(2), 200, ids.get(0), true);
        assertThat(angleAt(session, ids.get(0), ids.get(1), ids.get(2))).isCloseTo(160, within(1e-6));
    }

    @Test
    void should_give_a_bond_a_torsion_keeping_every_bond_length()
    {
        StudioSession session = open("CCO");
        Map<String, Double> lengths = bondLengths(session);
        Atom first = session.molecule().atoms().get(0);
        Atom second = session.molecule().atoms().get(1);
        String bond = session.bondId(session.molecule().bondBetween(first, second).orElseThrow());
        String from = session.atomId(session.molecule().neighboursOf(first).stream().filter(atom -> atom.element().equals("H")).findFirst().orElseThrow());
        String to = session.atomId(session.molecule().atoms().get(2));
        String firstId = session.atomId(first);
        String secondId = session.atomId(second);

        StudioSession.Applied applied = session.setBondTorsion(bond, from, to, 60, firstId, true);

        assertThat(Vec3.torsion(session.positionOf(from), session.positionOf(firstId), session.positionOf(secondId), session.positionOf(to)))
                .isCloseTo(60, within(1e-6));
        assertThat(applied.moved()).doesNotContainKeys(firstId, from);
        bondLengths(session).forEach((id, length) -> assertThat(length).isCloseTo(lengths.get(id), within(1e-9)));

        session.undo();
        bondLengths(session).forEach((id, length) -> assertThat(length).isCloseTo(lengths.get(id), within(1e-9)));
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void should_refuse_to_turn_a_ring_bond_or_measure_between_the_wrong_atoms()
    {
        StudioSession ring = open("C1CC1");
        Atom first = ring.molecule().atoms().get(0);
        Atom second = ring.molecule().atoms().get(1);
        String bond = ring.bondId(ring.molecule().bondBetween(first, second).orElseThrow());
        String from = ring.atomId(ring.molecule().neighboursOf(first).stream().filter(atom -> atom.element().equals("H")).findFirst().orElseThrow());
        String to = ring.atomId(ring.molecule().neighboursOf(second).stream().filter(atom -> atom.element().equals("H")).findFirst().orElseThrow());
        assertThat(code(() -> ring.setBondTorsion(bond, from, to, 60, null, true))).isEqualTo("INVALID_EDIT");

        StudioSession chain = open("CCO");
        String middle = chain.bondId(chain.molecule().bonds().getFirst());
        assertThat(code(() -> chain.setBondTorsion(middle, ids(chain).get(2), ids(chain).get(0), 60, null, true))).isEqualTo("INVALID_EDIT");
        assertThat(code(() -> chain.setBondTorsion(middle, "a99", ids(chain).get(2), 60, null, true))).isEqualTo("UNKNOWN_ID");
        assertThat(chain.version()).isEqualTo(1);
    }

    @Test
    void should_give_an_atom_an_angle_as_one_edit_that_can_be_undone()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        Map<String, Double> lengths = bondLengths(session);
        double before = angleAt(session, ids.get(0), ids.get(1), ids.get(2));

        StudioSession.Applied applied = session.setAngle(ids.get(1), ids.get(0), ids.get(2), 120, ids.get(0), true);

        assertThat(angleAt(session, ids.get(0), ids.get(1), ids.get(2))).isCloseTo(120, within(1e-6));
        assertThat(applied.moved()).containsKey(ids.get(2)).doesNotContainKeys(ids.get(0), ids.get(1));
        bondLengths(session).forEach((id, length) -> assertThat(length).isCloseTo(lengths.get(id), within(1e-9)));

        session.undo();
        assertThat(angleAt(session, ids.get(0), ids.get(1), ids.get(2))).isCloseTo(before, within(1e-9));
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void should_file_a_drag_of_an_angle_as_one_edit_to_undo()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        double before = angleAt(session, ids.get(0), ids.get(1), ids.get(2));

        session.setAngle(ids.get(1), ids.get(0), ids.get(2), 100, ids.get(0), false);
        session.setAngle(ids.get(1), ids.get(0), ids.get(2), 130, ids.get(0), false);
        session.setAngle(ids.get(1), ids.get(0), ids.get(2), 130, ids.get(0), true);
        assertThat(angleAt(session, ids.get(0), ids.get(1), ids.get(2))).isCloseTo(130, within(1e-6));

        session.undo();
        assertThat(angleAt(session, ids.get(0), ids.get(1), ids.get(2))).isCloseTo(before, within(1e-9));
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void should_refuse_an_angle_that_would_move_a_locked_atom_but_not_one_on_the_held_side()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        session.lock(List.of(ids.get(2)), true);
        assertThat(code(() -> session.setAngle(ids.get(1), ids.get(0), ids.get(2), 120, ids.get(0), true))).isEqualTo("LOCKED");

        session.lock(List.of(ids.get(2)), false);
        session.lock(List.of(ids.get(0)), true);
        session.setAngle(ids.get(1), ids.get(0), ids.get(2), 120, ids.get(0), true);
        assertThat(session.canUndo()).isTrue();
    }

    @Test
    void should_refuse_an_angle_edit_that_is_not_allowed_or_names_something_it_does_not_hold()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        long version = session.version();

        assertThat(code(() -> session.setAngle("a99", ids.get(0), ids.get(2), 120, null, true))).isEqualTo("UNKNOWN_ID");
        assertThat(code(() -> session.setAngle(ids.get(1), ids.get(0), ids.get(2), Double.NaN, null, true))).isEqualTo("INVALID_EDIT");
        assertThat(code(() -> session.setAngle(ids.get(1), ids.get(0), ids.get(0), 120, null, true))).isEqualTo("INVALID_EDIT");
        assertThat(code(() -> session.setAngle(ids.get(2), ids.get(0), ids.get(1), 120, null, true))).isEqualTo("INVALID_EDIT");

        StudioSession ring = open("C1CC1");
        List<String> ringIds = ids(ring);
        assertThat(code(() -> ring.setAngle(ringIds.get(0), ringIds.get(1), ringIds.get(2), 100, null, true))).isEqualTo("INVALID_EDIT");
        assertThat(session.version()).isEqualTo(version);
    }

    /**
     * @param session a session
     * @param first an atom
     * @param centre the atom the angle is at
     * @param last another atom
     * @return the angle, in degrees
     */
    private static double angleAt(StudioSession session, String first, String centre, String last)
    {
        return Vec3.angle(session.positionOf(first), session.positionOf(centre), session.positionOf(last));
    }

    /**
     * @param session a session
     * @return the length of each of its bonds, by bond
     */
    private static Map<String, Double> bondLengths(StudioSession session)
    {
        Map<String, Double> lengths = new LinkedHashMap<>();
        for (Bond bond : session.molecule().bonds())
        {
            lengths.put(session.bondId(bond), session.positionOf(session.atomId(bond.first())).distanceTo(session.positionOf(session.atomId(bond.second()))));
        }
        return lengths;
    }

    /**
     * @param edit an edit the session should refuse
     * @return the code it refused with
     */
    private static String code(Runnable edit)
    {
        try
        {
            edit.run();
        }
        catch (StudioEditException refused)
        {
            return refused.code();
        }
        throw new AssertionError("The edit was not refused");
    }

    private static List<String> ids(StudioSession session)
    {
        return session.molecule().atoms().stream().map(session::atomId).toList();
    }

    /**
     * @param smiles a molecule
     * @return a session opened on it
     */
    private StudioSession open(String smiles)
    {
        return StudioSession.open("session-" + smiles.hashCode(), store(smiles), 1);
    }

    /**
     * @param smiles a molecule
     * @return it, stored
     */
    private StoredMolecule store(String smiles)
    {
        return molecules.add(parser.parse(smiles));
    }
}
