package com.ruben.chemistry.render;

import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.space.Camera;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class ProjectedLayoutTest
{
    private static final DepictionOptions CONDENSED = DepictionOptions.defaults().withHydrogens(HydrogenStyle.CONDENSED);
    private static final Camera FRONT = Camera.orbit(Vec3.ZERO, 0, 0, 30, 0);

    @Test
    void best_view_draws_every_atom_and_bond_once()
    {
        Molecule molecule = Molecule.fromSmiles("C1CCCCC1O");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, CONDENSED).depiction();

        assertThat(depiction.atoms()).hasSize(7);
        assertThat(depiction.bonds()).hasSize(7);
        assertThat(depiction.rings()).hasSize(1);
    }

    @Test
    void separate_hydrogens_are_drawn_as_atoms_of_their_own()
    {
        Molecule molecule = Molecule.fromSmiles("CO");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, DepictionOptions.defaults()).depiction();

        assertThat(depiction.atoms()).hasSize(6);
        assertThat(depiction.atoms().stream().filter(AtomPlacement::isGeneratedHydrogen)).hasSize(4);
    }

    @Test
    void hydrogens_none_draws_only_heavy_atoms()
    {
        Molecule molecule = Molecule.fromSmiles("CO");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, DepictionOptions.defaults().withHydrogens(HydrogenStyle.NONE)).depiction();

        assertThat(depiction.atoms()).hasSize(2);
        assertThat(depiction.atoms()).noneMatch(AtomPlacement::isGeneratedHydrogen);
    }

    @Test
    void condensed_hydrogens_are_counted_on_their_atom()
    {
        Molecule molecule = Molecule.fromSmiles("CO");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, CONDENSED).depiction();

        assertThat(depiction.atoms()).extracting(AtomPlacement::hydrogens).containsExactly(3, 1);
    }

    @Test
    void skeletal_style_leaves_carbons_as_vertices_but_not_heteroatoms()
    {
        Molecule molecule = Molecule.fromSmiles("CCO");
        Structure3D structure = Embedder.embed(molecule);
        DepictionOptions skeletal = DepictionOptions.defaults().withStyle(DepictionStyle.SKELETAL);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, skeletal).depiction();

        assertThat(depiction.atoms()).extracting(AtomPlacement::vertex).containsExactly(true, true, false);
    }

    @Test
    void depth_bonds_are_plain_unless_asked_for()
    {
        Molecule molecule = Molecule.fromSmiles("CC(C)(C)C");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, DepictionOptions.defaults()).depiction();

        assertThat(depiction.bonds()).allMatch(bond -> bond.style() == BondStyle.PLAIN);
    }

    @Test
    void depth_bonds_wedge_and_hash_bonds_that_leave_the_page()
    {
        Molecule molecule = Molecule.fromSmiles("CC(C)(C)C");
        Structure3D structure = Embedder.embed(molecule);
        Camera camera = Camera.orbit(Vec3.ZERO, 20, 15, 30, 0);

        Depiction depiction = ProjectedLayout.fromCamera(molecule, structure, 0, camera, DepictionOptions.defaults().withDepthBonds(true)).depiction();

        assertThat(depiction.bonds()).extracting(BondPlacement::style).contains(BondStyle.WEDGE, BondStyle.HASH);
        for (BondPlacement bond : depiction.bonds().stream().filter(b -> b.style().isStereo()).toList())
        {
            int degreeOfNarrow = (int) depiction.bonds().stream().filter(b -> b.first() == bond.first() || b.second() == bond.first()).count();
            int degreeOfBroad = (int) depiction.bonds().stream().filter(b -> b.first() == bond.second() || b.second() == bond.second()).count();
            assertThat(degreeOfNarrow).isGreaterThanOrEqualTo(degreeOfBroad);
        }
    }

    @Test
    void bond_orders_are_carried_over()
    {
        Molecule molecule = Molecule.fromSmiles("C=O");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, CONDENSED).depiction();

        assertThat(depiction.bonds()).extracting(BondPlacement::order).containsExactly(BondOrder.DOUBLE);
    }

    @Test
    void charges_are_carried_over()
    {
        Molecule molecule = Molecule.fromSmiles("[NH4+]");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, CONDENSED).depiction();

        assertThat(depiction.atoms()).extracting(AtomPlacement::charge).containsExactly(1);
    }

    @Test
    void best_view_keeps_bonds_about_one_bond_long()
    {
        Molecule molecule = Molecule.fromSmiles("C1CC2CCC1C2");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, CONDENSED).depiction();

        for (BondPlacement bond : depiction.bonds())
        {
            double length = depiction.atom(bond.first()).position().minus(depiction.atom(bond.second()).position()).length();
            assertThat(length).isBetween(0.3, 3.0);
        }
    }

    @Test
    void forced_view_draws_the_view_the_camera_sees()
    {
        Molecule molecule = Molecule.fromSmiles("CCO");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.fromCamera(molecule, structure, 0, FRONT, CONDENSED).depiction();

        assertThat(depiction.atoms()).hasSize(3);
        assertThat(depiction.bonds()).hasSize(2);
        assertThat(depiction.atoms()).extracting(AtomPlacement::element).containsExactly("C", "C", "O");
    }

    @Test
    void forced_view_scales_the_median_bond_to_one()
    {
        Molecule molecule = Molecule.fromSmiles("CCCCO");
        Structure3D structure = Embedder.embed(molecule);
        Camera camera = Camera.orbit(Vec3.ZERO, 0.3, 0.4, 30, 0);

        Depiction depiction = ProjectedLayout.fromCamera(molecule, structure, 0, camera, CONDENSED).depiction();

        double[] lengths = depiction.bonds().stream()
            .mapToDouble(bond -> depiction.atom(bond.first()).position().minus(depiction.atom(bond.second()).position()).length())
            .sorted().toArray();
        assertThat(lengths[lengths.length / 2]).isCloseTo(1.0, offset(0.25));
    }

    @Test
    void different_cameras_give_different_forced_views()
    {
        Molecule molecule = Molecule.fromSmiles("CC(C)CO");
        Structure3D structure = Embedder.embed(molecule);

        Depiction front = ProjectedLayout.fromCamera(molecule, structure, 0, FRONT, CONDENSED).depiction();
        Depiction side = ProjectedLayout.fromCamera(molecule, structure, 0, Camera.orbit(Vec3.ZERO, 1.5, 0.7, 30, 0), CONDENSED).depiction();

        assertThat(front.atoms().stream().map(AtomPlacement::position).toList())
            .isNotEqualTo(side.atoms().stream().map(AtomPlacement::position).toList());
    }

    @Test
    void camera_view_keeps_disconnected_parts_where_they_are_seen()
    {
        Molecule molecule = Molecule.fromSmiles("CC.O");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.fromCamera(molecule, structure, 0, FRONT, CONDENSED).depiction();

        assertThat(depiction.fragments()).hasSize(2);
        assertThat(depiction.fragments()).anySatisfy(frame -> assertThat(frame.bounds().centre().length()).isGreaterThan(1e-6));
    }

    @Test
    void camera_view_leaves_out_a_part_out_of_view_without_moving_the_rest()
    {
        Molecule molecule = Molecule.fromSmiles("CC.O");
        Structure3D structure = Embedder.embed(molecule);

        Depiction whole = ProjectedLayout.fromCamera(molecule, structure, 0, FRONT, CONDENSED).depiction();
        Depiction inView = ProjectedLayout.fromCamera(molecule, structure, 0, FRONT, CONDENSED, Set.of(0, 1)).depiction();

        assertThat(inView.atoms()).extracting(AtomPlacement::element).containsExactly("C", "C");
        assertThat(inView.fragments()).hasSize(1);
        for (int atom = 0; atom < 2; atom++)
        {
            assertThat(inView.atom(atom).position().minus(whole.atom(atom).position()).length()).isLessThan(1e-9);
        }
    }

    @Test
    void disconnected_parts_are_each_centred()
    {
        Molecule molecule = Molecule.fromSmiles("CC.O");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, CONDENSED).depiction();

        assertThat(depiction.fragments()).hasSize(2);
        assertThat(depiction.fragments()).allSatisfy(frame -> assertThat(frame.bounds().centre().length()).isLessThan(1e-9));
    }

    @Test
    void only_the_parts_holding_the_chosen_atoms_are_drawn()
    {
        Molecule molecule = Molecule.fromSmiles("CC.O");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, CONDENSED, Set.of(2)).depiction();

        assertThat(depiction.atoms()).extracting(AtomPlacement::element).containsExactly("O");
        assertThat(depiction.fragments()).hasSize(1);
    }

    @Test
    void a_part_is_drawn_whole_if_any_of_its_atoms_is_chosen()
    {
        Molecule molecule = Molecule.fromSmiles("CC.O");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, CONDENSED, Set.of(1)).depiction();

        assertThat(depiction.atoms()).extracting(AtomPlacement::element).containsExactly("C", "C");
        assertThat(depiction.bonds()).hasSize(1);
    }

    @Test
    void left_out_parts_are_renumbered_so_fragments_stay_contiguous()
    {
        Molecule molecule = Molecule.fromSmiles("CC.O.N");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.fromCamera(molecule, structure, 0, FRONT, CONDENSED, Set.of(0, 3)).depiction();

        assertThat(depiction.atoms()).extracting(AtomPlacement::fragment).containsExactly(0, 0, 1);
    }

    @Test
    void rings_in_left_out_parts_are_not_drawn()
    {
        Molecule molecule = Molecule.fromSmiles("C1CCCCC1.O");
        Structure3D structure = Embedder.embed(molecule);

        Depiction depiction = ProjectedLayout.bestView(molecule, structure, 0, CONDENSED, Set.of(6)).depiction();

        assertThat(depiction.rings()).isEmpty();
    }

    @Test
    void choosing_no_atoms_is_rejected()
    {
        Molecule molecule = Molecule.fromSmiles("CC");
        Structure3D structure = Embedder.embed(molecule);

        assertThatThrownBy(() -> ProjectedLayout.bestView(molecule, structure, 0, CONDENSED, Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void choosing_an_atom_that_does_not_exist_is_rejected()
    {
        Molecule molecule = Molecule.fromSmiles("CC");
        Structure3D structure = Embedder.embed(molecule);

        assertThatThrownBy(() -> ProjectedLayout.bestView(molecule, structure, 0, CONDENSED, Set.of(5)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
