package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BondMotionTest
{
    /** Pentane's carbons 0 to 4, with the middle bond 1-2 along x and the torsion 0-1-2-3 starting at zero (cis). */
    private static final Molecule CHAIN = Molecule.fromSmiles("CCCCC");

    private static final Vec3[] CHAIN_AT = {
        new Vec3(0, 1, 0), new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(1, 1, 0), new Vec3(1, 1, 1)
    };

    private static final Molecule RING = Molecule.fromSmiles("C1CCC1");

    private static final Vec3[] RING_AT = {
        new Vec3(0, 0, 0), new Vec3(1.5, 0, 0), new Vec3(1.5, 1.5, 0), new Vec3(0, 1.5, 0)
    };

    private static Function<Atom, Vec3> placed(Molecule molecule, Vec3[] at)
    {
        Map<Atom, Vec3> positions = new IdentityHashMap<>();
        for (int i = 0; i < at.length; i++)
        {
            positions.put(molecule.atoms().get(i), at[i]);
        }
        return positions::get;
    }

    private static Atom atom(Molecule molecule, int index)
    {
        return molecule.atoms().get(index);
    }

    private static Bond bond(Molecule molecule, int first, int second)
    {
        return molecule.bondBetween(atom(molecule, first), atom(molecule, second)).orElseThrow();
    }

    /** Where every atom is once the moved ones have gone where they were sent. */
    private static Function<Atom, Vec3> after(Function<Atom, Vec3> before, Map<Atom, Vec3> moved)
    {
        return atom -> moved.containsKey(atom) ? moved.get(atom) : before.apply(atom);
    }

    private static List<Integer> indices(Molecule molecule, Map<Atom, Vec3> moved)
    {
        return IntStream.range(0, molecule.atomCount()).filter(i -> moved.containsKey(molecule.atoms().get(i))).boxed().toList();
    }

    @Test
    void should_find_everything_beyond_a_bond_as_its_side_and_none_for_a_ring_bond()
    {
        Bond middle = bond(CHAIN, 1, 2);
        assertThat(BondMotion.sideOf(CHAIN, middle, atom(CHAIN, 2)).orElseThrow()).hasSize(3)
                .contains(atom(CHAIN, 2), atom(CHAIN, 3), atom(CHAIN, 4));
        assertThat(BondMotion.sideOf(CHAIN, middle, atom(CHAIN, 1)).orElseThrow()).hasSize(2);
        assertThat(BondMotion.sideOf(RING, bond(RING, 0, 1), atom(RING, 0))).isEmpty();
    }

    @Test
    void should_refuse_a_side_from_an_atom_that_is_not_on_the_bond()
    {
        assertThatThrownBy(() -> BondMotion.sideOf(CHAIN, bond(CHAIN, 1, 2), atom(CHAIN, 4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_carry_the_far_side_along_the_bond_when_the_first_atom_is_held()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Map<Atom, Vec3> moved = BondMotion.withLength(CHAIN, bond(CHAIN, 1, 2), positions, 1.5, atom(CHAIN, 1));

        assertThat(indices(CHAIN, moved)).containsExactly(2, 3, 4);
        assertThat(moved.get(atom(CHAIN, 2)).x()).isCloseTo(1.5, within(1e-9));
        assertThat(moved.get(atom(CHAIN, 3)).x()).isCloseTo(1.5, within(1e-9));
        assertThat(moved.get(atom(CHAIN, 3)).y()).isCloseTo(1, within(1e-9));
    }

    @Test
    void should_carry_the_near_side_the_other_way_when_the_second_atom_is_held()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Map<Atom, Vec3> moved = BondMotion.withLength(CHAIN, bond(CHAIN, 1, 2), positions, 0.5, atom(CHAIN, 2));

        assertThat(indices(CHAIN, moved)).containsExactly(0, 1);
        assertThat(moved.get(atom(CHAIN, 1)).x()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void should_move_both_sides_half_each_when_neither_atom_is_held()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Map<Atom, Vec3> moved = BondMotion.withLength(CHAIN, bond(CHAIN, 1, 2), positions, 2, null);

        assertThat(indices(CHAIN, moved)).containsExactly(0, 1, 2, 3, 4);
        assertThat(moved.get(atom(CHAIN, 1)).x()).isCloseTo(-0.5, within(1e-9));
        assertThat(moved.get(atom(CHAIN, 2)).x()).isCloseTo(1.5, within(1e-9));
    }

    @Test
    void should_change_no_other_length_when_lengthening_a_bond()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Function<Atom, Vec3> moved = after(positions, BondMotion.withLength(CHAIN, bond(CHAIN, 1, 2), positions, 1.7, atom(CHAIN, 1)));

        for (Bond bond : CHAIN.bonds())
        {
            double expected = bond == bond(CHAIN, 1, 2) ? 1.7 : 1;
            assertThat(moved.apply(bond.first()).distanceTo(moved.apply(bond.second()))).isCloseTo(expected, within(1e-9));
        }
    }

    @Test
    void should_move_only_the_atom_of_a_ring_bond_that_is_not_held()
    {
        Function<Atom, Vec3> positions = placed(RING, RING_AT);
        Map<Atom, Vec3> moved = BondMotion.withLength(RING, bond(RING, 0, 1), positions, 2, atom(RING, 0));

        assertThat(indices(RING, moved)).containsExactly(1);
        assertThat(moved.get(atom(RING, 1)).x()).isCloseTo(2, within(1e-9));
    }

    @Test
    void should_refuse_a_length_that_is_not_sensible()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Bond middle = bond(CHAIN, 1, 2);
        for (double length : new double[] {0, -1, 0.05, 10.5, Double.NaN, Double.POSITIVE_INFINITY})
        {
            assertThatThrownBy(() -> BondMotion.withLength(CHAIN, middle, positions, length, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void should_refuse_to_hold_an_atom_that_is_not_on_the_bond()
    {
        assertThatThrownBy(() -> BondMotion.withLength(CHAIN, bond(CHAIN, 1, 2), placed(CHAIN, CHAIN_AT), 1.5, atom(CHAIN, 4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_refuse_a_bond_whose_atoms_are_at_the_same_place()
    {
        Vec3[] squashed = CHAIN_AT.clone();
        squashed[2] = squashed[1];
        assertThatThrownBy(() -> BondMotion.withLength(CHAIN, bond(CHAIN, 1, 2), placed(CHAIN, squashed), 1.5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reach_the_torsion_asked_for()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        for (double target : new double[] {90, -60, 33, 180, 0})
        {
            Function<Atom, Vec3> turned = after(positions, BondMotion.withTorsion(
                    CHAIN, bond(CHAIN, 1, 2), atom(CHAIN, 0), atom(CHAIN, 3), positions, target, atom(CHAIN, 1)));

            assertThat(Math.abs(torsionOf(turned))).isCloseTo(Math.abs(target), within(1e-9));
        }
    }

    @Test
    void should_give_a_positive_torsion_when_the_far_atom_is_clockwise_seen_along_the_bond()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Function<Atom, Vec3> turned = after(positions, BondMotion.withTorsion(
                CHAIN, bond(CHAIN, 1, 2), atom(CHAIN, 0), atom(CHAIN, 3), positions, 90, atom(CHAIN, 1)));

        assertThat(torsionOf(turned)).isCloseTo(90, within(1e-9));
        assertThat(turned.apply(atom(CHAIN, 3)).z()).isGreaterThan(0.9);
    }

    @Test
    void should_take_the_far_side_along_when_turning_and_keep_every_length()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Map<Atom, Vec3> moved = BondMotion.withTorsion(CHAIN, bond(CHAIN, 1, 2), atom(CHAIN, 0), atom(CHAIN, 3), positions, 120, atom(CHAIN, 1));
        Function<Atom, Vec3> turned = after(positions, moved);

        assertThat(indices(CHAIN, moved)).containsExactly(2, 3, 4);
        for (Bond bond : CHAIN.bonds())
        {
            assertThat(turned.apply(bond.first()).distanceTo(turned.apply(bond.second()))).isCloseTo(1, within(1e-9));
        }
        assertThat(Vec3.angle(turned.apply(atom(CHAIN, 2)), turned.apply(atom(CHAIN, 3)), turned.apply(atom(CHAIN, 4))))
                .isCloseTo(Vec3.angle(CHAIN_AT[2], CHAIN_AT[3], CHAIN_AT[4]), within(1e-9));
    }

    @Test
    void should_turn_the_near_side_to_the_same_torsion_when_the_second_atom_is_held()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Map<Atom, Vec3> moved = BondMotion.withTorsion(CHAIN, bond(CHAIN, 1, 2), atom(CHAIN, 0), atom(CHAIN, 3), positions, 90, atom(CHAIN, 2));

        assertThat(indices(CHAIN, moved)).containsExactly(0, 1);
        assertThat(torsionOf(after(positions, moved))).isCloseTo(90, within(1e-9));
    }

    @Test
    void should_turn_both_sides_to_the_same_torsion_when_neither_atom_is_held()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Map<Atom, Vec3> moved = BondMotion.withTorsion(CHAIN, bond(CHAIN, 1, 2), atom(CHAIN, 0), atom(CHAIN, 3), positions, 90, null);

        assertThat(indices(CHAIN, moved)).containsExactly(0, 1, 2, 3, 4);
        assertThat(torsionOf(after(positions, moved))).isCloseTo(90, within(1e-9));
    }

    @Test
    void should_turn_the_short_way_round()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Function<Atom, Vec3> turned = after(positions, BondMotion.withTorsion(
                CHAIN, bond(CHAIN, 1, 2), atom(CHAIN, 0), atom(CHAIN, 3), positions, 350, atom(CHAIN, 1)));

        assertThat(torsionOf(turned)).isCloseTo(-10, within(1e-9));
    }

    @Test
    void should_refuse_to_turn_a_ring_bond()
    {
        assertThatThrownBy(() -> BondMotion.withTorsion(RING, bond(RING, 0, 1), atom(RING, 3), atom(RING, 2), placed(RING, RING_AT), 30, atom(RING, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ring");
    }

    @Test
    void should_refuse_atoms_that_are_not_bonded_one_to_each_end_of_the_bond()
    {
        Function<Atom, Vec3> positions = placed(CHAIN, CHAIN_AT);
        Bond middle = bond(CHAIN, 1, 2);

        // swapped ends, an atom of the bond itself, and an atom two bonds away
        assertThatThrownBy(() -> BondMotion.withTorsion(CHAIN, middle, atom(CHAIN, 3), atom(CHAIN, 0), positions, 90, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BondMotion.withTorsion(CHAIN, middle, atom(CHAIN, 2), atom(CHAIN, 3), positions, 90, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BondMotion.withTorsion(CHAIN, middle, atom(CHAIN, 0), atom(CHAIN, 4), positions, 90, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_refuse_an_angle_that_is_not_a_number()
    {
        assertThatThrownBy(() -> BondMotion.withTorsion(
                CHAIN, bond(CHAIN, 1, 2), atom(CHAIN, 0), atom(CHAIN, 3), placed(CHAIN, CHAIN_AT), Double.NaN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static double torsionOf(Function<Atom, Vec3> positions)
    {
        return Vec3.torsion(
                positions.apply(atom(CHAIN, 0)), positions.apply(atom(CHAIN, 1)),
                positions.apply(atom(CHAIN, 2)), positions.apply(atom(CHAIN, 3)));
    }
}
