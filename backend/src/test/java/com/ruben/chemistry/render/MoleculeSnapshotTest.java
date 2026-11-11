package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoleculeSnapshotTest
{
    @Test
    void resolves_atoms_and_bonds_by_origin_index()
    {
        Molecule molecule = Molecule.fromSmiles("CC=O");
        MoleculeSnapshot snapshot = MoleculeSnapshot.of(molecule);

        assertThat(snapshot.getOrigin()).isSameAs(molecule);
        assertThat(snapshot.getAtom(2)).isSameAs(molecule.atoms().get(2));
        assertThat(snapshot.getBond(1)).isSameAs(molecule.bonds().get(1));
    }

    @Test
    void refuses_an_index_that_is_not_there()
    {
        MoleculeSnapshot snapshot = MoleculeSnapshot.of(Molecule.fromSmiles("CO"));

        assertThatThrownBy(() -> snapshot.getAtom(2)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> snapshot.getAtom(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> snapshot.getBond(1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> snapshot.getFragment(1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> snapshot.getFragmentIndexOf(5)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void finds_the_fragment_of_an_atom_and_of_a_bond()
    {
        Molecule molecule = Molecule.fromSmiles("CC.O.NN");
        MoleculeSnapshot snapshot = MoleculeSnapshot.of(molecule);

        assertThat(snapshot.getFragmentCount()).isEqualTo(3);
        assertThat(snapshot.getFragmentIndexOf(0)).isZero();
        assertThat(snapshot.getFragmentIndexOf(1)).isZero();
        assertThat(snapshot.getFragmentIndexOf(2)).isEqualTo(1);
        assertThat(snapshot.getFragmentIndexOf(4)).isEqualTo(2);
        assertThat(snapshot.getFragmentOf(4)).isSameAs(snapshot.getFragment(2));
        assertThat(snapshot.getFragmentOf(4).atoms()).contains(snapshot.getAtom(3), snapshot.getAtom(4));
        assertThat(snapshot.getFragmentIndexOfBond(1)).isEqualTo(2);
        assertThat(snapshot.getFragmentOfBond(0)).isSameAs(snapshot.getFragment(0));
    }

    @Test
    void has_the_origin_as_its_only_fragment_if_connected()
    {
        Molecule molecule = Molecule.fromSmiles("CCO");
        MoleculeSnapshot snapshot = MoleculeSnapshot.of(molecule);

        assertThat(snapshot.getFragmentCount()).isEqualTo(1);
        assertThat(snapshot.getFragmentOf(2)).isSameAs(molecule);
    }

    @Test
    void agrees_with_the_depiction_it_was_laid_out_with()
    {
        Layout layout = MoleculeLayout.layout(Molecule.fromSmiles("CC.O"));

        for (AtomPlacement atom : layout.depiction().atoms())
        {
            if (atom.sourceAtom().isPresent())
            {
                assertThat(atom.fragment()).isEqualTo(layout.snapshot().getFragmentIndexOf(atom.sourceAtom().getAsInt()));
            }
        }
        for (BondPlacement bond : layout.depiction().bonds())
        {
            if (bond.sourceBond().isPresent())
            {
                assertThat(layout.depiction().atom(bond.first()).fragment())
                        .isEqualTo(layout.snapshot().getFragmentIndexOfBond(bond.sourceBond().getAsInt()));
            }
        }
        assertThat(layout.depiction().atoms()).extracting(AtomPlacement::fragment).contains(0, 1);
    }
}
