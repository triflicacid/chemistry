package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AtomPlacementTest
{
    private static AtomPlacement placing(Molecule molecule, Vec3... at)
    {
        Map<Atom, Vec3> positions = new IdentityHashMap<>();
        for (int i = 0; i < at.length; i++)
        {
            positions.put(molecule.atoms().get(i), at[i]);
        }
        Function<Atom, Vec3> positionOf = positions::get;
        return new AtomPlacement(molecule, positionOf);
    }

    @Test
    void should_miss_four_hydrogens_on_a_carbon_with_no_bond()
    {
        Molecule carbon = lone();

        assertThat(placing(carbon, new Vec3(0, 0, 0)).hydrogensMissing(carbon.atoms().getFirst())).isEqualTo(4);
    }

    @Test
    void should_miss_three_hydrogens_on_a_carbon_with_one_single_bond()
    {
        Molecule bare = bareChain(BondKind.SINGLE);

        assertThat(placing(bare, new Vec3(0, 0, 0), new Vec3(1.5, 0, 0)).hydrogensMissing(bare.atoms().getFirst())).isEqualTo(3);
    }

    @Test
    void should_miss_two_hydrogens_on_a_carbon_with_a_double_bond()
    {
        Molecule bare = bareChain(BondKind.DOUBLE);

        assertThat(placing(bare, new Vec3(0, 0, 0), new Vec3(1.3, 0, 0)).hydrogensMissing(bare.atoms().getFirst())).isEqualTo(2);
    }

    @Test
    void should_point_a_hydrogen_away_from_the_one_bond_of_an_atom_with_a_triple_bond()
    {
        Molecule bare = bareChain(BondKind.TRIPLE);
        AtomPlacement placement = placing(bare, new Vec3(0, 0, 0), new Vec3(1.2, 0, 0));

        Vec3 direction = placement.hydrogenDirection(bare.atoms().getFirst(), List.of(new Vec3(1, 0, 0)));

        assertThat(direction.x()).isCloseTo(-1, within(1e-9));
        assertThat(direction.y()).isCloseTo(0, within(1e-9));
    }

    @Test
    void should_put_a_new_atom_along_x_when_the_target_has_no_neighbours()
    {
        Molecule carbon = lone();
        AtomPlacement placement = placing(carbon, new Vec3(1, 2, 3));

        Vec3 where = placement.positionFor(carbon.atoms().getFirst(), "C");

        assertThat(where.minus(new Vec3(1, 2, 3)).length()).isCloseTo(AtomPlacement.bondLength("C", "C"), within(1e-9));
        assertThat(where.y()).isCloseTo(2, within(1e-9));
        assertThat(where.z()).isCloseTo(3, within(1e-9));
    }

    @Test
    void should_leave_a_tetrahedral_angle_from_a_single_arm()
    {
        Vec3 arm = new Vec3(1, 0, 0);

        Vec3 direction = AtomPlacement.freeDirection(List.of(arm));

        assertThat(direction.length()).isCloseTo(1, within(1e-9));
        assertThat(direction.dot(arm)).isCloseTo(-1.0 / 3, within(1e-9));
    }

    @Test
    void should_leave_the_third_place_of_two_wide_arms_in_their_plane()
    {
        Vec3 first = new Vec3(1, 0, 0);
        Vec3 second = new Vec3(Math.cos(Math.toRadians(120)), Math.sin(Math.toRadians(120)), 0);

        Vec3 direction = AtomPlacement.freeDirection(List.of(first, second));

        assertThat(direction.z()).isCloseTo(0, within(1e-9));
        assertThat(direction.dot(first)).isCloseTo(Math.cos(Math.toRadians(120)), within(1e-9));
    }

    @Test
    void should_leave_only_the_normal_to_three_arms_in_a_plane()
    {
        List<Vec3> arms = List.of(
                new Vec3(1, 0, 0),
                new Vec3(Math.cos(Math.toRadians(120)), Math.sin(Math.toRadians(120)), 0),
                new Vec3(Math.cos(Math.toRadians(240)), Math.sin(Math.toRadians(240)), 0));

        Vec3 direction = AtomPlacement.freeDirection(arms);

        assertThat(Math.abs(direction.z())).isCloseTo(1, within(1e-9));
    }

    @Test
    void should_choose_the_direction_furthest_from_a_crowd_of_arms()
    {
        List<Vec3> arms = List.of(new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, -1, 0));

        Vec3 direction = AtomPlacement.freeDirection(arms);

        assertThat(Math.abs(direction.z())).isGreaterThan(0.95);
    }

    /** One carbon with no bond and none of the hydrogens a SMILES would count. */
    private static Molecule lone()
    {
        Molecule.Builder builder = new Molecule.Builder();
        builder.addAtom(Atom.builder("C").hydrogenCount(0).build());
        return builder.build();
    }

    private enum BondKind
    {
        SINGLE("CC"), DOUBLE("C=C"), TRIPLE("C#C");

        private final String smiles;

        BondKind(String smiles)
        {
            this.smiles = smiles;
        }
    }

    /** Two carbons with the bond asked for and none of the hydrogens a SMILES would count. */
    private static Molecule bareChain(BondKind kind)
    {
        Molecule written = Molecule.fromSmiles(kind.smiles);
        Molecule.Builder builder = new Molecule.Builder();
        Atom first = Atom.builder("C").hydrogenCount(0).build();
        Atom second = Atom.builder("C").hydrogenCount(0).build();
        builder.addAtom(first);
        builder.addAtom(second);
        builder.addBond(first, second, written.bonds().getFirst().order());
        return builder.build();
    }
}
