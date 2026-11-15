package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Quaternion;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(stored.latestStructure3d()).isEmpty();

        StudioSession session = StudioSession.open("session-1", stored, 1);

        assertThat(stored.latestStructure3d()).isPresent();
        assertThat(session.sourceMoleculeId()).isEqualTo(stored.id());
    }

    @Test
    void opens_on_the_shape_the_molecule_already_has()
    {
        StoredMolecule stored = store("CCO");
        Structure3D structure = Embedder.embed(stored.molecule(), Embedder.Options.DEFAULT.withSeed(7));
        stored.setLatestStructure3d(structure);

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
        stored.setLatestStructure3d(session.structure());

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
        stored.setLatestStructure3d(session.structure());
        stored.setLockedSites(session.lockedSites());

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
        stored.setLatestStructure3d(session.structure());
        stored.setPose(session.pose());

        StudioSession reopened = StudioSession.open("again", stored, 1);

        assertThat(reopened.pose()).isEqualTo(pose);
        assertThat(reopened.rebuilt(parser.parse("CC").molecule(), 1).pose()).isEqualTo(Pose.IDENTITY);
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
