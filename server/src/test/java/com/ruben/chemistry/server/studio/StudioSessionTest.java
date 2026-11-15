package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.space.Embedder;
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
        assertThat(session.pinned()).isEmpty();
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

    /**
     * @param session a session
     * @return what it calls each of its atoms, in the order it holds them
     */
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
