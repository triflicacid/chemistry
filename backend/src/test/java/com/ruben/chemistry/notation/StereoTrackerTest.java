package com.ruben.chemistry.notation;

import com.ruben.chemistry.core.BondDirection;
import com.ruben.chemistry.core.DoubleBondStereo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StereoTrackerTest
{
    /** One declaration received from the tracker. */
    private record Declaration(String first, String second, DoubleBondStereo stereo)
    {
    }

    private final List<Declaration> declared = new ArrayList<>();
    private final StereoTracker<String> tracker = new StereoTracker<>(
        (first, second, stereo) -> declared.add(new Declaration(first, second, stereo)));

    @Test
    void declares_nothing_until_both_sides_of_the_double_bond_have_a_direction()
    {
        // F/C=C F: the second substituent is written without a direction, so the configuration is never pinned down
        tracker.recordBond("F1", "C1", false);
        tracker.recordDirection("F1", "C1", BondDirection.SLASH);
        tracker.recordBond("C1", "C2", true);
        tracker.recordBond("C2", "F2", false);

        assertThat(declared).isEmpty();
    }

    @Test
    void declares_nothing_for_a_bond_that_is_not_a_double_bond()
    {
        tracker.recordBond("F1", "C1", false);
        tracker.recordDirection("F1", "C1", BondDirection.SLASH);
        tracker.recordBond("C1", "C2", false);
        tracker.recordBond("C2", "F2", false);
        tracker.recordDirection("C2", "F2", BondDirection.SLASH);

        assertThat(declared).isEmpty();
    }

    @Test
    void matching_directions_either_side_put_the_substituents_on_opposite_sides()
    {
        // F/C=C/F: both directions written the same way, which is trans
        tracker.recordBond("F1", "C1", false);
        tracker.recordDirection("F1", "C1", BondDirection.SLASH);
        tracker.recordBond("C1", "C2", true);
        tracker.recordBond("C2", "F2", false);
        tracker.recordDirection("C2", "F2", BondDirection.SLASH);

        assertThat(declared).containsExactly(new Declaration("C2", "C1", DoubleBondStereo.OPPOSITE_SIDE));
    }

    @Test
    void opposing_directions_either_side_put_the_substituents_on_the_same_side()
    {
        // F/C=C\F: the mirrored direction is cis
        tracker.recordBond("F1", "C1", false);
        tracker.recordDirection("F1", "C1", BondDirection.SLASH);
        tracker.recordBond("C1", "C2", true);
        tracker.recordBond("C2", "F2", false);
        tracker.recordDirection("C2", "F2", BondDirection.BACKSLASH);

        assertThat(declared).containsExactly(new Declaration("C2", "C1", DoubleBondStereo.SAME_SIDE));
    }

    @Test
    void a_direction_on_a_later_substituent_flips_the_declared_configuration()
    {
        // F/C=C(Cl)/F, where Cl is the earlier-written substituent of the second carbon and so
        // its default reference, while the direction was written on F instead
        tracker.recordBond("F1", "C1", false);
        tracker.recordDirection("F1", "C1", BondDirection.SLASH);
        tracker.recordBond("C1", "C2", true);
        tracker.recordBond("C2", "Cl", false);
        tracker.recordBond("C2", "F2", false);
        tracker.recordDirection("C2", "F2", BondDirection.SLASH);

        assertThat(declared).containsExactly(new Declaration("C2", "C1", DoubleBondStereo.SAME_SIDE));
    }

    @Test
    void a_ring_closure_direction_counts_from_its_own_atoms_occurrence()
    {
        // each occurrence of a ring-closure digit carries its own symbol, beside its own atom
        tracker.recordBond("C1", "C2", true);
        tracker.recordBond("C1", "X", false);
        tracker.recordBond("C2", "Y", false);
        tracker.recordRingClosureDirection("C1", "X", BondDirection.SLASH);
        tracker.recordRingClosureDirection("C2", "Y", BondDirection.BACKSLASH);

        assertThat(declared).containsExactly(new Declaration("C2", "C1", DoubleBondStereo.OPPOSITE_SIDE));
    }

    @Test
    void accepts_a_second_direction_at_one_atom_that_agrees_with_the_first()
    {
        // F/C(/Cl)=C/F: Cl sits on the opposite side of C1 from F, which is consistent
        tracker.recordBond("F1", "C1", false);
        tracker.recordDirection("F1", "C1", BondDirection.SLASH);
        tracker.recordBond("C1", "Cl", false);

        assertThat(tracker.recordDirection("C1", "Cl", BondDirection.SLASH)).isTrue();

        tracker.recordBond("C1", "C2", true);
        tracker.recordBond("C2", "F2", false);
        tracker.recordDirection("C2", "F2", BondDirection.SLASH);

        // the configuration the two fluorines alone would have given
        assertThat(declared).containsExactly(new Declaration("C2", "C1", DoubleBondStereo.OPPOSITE_SIDE));
    }

    @Test
    void rejects_a_second_direction_at_one_atom_that_contradicts_the_first()
    {
        // F/C(\Cl)=C/F: F and Cl would both sit on the same side of C1, which no atom can do
        tracker.recordBond("F1", "C1", false);
        tracker.recordDirection("F1", "C1", BondDirection.SLASH);
        tracker.recordBond("C1", "Cl", false);

        assertThat(tracker.recordDirection("C1", "Cl", BondDirection.BACKSLASH)).isFalse();

        // the rejected direction was not recorded, so the first one still decides
        tracker.recordBond("C1", "C2", true);
        tracker.recordBond("C2", "F2", false);
        tracker.recordDirection("C2", "F2", BondDirection.SLASH);

        assertThat(declared).containsExactly(new Declaration("C2", "C1", DoubleBondStereo.OPPOSITE_SIDE));
    }

    @Test
    void declares_nothing_for_an_atom_shared_by_two_double_bonds()
    {
        // F/C=C=C/F: an allene, whose chirality is axial rather than cis or trans
        tracker.recordBond("F1", "C1", false);
        tracker.recordDirection("F1", "C1", BondDirection.SLASH);
        tracker.recordBond("C1", "C2", true);
        tracker.recordBond("C2", "C3", true);
        tracker.recordBond("C3", "F2", false);
        tracker.recordDirection("C3", "F2", BondDirection.SLASH);

        assertThat(declared).isEmpty();
    }
}
